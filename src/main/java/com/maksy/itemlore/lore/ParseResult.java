package com.maksy.itemlore.lore;

public record ParseResult(LoreDocument document, String errorMessage, int visibleCodePoints) {
	public ParseResult {
		document = document == null ? LoreDocument.empty() : document;
	}

	public static ParseResult success(LoreDocument document) {
		return new ParseResult(document, null, document.visibleCodePoints());
	}

	public static ParseResult error(String message, int visibleCodePoints) {
		return new ParseResult(LoreDocument.empty(), message, Math.max(0, visibleCodePoints));
	}

	public boolean isSuccess() {
		return errorMessage == null;
	}
}
