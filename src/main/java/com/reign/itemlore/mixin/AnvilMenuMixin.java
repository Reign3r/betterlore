package com.reign.itemlore.mixin;

import com.reign.itemlore.access.AnvilLoreMenuBridge;
import com.reign.itemlore.lore.LoreComponents;
import com.reign.itemlore.lore.LoreDocument;
import com.reign.itemlore.lore.LoreMarkupDecompiler;
import com.reign.itemlore.lore.LoreMarkupParser;
import com.reign.itemlore.lore.ParseResult;
import com.reign.itemlore.net.ClientboundAnvilLoreStatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
	private int itemLore$sessionId = 0;

	@Unique
	private boolean itemLore$hasClientLoreEdit = false;

	@Unique
	private boolean itemLore$lastLoreParseValid = true;

	@Unique
	private String itemLore$rawLoreMarkup = "";

	@Unique
	private LoreDocument itemLore$loreDocument = LoreDocument.empty();

	@Unique
	private boolean itemLore$hasClientNameEdit = false;

	@Unique
	private boolean itemLore$lastNameParseValid = true;

	@Unique
	private String itemLore$rawNameMarkup = "";

	@Unique
	private LoreDocument itemLore$nameDocument = LoreDocument.empty();

	@Unique
	private ItemStack itemLore$lastLeftStack = ItemStack.EMPTY;

	private AnvilMenuMixin(MenuType<?> menuType, int containerId, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slotDefinition) {
		super(menuType, containerId, inventory, access, slotDefinition);
	}

	@Inject(method = "createResult", at = @At("HEAD"))
	private void itemLore$detectLeftStackChange(CallbackInfo ci) {
		ItemStack currentLeft = inputSlots.getItem(0);
		if (ItemStack.matches(currentLeft, itemLore$lastLeftStack)) {
			return;
		}

		itemLore$lastLeftStack = currentLeft.copy();
		itemLore$sessionId++;
		itemLore$hasClientLoreEdit = false;
		itemLore$lastLoreParseValid = true;
		itemLore$rawLoreMarkup = "";
		itemLore$loreDocument = LoreDocument.empty();
		itemLore$hasClientNameEdit = false;
		itemLore$lastNameParseValid = true;
		itemLore$rawNameMarkup = "";
		itemLore$nameDocument = LoreDocument.empty();

		if (player instanceof ServerPlayer serverPlayer && ServerPlayNetworking.canSend(serverPlayer, ClientboundAnvilLoreStatePayload.TYPE)) {
			ServerPlayNetworking.send(
					serverPlayer,
					new ClientboundAnvilLoreStatePayload(
							((AnvilMenu) (Object) this).containerId,
							itemLore$sessionId,
							LoreMarkupDecompiler.toSafeLoreMarkup(currentLeft),
							LoreMarkupDecompiler.toSafeNameMarkup(currentLeft)
					)
			);
		}
	}

	@Inject(method = "createResult", at = @At("TAIL"))
	private void itemLore$applyTextResult(CallbackInfo ci) {
		if (!itemLore$hasClientLoreEdit && !itemLore$hasClientNameEdit) {
			return;
		}

		ItemStack left = inputSlots.getItem(0);
		if (left.isEmpty()) {
			return;
		}

		if (!itemLore$lastLoreParseValid || !itemLore$lastNameParseValid) {
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

		boolean loreChanged = itemLore$hasClientLoreEdit && !LoreComponents.equivalentToExistingLore(
				left.get(DataComponents.LORE),
				itemLore$loreDocument
		);
		boolean nameChanged = itemLore$hasClientNameEdit && !LoreComponents.equivalentToExistingName(
				left.get(DataComponents.CUSTOM_NAME),
				itemLore$nameDocument
		);
		boolean vanillaAppliedRawName = itemLore$hasClientNameEdit
				&& itemLore$vanillaAlreadyAppliedRequestedName(vanillaOutput, itemLore$rawNameMarkup);

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
			LoreComponents.applyNameTo(output, itemLore$rawNameMarkup, itemLore$nameDocument);
		}

		if (nameChanged) {
			LoreComponents.applyNameTo(output, itemLore$rawNameMarkup, itemLore$nameDocument);
			if (!vanillaAppliedRawName) {
				extraCost++;
			}
		}

		if (loreChanged) {
			LoreComponents.applyTo(output, itemLore$rawLoreMarkup, itemLore$loreDocument);
			extraCost++;
		}

		cost.set(baseCost + extraCost);
		resultSlots.setItem(0, output);
		((AnvilMenu) (Object) this).broadcastChanges();
	}

	@Override
	public void itemLore$handleClientLoreUpdate(int sessionId, String rawLoreMarkup) {
		if (sessionId != itemLore$sessionId) {
			return;
		}

		String raw = rawLoreMarkup == null ? "" : rawLoreMarkup;
		ParseResult result = LoreMarkupParser.parse(raw);
		if (result.isSuccess()) {
			raw = LoreMarkupParser.toPreferredMarkup(raw);
		}
		itemLore$hasClientLoreEdit = true;
		itemLore$lastLoreParseValid = result.isSuccess();
		itemLore$rawLoreMarkup = raw;
		itemLore$loreDocument = result.isSuccess() ? result.document() : LoreDocument.empty();
		createResult();
	}

	@Override
	public void itemLore$handleClientNameUpdate(int sessionId, String rawNameMarkup) {
		if (sessionId != itemLore$sessionId) {
			return;
		}

		String raw = rawNameMarkup == null ? "" : rawNameMarkup;
		ParseResult result = LoreMarkupParser.parseName(raw);
		if (result.isSuccess()) {
			raw = LoreMarkupParser.toPreferredNameMarkup(raw);
		}
		itemLore$hasClientNameEdit = true;
		itemLore$lastNameParseValid = result.isSuccess();
		itemLore$rawNameMarkup = raw;
		itemLore$nameDocument = result.isSuccess() ? result.document() : LoreDocument.empty();
		createResult();
	}

	@Unique
	private boolean itemLore$vanillaAlreadyAppliedRequestedName(ItemStack vanillaOutput, String rawNameMarkup) {
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
