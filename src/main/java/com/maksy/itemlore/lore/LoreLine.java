package com.maksy.itemlore.lore;

import java.util.List;

public record LoreLine(List<LoreRun> runs) {
	public LoreLine {
		runs = List.copyOf(runs);
	}

	public boolean isEmpty() {
		return runs.stream().allMatch(run -> run.text().isEmpty());
	}
}
