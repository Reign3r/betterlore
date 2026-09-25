package com.reign.betterlore.lore.quicktext;

import java.util.List;

/** Historical color arithmetic, kept local so saved gradients do not depend on an external parser. */
final class LegacyGradientColors {
	private static final float[][] RGB_TO_LMS = {
			{0.4122214708f, 0.5363325363f, 0.0514459929f},
			{0.2119034982f, 0.6806995451f, 0.1073969566f},
			{0.0883024619f, 0.2817188376f, 0.6299787005f}};
	private static final float[][] LMS_TO_LAB = {
			{0.2104542553f, 0.7936177850f, -0.0040720468f},
			{1.9779984951f, -2.4285922050f, 0.4505937099f},
			{0.0259040371f, 0.7827717662f, -0.8086757660f}};
	private static final float[][] LAB_TO_LMS = {
			{1, 0.3963377774f, 0.2158037573f},
			{1, -0.1055613458f, -0.0638541728f},
			{1, -0.0894841775f, -1.2914855480f}};
	private static final float[][] LMS_TO_RGB = {
			{4.0767416621f, -3.3077115913f, 0.2309699292f},
			{-1.2684380046f, 2.6097574011f, -0.3413193965f},
			{-0.0041960863f, -0.7034186147f, 1.7076147010f}};

	private LegacyGradientColors() {
	}

	static int gradient(List<Integer> colors, int position, int length, String mode) {
		if (mode.equals("hard")) {
			return colors.get(Math.min(colors.size() - 1, (int) (position / ((float) Math.max(1, length) / colors.size()))));
		}
		float section = (float) Math.max(1, length) / (colors.size() - 1);
		int segment = Math.min((int) (position / section), colors.size() - 1);
		int next = Math.min(segment + 1, colors.size() - 1);
		float progress = (position % section) / section;
		if (mode.equals("hsv")) {
			float[] a = hsv(colors.get(segment));
			float[] b = hsv(colors.get(next));
			float delta = b[0] - a[0];
			if (Math.abs(delta) > 0.50001) {
				delta += delta < 0 ? 1 : -1;
			}
			float hue = (float) (a[0] + delta * ((double) (colors.size() - 1) / Math.max(1, length)) * (position % section));
			if (hue < 0) hue += 1;
			else if (hue > 1) hue -= 1;
			return hsv(clamp(hue), clamp(b[1] * progress + a[1] * (1 - progress)), clamp(b[2] * progress + a[2] * (1 - progress)));
		}
		float[] a = lab(colors.get(segment));
		float[] b = lab(colors.get(next));
		for (int i = 0; i < 3; i++) a[i] = a[i] + progress * (b[i] - a[i]);
		float[] lms = multiply(LAB_TO_LMS, a);
		for (int i = 0; i < 3; i++) lms[i] = lms[i] * lms[i] * lms[i];
		float[] rgb = multiply(LMS_TO_RGB, lms);
		return rgb(clamp(rgb[0]), clamp(rgb[1]), clamp(rgb[2]));
	}

	static int rainbow(int position, int length, double frequency, double saturation, double offset) {
		float f = (float) frequency;
		float hue = ((position * f + (f < 0 ? -f : 0) * length) / (length + 1) + (float) offset);
		return hsv(hue, (float) saturation, 1);
	}

	private static float[] lab(int color) {
		// Historical gradients transformed encoded RGB directly, with float
		// intermediates and truncation, rather than the modern linear-sRGB path.
		float[] lms = multiply(RGB_TO_LMS, new float[] {(color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f});
		for (int i = 0; i < 3; i++) lms[i] = (float) Math.cbrt(lms[i]);
		return multiply(LMS_TO_LAB, lms);
	}

	private static float[] multiply(float[][] matrix, float[] vector) {
		float[] result = new float[3];
		for (int i = 0; i < 3; i++) result[i] = matrix[i][0] * vector[0] + matrix[i][1] * vector[1] + matrix[i][2] * vector[2];
		return result;
	}

	private static float[] hsv(int color) {
		float r = (color >> 16 & 255) / 255f, g = (color >> 8 & 255) / 255f, b = (color & 255) / 255f;
		float high = Math.max(r, Math.max(g, b)), low = Math.min(r, Math.min(g, b)), delta = high - low;
		float hue = delta == 0 ? 0 : high == r ? (0.1666f * ((g - b) / delta) + 1) % 1
				: high == g ? (0.1666f * ((b - r) / delta) + 0.333f) % 1 : (0.1666f * ((r - g) / delta) + 0.666f) % 1;
		return new float[] {hue, high == 0 ? 0 : delta / high, high};
	}

	private static int hsv(float hue, float saturation, float value) {
		int sector = (int) (hue * 6) % 6;
		float fraction = hue * 6 - sector;
		float p = value * (1 - saturation), q = value * (1 - fraction * saturation), t = value * (1 - (1 - fraction) * saturation);
		return switch (sector) {
			case 0 -> rgb(value, t, p);
			case 1 -> rgb(q, value, p);
			case 2 -> rgb(p, value, t);
			case 3 -> rgb(p, q, value);
			case 4 -> rgb(t, p, value);
			case 5 -> rgb(value, p, q);
			default -> 0;
		};
	}

	private static float clamp(float value) {
		return Math.max(0, Math.min(1, value));
	}

	private static int rgb(float red, float green, float blue) {
		return ((int) (red * 255) & 255) << 16 | ((int) (green * 255) & 255) << 8 | ((int) (blue * 255) & 255);
	}
}
