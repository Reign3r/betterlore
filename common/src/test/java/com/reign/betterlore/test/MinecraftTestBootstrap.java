package com.reign.betterlore.test;

import org.junit.jupiter.api.Assumptions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

/** Initializes vanilla registries before a plain JUnit test touches component codecs. */
public final class MinecraftTestBootstrap {
	private static boolean bootstrapped;

	private MinecraftTestBootstrap() {
	}

	public static synchronized void ensureCodecRegistries() {
		Assumptions.assumeFalse(
				isPresent("net.minecraftforge.fml.ModLoader")
						|| isPresent("net.neoforged.fml.ModLoader"),
				"Forge-family component codecs require a launched mod-loader lifecycle; "
						+ "the shared codec round trip is covered by Fabric targets"
		);
		Assumptions.assumeFalse(
				isPresent("net.fabricmc.loader.api.FabricLoader")
						&& isPresent("net.minecraft.world.item.ItemStackTemplate")
						&& !isPresent("net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData"),
				"The early Fabric 26.1 line's plain-JUnit Minecraft jar references a "
						+ "lifecycle bridge that its matching Fabric API does not publish; later "
						+ "Fabric targets cover the shared codec and instance smoke covers this target"
		);
		if (bootstrapped) {
			return;
		}

		invokeIfPresent("net.minecraft.SharedConstants", "tryDetectVersion");
		invokeRequired("net.minecraft.server.Bootstrap", "bootStrap");
		bindDataDrivenItemComponentsIfPresent();
		bootstrapped = true;
	}

	/** 26.2 moved item defaults onto holder components populated during data loading. */
	private static void bindDataDrivenItemComponentsIfPresent() {
		try {
			Class<?> builtIns = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
			// Presence is the version boundary; running the real initializers requires
			// dynamic data-pack tags that a plain JUnit process intentionally lacks.
			builtIns.getField("DATA_COMPONENT_INITIALIZERS");
			Object itemRegistry = builtIns.getField("ITEM").get(null);
			Class<?> reference = Class.forName("net.minecraft.core.Holder$Reference");
			Class<?> componentMap = Class.forName("net.minecraft.core.component.DataComponentMap");
			Object emptyComponents = componentMap.getField("EMPTY").get(null);
			Method areComponentsBound = reference.getMethod("areComponentsBound");
			Method bindComponents = reference.getMethod("bindComponents", componentMap);
			List<?> itemHolders;
			try (Stream<?> holders = (Stream<?>) itemRegistry.getClass()
					.getMethod("listElements")
					.invoke(itemRegistry)) {
				itemHolders = holders.toList();
			}
			for (Object holder : itemHolders) {
				if (!(boolean) areComponentsBound.invoke(holder)) {
					bindComponents.invoke(holder, emptyComponents);
				}
			}
		} catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException ignored) {
			// Older versions initialize item components as part of built-in bootstrap.
		} catch (ReflectiveOperationException exception) {
			throw bootstrapFailure(
					"net.minecraft.core.registries.BuiltInRegistries",
					"DATA_COMPONENT_INITIALIZERS",
					exception
			);
		}
	}

	private static boolean isPresent(String className) {
		try {
			Class.forName(className, false, MinecraftTestBootstrap.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException ignored) {
			return false;
		}
	}

	private static void invokeIfPresent(String className, String methodName) {
		try {
			invoke(className, methodName);
		} catch (NoSuchMethodException ignored) {
			// A target that no longer needs this preparatory call can continue.
		} catch (ReflectiveOperationException exception) {
			throw bootstrapFailure(className, methodName, exception);
		}
	}

	private static void invokeRequired(String className, String methodName) {
		try {
			invoke(className, methodName);
		} catch (ReflectiveOperationException exception) {
			throw bootstrapFailure(className, methodName, exception);
		}
	}

	private static void invoke(String className, String methodName)
			throws ReflectiveOperationException {
		Class<?> owner = Class.forName(className);
		Method method = owner.getDeclaredMethod(methodName);
		method.setAccessible(true);
		try {
			method.invoke(null);
		} catch (InvocationTargetException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw exception;
		}
	}

	private static IllegalStateException bootstrapFailure(
			String className,
			String methodName,
			ReflectiveOperationException exception
	) {
		return new IllegalStateException(
				"Could not bootstrap Minecraft through " + className + "." + methodName,
				exception
		);
	}
}
