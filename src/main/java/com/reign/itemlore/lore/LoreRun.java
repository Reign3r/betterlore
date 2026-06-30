package com.reign.itemlore.lore;

import java.util.Objects;

public record LoreRun(String text, int rgb, int flags) {
	public static final int BOLD = 1;
	public static final int ITALIC = 1 << 1;
	public static final int UNDERLINED = 1 << 2;
	public static final int STRIKETHROUGH = 1 << 3;
	public static final int OBFUSCATED = 1 << 4;
	public static final int ALL_FLAGS = BOLD | ITALIC | UNDERLINED | STRIKETHROUGH | OBFUSCATED;

	public LoreRun {
		text = Objects.requireNonNull(text, "text");
		rgb &= 0xFFFFFF;
		flags &= ALL_FLAGS;
	}

	public LoreRun(String text, int rgb) {
		this(text, rgb, 0);
	}

	public boolean bold() {
		return has(BOLD);
	}

	public boolean italic() {
		return has(ITALIC);
	}

	public boolean underlined() {
		return has(UNDERLINED);
	}

	public boolean strikethrough() {
		return has(STRIKETHROUGH);
	}

	public boolean obfuscated() {
		return has(OBFUSCATED);
	}

	private boolean has(int flag) {
		return (flags & flag) != 0;
	}
}
