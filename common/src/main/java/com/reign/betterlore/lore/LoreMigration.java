package com.reign.betterlore.lore;

import com.reign.betterlore.lore.quicktext.QuickTextLoreEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Resolves stored source against the actual item, never against the current loader. */
final class LoreMigration {
	private LoreMigration() {
	}

	static String matchingMarkup(String source, List<Component> visible, boolean name) {
		if (source == null || source.isEmpty() || visible.isEmpty()) return null;
		// A few pre-migration loader paths stored the editor source itself as a
		// literal lore/name component. It is still safe to recover because the
		// complete visible text must equal the stored source and no foreign
		// insertion marker may be present. Without this check the literal line is
		// treated as foreign and a second, formatted line is appended on edit.
		if (matchesLiteralSource(source, visible) && isValidSource(source, name)) {
			return preferred(source, name);
		}
		if (matchesSource(source, visible, name)) return preferred(source, name);
		for (String candidate : QuickTextLoreEngine.migrationCandidates(source, name)) {
			if (matchesSource(candidate, visible, name)) return preferred(candidate, name);
		}
		return null;
	}

	private static boolean isValidSource(String source, boolean name) {
		return (name ? LoreMarkupParser.parseName(source) : LoreMarkupParser.parse(source)).isSuccess();
	}

	private static boolean matchesLiteralSource(String source, List<Component> visible) {
		StringBuilder text = new StringBuilder();
		for (int index = 0; index < visible.size(); index++) {
			Component component = visible.get(index);
			if (!isLiteral(component) || hasInsertion(component)) return false;
			if (index > 0) text.append('\n');
			text.append(component.getString());
		}
		return source.contentEquals(text);
	}

	private static boolean matchesSource(String source, List<Component> visible, boolean name) {
		ParseResult parsed = name ? LoreMarkupParser.parseName(source) : LoreMarkupParser.parse(source);
		if (!parsed.isSuccess()) return false;
		List<Component> expected = name ? List.of(LoreComponents.toNameComponent(parsed.document()))
				: LoreComponents.toComponents(parsed.document());
		return equivalent(visible, expected);
	}

	private static String preferred(String source, boolean name) {
		return name ? LoreMarkupParser.toPreferredNameMarkup(source) : LoreMarkupParser.toPreferredMarkup(source);
	}

	/** Vanilla codecs can regroup literal siblings without changing effective styles. */
	static boolean equivalent(List<Component> actual, List<Component> expected) {
		if (actual.equals(expected)) return true;
		if (actual.size() != expected.size()) return false;
		for (int i = 0; i < actual.size(); i++) {
			if (!isLiteral(actual.get(i)) || !isLiteral(expected.get(i))) return false;
			if (!styledText(actual.get(i)).equals(styledText(expected.get(i)))) return false;
		}
		return true;
	}

	private static boolean isLiteral(Component component) {
		return component.getContents() instanceof PlainTextContents
				&& component.getSiblings().stream().allMatch(LoreMigration::isLiteral);
	}

	private static boolean hasInsertion(Component component) {
		return component.getStyle().getInsertion() != null
				|| component.getSiblings().stream().anyMatch(LoreMigration::hasInsertion);
	}

	private static List<StyledCharacter> styledText(Component component) {
		List<StyledCharacter> characters = new ArrayList<>();
		component.visit((style, text) -> {
			Style effective = style.withBold(style.isBold()).withItalic(style.isItalic())
					.withUnderlined(style.isUnderlined()).withStrikethrough(style.isStrikethrough())
					.withObfuscated(style.isObfuscated());
			// Keep click/hover/font/insertion and other non-formatting attributes:
			// a visually similar foreign component must not be claimed as our own.
			Style normalized = effective;
			text.codePoints().forEach(cp -> characters.add(new StyledCharacter(cp, normalized)));
			return Optional.empty();
		}, Style.EMPTY);
		return characters;
	}

	private record StyledCharacter(int codePoint, Style style) {
	}
}
