package com.reign.betterlore.lore;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Identifies Better Lore lines without changing their visible text. */
public final class LoreOwnership {
	private static final String OWNED_LINE_MARKER = "better_lore:owned_lore_line";
	private static final String SEPARATOR_MARKER = "better_lore:owned_lore_separator";

	private LoreOwnership() {
	}

	/**
	 * Removes only proven Better Lore lines, then appends the replacement at the
	 * bottom. Unmarked components are retained exactly and in their original order.
	 */
	public static List<Component> compose(
			List<Component> visible,
			List<Component> legacyOwned,
			List<Component> replacementOwned
	) {
		Objects.requireNonNull(visible, "visible");
		Objects.requireNonNull(legacyOwned, "legacyOwned");
		Objects.requireNonNull(replacementOwned, "replacementOwned");

		Partition partition = partition(visible, legacyOwned);
		List<Component> combined = new ArrayList<>(
				partition.foreign().size() + replacementOwned.size() + 1
		);
		combined.addAll(partition.foreign());
		if (!combined.isEmpty()
				&& !replacementOwned.isEmpty()
				&& !combined.get(combined.size() - 1).getString().isEmpty()) {
			combined.add(markSeparator());
		}
		for (Component line : replacementOwned) {
			combined.add(markOwned(line));
		}
		return List.copyOf(combined);
	}

	/** Returns owned lines with their invisible ownership sentinel removed. */
	public static List<Component> ownedLines(List<Component> visible, List<Component> legacyOwned) {
		return partition(visible, legacyOwned).owned();
	}

	/** Returns every unowned line unchanged. */
	public static List<Component> foreignLines(List<Component> visible, List<Component> legacyOwned) {
		return partition(visible, legacyOwned).foreign();
	}

	public static boolean hasInlineOwnership(List<Component> visible) {
		Objects.requireNonNull(visible, "visible");
		return visible.stream().anyMatch(line -> isOwnedLine(line) || isSeparator(line));
	}

	public static boolean isOwnedLine(Component line) {
		return line != null && OWNED_LINE_MARKER.equals(line.getStyle().getInsertion());
	}

	public static boolean isSeparator(Component line) {
		return line != null && SEPARATOR_MARKER.equals(line.getStyle().getInsertion());
	}

	private static Partition partition(List<Component> visible, List<Component> legacyOwned) {
		List<Component> foreign = new ArrayList<>(visible.size());
		List<Component> owned = new ArrayList<>();
		boolean hasInlineOwnership = false;
		for (Component line : visible) {
			if (isOwnedLine(line)) {
				hasInlineOwnership = true;
				owned.add(clearMarker(line));
			} else if (isSeparator(line)) {
				hasInlineOwnership = true;
			} else {
				foreign.add(line);
			}
		}

		// Inline markers are authoritative. Before 1.3.0 Better Lore replaced the
		// complete lore list, so an untouched legacy item is safe to migrate only
		// when that entire list still equals the stored source. A matching sub-slice
		// may have been written by another mod after the Better Lore metadata became
		// stale and must therefore remain foreign.
		if (hasInlineOwnership || legacyOwned.isEmpty()) {
			return new Partition(foreign, owned);
		}

		if (!visible.equals(legacyOwned)) {
			return new Partition(visible, List.of());
		}
		return new Partition(List.of(), legacyOwned);
	}

	private static Component markOwned(Component line) {
		return line.copy().withStyle(style -> style.withInsertion(OWNED_LINE_MARKER));
	}

	private static Component markSeparator() {
		return Component.empty().withStyle(style -> style.withInsertion(SEPARATOR_MARKER));
	}

	private static Component clearMarker(Component line) {
		return line.copy().withStyle(style -> style.withInsertion(null));
	}

	private record Partition(List<Component> foreign, List<Component> owned) {
		private Partition {
			foreign = List.copyOf(foreign);
			owned = List.copyOf(owned);
		}
	}
}
