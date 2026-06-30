package com.reign.itemlore.lore;

import com.reign.itemlore.lore.quicktext.QuickTextLoreEngine;

import java.util.Locale;

public final class LoreMarkupParser {
	public static final int MAX_VISIBLE_CODEPOINTS = 255;
	public static final int MAX_RAW_CHARS = 4096;
	public static final int MAX_COLOR_TAGS = 64;
	public static final int MAX_LINES = 16;
	public static final int DEFAULT_COLOR = 0xAAAAAA;
	public static final int WRAP_VISIBLE_CHARS = 42;

	private LoreMarkupParser() {
	}

	public static ParseResult parse(String raw) {
		return QuickTextLoreEngine.parseLore(raw);
	}

	public static ParseResult parseName(String raw) {
		return QuickTextLoreEngine.parseName(raw);
	}

	public static String formatHex(int rgb) {
		return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
	}

	public static String toPreferredMarkup(String raw) {
		return QuickTextLoreEngine.toPreferredMarkup(raw);
	}

	public static String toPreferredNameMarkup(String raw) {
		return QuickTextLoreEngine.toPreferredNameMarkup(raw);
	}
}
