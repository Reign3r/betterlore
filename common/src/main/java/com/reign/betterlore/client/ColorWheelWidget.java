package com.reign.betterlore.client;

import com.reign.betterlore.color.ColorMath;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? } else {
import net.minecraft.client.gui.GuiGraphics;
//? }
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
//? if >=1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//? }
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

public class ColorWheelWidget extends AbstractWidget {
	public static final int WHEEL_SIZE = 72;
	public static final int VALUE_WIDTH = 10;
	public static final int PADDING = 5;
	public static final int POPUP_WIDTH = WHEEL_SIZE + VALUE_WIDTH + 3 * PADDING;
	public static final int POPUP_HEIGHT = WHEEL_SIZE + 2 * PADDING;

	private static final int CELL_SIZE = 2;
	private static final int WHEEL_CELLS = (WHEEL_SIZE + CELL_SIZE - 1) / CELL_SIZE;
	private static final int VANILLA_PANEL = 0xFFC6C6C6;
	private static final int VANILLA_PANEL_LIGHT = 0xFFFFFFFF;
	private static final int VANILLA_PANEL_DARK = 0xFF555555;
	private static final int VANILLA_PANEL_SHADOW = 0xFF3A3A3A;
	private static final int MARKER_DARK = 0xFF000000;
	private static final int MARKER_LIGHT = 0xFFFFFFFF;
	private static final int[] WHEEL_CELL_COLORS = buildWheelCellColors();

	@FunctionalInterface
	private interface FillTarget {
		void fill(int x1, int y1, int x2, int y2, int color);
	}

	private final IntConsumer changedListener;
	private final int[] valueStripColors = new int[WHEEL_SIZE];
	private int rgb;
	private double cachedStripHue = Double.NaN;
	private double cachedStripSaturation = Double.NaN;
	private boolean draggingWheel;
	private boolean draggingValue;

	public ColorWheelWidget(int x, int y, int initialColor, IntConsumer changedListener) {
		super(x, y, POPUP_WIDTH, POPUP_HEIGHT, Component.translatable("gui.better_lore.color_wheel"));
		this.rgb = initialColor & 0xFFFFFF;
		this.changedListener = changedListener;
		this.visible = false;
		this.active = false;
	}

	public void setColor(int rgb) {
		this.rgb = rgb & 0xFFFFFF;
	}

	public void stopDragging() {
		draggingWheel = false;
		draggingValue = false;
	}

	//? if >=26.1 {
	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (!visible) {
			return;
		}

		drawContents(graphics::fill);
	}
	//? } else {
	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (!visible) {
			return;
		}

		drawContents(graphics::fill);
	}
	//? }

	//? if >=1.21.9 {
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!visible || !active || event.button() != 0 || !isMouseOver(event.x(), event.y())) {
			return false;
		}

		if (isInsideWheel(event.x(), event.y())) {
			draggingWheel = true;
			pickFromWheel(event.x(), event.y());
			return true;
		}

		if (isInsideValueStrip(event.x(), event.y())) {
			draggingValue = true;
			pickFromValueStrip(event.y());
			return true;
		}

		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (!visible || !active || event.button() != 0) {
			return false;
		}

		if (draggingWheel) {
			pickFromWheel(event.x(), event.y());
			return true;
		}

		if (draggingValue) {
			pickFromValueStrip(event.y());
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0 && (draggingWheel || draggingValue)) {
			stopDragging();
			return true;
		}

		return super.mouseReleased(event);
	}
	//? } else {
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!visible || !active || button != 0 || !isMouseOver(mouseX, mouseY)) {
			return false;
		}

		if (isInsideWheel(mouseX, mouseY)) {
			draggingWheel = true;
			pickFromWheel(mouseX, mouseY);
			return true;
		}

		if (isInsideValueStrip(mouseX, mouseY)) {
			draggingValue = true;
			pickFromValueStrip(mouseY);
			return true;
		}

		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (!visible || !active || button != 0) {
			return false;
		}

		if (draggingWheel) {
			pickFromWheel(mouseX, mouseY);
			return true;
		}

		if (draggingValue) {
			pickFromValueStrip(mouseY);
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && (draggingWheel || draggingValue)) {
			stopDragging();
			return true;
		}

		return super.mouseReleased(mouseX, mouseY, button);
	}
	//? }

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}

	private void drawContents(FillTarget graphics) {
		drawPanel(graphics);
		drawWheel(graphics);
		drawValueStrip(graphics);
		drawMarkers(graphics);
	}

	private void drawPanel(FillTarget graphics) {
		int x = getX();
		int y = getY();
		graphics.fill(x, y, x + width, y + height, VANILLA_PANEL_SHADOW);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, VANILLA_PANEL);
		graphics.fill(x + 1, y + 1, x + width - 1, y + 2, VANILLA_PANEL_LIGHT);
		graphics.fill(x + 1, y + 1, x + 2, y + height - 1, VANILLA_PANEL_LIGHT);
		graphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, VANILLA_PANEL_DARK);
		graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, VANILLA_PANEL_DARK);
	}

	private void drawWheel(FillTarget graphics) {
		int wheelX = wheelX();
		int wheelY = wheelY();
		double radius = WHEEL_SIZE / 2.0D;
		double centerX = wheelX + radius;
		double centerY = wheelY + radius;

		for (int cellY = 0; cellY < WHEEL_CELLS; cellY++) {
			int y1 = wheelY + cellY * CELL_SIZE;
			int y2 = Math.min(y1 + CELL_SIZE, wheelY + WHEEL_SIZE);
			for (int cellX = 0; cellX < WHEEL_CELLS; cellX++) {
				int color = WHEEL_CELL_COLORS[cellY * WHEEL_CELLS + cellX];
				if (color != 0) {
					int x1 = wheelX + cellX * CELL_SIZE;
					int x2 = Math.min(x1 + CELL_SIZE, wheelX + WHEEL_SIZE);
					graphics.fill(x1, y1, x2, y2, color);
				}
			}
		}

		graphics.fill((int) centerX - 1, wheelY, (int) centerX + 1, wheelY + WHEEL_SIZE, 0x22000000);
		graphics.fill(wheelX, (int) centerY - 1, wheelX + WHEEL_SIZE, (int) centerY + 1, 0x22000000);
	}

	private void drawValueStrip(FillTarget graphics) {
		ColorMath.Hsv hsv = ColorMath.rgbToHsv(rgb);
		ensureValueStripCache(hsv.hue(), hsv.saturation());
		int stripX = valueStripX();
		int stripY = valueStripY();
		graphics.fill(stripX - 1, stripY - 1, stripX + VALUE_WIDTH + 1, stripY + WHEEL_SIZE + 1, MARKER_DARK);
		for (int y = 0; y < WHEEL_SIZE; y++) {
			graphics.fill(stripX, stripY + y, stripX + VALUE_WIDTH, stripY + y + 1, valueStripColors[y]);
		}
	}

	private void drawMarkers(FillTarget graphics) {
		ColorMath.Hsv hsv = ColorMath.rgbToHsv(rgb);
		int wheelX = wheelX();
		int wheelY = wheelY();
		double radius = WHEEL_SIZE / 2.0D;
		double angle = hsv.hue() * Math.PI * 2.0D;
		int markerX = (int) Math.round(wheelX + radius + Math.cos(angle) * hsv.saturation() * radius);
		int markerY = (int) Math.round(wheelY + radius + Math.sin(angle) * hsv.saturation() * radius);

		graphics.fill(markerX - 3, markerY, markerX + 4, markerY + 1, MARKER_DARK);
		graphics.fill(markerX, markerY - 3, markerX + 1, markerY + 4, MARKER_DARK);
		graphics.fill(markerX - 2, markerY, markerX + 3, markerY + 1, MARKER_LIGHT);
		graphics.fill(markerX, markerY - 2, markerX + 1, markerY + 3, MARKER_LIGHT);

		int stripX = valueStripX();
		int stripY = valueStripY();
		int valueY = stripY + (int) Math.round((1.0D - hsv.value()) * (WHEEL_SIZE - 1));
		graphics.fill(stripX - 2, valueY - 1, stripX + VALUE_WIDTH + 2, valueY + 2, MARKER_DARK);
		graphics.fill(stripX - 1, valueY, stripX + VALUE_WIDTH + 1, valueY + 1, MARKER_LIGHT);
	}

	private boolean isInsideWheel(double mouseX, double mouseY) {
		double radius = WHEEL_SIZE / 2.0D;
		double dx = mouseX - (wheelX() + radius);
		double dy = mouseY - (wheelY() + radius);
		return dx * dx + dy * dy <= radius * radius;
	}

	private boolean isInsideValueStrip(double mouseX, double mouseY) {
		return mouseX >= valueStripX()
				&& mouseX < valueStripX() + VALUE_WIDTH
				&& mouseY >= valueStripY()
				&& mouseY < valueStripY() + WHEEL_SIZE;
	}

	private void pickFromWheel(double mouseX, double mouseY) {
		ColorMath.Hsv current = ColorMath.rgbToHsv(rgb);
		double radius = WHEEL_SIZE / 2.0D;
		double dx = mouseX - (wheelX() + radius);
		double dy = mouseY - (wheelY() + radius);
		double distance = Math.min(radius, Math.sqrt(dx * dx + dy * dy));
		double hue = Math.atan2(dy, dx) / (Math.PI * 2.0D);
		if (hue < 0.0D) {
			hue += 1.0D;
		}
		double saturation = Math.max(0.0D, Math.min(1.0D, distance / radius));
		double value = current.value() <= 0.01D ? 1.0D : current.value();
		setRgbFromUser(ColorMath.hsvToRgb(hue, saturation, value));
	}

	private void pickFromValueStrip(double mouseY) {
		ColorMath.Hsv current = ColorMath.rgbToHsv(rgb);
		double t = (mouseY - valueStripY()) / Math.max(1.0D, WHEEL_SIZE - 1.0D);
		double value = 1.0D - Math.max(0.0D, Math.min(1.0D, t));
		setRgbFromUser(ColorMath.hsvToRgb(current.hue(), current.saturation(), value));
	}

	private void setRgbFromUser(int rgb) {
		int newRgb = rgb & 0xFFFFFF;
		if (this.rgb != newRgb) {
			this.rgb = newRgb;
			if (changedListener != null) {
				changedListener.accept(newRgb);
			}
		}
	}

	private int wheelX() {
		return getX() + PADDING;
	}

	private int wheelY() {
		return getY() + PADDING;
	}

	private int valueStripX() {
		return wheelX() + WHEEL_SIZE + PADDING;
	}

	private int valueStripY() {
		return wheelY();
	}

	private void ensureValueStripCache(double hue, double saturation) {
		if (Double.compare(cachedStripHue, hue) == 0 && Double.compare(cachedStripSaturation, saturation) == 0) {
			return;
		}

		cachedStripHue = hue;
		cachedStripSaturation = saturation;
		for (int y = 0; y < WHEEL_SIZE; y++) {
			double value = 1.0D - (double) y / Math.max(1, WHEEL_SIZE - 1);
			valueStripColors[y] = 0xFF000000 | ColorMath.hsvToRgb(hue, saturation, value);
		}
	}

	private static int[] buildWheelCellColors() {
		int[] colors = new int[WHEEL_CELLS * WHEEL_CELLS];
		double radius = WHEEL_SIZE / 2.0D;
		for (int cellY = 0; cellY < WHEEL_CELLS; cellY++) {
			for (int cellX = 0; cellX < WHEEL_CELLS; cellX++) {
				double sampleX = Math.min(WHEEL_SIZE - 0.5D, cellX * CELL_SIZE + CELL_SIZE / 2.0D);
				double sampleY = Math.min(WHEEL_SIZE - 0.5D, cellY * CELL_SIZE + CELL_SIZE / 2.0D);
				double dx = sampleX - radius;
				double dy = sampleY - radius;
				double distance = Math.sqrt(dx * dx + dy * dy);
				if (distance <= radius) {
					double hue = Math.atan2(dy, dx) / (Math.PI * 2.0D);
					if (hue < 0.0D) {
						hue += 1.0D;
					}
					double saturation = Math.min(1.0D, distance / radius);
					colors[cellY * WHEEL_CELLS + cellX] = 0xFF000000 | ColorMath.hsvToRgb(hue, saturation, 1.0D);
				}
			}
		}
		return colors;
	}

}
