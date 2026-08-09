package com.reign.betterlore.internal.serverapi;

import com.reign.betterlore.access.AnvilLoreMenuBridge;
import com.reign.betterlore.api.server.AnvilEditorDraft;
import com.reign.betterlore.api.server.AnvilEditorDraftResult;
import com.reign.betterlore.api.server.AnvilEditorDraftValidation;
import com.reign.betterlore.api.server.AnvilEditorSession;
import com.reign.betterlore.api.server.BetterLoreServerApi;
import com.reign.betterlore.config.BetterLoreConfig;
import com.reign.betterlore.lore.LoreComponents;
import com.reign.betterlore.lore.LoreMarkupDecompiler;
import com.reign.betterlore.lore.LoreMarkupParser;
import com.reign.betterlore.net.AnvilLoreNetworking;
import com.reign.betterlore.net.ClientboundAnvilLoreStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Version-selectable implementation behind the stable server API facade. */
public final class BetterLoreServerApiImpl implements BetterLoreServerApi {
	public BetterLoreServerApiImpl() {
	}

	@Override
	public int apiVersion() {
		return API_VERSION;
	}

	@Override
	public boolean hasNativeEditor(ServerPlayer player) {
		return player != null && AnvilLoreNetworking.canSendState(player, CapabilityProbe.PAYLOAD);
	}

	@Override
	public Optional<AnvilEditorSession> currentAnvilSession(ServerPlayer player) {
		if (player == null || !(player.containerMenu instanceof AnvilMenu menu)
				|| !(menu instanceof AnvilLoreMenuBridge bridge)
				|| !menu.stillValid(player)) {
			return Optional.empty();
		}

		ItemStack left = menu.getSlot(0).getItem();
		if (left.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(new AnvilEditorSession(
				menu.containerId,
				bridge.betterLore$getSessionId(),
				LoreMarkupDecompiler.toSafeNameMarkup(left),
				LoreMarkupDecompiler.toSafeOwnedLoreMarkup(left),
				BetterLoreConfig.loreEditLevelCost()
		));
	}

	@Override
	public AnvilEditorDraftValidation validateDraft(AnvilEditorDraft draft) {
		return AnvilEditorDraftValidator.validate(draft);
	}

	@Override
	public AnvilEditorDraftResult submitDraft(ServerPlayer player, AnvilEditorDraft draft) {
		AnvilEditorDraftValidation validation = validateDraft(draft);
		if (player == null || !(player.containerMenu instanceof AnvilMenu menu)
				|| !(menu instanceof AnvilLoreMenuBridge bridge)
				|| !menu.stillValid(player)) {
			return AnvilEditorDraftSubmission.route(
					draft,
					validation,
					false,
					0,
					0,
					false,
					null
			);
		}

		ItemStack left = menu.getSlot(0).getItem();
		ItemStack currentOutput = menu.getSlot(2).getItem();
		ItemStack prospectiveOutput = currentOutput.isEmpty() ? left : currentOutput;
		if (validation.valid()
				&& draft.loreEdited()
				&& menu.containerId == draft.containerId()
				&& bridge.betterLore$getSessionId() == draft.sessionId()
				&& !left.isEmpty()
				&& !LoreComponents.canApplyTo(
						prospectiveOutput,
						LoreMarkupParser.parse(validation.normalizedLoreMarkup()).document()
				)) {
			return new AnvilEditorDraftResult(
					AnvilEditorDraftResult.Status.INVALID_DRAFT,
					"Combined lore exceeds " + LoreComponents.MAX_VISIBLE_LORE_LINES
							+ " visible lines."
			);
		}

		return AnvilEditorDraftSubmission.route(
				draft,
				validation,
				true,
				menu.containerId,
				bridge.betterLore$getSessionId(),
				!left.isEmpty(),
				bridge::betterLore$handleServerDraft
		);
	}

	private static final class CapabilityProbe {
		private static final ClientboundAnvilLoreStatePayload PAYLOAD =
				new ClientboundAnvilLoreStatePayload(0, 0, "", "", 0);
	}
}
