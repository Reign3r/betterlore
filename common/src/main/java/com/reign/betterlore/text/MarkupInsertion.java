package com.reign.betterlore.text;

import java.util.Objects;

/** Loader-neutral cursor/selection wrapping used by the name and lore editors. */
public final class MarkupInsertion {
	private MarkupInsertion() {
	}

	public static Result wrap(
			String value,
			int cursor,
			int anchor,
			String openingTag,
			String closingTag,
			int maximumLength
	) {
		Objects.requireNonNull(openingTag, "openingTag");
		Objects.requireNonNull(closingTag, "closingTag");
		String source = value == null ? "" : value;
		int safeCursor = clamp(cursor, 0, source.length());
		int safeAnchor = clamp(anchor, 0, source.length());
		int start = Math.min(safeCursor, safeAnchor);
		int end = Math.max(safeCursor, safeAnchor);

		long resultingLength = (long) source.length() + openingTag.length() + closingTag.length();
		if (resultingLength > Math.max(0, maximumLength)) {
			return new Result(source, safeCursor, safeAnchor, false);
		}

		String selected = source.substring(start, end);
		String result = source.substring(0, start)
				+ openingTag
				+ selected
				+ closingTag
				+ source.substring(end);

		if (start == end) {
			int insertionCursor = start + openingTag.length();
			return new Result(result, insertionCursor, insertionCursor, true);
		}

		int selectionStart = start + openingTag.length();
		int selectionEnd = selectionStart + selected.length();
		return new Result(result, selectionEnd, selectionStart, true);
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	/** Cursor is the moving end of a selection; anchor is its fixed end. */
	public record Result(String text, int cursor, int anchor, boolean changed) {
	}
}
