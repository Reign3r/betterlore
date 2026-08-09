package com.reign.betterlore.lore;

import com.reign.betterlore.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

public final class LoreComponents {
	public static final int MAX_VISIBLE_LORE_LINES = ItemLore.MAX_LINES;

	private LoreComponents() {
	}

	public static void applyTo(ItemStack stack, LoreDocument document) {
		applyTo(stack, serializeDocument(document), document);
	}

	public static void applyTo(ItemStack stack, String rawMarkup, LoreDocument document) {
		List<Component> replacement = toComponents(document);
		List<Component> combined = planVisibleLore(
				visibleComponents(stack),
				legacyOwnedComponents(stack),
				replacement
		);
		setVisibleLore(stack, combined);

		if (replacement.isEmpty()) {
			ModDataComponents.removeRawLoreMarkup(stack);
			return;
		}

		String source = rawMarkup == null || rawMarkup.isEmpty()
				? serializeDocument(document)
				: rawMarkup;
		ModDataComponents.setOwnedLoreMarkup(stack, source);
	}

	public static void applyNameTo(ItemStack stack, String rawMarkup, LoreDocument document) {
		if (document.isEmpty()) {
			stack.remove(DataComponents.CUSTOM_NAME);
			ModDataComponents.removeRawNameMarkup(stack);
			return;
		}

		stack.set(DataComponents.CUSTOM_NAME, toNameComponent(document));
		if (rawMarkup != null) {
			ModDataComponents.setRawNameMarkup(stack, rawMarkup);
		}
	}

	public static boolean equivalentToExistingLore(ItemStack stack, LoreDocument document) {
		return ownedComponents(stack).equals(toComponents(document));
	}

	public static boolean equivalentToExistingName(Component existingName, LoreDocument document) {
		if (existingName == null) {
			return document.isEmpty();
		}
		return existingName.equals(toNameComponent(document));
	}

	public static List<Component> toComponents(LoreDocument document) {
		return hardWrap(document).stream()
				.map(LoreComponents::lineToComponent)
				.toList();
	}

	public static Component toNameComponent(LoreDocument document) {
		MutableComponent root = Component.empty();
		boolean firstLine = true;
		for (LoreLine line : document.lines()) {
			if (!firstLine) {
				root.append(Component.literal(" ").withStyle(Style.EMPTY.withItalic(false)));
			}
			root.append(nameLineToComponent(line));
			firstLine = false;
		}
		return root;
	}

	public static List<Component> ownedComponents(ItemStack stack) {
		return LoreOwnership.ownedLines(visibleComponents(stack), legacyOwnedComponents(stack));
	}

	public static List<Component> foreignComponents(ItemStack stack) {
		return LoreOwnership.foreignLines(visibleComponents(stack), legacyOwnedComponents(stack));
	}

	public static boolean canApplyTo(ItemStack stack, LoreDocument document) {
		return combinedComponents(stack, toComponents(document)).size() <= MAX_VISIBLE_LORE_LINES;
	}

	public static int combinedVisibleLineCount(ItemStack stack, LoreDocument document) {
		return combinedComponents(stack, toComponents(document)).size();
	}

	/** Moves an existing marked section below foreign lore without changing either section. */
	public static boolean normalizeOwnershipLayout(ItemStack stack) {
		List<Component> visible = visibleComponents(stack);
		boolean hasInlineOwnership = LoreOwnership.hasInlineOwnership(visible);
		if (!hasInlineOwnership) {
			return false;
		}

		List<Component> normalized = combinedComponents(stack, ownedComponents(stack));
		if (visible.equals(normalized) || normalized.size() > MAX_VISIBLE_LORE_LINES) {
			return false;
		}

		String rawMarkup = LoreMarkupDecompiler.toSafeOwnedLoreMarkup(stack);
		ParseResult parsed = LoreMarkupParser.parse(rawMarkup);
		if (!parsed.isSuccess()) {
			return false;
		}
		applyTo(stack, rawMarkup, parsed.document());
		return true;
	}

	private static List<Component> combinedComponents(ItemStack stack, List<Component> replacement) {
		return LoreOwnership.compose(
				visibleComponents(stack),
				legacyOwnedComponents(stack),
				replacement
		);
	}

	/** Pure application plan shared by unit tests and the ItemStack adapter. */
	static List<Component> planVisibleLore(
			List<Component> visible,
			List<Component> legacyOwned,
			List<Component> replacement
	) {
		List<Component> combined = LoreOwnership.compose(visible, legacyOwned, replacement);
		if (combined.size() > MAX_VISIBLE_LORE_LINES) {
			throw new IllegalArgumentException(
					"Combined lore exceeds " + MAX_VISIBLE_LORE_LINES + " visible lines."
			);
		}
		return combined;
	}

	private static List<Component> visibleComponents(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines();
	}

