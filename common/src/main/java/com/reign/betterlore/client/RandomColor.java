package com.reign.betterlore.client;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

/** Generates one complete 24-bit RGB value for the selected picker endpoint. */
public final class RandomColor {
	static final int RGB_BOUND = 0x1000000;

	private RandomColor() {
	}

	public static int nextRgb() {
		return nextRgb(bound -> ThreadLocalRandom.current().nextInt(bound));
	}

	static int nextRgb(IntUnaryOperator boundedRandom) {
		Objects.requireNonNull(boundedRandom, "boundedRandom");
		int rgb = boundedRandom.applyAsInt(RGB_BOUND);
		if (rgb < 0 || rgb >= RGB_BOUND) {
			throw new IllegalArgumentException("Random RGB source returned an out-of-range value: " + rgb);
		}
		return rgb;
	}
}
