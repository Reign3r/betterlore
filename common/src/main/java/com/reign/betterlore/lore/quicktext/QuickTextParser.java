package com.reign.betterlore.lore.quicktext;

import com.reign.betterlore.lore.LoreDocument;
import com.reign.betterlore.lore.LoreLine;
import com.reign.betterlore.lore.LoreMarkupParser;
import com.reign.betterlore.lore.LoreRun;
import com.reign.betterlore.lore.ParseResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Better Lore's loader-independent parser for its supported QuickText formatting. */
final class QuickTextParser {
	private QuickTextParser() {
	}

	static ParseResult parse(String input) {
		Parser parser = new Parser(input, Migration.NONE);
		ParseResult error = parser.parseUntil(null);
		if (error != null) {
			return error;
		}
		return ParseResult.success(parser.buildDocument());
	}

	/** Projects a valid mode-bearing opener into ordinary editor syntax. */
	static EditorTag editorTagAt(String input, int index) {
		Tag tag = readTagAt(input, index);
		if (tag == null || tag.closing()) return null;
		if (canonicalTagName(tag.name()).equals("gradient")) {
			GradientArguments arguments = parseGradientArguments(tag, false);
			if (arguments == null || tag.args().stream().allMatch(arg -> parseColor(arg) != null)) return null;
			GradientMode mode = arguments.mode();
			String name = mode == GradientMode.HARD ? "hgr" : "gr";
			StringBuilder opening = new StringBuilder("<").append(name);
			for (String arg : tag.args()) if (parseColor(arg) != null) opening.append(' ').append(arg);
			String hidden = mode == GradientMode.OKLAB || mode == GradientMode.HARD ? ""
					: mode == GradientMode.HSV ? "hsv" : "type:" + mode.name().toLowerCase(Locale.ROOT);
			return new EditorTag(tag.endIndex(), opening.append('>').toString(), hidden);
		}
		if (canonicalTagName(tag.name()).equals("rainbow") && parseRainbowArguments(tag, false) != null
				&& "legacy_hsv".equalsIgnoreCase(namedArgument(tag.args(), "type"))) {
			StringBuilder opening = new StringBuilder("<").append(tag.name());
			for (String arg : tag.args()) if (!arg.toLowerCase(Locale.ROOT).startsWith("type:")) opening.append(' ').append(arg);
			return new EditorTag(tag.endIndex(), opening.append('>').toString(), "type:legacy_hsv");
		}
		return null;
	}

	record EditorTag(int end, String opening, String hiddenMode) {}

	static int editorTokenEnd(String input, int index) {
		Tag tag = readTagAt(input, index);
		return tag == null ? index + 1 : tag.endIndex();
	}

	static String migrate(String input, boolean fabric) {
		if (fabric) return LegacyQuickTextSyntax.normalize(input);
		Parser parser = new Parser(input, Migration.COMMON);
		return parser.parseUntil(null) == null ? parser.markup.toString() : null;
	}

	private enum Migration { NONE, COMMON }

	static String legacyOpening(String name, List<String> args) {
		Tag tag = new Tag(name, args, false, 0);
		String canonical = canonicalTagName(switch (name) {
			case "colour" -> "color";
			case "em" -> "italic";
			case "matrix" -> "obfuscated";
			default -> name;
		});
		Integer namedColor = parseColor(switch (name) {
			case "orange" -> "gold";
			case "light_gray", "light_grey" -> "gray";
			case "pink" -> "light_purple";
			case "purple" -> "dark_purple";
			default -> name;
		});
		if (namedColor != null) return "<c " + LoreMarkupParser.formatHex(namedColor) + ">";
		// These short forms belong to the shared parser, not the old dependency.
		if (name.equals("s") || name.equals("o")) return null;
		if (canonical.equals("gradient")) {
			GradientArguments gradient = parseGradientArguments(tag, true);
			if (gradient == null) return "";
			StringBuilder opening = new StringBuilder("<gr type:").append(gradient.mode().name().toLowerCase(Locale.ROOT));
			for (int color : gradient.colors()) opening.append(' ').append(LoreMarkupParser.formatHex(color));
			return opening.append('>').toString();
		}
		if (canonical.equals("rainbow")) {
			RainbowArguments rainbow = parseRainbowArguments(tag, true);
			return rainbow == null ? "" : "<rb type:legacy_hsv f:" + rainbow.frequency() + " s:" + rainbow.saturation() + " o:" + rainbow.offset() + ">";
		}
		if (canonical.equals("c") || name.startsWith("#")) {
			String value = canonical.equals("c") ? orderedOrNamedArgument(args, "value", 0) : name;
			Integer color = value == null ? null : parseColor(value);
			return color == null ? "" : "<c " + LoreMarkupParser.formatHex(color) + ">";
		}
		return isPairedFormattingTag(canonical) ? "<" + canonical + ">" : null;
	}

