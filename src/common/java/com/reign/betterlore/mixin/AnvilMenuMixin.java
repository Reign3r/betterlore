package com.reign.betterlore.mixin;

import com.reign.betterlore.access.AnvilLoreMenuBridge;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu implements AnvilLoreMenuBridge {
	@Shadow
	@Final
	private DataSlot cost;

	@Shadow
	public abstract void createResult();

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

	private AnvilMenuMixin(MenuType<?> menuType, int containerId, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slotDefinition) {
		super(menuType, containerId, inventory, access, slotDefinition);
	}

	@Inject(method = "createResult", at = @At("HEAD"))
	private void betterLore$detectLeftStackChange(CallbackInfo ci) {
		ItemStack currentLeft = inputSlots.getItem(0);
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

		if (player instanceof ServerPlayer serverPlayer) {
			ClientboundAnvilLoreStatePayload payload = new ClientboundAnvilLoreStatePayload(
					((AnvilMenu) (Object) this).containerId,
					betterLore$sessionId,
					LoreMarkupDecompiler.toSafeLoreMarkup(currentLeft),
					LoreMarkupDecompiler.toSafeNameMarkup(currentLeft)
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

		ItemStack left = inputSlots.getItem(0);
		if (left.isEmpty()) {
			return;
		}

		if (!betterLore$lastLoreParseValid || !betterLore$lastNameParseValid) {
			resultSlots.setItem(0, ItemStack.EMPTY);
			cost.set(0);
			((AnvilMenu) (Object) this).broadcastChanges();
			return;
		}

		ItemStack right = inputSlots.getItem(1);
		ItemStack vanillaOutput = resultSlots.getItem(0);

		if (vanillaOutput.isEmpty() && !right.isEmpty()) {
			return;
		}

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

		if (!loreChanged && !nameChanged && !vanillaAppliedRawName) {
			return;
		}

		if (!loreChanged && !nameChanged && vanillaAppliedRawName && right.isEmpty()) {
			resultSlots.setItem(0, ItemStack.EMPTY);
			cost.set(0);
			((AnvilMenu) (Object) this).broadcastChanges();
			return;
		}

		ItemStack output = vanillaOutput.isEmpty() ? left.copy() : vanillaOutput.copy();
		int baseCost = Math.max(0, cost.get());
		int extraCost = 0;

		if (vanillaAppliedRawName && !nameChanged) {
			baseCost = Math.max(0, baseCost - 1);
			LoreComponents.applyNameTo(output, betterLore$rawNameMarkup, betterLore$nameDocument);
		}

		if (nameChanged) {
			LoreComponents.applyNameTo(output, betterLore$rawNameMarkup, betterLore$nameDocument);
			if (!vanillaAppliedRawName) {
				extraCost++;
			}
		}

		if (loreChanged) {
			LoreComponents.applyTo(output, betterLore$rawLoreMarkup, betterLore$loreDocument);
			extraCost++;
		}

		cost.set(baseCost + extraCost);
		resultSlots.setItem(0, output);
		((AnvilMenu) (Object) this).broadcastChanges();
	}

	@Override
	public void betterLore$handleClientLoreUpdate(int sessionId, String rawLoreMarkup) {
		if (sessionId != betterLore$sessionId) {
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
		if (sessionId != betterLore$sessionId) {
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
