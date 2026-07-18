package com.reign.betterlore.mixin;

import com.reign.betterlore.access.AnvilLoreMenuBridge;
import com.reign.betterlore.anvil.AnvilEditPlanner;
import com.reign.betterlore.config.BetterLoreConfig;
import com.reign.betterlore.lore.LoreComponents;
import com.reign.betterlore.lore.LoreDocument;
import com.reign.betterlore.lore.LoreMarkupDecompiler;
import com.reign.betterlore.lore.LoreMarkupParser;
import com.reign.betterlore.lore.ParseResult;
import com.reign.betterlore.net.AnvilLoreNetworking;
import com.reign.betterlore.net.ClientboundAnvilLoreStatePayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin implements AnvilLoreMenuBridge {
	@Shadow
	@Final
	private DataSlot cost;

	@Shadow
	public abstract void createResult();

	@Unique
	private Player betterLore$menuPlayer;

	@Unique
	private int betterLore$sessionId = 0;

	@Unique
	private boolean betterLore$hasClientLoreEdit = false;

	@Unique
	private boolean betterLore$lastLoreParseValid = true;

	@Unique
	private String betterLore$rawLoreMarkup = "";

	@Unique
	private LoreDocument betterLore$loreDocument = LoreDocument.empty();

	@Unique
	private boolean betterLore$hasClientNameEdit = false;

	@Unique
	private boolean betterLore$lastNameParseValid = true;

	@Unique
	private String betterLore$rawNameMarkup = "";

	@Unique
	private LoreDocument betterLore$nameDocument = LoreDocument.empty();

	@Unique
	private ItemStack betterLore$lastLeftStack = ItemStack.EMPTY;

	@Unique
	private boolean betterLore$allowFreeResult = false;

	@Unique
	private int betterLore$loreEditLevelCost = BetterLoreConfig.DEFAULT_LORE_EDIT_LEVEL_COST;

	@Inject(
			method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
			at = @At("TAIL")
	)
	private void betterLore$capturePlayer(
			int containerId,
			Inventory inventory,
			ContainerLevelAccess access,
			CallbackInfo ci
	) {
		betterLore$menuPlayer = inventory.player;
		betterLore$loreEditLevelCost = BetterLoreConfig.loreEditLevelCost();
	}

	@Inject(method = "createResult", at = @At("HEAD"))
	private void betterLore$detectLeftStackChange(CallbackInfo ci) {
		betterLore$allowFreeResult = false;
		AnvilMenu menu = (AnvilMenu) (Object) this;
		ItemStack currentLeft = menu.getSlot(0).getItem();
		if (ItemStack.matches(currentLeft, betterLore$lastLeftStack)) {
			return;
		}

		betterLore$lastLeftStack = currentLeft.copy();
		betterLore$sessionId++;
		betterLore$hasClientLoreEdit = false;
		betterLore$lastLoreParseValid = true;
		betterLore$rawLoreMarkup = "";
		betterLore$loreDocument = LoreDocument.empty();
		betterLore$hasClientNameEdit = false;
		betterLore$lastNameParseValid = true;
		betterLore$rawNameMarkup = "";
		betterLore$nameDocument = LoreDocument.empty();

		if (betterLore$menuPlayer instanceof ServerPlayer serverPlayer) {
			ClientboundAnvilLoreStatePayload payload = new ClientboundAnvilLoreStatePayload(
					menu.containerId,
					betterLore$sessionId,
					LoreMarkupDecompiler.toSafeLoreMarkup(currentLeft),
					LoreMarkupDecompiler.toSafeNameMarkup(currentLeft),
					BetterLoreConfig.loreEditLevelCost()
			);

			if (AnvilLoreNetworking.canSendState(serverPlayer, payload)) {
				AnvilLoreNetworking.sendState(serverPlayer, payload);
			}
		}
	}

	@Inject(method = "createResult", at = @At("TAIL"))
	private void betterLore$applyTextResult(CallbackInfo ci) {
		if (!betterLore$hasClientLoreEdit && !betterLore$hasClientNameEdit) {
			return;
		}

		AnvilMenu menu = (AnvilMenu) (Object) this;
		ItemStack left = menu.getSlot(0).getItem();
		if (left.isEmpty()) {
			return;
		}

		ItemStack right = menu.getSlot(1).getItem();
		ItemStack vanillaOutput = menu.getSlot(2).getItem();
		boolean loreChanged = betterLore$hasClientLoreEdit && !LoreComponents.equivalentToExistingLore(
				left.get(DataComponents.LORE),
				betterLore$loreDocument
		);
		boolean nameChanged = betterLore$hasClientNameEdit && !LoreComponents.equivalentToExistingName(
				left.get(DataComponents.CUSTOM_NAME),
				betterLore$nameDocument
		);
		boolean vanillaAppliedRawName = betterLore$hasClientNameEdit
				&& betterLore$vanillaAlreadyAppliedRequestedName(vanillaOutput, betterLore$rawNameMarkup);

		AnvilEditPlanner.Plan plan = AnvilEditPlanner.plan(new AnvilEditPlanner.Input(
				betterLore$lastLoreParseValid && betterLore$lastNameParseValid,
				!right.isEmpty(),
				!vanillaOutput.isEmpty(),
				loreChanged,
				nameChanged,
				vanillaAppliedRawName,
				cost.get(),
				betterLore$loreEditLevelCost
		));

		switch (plan.outcome()) {
			case PASS_THROUGH -> {
				return;
			}
			case INVALID, CLEAR_OUTPUT -> {
				menu.getSlot(2).set(ItemStack.EMPTY);
				cost.set(0);
				menu.broadcastChanges();
				return;
			}
			case APPLY -> {
				ItemStack output = plan.copyLeftAsOutput() ? left.copy() : vanillaOutput.copy();
				if (plan.applyName()) {
					LoreComponents.applyNameTo(output, betterLore$rawNameMarkup, betterLore$nameDocument);
				}
				if (plan.applyLore()) {
					LoreComponents.applyTo(output, betterLore$rawLoreMarkup, betterLore$loreDocument);
				}
				cost.set(plan.finalCost());
				betterLore$allowFreeResult = plan.finalCost() == 0;
				menu.getSlot(2).set(output);
				menu.broadcastChanges();
			}
		}
	}

	@Override
	public void betterLore$setLoreEditLevelCost(int loreEditLevelCost) {
		betterLore$loreEditLevelCost = BetterLoreConfig.clampLoreEditLevelCost(loreEditLevelCost);
	}

	@Override
	public void betterLore$handleClientLoreUpdate(int sessionId, String rawLoreMarkup) {
		AnvilMenu menu = (AnvilMenu) (Object) this;
		if (sessionId != betterLore$sessionId || menu.getSlot(0).getItem().isEmpty()) {
			return;
		}

		String raw = rawLoreMarkup == null ? "" : rawLoreMarkup;
		ParseResult result = LoreMarkupParser.parse(raw);
		if (result.isSuccess()) {
			raw = LoreMarkupParser.toPreferredMarkup(raw);
		}
		betterLore$hasClientLoreEdit = true;
		betterLore$lastLoreParseValid = result.isSuccess();
		betterLore$rawLoreMarkup = raw;
		betterLore$loreDocument = result.isSuccess() ? result.document() : LoreDocument.empty();
		createResult();
	}

	@Override
	public void betterLore$handleClientNameUpdate(int sessionId, String rawNameMarkup) {
		AnvilMenu menu = (AnvilMenu) (Object) this;
		if (sessionId != betterLore$sessionId || menu.getSlot(0).getItem().isEmpty()) {
			return;
		}

		String raw = rawNameMarkup == null ? "" : rawNameMarkup;
		ParseResult result = LoreMarkupParser.parseName(raw);
		if (result.isSuccess()) {
			raw = LoreMarkupParser.toPreferredNameMarkup(raw);
		}
		betterLore$hasClientNameEdit = true;
		betterLore$lastNameParseValid = result.isSuccess();
		betterLore$rawNameMarkup = raw;
		betterLore$nameDocument = result.isSuccess() ? result.document() : LoreDocument.empty();
		createResult();
	}

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void betterLore$allowZeroCostLoreResult(Player player, boolean hasStack, CallbackInfoReturnable<Boolean> cir) {
		if (betterLore$allowFreeResult && hasStack) {
			cir.setReturnValue(true);
		}
	}

	@Unique
	private boolean betterLore$vanillaAlreadyAppliedRequestedName(ItemStack vanillaOutput, String rawNameMarkup) {
		if (vanillaOutput.isEmpty()) {
			return false;
		}

		Component customName = vanillaOutput.get(DataComponents.CUSTOM_NAME);
		if (rawNameMarkup == null || rawNameMarkup.isEmpty()) {
			return customName == null;
		}

		return customName != null && rawNameMarkup.equals(customName.getString());
	}
}
