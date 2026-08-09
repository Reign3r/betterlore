package com.reign.betterlore.lore;

import com.reign.betterlore.test.MinecraftTestBootstrap;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoreComponentsOwnershipTest {
	@Test
	void foreignOnlyLoreIsNotExposedAndSurvivesFirstApply() {
		Component foreign = Component.literal("Another mod").withStyle(style -> style.withBold(true));
		List<Component> result = apply(List.of(foreign), List.of(), "<i>Mine</i>");

		assertSame(foreign, result.get(0));
		assertTrue(LoreOwnership.isSeparator(result.get(1)));
		assertTrue(LoreOwnership.isOwnedLine(result.get(2)));
		assertEquals("Mine", result.get(2).getString());
		assertEquals(
				"<i>Mine</i>",
				LoreMarkupDecompiler.toSafeComponentListMarkup(
						LoreOwnership.ownedLines(result, List.of())
				)
		);
	}

	@Test
	void replacementAndClearTouchOnlyOwnedLinesAndKeepThemAtBottom() {
		Component head = Component.literal("Foreign head");
		List<Component> initial = apply(List.of(head), List.of(), "Old owned");

		Component tail = Component.literal("Foreign tail").withStyle(style -> style.withUnderlined(true));
		List<Component> interleaved = new ArrayList<>(initial);
		interleaved.add(tail);

		List<Component> replaced = apply(interleaved, List.of(), "Replacement");
		assertEquals(List.of(head, tail), replaced.subList(0, 2));
		assertTrue(LoreOwnership.isSeparator(replaced.get(2)));
		assertTrue(LoreOwnership.isOwnedLine(replaced.get(3)));

		List<Component> cleared = LoreComponents.planVisibleLore(replaced, List.of(), List.of());
		assertEquals(List.of(head, tail), cleared);
	}

	@Test
	void legacySourceMigratesOnlyAnUntouchedCompleteLoreList() {
		Component legacy = components("Legacy").get(0);
		List<Component> exact = apply(
				List.of(legacy),
				List.of(legacy),
				"Updated"
		);
		assertEquals(List.of("Updated"), strings(exact));
		assertTrue(LoreOwnership.isOwnedLine(exact.get(0)));

		List<Component> staleMetadata = apply(
				List.of(Component.literal("Foreign"), legacy),
				List.of(legacy),
				"Updated"
		);
		assertEquals(List.of("Foreign", "Legacy", "", "Updated"), strings(staleMetadata));
		assertFalse(LoreOwnership.isOwnedLine(staleMetadata.get(1)));
	}

	@Test
	void strippedCurrentMarkersNeverAuthorizeDeletionByTextMatch() {
		List<Component> current = apply(List.of(), List.of(), "Old owned");
		List<Component> stripped = current.stream()
				.map(line -> (Component) line.copy().withStyle(style -> style.withInsertion(null)))
				.toList();

		assertFalse(LoreOwnership.hasInlineOwnership(stripped));
		List<Component> replaced = apply(stripped, List.of(), "Replacement");
		assertEquals(List.of("Old owned", "", "Replacement"), strings(replaced));
	}

	@Test
	void overflowIsRejectedBeforeTheInputPlanChanges() {
		List<Component> foreign = new ArrayList<>();
		for (int line = 0; line < LoreComponents.MAX_VISIBLE_LORE_LINES; line++) {
			foreign.add(Component.literal("Foreign " + line));
		}
		List<Component> original = List.copyOf(foreign);

		assertThrows(
				IllegalArgumentException.class,
				() -> apply(foreign, List.of(), "Owned")
		);
		assertEquals(original, foreign);
	}

	@Test
	void ownershipMarkerAndComponentStructureSurviveVanillaCopies() {
		Component marked = LoreOwnership.compose(
				List.of(),
				List.of(),
				List.of(Component.translatable(
						"another_mod.foreign_lore",
						Component.literal("argument").withStyle(style -> style.withBold(true))
				))
		).get(0);
		Component copied = marked.copy();

		assertEquals(marked, copied);
		assertTrue(LoreOwnership.isOwnedLine(copied));
		assertEquals(List.of(), LoreOwnership.compose(List.of(copied), List.of(), List.of()));
	}

	@Test
	void ownershipMarkerSurvivesVanillaComponentSerialization() {
		MinecraftTestBootstrap.ensureCodecRegistries();
		Component marked = LoreOwnership.compose(
				List.of(),
				List.of(),
				List.of(Component.literal("Owned").withStyle(style -> style.withBold(true)))
		).get(0);
		Tag encoded = ComponentSerialization.CODEC
				.encodeStart(NbtOps.INSTANCE, marked)
				.getOrThrow();
		Component decoded = ComponentSerialization.CODEC
				.parse(NbtOps.INSTANCE, encoded)
				.getOrThrow();

		assertEquals(marked, decoded);
		assertTrue(LoreOwnership.isOwnedLine(decoded));
		assertEquals(List.of(), LoreOwnership.compose(List.of(decoded), List.of(), List.of()));
	}

	private static List<Component> apply(
			List<Component> visible,
			List<Component> legacyOwned,
			String rawLore
	) {
		return LoreComponents.planVisibleLore(visible, legacyOwned, components(rawLore));
	}

	private static List<Component> components(String rawLore) {
		ParseResult parsed = LoreMarkupParser.parse(rawLore);
		assertTrue(parsed.isSuccess());
		return LoreComponents.toComponents(parsed.document());
	}

	private static List<String> strings(List<Component> components) {
		return components.stream().map(Component::getString).toList();
	}
}
