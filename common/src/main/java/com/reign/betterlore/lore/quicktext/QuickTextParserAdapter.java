package com.reign.betterlore.lore.quicktext;

import com.reign.betterlore.lore.ParseResult;

/**
 * Loader-local bridge for real QuickText implementations.
 *
 * <p>The common engine deliberately depends only on this interface. Fabric can
 * provide a Text Placeholder API-backed adapter, while Forge/NeoForge can omit
 * the adapter and use Better Lore's clean-room QuickText-compatible fallback.
 */
public interface QuickTextParserAdapter {
	ParseResult parse(String sanitizedQuickText);
}
