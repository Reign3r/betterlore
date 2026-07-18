package com.reign.betterlore.color;

/** Pure color conversion helpers shared by the UI and regression tests. */
public final class ColorMath {
	private ColorMath() {
	}

	public static Hsv rgbToHsv(int rgb) {
		double r = ((rgb >> 16) & 0xFF) / 255.0D;
		double g = ((rgb >> 8) & 0xFF) / 255.0D;
		double b = (rgb & 0xFF) / 255.0D;
		double max = Math.max(r, Math.max(g, b));
		double min = Math.min(r, Math.min(g, b));
		double delta = max - min;
		double hue;
		if (delta == 0.0D) {
			hue = 0.0D;
		} else if (max == r) {
			hue = ((g - b) / delta) % 6.0D;
		} else if (max == g) {
			hue = (b - r) / delta + 2.0D;
		} else {
			hue = (r - g) / delta + 4.0D;
		}
		hue /= 6.0D;
		if (hue < 0.0D) {
			hue += 1.0D;
		}
		double saturation = max == 0.0D ? 0.0D : delta / max;
		return new Hsv(hue, saturation, max);
	}

	public static int hsvToRgb(double hue, double saturation, double value) {
		hue = hue - Math.floor(hue);
		saturation = clamp01(saturation);
		value = clamp01(value);
		double h = hue * 6.0D;
		int sector = (int) Math.floor(h);
		double fraction = h - sector;
		double p = value * (1.0D - saturation);
		double q = value * (1.0D - fraction * saturation);
		double t = value * (1.0D - (1.0D - fraction) * saturation);

		double r;
		double g;
		double b;
		switch (Math.floorMod(sector, 6)) {
			case 0 -> {
				r = value;
				g = t;
				b = p;
			}
			case 1 -> {
				r = q;
				g = value;
				b = p;
			}
			case 2 -> {
				r = p;
				g = value;
				b = t;
			}
			case 3 -> {
				r = p;
				g = q;
				b = value;
			}
			case 4 -> {
				r = t;
				g = p;
				b = value;
			}
			default -> {
				r = value;
				g = p;
				b = q;
			}
		}

		return (channel(r) << 16) | (channel(g) << 8) | channel(b);
	}

	private static int channel(double value) {
		return Math.max(0, Math.min(255, (int) Math.round(value * 255.0D)));
	}

	private static double clamp01(double value) {
		return Math.max(0.0D, Math.min(1.0D, value));
	}

	public record Hsv(double hue, double saturation, double value) {
	}
}