	private static final class Parser {
		private final String input;
		private final Migration migration;
		private final StringBuilder markup = new StringBuilder();
		private final List<Boolean> explicitColors = new ArrayList<>();
		private int lineBreaks;
		private final List<LoreLine> lines = new ArrayList<>();
		private final List<LoreRun> currentRuns = new ArrayList<>();
		private final List<Integer> colorStack = new ArrayList<>();
		private final StringBuilder currentText = new StringBuilder();
		private int index;
		private int visible;
		private int boldDepth;
		private int italicDepth;
		private int underlinedDepth;
		private int strikethroughDepth;
		private int obfuscatedDepth;

		private Parser(String input, Migration migration) {
			this.input = input;
			this.migration = migration;
			this.colorStack.add(LoreMarkupParser.DEFAULT_COLOR);
		}

		private ParseResult parseUntil(String closingTag) {
			while (index < input.length()) {
				Tag tag = readTagAt(input, index);
				if (tag != null) {
					if (tag.closing()) {
						if (closingTag != null && (tag.name().isEmpty() || canonical(tag.name()).equals(canonical(closingTag)))) {
							index = tag.endIndex();
							return null;
						}
						ParseResult error = appendLiteralTag(tag);
						if (error != null || migration == Migration.COMMON) {
							return error;
						}
						continue;
					}

					ParseResult result = handleOpeningTag(tag);
					if (result != null) {
						return result;
					}
					continue;
				}

				ParseResult result = appendLiteralAt(index);
				if (result != null) {
					return result;
				}
			}
			return null;
		}

		private ParseResult handleOpeningTag(Tag tag) {
			return switch (tag.name()) {
				case "c", "color" -> handleColorTag(tag);
				case "gr", "gradient", "hgr", "hard_gradient" -> handleGradientTag(tag);
				case "rainbow", "rb" -> handleRainbowTag(tag);
				case "b", "bold" -> handleFormatTag(tag, LoreRun.BOLD);
				case "i", "italic" -> handleFormatTag(tag, LoreRun.ITALIC);
				case "u", "underline", "underlined" -> handleFormatTag(tag, LoreRun.UNDERLINED);
				case "s", "st", "strikethrough" -> handleFormatTag(tag, LoreRun.STRIKETHROUGH);
				case "o", "obf", "obfuscated" -> handleFormatTag(tag, LoreRun.OBFUSCATED);
				default -> appendLiteralTag(tag);
			};
		}

		private ParseResult handleColorTag(Tag tag) {
			String colorValue = orderedOrNamedArgument(tag.args(), "value", 0);
			if (colorValue == null) {
				return appendLiteralTag(tag);
			}

			Integer rgb = parseColor(colorValue);
			if (rgb == null) {
				return appendLiteralTag(tag);
			}

			flushRun(currentColor(), currentFlags());
			colorStack.add(rgb);
			trace("<c " + LoreMarkupParser.formatHex(rgb) + ">");
			index = tag.endIndex();
			ParseResult result = parseUntil(tag.name());
			trace("</c>");
			flushRun(currentColor(), currentFlags());
			if (colorStack.size() > 1) {
				colorStack.remove(colorStack.size() - 1);
			}
			return result;
		}

		private ParseResult handleGradientTag(Tag tag) {
			GradientArguments arguments = parseGradientArguments(tag, false);
			if (arguments == null) {
				return appendLiteralTag(tag);
			}

			TaggedContent content = readTaggedContent(tag);
			if (content == null) {
				return appendLiteralTag(tag);
			}

			Parser child = new Parser(content.value(), migration);
			ParseResult error = child.parseUntil(null);
			if (error != null) {
				return error;
			}
			trace("<gr" + (arguments.mode() == GradientMode.OKLAB ? ""
					: " type:" + arguments.mode().name().toLowerCase(Locale.ROOT)));
			for (int rgb : arguments.colors()) trace(" " + LoreMarkupParser.formatHex(rgb));
			trace(">" + child.markup + "</gr>");

			index = content.endIndex();
			flushRun(currentColor(), currentFlags());
			return appendGradientDocument(child, arguments.colors(), arguments.mode(), currentFlags());
		}

