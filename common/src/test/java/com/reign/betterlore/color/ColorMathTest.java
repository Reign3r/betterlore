package com.reign.betterlore.color;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorMathTest {
	@Test
	void primaryColorsRoundTrip() {
		assertRoundTrip(0xFF0000);
		assertRoundTrip(0x00FF00);
		assertRoundTrip(0x0000FF);
	}

	@Test
	void arbitraryColorsRoundTripWithinOneChannelStep() {
		assertRoundTrip(0xA6C2FF);
		assertRoundTrip(0xDBFFA9);
		assertRoundTrip(0x03198C);
	}

	@Test
	void clampsSaturationAndValue() {
		assertEquals(0xFFFFFF, ColorMath.hsvToRgb(0.0, -1.0, 2.0));
		assertEquals(0x000000, ColorMath.hsvToRgb(0.5, 1.0, -1.0));
	}

	@Test
	void hueWrapsInBothDirections() {
		assertEquals(ColorMath.hsvToRgb(0.25, 1.0, 1.0), ColorMath.hsvToRgb(1.25, 1.0, 1.0));
		assertEquals(ColorMath.hsvToRgb(0.75, 1.0, 1.0), ColorMath.hsvToRgb(-0.25, 1.0, 1.0));
	}

	private static void assertRoundTrip(int rgb) {
		ColorMath.Hsv hsv = ColorMath.rgbToHsv(rgb);
		int actual = ColorMath.hsvToRgb(hsv.hue(), hsv.saturation(), hsv.value());
		for (int shift : new int[] {16, 8, 0}) {
			int expectedChannel = (rgb >> shift) & 0xFF;
			int actualChannel = (actual >> shift) & 0xFF;
			assertEquals(expectedChannel, actualChannel, 1);
		}
	}
}
