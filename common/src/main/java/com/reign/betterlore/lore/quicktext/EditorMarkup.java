package com.reign.betterlore.lore.quicktext;

import com.reign.betterlore.lore.LoreMarkupParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Visible editor text with bounded, out-of-band gradient compatibility metadata. */
public final class EditorMarkup {
	private static final Pattern OPENER = Pattern.compile("<(gr|gradient|hgr|hard_gradient|rb|rainbow)(?=\\s|>)", Pattern.CASE_INSENSITIVE);
	private static final int HISTORY_LIMIT = 32;
	private State state = new State("", List.of());
	private String raw = "";
	private final Map<String, State> history = new LinkedHashMap<>();

	public void load(String stored) {
		String source = stored == null ? "" : stored;
		if (source.length() > LoreMarkupParser.MAX_RAW_CHARS) source = source.substring(0, LoreMarkupParser.MAX_RAW_CHARS);
		history.clear();
		if (!commit(project(new State(source, List.of())))) commit(new State(source, List.of()));
	}

	public String text() { return state.text(); }
	public String raw() { return raw; }

	public static int visiblePosition(String source, int position) {
		int limit = Math.max(0, Math.min(position, source.length())), visible = 0;
		for (int i = 0; i < limit;) {
			QuickTextParser.EditorTag tag = QuickTextParser.editorTagAt(source, i);
			int end = tag == null ? QuickTextParser.editorTokenEnd(source, i) : tag.end();
			int length = tag == null ? end - i : tag.opening().length();
			if (limit < end) return visible + Math.min(limit - i, length);
			visible += length;
			i = end;
		}
		return visible;
	}

	/** Returns false rather than sending an oversized draft containing hidden attributes. */
	public boolean edit(String text) {
		String next = text == null ? "" : text;
		if (next.equals(state.text())) return true;
		if (next.length() > LoreMarkupParser.MAX_RAW_CHARS) return false;
		State previous = history.get(next);
		if (previous != null) return commit(previous);
		String old = state.text();
		State updated;
		int contained = old.isEmpty() ? -1 : next.indexOf(old);
		int remaining = next.isEmpty() ? -1 : old.indexOf(next);
		if (remaining >= 0) {
			int offset = remaining;
			updated = new State(next, state.modes().stream()
					.filter(mode -> mode.position() >= offset && mode.position() < offset + next.length())
					.map(mode -> new Mode(mode.position() - offset, mode.attribute())).toList());
		} else if (contained >= 0) {
			// Pasting/wrapping around existing markup must not transfer an inner
			// gradient's mode to a newly inserted identical opening tag.
			updated = replace(state, old.length(), old.length(), next.substring(contained + old.length()));
			updated = replace(updated, 0, 0, next.substring(0, contained));
		} else {
			int start = 0;
			while (start < old.length() && start < next.length() && old.charAt(start) == next.charAt(start)) start++;
			int oldEnd = old.length(), newEnd = next.length();
			while (oldEnd > start && newEnd > start && old.charAt(oldEnd - 1) == next.charAt(newEnd - 1)) { oldEnd--; newEnd--; }
			updated = replace(state, start, oldEnd, next.substring(start, newEnd));
		}
		return commit(project(updated));
	}

	/** Formatting buttons insert two delimiters while retaining metadata inside the selection. */
	public boolean wrap(int start, int end, String opening, String closing) {
		int from = Math.max(0, Math.min(start, state.text().length()));
		int to = Math.max(from, Math.min(end, state.text().length()));
		if ((long) state.text().length() + opening.length() + closing.length() > LoreMarkupParser.MAX_RAW_CHARS) return false;
		State updated = replace(state, to, to, closing);
		updated = replace(updated, from, from, opening);
		return commit(project(updated));
	}

	private boolean commit(State next) {
		String stored = stored(next);
		if (stored.length() > LoreMarkupParser.MAX_RAW_CHARS) return false;
		state = next;
		raw = stored;
		history.put(next.text(), next);
		while (history.size() > HISTORY_LIMIT) history.remove(history.keySet().iterator().next());
		return true;
	}

	private static State replace(State old, int start, int end, String replacement) {
		String text = old.text().substring(0, start) + replacement + old.text().substring(end);
		int delta = replacement.length() - (end - start);
		List<Mode> modes = new ArrayList<>();
		for (Mode mode : old.modes()) {
			int position = mode.position();
			Matcher opener = opener(old.text(), position);
			if (opener == null) continue;
			if (end <= position) position += delta;
			else if (start < opener.end()) continue;
			Matcher retained = opener(text, position);
			if (retained != null && family(retained.group(1)).equals(family(opener.group(1)))) modes.add(new Mode(position, mode.attribute()));
		}
		return new State(text, List.copyOf(modes));
	}

	private static State project(State source) {
		StringBuilder visible = new StringBuilder();
		List<Mode> modes = new ArrayList<>();
		for (int i = 0; i < source.text().length();) {
			QuickTextParser.EditorTag tag = QuickTextParser.editorTagAt(source.text(), i);
			if (tag != null) {
				if (!tag.hiddenMode().isEmpty()) modes.add(new Mode(visible.length(), tag.hiddenMode()));
				visible.append(tag.opening());
				i = tag.end();
			} else {
				for (Mode mode : source.modes()) if (mode.position() == i) modes.add(new Mode(visible.length(), mode.attribute()));
				int end = QuickTextParser.editorTokenEnd(source.text(), i);
				visible.append(source.text(), i, end);
				i = end;
			}
		}
		return new State(visible.toString(), List.copyOf(modes));
	}

	private static String stored(State source) {
		StringBuilder result = new StringBuilder(source.text());
		for (int i = source.modes().size() - 1; i >= 0; i--) {
			Mode mode = source.modes().get(i);
			Matcher opener = opener(source.text(), mode.position());
			if (opener != null) result.insert(opener.end(), " " + mode.attribute());
		}
		return result.toString();
	}

	private static Matcher opener(String text, int position) {
		if (position < 0 || position >= text.length()) return null;
		int escapes = 0;
		for (int i = position - 1; i >= 0 && text.charAt(i) == '\\'; i--) escapes++;
		if ((escapes & 1) != 0) return null;
		Matcher matcher = OPENER.matcher(text).region(position, text.length());
		return matcher.lookingAt() ? matcher : null;
	}

	private static String family(String name) {
		return name.equalsIgnoreCase("rb") || name.equalsIgnoreCase("rainbow") ? "rainbow" : "gradient";
	}

	private record Mode(int position, String attribute) {}
	private record State(String text, List<Mode> modes) {}
}