		private ParseResult handleRainbowTag(Tag tag) {
			RainbowArguments arguments = parseRainbowArguments(tag, false);
			if (arguments == null) {
				return appendLiteralTag(tag);
			}

			TaggedContent content = readTaggedContent(tag);
			if (content == null) {
				return appendLiteralTag(tag);
			}

			Parser child = new Parser(content.value(), migration);
			ParseResult error = child.parseUntil(null);
			if (error != null) {
				return error;
			}
			trace("<rb f:" + arguments.frequency() + " s:" + arguments.saturation() + " o:" + arguments.offset()
					+ (arguments.legacy() ? " type:legacy_hsv" : "") + ">" + child.markup + "</rb>");

			index = content.endIndex();
			flushRun(currentColor(), currentFlags());
			return appendRainbowDocument(child, arguments, currentFlags());
		}

		private TaggedContent readTaggedContent(Tag tag) {
			int contentStart = tag.endIndex();
			int closeStart = findClosingTag(input, contentStart, canonical(tag.name()), migration == Migration.COMMON);
			if (closeStart < 0) {
				// QuickText allows an omitted final closing tag. In that case the
				// modifier consumes the remainder of the static input.
				return new TaggedContent(input.substring(contentStart), input.length());
			}

			Tag closeTag = readTagAt(input, closeStart);
			return new TaggedContent(
					input.substring(contentStart, closeStart),
					closeTag == null ? closeStart : closeTag.endIndex()
			);
		}

		private ParseResult handleFormatTag(Tag tag, int flag) {
			flushRun(currentColor(), currentFlags());
			addFlag(flag, 1);
			trace("<" + tag.name() + ">");
			index = tag.endIndex();
			ParseResult result = parseUntil(tag.name());
			trace("</" + tag.name() + ">");
			flushRun(currentColor(), currentFlags());
			addFlag(flag, -1);
			return result;
		}

		private ParseResult appendGradientDocument(Parser child, List<Integer> colors, GradientMode mode, int outerFlags) {
			boolean legacy = mode.name().startsWith("LEGACY_");
			LoreDocument document = child.buildDocument(!legacy);
			int count = Math.max(1, child.visible + (legacy ? child.lineBreaks : 0));
			int gradientIndex = 0;
			int visibleIndex = 0;
			for (int lineIndex = 0; lineIndex < document.lines().size(); lineIndex++) {
				if (lineIndex > 0) {
					if (legacy) gradientIndex++;
					ParseResult error = addLineBreak();
					if (error != null) {
						return error;
					}
				}

				for (LoreRun run : document.lines().get(lineIndex).runs()) {
					for (int offset = 0; offset < run.text().length();) {
						int cp = run.text().codePointAt(offset);
						offset += Character.charCount(cp);
						int color = legacy && child.explicitColors.get(visibleIndex) ? run.rgb() : interpolate(colors, gradientIndex, count, mode);
						ParseResult error = appendVisibleCodePoint(cp, color, outerFlags | run.flags(), true);
						if (error != null) {
							return error;
						}
						gradientIndex += legacy && child.explicitColors.get(visibleIndex) ? Character.charCount(cp) : 1;
						visibleIndex++;
					}
				}
			}
			return null;
		}

