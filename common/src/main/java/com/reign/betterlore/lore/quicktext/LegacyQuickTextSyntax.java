package com.reign.betterlore.lore.quicktext;

import java.util.ArrayList;
import java.util.List;

/** Balances the historical tag stack before the shared parser renders a saved expression. */
final class LegacyQuickTextSyntax {
	private LegacyQuickTextSyntax() {
	}

	static String normalize(String input) {
		StringBuilder output = new StringBuilder();
		List<Frame> stack = new ArrayList<>();
		for (int index = 0; index < input.length();) {
			char ch = input.charAt(index);
			if (ch == '\\' && index + 1 < input.length() && (input.charAt(index + 1) == '<' || input.charAt(index + 1) == '\\')) {
				literal(output, input.charAt(index + 1));
				index += 2;
				continue;
			}
			int end = ch == '<' ? input.indexOf('>', index + 1) : -1;
			if (end >= 0) {
				String body = input.substring(index + 1, end).trim();
				String[] parts = body.split("\\s+");
				String name = parts[0];
				if (name.startsWith("/")) {
					String closing = name.substring(1);
					int match = closing.equals("*") ? 0 : stack.size() - 1;
					if (!closing.isEmpty() && !closing.equals("*")) {
						while (match >= 0 && !stack.get(match).name().equals(closing)) match--;
					}
					if (match >= 0 && !stack.isEmpty()) {
						while (stack.size() > match) output.append(stack.removeLast().closing());
						index = end + 1;
						continue;
					}
				} else {
					String opening = QuickTextParser.legacyOpening(name, List.of(parts).subList(1, parts.length));
					if (opening != null) {
						String closing = opening.isEmpty() ? "" : "</" + opening.substring(1).split("[ >]", 2)[0] + ">";
						output.append(opening);
						stack.add(new Frame(name, closing));
						index = end + 1;
						continue;
					}
				}
			}
			// Unknown openers are text, but a later recognized opener inside that
			// text still participates in the historical tag stack.
			literal(output, ch);
			index++;
		}
		while (!stack.isEmpty()) output.append(stack.removeLast().closing());
		return output.toString();
	}

	private static void literal(StringBuilder output, char ch) {
		if (ch == '<' || ch == '\\') output.append('\\');
		output.append(ch);
	}

	private record Frame(String name, String closing) {
	}
}
