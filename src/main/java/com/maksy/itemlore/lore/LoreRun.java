package com.maksy.itemlore.lore;

import java.util.Objects;

public record LoreRun(String text, int rgb) {
	public LoreRun {
		text = Objects.requireNonNull(text, "text");
		rgb &= 0xFFFFFF;
	}
}
