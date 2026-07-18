package com.reign.betterlore.lore.quicktext;

import com.reign.betterlore.lore.LoreDocument;
import com.reign.betterlore.lore.ParseResult;

public final class QuickTextLoreEngine {
	private QuickTextLoreEngine() {
	}

	public static ParseResult parseLore(String raw) {
		QuickTextSanitizer.SanitizedInput sanitized = QuickTextSanitizer.sanitizeLore(raw);
		return parseSanitized(sanitized);
	}

	public static ParseResult parseName(String raw) {
		QuickTextSanitizer.SanitizedInput sanitized = QuickTextSanitizer.sanitizeName(raw);
		return parseSanitized(sanitized);
	}

	public static String toPreferredMarkup(String raw) {
		QuickTextSanitizer.SanitizedInput sanitized = QuickTextSanitizer.sanitizeLore(raw);
		return sanitized.isSuccess() ? sanitized.value() : raw == null ? "" : raw;
	}

	public static String toPreferredNameMarkup(String raw) {
		QuickTextSanitizer.SanitizedInput sanitized = QuickTextSanitizer.sanitizeName(raw);
		return sanitized.isSuccess() ? sanitized.value() : raw == null ? "" : raw;
	}

	private static ParseResult parseSanitized(QuickTextSanitizer.SanitizedInput sanitized) {
		if (!sanitized.isSuccess()) {
			return ParseResult.error(sanitized.errorMessage(), 0);
		}

		if (sanitized.value().isEmpty()) {
			return ParseResult.success(LoreDocument.empty());
		}

		QuickTextParserAdapter adapter = QuickTextParserAdapters.adapter();
		if (adapter != null) {
			try {
				ParseResult result = adapter.parse(sanitized.value());
				if (result.isSuccess()) {
					return result;
				}
			} catch (RuntimeException | LinkageError ignored) {
				// A loader-specific adapter can be present on the classpath without
				// its runtime launcher being active, such as Fabric APIs in plain
				// JUnit or future non-Fabric loaders. Fall back to the common parser.
			}
		}

		return QuickTextFallbackParser.parse(sanitized.value());
	}
}
