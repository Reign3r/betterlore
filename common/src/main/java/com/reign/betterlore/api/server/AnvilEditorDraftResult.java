package com.reign.betterlore.api.server;

import java.util.Objects;

/** Result of attempting to submit an anvil draft to the authoritative menu. */
public record AnvilEditorDraftResult(Status status, String message) {
	public AnvilEditorDraftResult {
		status = Objects.requireNonNull(status, "status");
		message = message == null ? "" : message;
	}

	public boolean applied() {
		return status == Status.APPLIED;
	}

	public enum Status {
		APPLIED,
		NO_CHANGES,
		INVALID_DRAFT,
		NO_ACTIVE_ANVIL,
		WRONG_CONTAINER,
		STALE_SESSION,
		EMPTY_INPUT
	}
}