		private ParseResult appendRainbowDocument(Parser child, RainbowArguments arguments, int outerFlags) {
			LoreDocument document = child.buildDocument(!arguments.legacy());
			int count = Math.max(1, child.visible + (arguments.legacy() ? child.lineBreaks : 0));
			int colorIndex = 0;
			int visibleIndex = 0;
			for (int lineIndex = 0; lineIndex < document.lines().size(); lineIndex++) {
				if (lineIndex > 0) {
					if (arguments.legacy()) colorIndex++;
					ParseResult error = addLineBreak();
					if (error != null) {
						return error;
					}
				}

				for (LoreRun run : document.lines().get(lineIndex).runs()) {
					for (int offset = 0; offset < run.text().length();) {
						int cp = run.text().codePointAt(offset);
						offset += Character.charCount(cp);
						double progress = count <= 1 ? 0.0 : (double) colorIndex / (double) (count - 1);
						double hue = positiveModulo(arguments.offset() + progress * arguments.frequency(), 1.0);
						int color = hsvToRgb(hue, arguments.saturation(), 1.0);
						if (arguments.legacy()) {
							color = child.explicitColors.get(visibleIndex) ? run.rgb()
									: LegacyGradientColors.rainbow(colorIndex, count, arguments.frequency(), arguments.saturation(), arguments.offset());
						}
						ParseResult error = appendVisibleCodePoint(cp, color, outerFlags | run.flags(), true);
						if (error != null) {
							return error;
						}
						colorIndex += arguments.legacy() && child.explicitColors.get(visibleIndex) ? Character.charCount(cp) : 1;
						visibleIndex++;
					}
				}
			}
			return null;
		}

		private ParseResult appendLiteralTag(Tag tag) {
			String literal = input.substring(index, tag.endIndex());
			traceLiteral(literal);
			index = tag.endIndex();
			for (int i = 0; i < literal.length();) {
				int cp = literal.codePointAt(i);
				ParseResult error = appendVisibleCodePoint(cp, currentColor(), currentFlags());
				if (error != null) {
					return error;
				}
				i += Character.charCount(cp);
			}
			return null;
		}

		private ParseResult appendLiteralAt(int sourceIndex) {
			if (input.startsWith("\\<", sourceIndex)) {
				traceLiteral("<");
				index = sourceIndex + 2;
				return appendVisibleCodePoint('<', currentColor(), currentFlags());
			}
			if (input.startsWith("\\\\", sourceIndex)) {
				traceLiteral("\\");
				index = sourceIndex + 2;
				return appendVisibleCodePoint('\\', currentColor(), currentFlags());
			}

			int cp = input.codePointAt(sourceIndex);
			traceLiteral(Character.toString(cp));
			index = sourceIndex + Character.charCount(cp);
			if (cp == '\n') {
				return addLineBreak();
			}
			return appendVisibleCodePoint(cp, currentColor(), currentFlags());
		}

		private ParseResult appendVisibleCodePoint(int cp, int color, int flags) {
			return appendVisibleCodePoint(cp, color, flags, colorStack.size() > 1);
		}

		private ParseResult appendVisibleCodePoint(int cp, int color, int flags, boolean explicitColor) {
			explicitColors.add(explicitColor);
			currentText.appendCodePoint(cp);
			flushRun(color, flags);
			visible++;
			if (visible > LoreMarkupParser.MAX_VISIBLE_CODEPOINTS) {
				return ParseResult.error("Lore is limited to 255 visible symbols.", visible);
			}
			return null;
		}

		private String canonical(String name) {
			return migration == Migration.COMMON && name.equals("gr") ? "gr" : canonicalTagName(name);
		}

		private void trace(String value) {
			if (migration != Migration.NONE) markup.append(value);
		}

		private void traceLiteral(String value) {
			if (migration != Migration.NONE) markup.append(value.replace("\\", "\\\\").replace("<", "\\<"));
		}

		private void flushRun(int color, int flags) {
			if (currentText.isEmpty()) {
				return;
			}

			String value = currentText.toString();
			if (!currentRuns.isEmpty()) {
				LoreRun previous = currentRuns.getLast();
				if (previous.rgb() == color && previous.flags() == flags) {
					currentRuns.set(currentRuns.size() - 1, new LoreRun(previous.text() + value, color, flags));
					currentText.setLength(0);
					return;
				}
			}

			currentRuns.add(new LoreRun(value, color, flags));
			currentText.setLength(0);
		}

		private ParseResult addLineBreak() {
			lineBreaks++;
			flushRun(currentColor(), currentFlags());
			lines.add(new LoreLine(List.copyOf(currentRuns)));
			currentRuns.clear();

			if (lines.size() >= LoreMarkupParser.MAX_LINES) {
				return ParseResult.error("Too many lore lines.", visible);
			}
			return null;
		}

		private LoreDocument buildDocument() {
			return buildDocument(true);
		}

