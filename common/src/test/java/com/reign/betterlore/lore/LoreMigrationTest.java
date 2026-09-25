package com.reign.betterlore.lore;

import com.reign.betterlore.lore.quicktext.QuickTextLoreEngine;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Runs without registry bootstrap on every loader/target, including Forge. */
class LoreMigrationTest {
	static Stream<LegacyItemFixtures.Item> items() {
		return LegacyItemFixtures.items().stream();
	}

	@ParameterizedTest
	@MethodSource("items")
	void savedLoreRemainsEditableWithExactFormatting(LegacyItemFixtures.Item item) {
		List<Component> before = item.lore().stream().map(Component::copy).map(c -> (Component) c).toList();
		String source = LoreMarkupDecompiler.toSafeOwnedLoreMarkup(item.rawLore(), item.lore(), !item.currentOwnership());
		if (item.lore().isEmpty()) {
			assertEquals("", source);
			return;
		}
		assertFalse(source.isEmpty());
		List<Component> expected = owned(item);
		assertTrue(LoreMigration.equivalent(expected, components(source)), item.label());
		if (item.rawLore().contains("<gr")) {
			assertTrue(source.contains("<gr"), "Preserve editable gradients, not individual color runs");
		}
		assertEquals(before, item.lore(), "Opening the editor must not rewrite saved components");

		List<Component> saved = LoreComponents.planVisibleLore(item.lore(), legacy(item), components(source));
		assertEquals(expected.size(), saved.size(), "Migration must replace old lore, never append a duplicate");
		assertTrue(saved.stream().allMatch(LoreOwnership::isOwnedLine));
		assertEquals(source, LoreMarkupDecompiler.toSafeOwnedLoreMarkup(source, saved, false));
		assertTrue(LoreMigration.equivalent(expected, LoreOwnership.ownedLines(saved, List.of())));
		assertEquals(List.of(), LoreComponents.planVisibleLore(saved, List.of(), List.of()));
	}

	@ParameterizedTest
	@MethodSource("items")
	void legacyNamesKeepTheirFormattingWithoutRevivingStaleSource(LegacyItemFixtures.Item item) {
		String migrated = LoreMarkupDecompiler.matchingStoredNameMarkup(item.rawName(), item.name());
		if (item.name() == null || item.name().getString().equals("A||XD||")) {
			assertNull(migrated);
			return;
		}
		assertNotNull(migrated, item.label());
		ParseResult parsed = LoreMarkupParser.parseName(migrated);
		assertTrue(parsed.isSuccess());
		assertTrue(LoreComponents.equivalentToExistingName(item.name(), parsed.document()));
		assertEquals(migrated, LoreMarkupDecompiler.matchingStoredNameMarkup(migrated, item.name()));
	}

	@Test
	void oldFabricAndNewNativeGradientsAreDetectedFromTheItemOnEveryLoader() {
		String source = "<gr #ff6600 #aa1208>Titanfall</gr>";
		LegacyItemFixtures.Item old = LegacyItemFixtures.items().stream()
				.filter(i -> i.label().equals("fabric minecraft:bucket")).findFirst().orElseThrow();
		String migrated = LoreMigration.matchingMarkup(source, owned(old), false);
		assertEquals("<gr type:legacy_oklab #ff6600 #aa1208>Titanfall</gr>", migrated);
		assertEquals(source, LoreMigration.matchingMarkup(source, components(source), false));
		String edited = migrated.replace("Titanfall", "A longer name").replace("#ff6600", "#00ff00");
		assertEquals(13, LoreMarkupParser.parse(edited).document().visibleCodePoints());
		assertEquals(edited, LoreMigration.matchingMarkup(edited, components(edited), false));
	}

