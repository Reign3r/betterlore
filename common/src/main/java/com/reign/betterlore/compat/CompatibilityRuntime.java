package com.reign.betterlore.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

/**
 * Selects the implementation compiled for the running loader and Minecraft
 * binary family.
 *
 * <p>Release adapters relocate version-sensitive classes below
 * {@value #GENERATED_CLASS_PREFIX}. Exact-build development jars retain the
 * original class, which is also the fallback. Loader APIs are deliberately
 * accessed through reflection so this class has the same bytecode and linkage
 * requirements on Fabric, Forge, and NeoForge.</p>
 */
public final class CompatibilityRuntime {
	public static final String GENERATED_CLASS_PREFIX = "com.reign.betterlore.compat.generated.";
	public static final String GENERATED_MIXIN_PREFIX = "com.reign.betterlore.mixin.generated.";

	private static final String ROOT_PACKAGE = "com.reign.betterlore.";
	private static final String UNKNOWN = "unknown";
	private static final String EXACT = "exact";
	private static volatile Selection selection;
	private static volatile boolean selectionLogged;

	private CompatibilityRuntime() {
	}

	/**
	 * Initializes and caches runtime selection for a known loader entrypoint.
	 */
	public static Selection initialize(String loader) {
		String normalizedLoader = normalizeLoader(loader);
		Selection current = selection;
		if (isUsable(current, normalizedLoader)) {
			logOnce(current);
			return current;
		}

		synchronized (CompatibilityRuntime.class) {
			current = selection;
			if (!isUsable(current, normalizedLoader)) {
				current = detect(normalizedLoader);
				selection = current;
			}
		}
		logOnce(current);
		return current;
	}

	/**
	 * Returns the cached selection, detecting the active loader when mixins run
	 * before the normal mod entrypoint.
	 */
	public static Selection current() {
		Selection current = selection;
		if (current != null && !UNKNOWN.equals(current.loader())
				&& !UNKNOWN.equals(current.minecraftVersion())) {
			logOnce(current);
			return current;
		}

		synchronized (CompatibilityRuntime.class) {
			current = selection;
			if (current == null || UNKNOWN.equals(current.loader())
					|| UNKNOWN.equals(current.minecraftVersion())) {
				current = detect(detectLoader(runtimeClassLoader()));
				selection = current;
			}
		}
		if (!UNKNOWN.equals(current.loader()) && !UNKNOWN.equals(current.minecraftVersion())) {
			logOnce(current);
		}
		return current;
	}

	/**
	 * Resolves a relocated family implementation, falling back to the original
	 * exact-build class when running from a normal development jar.
	 */
	public static Class<?> resolveImplementationClass(String originalClassName) {
		if (originalClassName == null || !originalClassName.startsWith(ROOT_PACKAGE)) {
			throw new IllegalArgumentException("Better Lore implementation must be below " + ROOT_PACKAGE);
		}

		Selection current = current();
		ClassLoader primaryLoader = runtimeClassLoader();
		String relocatedClassName = relocatedClassName(current, originalClassName);
		if (relocatedClassName != null) {
			Class<?> relocated = tryLoad(relocatedClassName, primaryLoader);
			if (relocated != null) {
				return relocated;
			}
		}

		Class<?> original = tryLoad(originalClassName, primaryLoader);
		if (original != null) {
			return original;
		}

		String selected = current.loader() + " " + current.minecraftVersion()
				+ " (" + current.familyId() + ")";
		throw new IllegalStateException("No Better Lore implementation for " + selected
				+ ": tried " + (relocatedClassName == null ? "no relocated class" : relocatedClassName)
				+ " and " + originalClassName);
	}

	/**
	 * Instantiates a public no-argument implementation and validates its stable
	 * facade contract.
	 */
	public static <T> T instantiate(String originalClassName, Class<T> contract) {
		Class<? extends T> implementation;
		try {
			implementation = resolveImplementationClass(originalClassName).asSubclass(contract);
		} catch (ClassCastException exception) {
			throw new IllegalStateException(originalClassName + " does not implement " + contract.getName(), exception);
		}
		return contract.cast(instantiate(implementation, new Class<?>[0], new Object[0]));
	}

