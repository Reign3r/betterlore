package com.reign.betterlore.client;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomColorTest {
	@Test
	void acceptsBothInclusiveRgbEndpoints() {
		assertEquals(0x000000, RandomColor.nextRgb(bound -> 0));
		assertEquals(0xFFFFFF, RandomColor.nextRgb(bound -> bound - 1));
	}

	@Test
	void seededRandomNeverEscapesTwentyFourBits() {
		Random random = new Random(0xB3773L);
		for (int sample = 0; sample < 10_000; sample++) {
			int rgb = RandomColor.nextRgb(random::nextInt);
			assertTrue(rgb >= 0 && rgb <= 0xFFFFFF);
		}
	}

	@Test
	void rejectsBrokenRandomSources() {
		assertThrows(IllegalArgumentException.class, () -> RandomColor.nextRgb(bound -> -1));
		assertThrows(IllegalArgumentException.class, () -> RandomColor.nextRgb(bound -> bound));
	}
}
