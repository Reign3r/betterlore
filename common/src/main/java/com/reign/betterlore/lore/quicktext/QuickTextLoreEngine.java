package com.reign.betterlore.lore.quicktext;

import com.reign.betterlore.lore.LoreDocument;
import com.reign.betterlore.lore.ParseResult;

import java.util.ArrayList;
import java.util.List;

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

		return QuickTextParser.parse(sanitized.value());
	}

	/** Bounded, dependency-free translations of previously supported parser behavior. */
	public static List<String> migrationCandidates(String raw, boolean name) {
		QuickTextSanitizer.SanitizedInput sanitized = name
				? QuickTextSanitizer.sanitizeName(raw) : QuickTextSanitizer.sanitizeLore(raw);
		if (!sanitized.isSuccess() || sanitized.value().isEmpty()) return List.of();
		List<String> candidates = new ArrayList<>();
		for (boolean fabric : new boolean[] {false, true}) {
			String migrated = QuickTextParser.migrate(sanitized.value(), fabric);
			if (migrated != null && (name ? parseName(migrated) : parseLore(migrated)).isSuccess()) {
				candidates.add(migrated);
			}
		}
		return List.copyOf(candidates);
	}
}