	/**
	 * Instantiates a public no-argument loader implementation.
	 */
	public static Object instantiate(String originalClassName) {
		return instantiate(resolveImplementationClass(originalClassName), new Class<?>[0], new Object[0]);
	}

	/**
	 * Instantiates a public loader implementation with an injected constructor.
	 */
	public static Object instantiate(String originalClassName, Class<?>[] parameterTypes, Object... arguments) {
		return instantiate(resolveImplementationClass(originalClassName), parameterTypes, arguments);
	}

	/**
	 * Applies ordinary exact-build mixins and only the generated mixins for the
	 * selected loader/family pair.
	 */
	public static boolean shouldApplyMixin(String mixinClassName) {
		if (!mixinClassName.startsWith(GENERATED_MIXIN_PREFIX)) {
			return true;
		}
		Selection current = current();
		String selectedPrefix = GENERATED_MIXIN_PREFIX + current.loader() + "." + current.familyId() + ".";
		return mixinClassName.startsWith(selectedPrefix);
	}

	/**
	 * Pure mapping used by release verification as well as runtime detection.
	 */
	public static String familyId(String loader, String minecraftVersion) {
		String normalizedLoader = normalizeLoader(loader);
		String normalizedVersion = normalizeMinecraftVersion(minecraftVersion);
		return switch (normalizedLoader) {
			case "fabric" -> fabricFamily(normalizedVersion);
			case "forge" -> forgeFamily(normalizedVersion);
			case "neoforge" -> neoForgeFamily(normalizedVersion);
			default -> EXACT;
		};
	}

	public record Selection(String loader, String minecraftVersion, String familyId) {
	}

	private static Selection detect(String expectedLoader) {
		ClassLoader loader = runtimeClassLoader();
		String detectedLoader = UNKNOWN.equals(expectedLoader) ? detectLoader(loader) : expectedLoader;
		String minecraftVersion = detectMinecraftVersion(detectedLoader, loader);
		return new Selection(detectedLoader, minecraftVersion, familyId(detectedLoader, minecraftVersion));
	}

	private static String detectLoader(ClassLoader loader) {
		if (classExists("net.neoforged.fml.loading.FMLLoader", loader)) {
			return "neoforge";
		}
		if (classExists("net.minecraftforge.fml.loading.FMLLoader", loader)) {
			return "forge";
		}
		if (classExists("net.fabricmc.loader.api.FabricLoader", loader)) {
			return "fabric";
		}
		return UNKNOWN;
	}

	private static String detectMinecraftVersion(String loader, ClassLoader classLoader) {
		String detected = switch (loader) {
			case "fabric" -> detectFabricMinecraftVersion(classLoader);
			case "forge" -> detectForgeMinecraftVersion(classLoader);
			case "neoforge" -> detectNeoForgeMinecraftVersion(classLoader);
			default -> null;
		};
		if (detected == null || detected.isBlank()) {
			detected = firstSystemProperty("betterlore.minecraftVersion", "fml.mcVersion", "minecraft.version");
		}
		return normalizeMinecraftVersion(detected);
	}

	private static String detectFabricMinecraftVersion(ClassLoader loader) {
		try {
			Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader", false, loader);
			Object instance = fabricLoader.getMethod("getInstance").invoke(null);
			Object optionalContainer = fabricLoader.getMethod("getModContainer", String.class)
					.invoke(instance, "minecraft");
			if (!(optionalContainer instanceof Optional<?> optional) || optional.isEmpty()) {
				return null;
			}
			Class<?> modContainer = Class.forName("net.fabricmc.loader.api.ModContainer", false, loader);
			Object metadata = modContainer.getMethod("getMetadata").invoke(optional.get());
			Class<?> modMetadata = Class.forName("net.fabricmc.loader.api.metadata.ModMetadata", false, loader);
			Object version = modMetadata.getMethod("getVersion").invoke(metadata);
			Class<?> semanticVersion = Class.forName("net.fabricmc.loader.api.Version", false, loader);
			return String.valueOf(semanticVersion.getMethod("getFriendlyString").invoke(version));
		} catch (ReflectiveOperationException | LinkageError exception) {
			return null;
		}
	}