	@Test
	void matchingIsIndependentOfLiteralNodeGroupingButRetainsForeignMetadata() {
		Component expected = components("<b>Two words</b>").getFirst();
		Component regrouped = Component.literal("Two ").setStyle(expected.getSiblings().getFirst().getStyle())
				.append(Component.literal("words"));
		assertTrue(LoreMigration.equivalent(List.of(expected), List.of(regrouped)));
		assertEquals("<b>Two words</b>", LoreMigration.matchingMarkup("<b>Two words</b>", List.of(regrouped), false));
		assertNull(LoreMigration.matchingMarkup("<b>Two words</b>",
				List.of(regrouped.copy().withStyle(s -> s.withInsertion("another_mod:data"))), false));
		assertNull(LoreMigration.matchingMarkup("<b>Two words</b>",
				List.of(Component.translatable("Two words").setStyle(regrouped.getStyle())), false));
	}

	@Test
	void literalLegacyMarkupIsRecoveredBeforeReplacingLoreOrName() {
		String source = "<gr #ff6600 #aa1208>Block of Redstone</gr>";
		Component literal = Component.literal(source)
				.withStyle(style -> style.withColor(0xff6600));

		assertEquals(source, LoreMigration.matchingMarkup(source, List.of(literal), false));
		assertEquals(source, LoreMarkupDecompiler.matchingStoredNameMarkup(source, literal));
		assertEquals(List.of(literal), LoreComponents.legacyOwnedComponents(source, List.of(literal), false));
		assertEquals(source, LoreMarkupDecompiler.toSafeOwnedLoreMarkup(source, List.of(literal), true));
	}

	@Test
	void literalLegacyMarkupWithForeignInsertionIsNotClaimed() {
		String source = "<gr #ff6600 #aa1208>Block of Redstone</gr>";
		Component foreign = Component.literal(source)
				.withStyle(style -> style.withInsertion("another_mod:data"));

		assertNull(LoreMigration.matchingMarkup(source, List.of(foreign), false));
		assertNull(LoreMarkupDecompiler.matchingStoredNameMarkup(source, foreign));
	}

	@Test
	void malformedLiteralMarkupIsNotRecovered() {
		String malformed = "bad\u0000markup";
		Component literal = Component.literal(malformed);

		assertNull(LoreMigration.matchingMarkup(malformed, List.of(literal), false));
		assertNull(LoreMarkupDecompiler.matchingStoredNameMarkup(malformed, literal));
	}

	@Test
	void partialMatchesAndStrippedCurrentMarkersDoNotClaimForeignLore() {
		LegacyItemFixtures.Item item = LegacyItemFixtures.items().getFirst();
		List<Component> changed = new ArrayList<>(item.lore());
		changed.add(Component.literal("Foreign tail"));
		assertTrue(LoreComponents.legacyOwnedComponents(item.rawLore(), changed, false).isEmpty());
		assertEquals("", LoreMarkupDecompiler.toSafeOwnedLoreMarkup(item.rawLore(), changed, true));
		assertTrue(LoreComponents.legacyOwnedComponents(item.rawLore(), item.lore(), true).isEmpty());
		assertEquals("", LoreMarkupDecompiler.toSafeOwnedLoreMarkup(item.rawLore(), item.lore(), false));
	}

	@Test
	void markedMigrationPreservesForeignHeadAndTailThroughReplaceAndClear() {
		LegacyItemFixtures.Item item = LegacyItemFixtures.items().stream()
				.filter(i -> i.label().equals("fabric minecraft:bucket")).findFirst().orElseThrow();
		Component head = Component.literal("Foreign head"), tail = Component.literal("Foreign tail");
		List<Component> mixed = new ArrayList<>();
		mixed.add(head);
		mixed.addAll(item.lore());
		mixed.add(tail);
		String migrated = LoreMarkupDecompiler.toSafeOwnedLoreMarkup(item.rawLore(), mixed, false);
		assertTrue(migrated.contains("type:legacy_oklab"));
		List<Component> replaced = LoreComponents.planVisibleLore(mixed, List.of(), components(migrated));
		assertEquals(List.of(head, tail), LoreOwnership.foreignLines(replaced, List.of()));
		assertEquals(List.of(head, tail), LoreComponents.planVisibleLore(replaced, List.of(), List.of()));
		assertSame(head, replaced.getFirst());
	}