	private static List<Component> legacyOwnedComponents(ItemStack stack) {
		if (ModDataComponents.hasCurrentLoreOwnership(stack)) {
			return List.of();
		}
		String rawMarkup = ModDataComponents.getRawLoreMarkup(stack);
		if (rawMarkup == null || rawMarkup.isEmpty()) {
			return List.of();
		}
		ParseResult parsed = LoreMarkupParser.parse(rawMarkup);
		return parsed.isSuccess() ? toComponents(parsed.document()) : List.of();
	}

	private static String serializeDocument(LoreDocument document) {
		StringBuilder markup = new StringBuilder();
		for (LoreLine line : document.lines()) {
			if (!markup.isEmpty()) {
				markup.append('\n');
			}
			markup.append(LoreMarkupDecompiler.toSafeLineMarkup(line));
		}
		return markup.toString();
	}

	private static void setVisibleLore(ItemStack stack, List<Component> lines) {
		if (lines.isEmpty()) {
			stack.remove(DataComponents.LORE);
		} else {
			stack.set(DataComponents.LORE, new ItemLore(lines));
		}
	}

	private static Component nameLineToComponent(LoreLine line) {
		MutableComponent root = Component.empty();

		for (LoreRun run : line.runs()) {
			if (run.text().isEmpty()) {
				continue;
			}

			root.append(Component.literal(run.text()).withStyle(styleForRun(run, false)));
		}

		return root;
	}

	private static Component lineToComponent(LoreLine line) {
		MutableComponent root = Component.empty();

		for (LoreRun run : line.runs()) {
			if (run.text().isEmpty()) {
				continue;
			}

			root.append(Component.literal(run.text()).withStyle(styleForRun(run, true)));
		}

		return root;
	}

	private static Style styleForRun(LoreRun run, boolean forceLoreColor) {
		Style style = Style.EMPTY
				.withItalic(run.italic())
				.withBold(run.bold())
				.withUnderlined(run.underlined())
				.withStrikethrough(run.strikethrough())
				.withObfuscated(run.obfuscated());

		if (forceLoreColor || run.rgb() != LoreMarkupParser.DEFAULT_COLOR) {
			style = style.withColor(TextColor.fromRgb(run.rgb()));
		}

		return style;
	}

	private static List<LoreLine> hardWrap(LoreDocument document) {
		List<LoreLine> wrapped = new ArrayList<>();
		for (LoreLine line : document.lines()) {
			wrapped.addAll(hardWrapLine(line));
		}
		return List.copyOf(wrapped);
	}

	private static List<LoreLine> hardWrapLine(LoreLine line) {
		List<StyledCodePoint> codePoints = toStyledCodePoints(line);
		if (codePoints.size() <= LoreMarkupParser.WRAP_VISIBLE_CHARS) {
			return List.of(line);
		}

		List<LoreLine> wrapped = new ArrayList<>();
		int start = 0;
		while (start < codePoints.size()) {
			while (start < codePoints.size() && Character.isWhitespace(codePoints.get(start).codePoint())) {
				start++;
			}

			if (start >= codePoints.size()) {
				break;
			}

			int remaining = codePoints.size() - start;
			if (remaining <= LoreMarkupParser.WRAP_VISIBLE_CHARS) {
				wrapped.add(toLine(codePoints, start, codePoints.size()));
				break;
			}

			int hardEnd = start + LoreMarkupParser.WRAP_VISIBLE_CHARS;
			int split = -1;
			for (int i = hardEnd - 1; i > start; i--) {
				if (Character.isWhitespace(codePoints.get(i).codePoint())) {
					split = i;
					break;
				}
			}

			int end = split > start ? split : hardEnd;
			wrapped.add(toLine(codePoints, start, end));
			start = split > start ? split + 1 : end;
		}

		return List.copyOf(wrapped);
	}

	private static List<StyledCodePoint> toStyledCodePoints(LoreLine line) {
		List<StyledCodePoint> codePoints = new ArrayList<>();
		for (LoreRun run : line.runs()) {
			for (int i = 0; i < run.text().length();) {
				int cp = run.text().codePointAt(i);
				codePoints.add(new StyledCodePoint(cp, run.rgb(), run.flags()));
				i += Character.charCount(cp);
			}
		}
		return codePoints;
	}

	private static LoreLine toLine(List<StyledCodePoint> codePoints, int start, int end) {
		List<LoreRun> runs = new ArrayList<>();
		StringBuilder text = new StringBuilder();
		int color = codePoints.get(start).rgb();
		int flags = codePoints.get(start).flags();

		for (int i = start; i < end; i++) {
			StyledCodePoint cp = codePoints.get(i);
			if (cp.rgb() != color || cp.flags() != flags) {
				runs.add(new LoreRun(text.toString(), color, flags));
				text.setLength(0);
				color = cp.rgb();
				flags = cp.flags();
			}
			text.appendCodePoint(cp.codePoint());
		}

		if (!text.isEmpty()) {
			runs.add(new LoreRun(text.toString(), color, flags));
		}

		return new LoreLine(runs);
	}

	private record StyledCodePoint(int codePoint, int rgb, int flags) {
	}
}
