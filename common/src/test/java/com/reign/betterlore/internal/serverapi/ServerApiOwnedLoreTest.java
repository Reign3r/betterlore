package com.reign.betterlore.internal.serverapi;

import com.reign.betterlore.lore.LoreComponents;
import com.reign.betterlore.lore.LoreMarkupParser;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerApiOwnedLoreTest {
	@Test
	void snapshotNeverExposesUnmarkedForeignLore() {
		assertEquals("", ServerApiOwnedLore.snapshotMarkup(
				null,
				false,
				List.of(Component.literal("Lore supplied by another mod"))
		));
	}

	@Test
	void snapshotReadsCurrentMarkedLinesAndPreservesTheirActualFormatting() {
		List<Component> visible = ServerApiLoreOwnership.compose(
				List.of(Component.literal("Foreign")),
				List.of(),
				List.of(Component.literal("Mine").withStyle(style -> style.withBold(true)))
		);

		assertEquals("<b>Mine</b>", ServerApiOwnedLore.snapshotMarkup(
				"stale source",
				true,
				visible
		));
	}

	@Test
	void snapshotRetainsStoredMarkupWhenItExactlyMatchesMarkedOutput() {
		String raw = "<i>Mine</i>";
		List<Component> rendered = LoreComponents.toComponents(
				LoreMarkupParser.parse(raw).document()
		);
		List<Component> visible = ServerApiLoreOwnership.compose(
				List.of(Component.literal("Foreign")),
				List.of(),
				rendered
		);

		assertEquals(raw, ServerApiOwnedLore.snapshotMarkup(raw, true, visible));
	}

	@Test
	void snapshotRecoversOnlyAnUntouchedCompleteLegacyList() {
		String raw = "<i>Legacy</i>";
		Component rendered = LoreComponents.toComponents(
				LoreMarkupParser.parse(raw).document()
		).get(0);

		assertEquals(raw, ServerApiOwnedLore.snapshotMarkup(
				raw,
				false,
				List.of(rendered)
		));
		assertEquals("", ServerApiOwnedLore.snapshotMarkup(
				raw,
				false,
				List.of(Component.literal("Foreign"), rendered)
		));
	}

	@Test
	void strippedCurrentMarkersNeverAuthorizeLegacyDeletionOrEditing() {
		String raw = "Owned before markers were stripped";
		Component nowUnmarked = LoreComponents.toComponents(
				LoreMarkupParser.parse(raw).document()
		).get(0);

		assertEquals("", ServerApiOwnedLore.snapshotMarkup(
				raw,
				true,
				List.of(nowUnmarked)
		));
	}
}