		private LoreDocument buildDocument(boolean trimTrailing) {
			flushRun(currentColor(), currentFlags());
			lines.add(new LoreLine(List.copyOf(currentRuns)));
			return new LoreDocument(trimTrailing ? trimTrailingEmptyLines(lines) : List.copyOf(lines), visible);
		}

		private int currentColor() {
			return colorStack.getLast();
		}

		private int currentFlags() {
			int flags = 0;
			if (boldDepth > 0) {
				flags |= LoreRun.BOLD;
			}
			if (italicDepth > 0) {
				flags |= LoreRun.ITALIC;
			}
			if (underlinedDepth > 0) {
				flags |= LoreRun.UNDERLINED;
			}
			if (strikethroughDepth > 0) {
				flags |= LoreRun.STRIKETHROUGH;
			}
			if (obfuscatedDepth > 0) {
				flags |= LoreRun.OBFUSCATED;
			}
			return flags;
		}

		private void addFlag(int flag, int delta) {
			switch (flag) {
				case LoreRun.BOLD -> boldDepth = Math.max(0, boldDepth + delta);
				case LoreRun.ITALIC -> italicDepth = Math.max(0, italicDepth + delta);
				case LoreRun.UNDERLINED -> underlinedDepth = Math.max(0, underlinedDepth + delta);
				case LoreRun.STRIKETHROUGH -> strikethroughDepth = Math.max(0, strikethroughDepth + delta);
				case LoreRun.OBFUSCATED -> obfuscatedDepth = Math.max(0, obfuscatedDepth + delta);
				default -> {
				}
			}
		}
	}

	private static Tag readTagAt(String input, int index) {
		if (index < 0 || index >= input.length() || input.charAt(index) != '<' || isEscaped(input, index)) {
			return null;
		}

		int end = findTagEnd(input, index + 1);
		if (end < 0) {
			return null;
		}

		String body = input.substring(index + 1, end).trim();
		if (body.isEmpty()) {
			return null;
		}

		boolean closing = body.startsWith("/");
		if (closing) {
			body = body.substring(1).trim();
			if (body.isEmpty()) {
				return new Tag("", List.of(), true, end + 1);
			}
		}

		List<String> parts = splitTagBody(body);
		if (parts.isEmpty()) {
			return null;
		}

		return new Tag(parts.getFirst().toLowerCase(Locale.ROOT), List.copyOf(parts.subList(1, parts.size())), closing, end + 1);
	}

	private static int findClosingTag(String input, int start, String outerName, boolean legacyCommon) {
		List<String> stack = new ArrayList<>();
		stack.add(outerName);
		for (int i = start; i < input.length(); i++) {
			Tag tag = readTagAt(input, i);
			if (tag == null) {
				continue;
			}

			String name = legacyCommon && tag.name().equals("gr") ? "gr" : canonicalTagName(tag.name());
			if (!tag.closing()) {
				if (isPairedFormattingTag(name)) {
					stack.add(name);
				}
			} else if (tag.name().isEmpty()) {
				stack.removeLast();
				if (stack.isEmpty()) {
					return i;
				}
			} else if (!stack.isEmpty() && stack.getLast().equals(name)) {
				stack.removeLast();
				if (stack.isEmpty()) {
					return i;
				}
			}
			i = tag.endIndex() - 1;
		}
		return -1;
	}

	private static boolean isPairedFormattingTag(String canonicalName) {
		return switch (canonicalName) {
			case "c", "gradient", "rainbow", "b", "i", "u", "s", "o" -> true;
			default -> false;
		};
	}

	private static int findTagEnd(String input, int start) {
		for (int i = start; i < input.length(); i++) {
			if (input.charAt(i) == '>') {
				return i;
			}
		}
		return -1;
	}

	private static List<String> splitTagBody(String body) {
		String[] rawParts = body.split("\\s+");
		List<String> parts = new ArrayList<>(rawParts.length);
		for (String part : rawParts) {
			if (!part.isEmpty()) {
				parts.add(part);
			}
		}
		return parts;
	}

