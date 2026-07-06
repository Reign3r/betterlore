package com.reign.betterlore.lore.quicktext;

import com.reign.betterlore.lore.LoreMarkupParser;

public final class QuickTextSanitizer {
	private QuickTextSanitizer() {
	}

	public static SanitizedInput sanitizeLore(String raw) {
		return sanitize(raw, true, "Lore");
	}

	public static SanitizedInput sanitizeName(String raw) {
		return sanitize(raw, false, "Name");
	}

	private static SanitizedInput sanitize(String raw, boolean allowNewlines, String label) {
		if (raw == null || raw.isEmpty()) {
			return SanitizedInput.success("");
		}

		if (raw.length() > LoreMarkupParser.MAX_RAW_CHARS) {
			return SanitizedInput.error(label + " input is too long.");
		}

		String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
		StringBuilder out = new StringBuilder(normalized.length());

		for (int i = 0; i < normalized.length();) {
			char ch = normalized.charAt(i);
			if (Character.isHighSurrogate(ch)) {
				if (i + 1 >= normalized.length() || !Character.isLowSurrogate(normalized.charAt(i + 1))) {
					return SanitizedInput.error("Invalid Unicode input.");
				}
			} else if (Character.isLowSurrogate(ch)) {
				return SanitizedInput.error("Invalid Unicode input.");
			}

			int cp = normalized.codePointAt(i);
			i += Character.charCount(cp);

			if (cp == '\n') {
				out.append(allowNewlines ? '\n' : ' ');
				continue;
			}

			if (cp == '\t') {
				out.append("    ");
				continue;
			}

			if (cp == '\u00A7') {
				return SanitizedInput.error("Legacy formatting codes are not allowed.");
			}

			if (Character.isISOControl(cp)) {
				return SanitizedInput.error("Control characters are not allowed.");
			}

			out.appendCodePoint(cp);
		}

		String sanitized = out.toString();
		int tagCount = countQuickTextTags(sanitized);
		if (tagCount > LoreMarkupParser.MAX_COLOR_TAGS) {
			return SanitizedInput.error("Too many markup tags.");
		}

		return SanitizedInput.success(sanitized);
	}

	private static int countQuickTextTags(String input) {
		int count = 0;
		for (int i = 0; i < input.length(); i++) {
			if (input.charAt(i) != '<' || isEscaped(input, i)) {
				continue;
			}

			int end = findTagEnd(input, i + 1);
			if (end >= 0) {
				count++;
				i = end;
			}
		}
		return count;
	}

	private static int findTagEnd(String input, int start) {
		char quote = 0;
		for (int i = start; i < input.length(); i++) {
			char ch = input.charAt(i);
			if (quote != 0) {
				if (ch == quote && !isEscaped(input, i)) {
					quote = 0;
				}
				continue;
			}

			if (ch == '\n') {
				return -1;
			}

			if (ch == '\'' || ch == '"' || ch == '`') {
				quote = ch;
				continue;
			}

			if (ch == '>') {
				return i;
			}
		}
		return -1;
	}

	private static boolean isEscaped(String input, int index) {
		int backslashes = 0;
		for (int i = index - 1; i >= 0 && input.charAt(i) == '\\'; i--) {
			backslashes++;
		}
		return (backslashes & 1) == 1;
	}

	public record SanitizedInput(String value, String errorMessage) {
		public static SanitizedInput success(String value) {
			return new SanitizedInput(value, null);
		}

		public static SanitizedInput error(String message) {
			return new SanitizedInput("", message);
		}

		public boolean isSuccess() {
			return errorMessage == null;
		}
	}
}
