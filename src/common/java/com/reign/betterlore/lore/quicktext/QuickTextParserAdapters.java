package com.reign.betterlore.lore.quicktext;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

final class QuickTextParserAdapters {
	private static final QuickTextParserAdapter ADAPTER = loadAdapter();

	private QuickTextParserAdapters() {
	}

	static QuickTextParserAdapter adapter() {
		return ADAPTER;
	}

	private static QuickTextParserAdapter loadAdapter() {
		try {
			ServiceLoader<QuickTextParserAdapter> loader = ServiceLoader.load(QuickTextParserAdapter.class);
			Iterator<QuickTextParserAdapter> iterator = loader.iterator();
			return iterator.hasNext() ? iterator.next() : null;
		} catch (ServiceConfigurationError | RuntimeException | LinkageError error) {
			return null;
		}
	}
}