	private static Integer parseColor(String value) {
		String normalized = unwrap(value).toLowerCase(Locale.ROOT);
		Integer named = switch (normalized) {
			case "black" -> 0x000000;
			case "dark_blue" -> 0x0000AA;
			case "dark_green" -> 0x00AA00;
			case "dark_aqua" -> 0x00AAAA;
			case "dark_red" -> 0xAA0000;
			case "dark_purple" -> 0xAA00AA;
			case "gold" -> 0xFFAA00;
			case "gray", "grey" -> 0xAAAAAA;
			case "dark_gray", "dark_grey" -> 0x555555;
			case "blue" -> 0x5555FF;
			case "green" -> 0x55FF55;
			case "aqua" -> 0x55FFFF;
			case "red" -> 0xFF5555;
			case "light_purple" -> 0xFF55FF;
			case "yellow" -> 0xFFFF55;
			case "white" -> 0xFFFFFF;
			default -> null;
		};
		return named != null ? named : parseHex(normalized);
	}

	private static Integer parseHex(String value) {
		String unwrapped = unwrap(value);
		String hex = unwrapped.startsWith("#") ? unwrapped.substring(1) : unwrapped;
		if (hex.length() != 6) {
			return null;
		}
		for (int i = 0; i < hex.length(); i++) {
			if (Character.digit(hex.charAt(i), 16) < 0) {
				return null;
			}
		}
		return Integer.parseInt(hex, 16);
	}

	private static GradientArguments parseGradientArguments(Tag tag, boolean legacyFabric) {
		List<Integer> colors = new ArrayList<>();
		GradientMode mode = tag.name().equals("hgr") || tag.name().equals("hard_gradient")
				? GradientMode.HARD
				: GradientMode.OKLAB;
		if (legacyFabric) {
			// The historical dependency spelled HSV "hvs"; "hsv" and unknown
			// named modes fell back to OKLab. Positional mode words were colors.
			String type = namedArgument(tag.args(), "type");
			if (mode != GradientMode.HARD) mode = "hvs".equals(type) ? GradientMode.HSV
					: "hard".equals(type) ? GradientMode.HARD : GradientMode.OKLAB;
			for (String arg : orderedArguments(tag.args())) {
				Integer color = parseColor(arg);
				if (color != null) colors.add(color);
			}
			if (colors.size() == 1) colors.add(colors.getFirst());
			return colors.size() < 2 ? null : new GradientArguments(List.copyOf(colors),
					GradientMode.valueOf("LEGACY_" + mode.name()));
		}
		for (String rawArg : tag.args()) {
			String arg = unwrap(rawArg);
			String lower = arg.toLowerCase(Locale.ROOT);
			if (lower.startsWith("type:legacy_")) {
				try {
					mode = GradientMode.valueOf(lower.substring(5).toUpperCase(Locale.ROOT));
					continue;
				} catch (IllegalArgumentException ignored) {
					return null;
				}
			}
			if (lower.equals("hard") || lower.equals("type:hard")) {
				mode = GradientMode.HARD;
				continue;
			}
			if (lower.equals("oklab") || lower.equals("type:oklab")) {
				mode = GradientMode.OKLAB;
				continue;
			}
			if (lower.equals("hsv") || lower.equals("hvs") || lower.equals("type:hsv") || lower.equals("type:hvs")) {
				mode = GradientMode.HSV;
				continue;
			}

			Integer rgb = parseColor(arg);
			if (rgb == null) {
				return null;
			}
			colors.add(rgb);
		}
		return colors.size() < 2 ? null : new GradientArguments(List.copyOf(colors), mode);
	}

	private static RainbowArguments parseRainbowArguments(Tag tag, boolean legacyFabric) {
		Double frequency = numericArgument(tag.args(), "frequency", "f", 0, 1.0);
		Double saturation = numericArgument(tag.args(), "saturation", "s", 1, 1.0);
		Double offset = numericArgument(tag.args(), "offset", "o", 2, 0.0);
		if (frequency == null || saturation == null || offset == null) {
			return null;
		}
		if (!withinUnitInterval(frequency) || !withinUnitInterval(saturation) || !withinUnitInterval(offset)) {
			return null;
		}
		return new RainbowArguments(frequency, saturation, offset,
				legacyFabric || "legacy_hsv".equalsIgnoreCase(namedArgument(tag.args(), "type")));
	}

