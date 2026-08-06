package com.reign.betterlore.compat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Keeps exact-build mixin configurations working while selecting only the
 * relocated mixins for the active compatibility family in a release adapter.
 */
public final class BetterLoreMixinPlugin implements IMixinConfigPlugin {
	private static final String JEI_PLUGIN = "com.reign.betterlore.compat.jei.BetterLoreJeiPlugin";
	private static final String JEI_UID_METHOD = "getPluginUid";
	private static final String RESOURCE_LOCATION_RETURN =
			"()Lnet/minecraft/resources/ResourceLocation;";
	private static final String IDENTIFIER_RETURN =
			"()Lnet/minecraft/resources/Identifier;";

	@Override
	public void onLoad(String mixinPackage) {
		CompatibilityRuntime.current();
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return CompatibilityRuntime.shouldApplyMixin(mixinClassName);
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
			IMixinInfo mixinInfo) {
		if (!JEI_PLUGIN.equals(targetClassName)) {
			return;
		}

		String unavailableDescriptor = classExists("net.minecraft.resources.Identifier")
				? RESOURCE_LOCATION_RETURN
				: IDENTIFIER_RETURN;
		targetClass.methods.removeIf(method -> JEI_UID_METHOD.equals(method.name)
				&& unavailableDescriptor.equals(method.desc));
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
			IMixinInfo mixinInfo) {
	}

	private static boolean classExists(String className) {
		try {
			Class.forName(className, false, BetterLoreMixinPlugin.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException | LinkageError ignored) {
			return false;
		}
	}
}
