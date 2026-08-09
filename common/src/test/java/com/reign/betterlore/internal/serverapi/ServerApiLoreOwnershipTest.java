package com.reign.betterlore.internal.serverapi;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerApiLoreOwnershipTest {
	@Test
	void firstApplyPreservesForeignLoreAndAppendsOneMarkedSeparator() {
		Component foreignOne = Component.literal("Other mod one");
		Component foreignTwo = Component.literal("Other mod two")
				.withStyle(style -> style.withBold(true));

		List<Component> result = ServerApiLoreOwnership.compose(
				List.of(foreignOne, foreignTwo),
				List.of(),
				List.of(Component.literal("Better Lore"))
		);

		assertSame(foreignOne, result.get(0));
		assertSame(foreignTwo, result.get(1));
		assertTrue(ServerApiLoreOwnership.isSeparator(result.get(2)));
		assertEquals("", result.get(2).getString());
		assertTrue(ServerApiLoreOwnership.isOwnedLine(result.get(3)));
		assertEquals("Better Lore", result.get(3).getString());
	}

	@Test
	void replacementRemovesOnlyMarkedLinesAndMovesOwnedSectionToBottom() {
		Component originalForeign = Component.literal("Original external");
		List<Component> first = ServerApiLoreOwnership.compose(
				List.of(originalForeign),
				List.of(),
				List.of(Component.literal("Old one"), Component.literal("Old two"))
		);
		Component prepended = Component.literal("Prepended external");
		Component appended = Component.literal("Appended external");
		List<Component> changedElsewhere = new ArrayList<>(first);
		changedElsewhere.add(0, prepended);
		changedElsewhere.add(appended);

		List<Component> result = ServerApiLoreOwnership.compose(
				changedElsewhere,
				List.of(),
				List.of(Component.literal("Replacement"))
		);

		assertEquals(List.of("Prepended external", "Original external", "Appended external", "", "Replacement"),
				result.stream().map(Component::getString).toList());
		assertSame(prepended, result.get(0));
		assertSame(originalForeign, result.get(1));
		assertSame(appended, result.get(2));
		assertTrue(ServerApiLoreOwnership.isSeparator(result.get(3)));
		assertTrue(ServerApiLoreOwnership.isOwnedLine(result.get(4)));
	}

	@Test
	void clearingDropsOnlyOwnedSectionAndItsSeparator() {
		Component foreign = Component.literal("Untouched")
				.withStyle(style -> style.withUnderlined(true));
		List<Component> current = ServerApiLoreOwnership.compose(
				List.of(foreign),
				List.of(),
				List.of(Component.literal("Owned"))
		);

		List<Component> result = ServerApiLoreOwnership.compose(current, List.of(), List.of());

		assertEquals(List.of(foreign), result);
		assertSame(foreign, result.get(0));
	}

	@Test
	void inlineMarkersWinOverLegacyTextMatching() {
		List<Component> current = ServerApiLoreOwnership.compose(
				List.of(Component.literal("Legacy-looking foreign")),
				List.of(),
				List.of(Component.literal("Marked owned"))
		);

		List<Component> result = ServerApiLoreOwnership.compose(
				current,
				List.of(Component.literal("Legacy-looking foreign")),
				List.of(Component.literal("Replacement"))
		);

		assertEquals(List.of("Legacy-looking foreign", "", "Replacement"),
				result.stream().map(Component::getString).toList());
		assertFalse(ServerApiLoreOwnership.isOwnedLine(result.get(0)));
	}

	@Test
	void completeLegacyLoreMigratesWhenNothingElseIsPresent() {
		List<Component> result = ServerApiLoreOwnership.compose(
				List.of(Component.literal("Legacy owned")),
				List.of(Component.literal("Legacy owned")),
				List.of(Component.literal("Updated"))
		);

		assertEquals(List.of("Updated"),
				result.stream().map(Component::getString).toList());
		assertTrue(ServerApiLoreOwnership.isOwnedLine(result.get(0)));
	}

	@Test
	void legacySubSliceIsNeverClaimedWhenMetadataMayBeStale() {
		List<Component> result = ServerApiLoreOwnership.compose(
				List.of(
						Component.literal("Foreign head"),
						Component.literal("Legacy-looking foreign"),
						Component.literal("Foreign tail")
				),
				List.of(Component.literal("Legacy-looking foreign")),
				List.of(Component.literal("Updated"))
		);

		assertEquals(List.of("Foreign head", "Legacy-looking foreign", "Foreign tail", "", "Updated"),
				result.stream().map(Component::getString).toList());
		assertFalse(ServerApiLoreOwnership.isOwnedLine(result.get(1)));
	}

	@Test
	void existingForeignBlankLineProvidesExactlyOneVisibleSeparator() {
		Component foreignBlank = Component.literal("");

		List<Component> result = ServerApiLoreOwnership.compose(
				List.of(Component.literal("Foreign"), foreignBlank),
				List.of(),
				List.of(Component.literal("Owned"))
		);

		assertEquals(3, result.size());
		assertSame(foreignBlank, result.get(1));
		assertFalse(ServerApiLoreOwnership.isSeparator(result.get(1)));
		assertTrue(ServerApiLoreOwnership.isOwnedLine(result.get(2)));
	}

	@Test
	void compositionExposesCombinedOverflowWithoutTruncation() {
		List<Component> foreign = new ArrayList<>();
		for (int index = 0; index < 254; index++) {
			foreign.add(Component.literal("Foreign " + index));
		}

		assertEquals(256, ServerApiLoreOwnership.compose(
				foreign,
				List.of(),
				List.of(Component.literal("Owned"))
		).size());
		foreign.add(Component.literal("Foreign 254"));
		assertEquals(257, ServerApiLoreOwnership.compose(
				foreign,
				List.of(),
				List.of(Component.literal("Owned"))
		).size());
	}
}