	private static Double numericArgument(List<String> args, String longName, String shortName, int orderedIndex, double fallback) {
		String value = namedArgument(args, longName);
		if (value == null) {
			value = namedArgument(args, shortName);
		}
		if (value == null) {
			List<String> ordered = orderedArguments(args);
			if (orderedIndex >= ordered.size()) {
				return fallback;
			}
			value = ordered.get(orderedIndex);
		}
		try {
			return Double.parseDouble(unwrap(value));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static String orderedOrNamedArgument(List<String> args, String name, int orderedIndex) {
		String named = namedArgument(args, name);
		if (named != null) {
			return named;
		}
		List<String> ordered = orderedArguments(args);
		return orderedIndex < ordered.size() ? ordered.get(orderedIndex) : null;
	}

	private static String namedArgument(List<String> args, String name) {
		String prefix = name.toLowerCase(Locale.ROOT) + ":";
		for (String arg : args) {
			String lower = arg.toLowerCase(Locale.ROOT);
			if (lower.startsWith(prefix)) {
				return arg.substring(prefix.length());
			}
		}
		return null;
	}

	private static List<String> orderedArguments(List<String> args) {
		return args.stream().filter(arg -> !arg.contains(":")).toList();
	}

	private static String unwrap(String value) {
		if (value == null || value.length() < 2) {
			return value;
		}
		char first = value.charAt(0);
		char last = value.charAt(value.length() - 1);
		if ((first == '\'' || first == '"' || first == '`') && first == last) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}

	private static boolean withinUnitInterval(double value) {
		return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
	}

	private static String canonicalTagName(String tagName) {
		return switch (tagName) {
			case "color" -> "c";
			case "gr", "gradient", "hgr", "hard_gradient" -> "gradient";
			case "rb" -> "rainbow";
			case "bold" -> "b";
			case "italic" -> "i";
			case "underline", "underlined" -> "u";
			case "st", "strikethrough" -> "s";
			case "obf", "obfuscated" -> "o";
			default -> tagName;
		};
	}

	private static int interpolate(List<Integer> colors, int index, int count, GradientMode mode) {
		if (mode.name().startsWith("LEGACY_")) {
			return LegacyGradientColors.gradient(colors, index, count, mode.name().substring(7).toLowerCase(Locale.ROOT));
		}
		if (colors.size() == 1 || count <= 1) {
			return colors.getFirst();
		}

		double scaled = (double) index * (colors.size() - 1) / (double) (count - 1);
		int segment = Math.min(colors.size() - 2, (int) Math.floor(scaled));
		double t = scaled - segment;
		return switch (mode) {
			case HARD -> colors.get(Math.min(colors.size() - 1, (int) Math.round(scaled)));
			case HSV -> interpolateHsv(colors.get(segment), colors.get(segment + 1), t);
			case OKLAB -> interpolateOklab(colors.get(segment), colors.get(segment + 1), t);
			default -> throw new IllegalArgumentException("Unknown interpolation mode: " + mode);
		};
	}

	private static int interpolateHsv(int start, int end, double t) {
		double[] a = rgbToHsv(start);
		double[] b = rgbToHsv(end);
		double hueDelta = b[0] - a[0];
		if (hueDelta > 0.5) {
			hueDelta -= 1.0;
		} else if (hueDelta < -0.5) {
			hueDelta += 1.0;
		}
		double hue = positiveModulo(a[0] + hueDelta * t, 1.0);
		return hsvToRgb(hue, lerp(a[1], b[1], t), lerp(a[2], b[2], t));
	}

	private static double[] rgbToHsv(int rgb) {
		double r = ((rgb >> 16) & 0xFF) / 255.0;
		double g = ((rgb >> 8) & 0xFF) / 255.0;
		double b = (rgb & 0xFF) / 255.0;
		double max = Math.max(r, Math.max(g, b));
		double min = Math.min(r, Math.min(g, b));
		double delta = max - min;

		double hue;
		if (delta == 0.0) {
			hue = 0.0;
		} else if (max == r) {
			hue = positiveModulo((g - b) / delta, 6.0) / 6.0;
		} else if (max == g) {
			hue = ((b - r) / delta + 2.0) / 6.0;
		} else {
			hue = ((r - g) / delta + 4.0) / 6.0;
		}
		double saturation = max == 0.0 ? 0.0 : delta / max;
		return new double[] {hue, saturation, max};
	}

	private static int hsvToRgb(double hue, double saturation, double value) {
		hue = positiveModulo(hue, 1.0);
		saturation = Math.max(0.0, Math.min(1.0, saturation));
		value = Math.max(0.0, Math.min(1.0, value));
		double scaled = hue * 6.0;
		int sector = (int) Math.floor(scaled) % 6;
		double fraction = scaled - Math.floor(scaled);
		double p = value * (1.0 - saturation);
		double q = value * (1.0 - fraction * saturation);
		double t = value * (1.0 - (1.0 - fraction) * saturation);
		double r;
		double g;
		double b;
		switch (sector) {
			case 0 -> { r = value; g = t; b = p; }
			case 1 -> { r = q; g = value; b = p; }
			case 2 -> { r = p; g = value; b = t; }
			case 3 -> { r = p; g = q; b = value; }
			case 4 -> { r = t; g = p; b = value; }
			default -> { r = value; g = p; b = q; }
		}
		return (toByte(r) << 16) | (toByte(g) << 8) | toByte(b);
	}

	private static int toByte(double value) {
		return Math.max(0, Math.min(255, (int) Math.round(value * 255.0)));
	}

	private static double positiveModulo(double value, double modulus) {
		double result = value % modulus;
		return result < 0.0 ? result + modulus : result;
	}

	private static int interpolateOklab(int start, int end, double t) {
		double[] a = rgbToOklab(start);
		double[] b = rgbToOklab(end);
		double l = lerp(a[0], b[0], t);
		double aa = lerp(a[1], b[1], t);
		double bb = lerp(a[2], b[2], t);
		return oklabToRgb(l, aa, bb);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	private static double[] rgbToOklab(int rgb) {
		double r = srgbToLinear((rgb >> 16) & 0xFF);
		double g = srgbToLinear((rgb >> 8) & 0xFF);
		double b = srgbToLinear(rgb & 0xFF);

		double l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b;
		double m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b;
		double s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b;

		double lRoot = Math.cbrt(l);
		double mRoot = Math.cbrt(m);
		double sRoot = Math.cbrt(s);

		return new double[] {
				0.2104542553 * lRoot + 0.7936177850 * mRoot - 0.0040720468 * sRoot,
				1.9779984951 * lRoot - 2.4285922050 * mRoot + 0.4505937099 * sRoot,
				0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.8086757660 * sRoot
		};
	}

	private static int oklabToRgb(double l, double a, double b) {
		double lPrime = l + 0.3963377774 * a + 0.2158037573 * b;
		double mPrime = l - 0.1055613458 * a - 0.0638541728 * b;
		double sPrime = l - 0.0894841775 * a - 1.2914855480 * b;

		double l3 = lPrime * lPrime * lPrime;
		double m3 = mPrime * mPrime * mPrime;
		double s3 = sPrime * sPrime * sPrime;

		double r = 4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3;
		double g = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3;
		double blue = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3;

		return (linearToSrgb(r) << 16) | (linearToSrgb(g) << 8) | linearToSrgb(blue);
	}

	private static double srgbToLinear(int channel) {
		double value = channel / 255.0;
		return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
	}

	private static int linearToSrgb(double value) {
		value = Math.max(0.0, Math.min(1.0, value));
		double srgb = value <= 0.0031308 ? 12.92 * value : 1.055 * Math.pow(value, 1.0 / 2.4) - 0.055;
		return Math.max(0, Math.min(255, (int) Math.round(srgb * 255.0)));
	}


	private static boolean isEscaped(String input, int index) {
		int backslashes = 0;
		for (int i = index - 1; i >= 0 && input.charAt(i) == '\\'; i--) {
			backslashes++;
		}
		return (backslashes & 1) == 1;
	}

	private static List<LoreLine> trimTrailingEmptyLines(List<LoreLine> lines) {
		int end = lines.size();
		while (end > 0 && lines.get(end - 1).isEmpty()) {
			end--;
		}
		return List.copyOf(lines.subList(0, end));
	}

	private record Tag(String name, List<String> args, boolean closing, int endIndex) {
	}

	private record TaggedContent(String value, int endIndex) {
	}

	private enum GradientMode {
		OKLAB,
		HSV,
		HARD,
		LEGACY_OKLAB,
		LEGACY_HSV,
		LEGACY_HARD
	}

	private record GradientArguments(List<Integer> colors, GradientMode mode) {
	}

	private record RainbowArguments(double frequency, double saturation, double offset, boolean legacy) {
	}
}