	private static String detectForgeMinecraftVersion(ClassLoader loader) {
		try {
			Class<?> fmlLoader = Class.forName("net.minecraftforge.fml.loading.FMLLoader", false, loader);
			Object versionInfo = fmlLoader.getMethod("versionInfo").invoke(null);
			return minecraftVersionFromInfo(versionInfo);
		} catch (ReflectiveOperationException | LinkageError exception) {
			return null;
		}
	}

	private static String detectNeoForgeMinecraftVersion(ClassLoader loader) {
		try {
			Class<?> fmlLoader = Class.forName("net.neoforged.fml.loading.FMLLoader", false, loader);
			Object versionInfo = invokeStaticIfPresent(fmlLoader, "versionInfo");
			if (versionInfo == null) {
				Object currentLoader = fmlLoader.getMethod("getCurrent").invoke(null);
				versionInfo = currentLoader.getClass().getMethod("getVersionInfo").invoke(currentLoader);
			}
			return minecraftVersionFromInfo(versionInfo);
		} catch (ReflectiveOperationException | LinkageError exception) {
			return null;
		}
	}

	private static Object invokeStaticIfPresent(Class<?> owner, String methodName)
			throws ReflectiveOperationException {
		try {
			Method method = owner.getMethod(methodName);
			return method.invoke(null);
		} catch (NoSuchMethodException exception) {
			return null;
		}
	}

	private static String minecraftVersionFromInfo(Object versionInfo) throws ReflectiveOperationException {
		if (versionInfo == null) {
			return null;
		}
		return String.valueOf(versionInfo.getClass().getMethod("mcVersion").invoke(versionInfo));
	}

