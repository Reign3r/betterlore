package com.reign.betterlore.api.server;

import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Stable server integration contract for Better Lore.
 *
 * <p>Calls that inspect or mutate a menu must run on the logical server
 * thread. Integrations should check {@link #apiVersion()} before depending on
 * methods introduced by a later contract revision.</p>
 */
public interface BetterLoreServerApi {
	int API_VERSION = 1;

	int apiVersion();

	/** Returns whether this connection supports Better Lore's native client editor. */
	boolean hasNativeEditor(ServerPlayer player);

	/** Returns a safe snapshot when the player has a non-empty Better Lore anvil session. */
	Optional<AnvilEditorSession> currentAnvilSession(ServerPlayer player);

	/** Validates and canonicalizes both edited fields without inspecting game state. */
	AnvilEditorDraftValidation validateDraft(AnvilEditorDraft draft);

	/** Validates and submits a draft to the matching authoritative anvil session. */
	AnvilEditorDraftResult submitDraft(ServerPlayer player, AnvilEditorDraft draft);
}