	@Test
	void historicalColorAndFormattingAliasesRemainEditable() {
		String source = "<red><em><matrix>Legacy</matrix></em></red>";
		Component saved = Component.literal("Legacy").withStyle(s -> s.withColor(0xff5555).withItalic(true).withObfuscated(true));
		String migrated = LoreMigration.matchingMarkup(source, List.of(saved), false);
		assertNotNull(migrated);
		assertTrue(LoreMigration.equivalent(List.of(saved), components(migrated)));
		assertEquals(migrated, LoreMigration.matchingMarkup(migrated, List.of(saved), false));
	}

	@Test
	void malformedStoredTagsCannotClaimOrRewriteUnrelatedItemText() {
		Component saved = Component.literal("Existing item text")
				.withStyle(style -> style.withColor(0x123456).withBold(true).withInsertion("another_mod:data"));
		Component before = saved.copy();
		for (String raw : List.of("<gr #ff6600 #aa1208>AB</wrong>",
				"<b><gr #ff6600 #aa1208>A</b>B</gr>", "<rb>AB</wrong>", "<gr 0>hello</gr>")) {
			assertNull(LoreMarkupDecompiler.matchingStoredNameMarkup(raw, saved));
			List<Component> lore = List.of(saved);
			List<Component> legacy = LoreComponents.legacyOwnedComponents(raw, lore, false);
			assertTrue(legacy.isEmpty(), raw);
			assertEquals("", LoreMarkupDecompiler.toSafeOwnedLoreMarkup(raw, lore, true));
			assertEquals(lore, LoreComponents.planVisibleLore(lore, legacy, List.of()));
			assertEquals(before, saved, "Reading malformed stored markup must not change the item");
		}
	}

	@Test
	void unclosedLegacyGradientStillMigratesWithItsSavedFormatting() {
		LegacyItemFixtures.Item old = LegacyItemFixtures.items().stream()
				.filter(i -> i.label().equals("fabric minecraft:bucket")).findFirst().orElseThrow();
		List<Component> visible = owned(old);
		List<Component> before = visible.stream().map(Component::copy).map(c -> (Component) c).toList();
		String migrated = LoreMigration.matchingMarkup("<gr #ff6600 #aa1208>Titanfall", visible, false);
		assertNotNull(migrated);
		assertTrue(migrated.contains("type:legacy_oklab"));
		assertTrue(LoreMigration.equivalent(visible, components(migrated)));
		assertEquals(before, visible);
	}

	@Test
	void malformedAndOversizedLegacyInputsRemainBounded() {
		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
			for (int i = 0; i < 100; i++) {
				QuickTextLoreEngine.migrationCandidates("<gr 0>hello</gr>", false);
				QuickTextLoreEngine.migrationCandidates("<gr #ff6600 0 #aa1208>hello</gr>", false);
			}
		});
		assertTrue(QuickTextLoreEngine.migrationCandidates("x".repeat(4097), false).isEmpty());
		assertTrue(QuickTextLoreEngine.migrationCandidates("<b>".repeat(257), false).isEmpty());
	}

	private static List<Component> owned(LegacyItemFixtures.Item item) {
		return LoreOwnership.ownedLines(item.lore(), legacy(item));
	}

	private static List<Component> legacy(LegacyItemFixtures.Item item) {
		return LoreComponents.legacyOwnedComponents(item.rawLore(), item.lore(), item.currentOwnership());
	}

	private static List<Component> components(String raw) {
		ParseResult parsed = LoreMarkupParser.parse(raw);
		assertTrue(parsed.isSuccess(), raw);
		return LoreComponents.toComponents(parsed.document());
	}
}