	private static Object instantiate(Class<?> implementation, Class<?>[] parameterTypes, Object[] arguments) {
		try {
			return implementation.getConstructor(parameterTypes).newInstance(arguments);
		} catch (InvocationTargetException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException("Better Lore implementation constructor failed: "
					+ implementation.getName(), cause);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Cannot construct Better Lore implementation "
					+ implementation.getName(), exception);
		}
	}

	private static String relocatedClassName(Selection current, String originalClassName) {
		if (UNKNOWN.equals(current.loader()) || UNKNOWN.equals(current.minecraftVersion())
				|| EXACT.equals(current.familyId())) {
			return null;
		}
		return GENERATED_CLASS_PREFIX + current.loader() + "." + current.familyId() + "."
				+ originalClassName.substring(ROOT_PACKAGE.length());
	}

	private static Class<?> tryLoad(String className, ClassLoader primaryLoader) {
		try {
			return Class.forName(className, false, primaryLoader);
		} catch (ClassNotFoundException ignored) {
			ClassLoader fallbackLoader = CompatibilityRuntime.class.getClassLoader();
			if (fallbackLoader == primaryLoader) {
				return null;
			}
			try {
				return Class.forName(className, false, fallbackLoader);
			} catch (ClassNotFoundException alsoIgnored) {
				return null;
			}
		}
	}

	private static boolean classExists(String className, ClassLoader loader) {
		try {
			Class.forName(className, false, loader);
			return true;
		} catch (ClassNotFoundException | LinkageError ignored) {
			return false;
		}
	}

	private static ClassLoader runtimeClassLoader() {
		ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
		return contextLoader == null ? CompatibilityRuntime.class.getClassLoader() : contextLoader;
	}

	private static boolean isUsable(Selection current, String expectedLoader) {
		return current != null && expectedLoader.equals(current.loader())
				&& !UNKNOWN.equals(current.minecraftVersion());
	}

	private static String normalizeLoader(String loader) {
		if (loader == null) {
			return UNKNOWN;
		}
		String normalized = loader.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "fabric", "forge", "neoforge" -> normalized;
			case "neo", "neo-forge" -> "neoforge";
			default -> UNKNOWN;
		};
	}

	private static String normalizeMinecraftVersion(String version) {
		if (version == null) {
			return UNKNOWN;
		}
		String normalized = version.trim();
		if (normalized.equals("1.21.0")) {
			return "1.21";
		}
		if (normalized.equals("26.1.0")) {
			return "26.1";
		}
		return normalized.isEmpty() ? UNKNOWN : normalized;
	}

	private static String firstSystemProperty(String... names) {
		for (String name : names) {
			try {
				String value = System.getProperty(name);
				if (value != null && !value.isBlank()) {
					return value;
				}
			} catch (SecurityException ignored) {
				// Continue to the next loader-neutral fallback.
			}
		}
		return null;
	}

	private static String fabricFamily(String version) {
		return switch (version) {
			case "1.20.5" -> "mc1_20_5";
			case "1.20.6" -> "mc1_20_6";
			case "1.21", "1.21.1" -> "mc1_21_to_1_21_1";
			case "1.21.2", "1.21.3" -> "mc1_21_2_to_1_21_3";
			case "1.21.4" -> "mc1_21_4";
			case "1.21.5" -> "mc1_21_5";
			case "1.21.6" -> "mc1_21_6";
			case "1.21.7", "1.21.8" -> "mc1_21_7_to_1_21_8";
			case "1.21.9", "1.21.10" -> "mc1_21_9_to_1_21_10";
			case "1.21.11" -> "mc1_21_11";
			case "26.1", "26.1.1", "26.1.2" -> "mc26_1_to_26_1_2";
			case "26.2" -> "mc26_2";
			default -> EXACT;
		};
	}

	private static String forgeFamily(String version) {
		return switch (version) {
			case "1.20.6" -> "mc1_20_6";
			case "1.21", "1.21.1" -> "mc1_21_to_1_21_1";
			case "1.21.3" -> "mc1_21_3";
			case "1.21.4" -> "mc1_21_4";
			case "1.21.5" -> "mc1_21_5";
			case "1.21.6", "1.21.7", "1.21.8" -> "mc1_21_6_to_1_21_8";
			case "1.21.9", "1.21.10" -> "mc1_21_9_to_1_21_10";
			case "1.21.11" -> "mc1_21_11";
			case "26.1", "26.1.1", "26.1.2" -> "mc26_1_to_26_1_2";
			case "26.2" -> "mc26_2";
			default -> EXACT;
		};
	}

	private static String neoForgeFamily(String version) {
		return switch (version) {
			case "1.20.5" -> "mc1_20_5";
			case "1.20.6" -> "mc1_20_6";
			case "1.21", "1.21.1" -> "mc1_21_to_1_21_1";
			case "1.21.2", "1.21.3" -> "mc1_21_2_to_1_21_3";
			case "1.21.4" -> "mc1_21_4";
			case "1.21.5" -> "mc1_21_5";
			case "1.21.6" -> "mc1_21_6";
			case "1.21.7" -> "mc1_21_7";
			case "1.21.8" -> "mc1_21_8";
			case "1.21.9", "1.21.10" -> "mc1_21_9_to_1_21_10";
			case "1.21.11" -> "mc1_21_11";
			case "26.1", "26.1.1", "26.1.2" -> "mc26_1_to_26_1_2";
			case "26.2" -> "mc26_2";
			default -> EXACT;
		};
	}

	private static void logOnce(Selection current) {
		if (selectionLogged) {
			return;
		}
		synchronized (CompatibilityRuntime.class) {
			if (selectionLogged) {
				return;
			}
			System.getLogger("better_lore").log(System.Logger.Level.INFO,
					"Better Lore compatibility adapter: " + current.loader() + " "
							+ current.minecraftVersion() + " -> " + current.familyId());
			selectionLogged = true;
		}
	}
}
