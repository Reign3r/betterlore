package com.reign.betterlore.lore.quicktext;

import com.reign.betterlore.lore.LoreDocument;
import com.reign.betterlore.lore.ParseResult;
import eu.pb4.placeholders.api.ParserContext;
import eu.pb4.placeholders.api.parsers.NodeParser;
import net.minecraft.network.chat.Component;

public final class QuickTextLoreEngine {
	private static final NodeParser QUICK_TEXT = NodeParser.builder()
			.quickText()
			.requireSafe()
			.build();

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

		try {
			Component component = QUICK_TEXT.parseComponent(sanitized.value(), ParserContext.of());
			return ComponentLoreFlattener.flatten(component);
		} catch (LinkageError error) {
			return QuickTextFallbackParser.parse(sanitized.value());
		} catch (RuntimeException exception) {
			return ParseResult.error("Invalid QuickText markup.", 0);
		}
	}
}
