package com.maksy.itemlore.lore;

import java.util.List;

public record LoreDocument(List<LoreLine> lines, int visibleCodePoints) {
	public LoreDocument {
		lines = List.copyOf(lines);
	}

	public static LoreDocument empty() {
		return new LoreDocument(List.of(), 0);
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}
}
