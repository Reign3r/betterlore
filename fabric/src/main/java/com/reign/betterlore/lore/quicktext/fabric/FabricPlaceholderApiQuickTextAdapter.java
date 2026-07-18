package com.reign.betterlore.lore.quicktext.fabric;

import com.reign.betterlore.lore.ParseResult;
import com.reign.betterlore.lore.quicktext.ComponentLoreFlattener;
import com.reign.betterlore.lore.quicktext.QuickTextParserAdapter;
import eu.pb4.placeholders.api.ParserContext;
import eu.pb4.placeholders.api.parsers.NodeParser;
import net.minecraft.network.chat.Component;

/** Fabric adapter using Text Placeholder API's QuickText parser. */
public final class FabricPlaceholderApiQuickTextAdapter implements QuickTextParserAdapter {
	private static final NodeParser QUICK_TEXT = NodeParser.builder()
			.quickText()
			.requireSafe()
			.build();

	@Override
	public ParseResult parse(String sanitizedQuickText) {
		try {
			//? if >=26.1 {
			Component component = QUICK_TEXT.parseComponent(sanitizedQuickText, ParserContext.of());
			//? } else {
			Component component = QUICK_TEXT.parseText(sanitizedQuickText, ParserContext.of());
			//? }
			return ComponentLoreFlattener.flatten(component);
		} catch (RuntimeException exception) {
			return ParseResult.error("Invalid QuickText markup.", 0);
		}
	}
}
