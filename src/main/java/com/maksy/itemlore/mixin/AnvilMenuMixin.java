package com.maksy.itemlore.mixin;

import com.maksy.itemlore.access.AnvilLoreMenuBridge;
import com.maksy.itemlore.lore.LoreComponents;
import com.maksy.itemlore.lore.LoreDocument;
import com.maksy.itemlore.lore.LoreMarkupDecompiler;
import com.maksy.itemlore.lore.LoreMarkupParser;
import com.maksy.itemlore.lore.ParseResult;
import com.maksy.itemlore.net.ClientboundAnvilLoreStatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
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
	private boolean itemLore$hasClientEdit = false;

	@Unique
	private boolean itemLore$lastParseValid = true;

	@Unique
	private LoreDocument itemLore$document = LoreDocument.empty();

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
		itemLore$hasClientEdit = false;
		itemLore$lastParseValid = true;
		itemLore$document = LoreDocument.empty();

		if (player instanceof ServerPlayer serverPlayer && ServerPlayNetworking.canSend(serverPlayer, ClientboundAnvilLoreStatePayload.TYPE)) {
			ServerPlayNetworking.send(
					serverPlayer,
					new ClientboundAnvilLoreStatePayload(
							((AnvilMenu) (Object) this).containerId,
							itemLore$sessionId,
							LoreMarkupDecompiler.toSafeMarkup(currentLeft)
					)
			);
		}
	}

	@Inject(method = "createResult", at = @At("TAIL"))
	private void itemLore$applyLoreResult(CallbackInfo ci) {
		if (!itemLore$hasClientEdit) {
			return;
		}

		ItemStack left = inputSlots.getItem(0);
		if (left.isEmpty()) {
			return;
		}

		if (!itemLore$lastParseValid) {
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

		boolean loreChanged = !LoreComponents.equivalentToExistingLore(
				left.get(DataComponents.LORE),
				itemLore$document
		);

		if (!loreChanged) {
			return;
		}

		ItemStack output = vanillaOutput.isEmpty() ? left.copy() : vanillaOutput.copy();
		LoreComponents.applyTo(output, itemLore$document);

		int baseCost = Math.max(0, cost.get());
		cost.set(baseCost + 1);
		resultSlots.setItem(0, output);
		((AnvilMenu) (Object) this).broadcastChanges();
	}

	@Override
	public void itemLore$handleClientLoreUpdate(int sessionId, String rawLoreMarkup) {
		if (sessionId != itemLore$sessionId) {
			return;
		}

		ParseResult result = LoreMarkupParser.parse(rawLoreMarkup);
		itemLore$hasClientEdit = true;
		itemLore$lastParseValid = result.isSuccess();
		itemLore$document = result.isSuccess() ? result.document() : LoreDocument.empty();
		createResult();
	}
}
