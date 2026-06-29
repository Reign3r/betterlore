package com.maksy.itemlore.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

public class ColorValueSlider extends AbstractSliderButton {
	private final String label;
	private final IntConsumer changedListener;
	private int intValue;

	public ColorValueSlider(int x, int y, int width, int height, String label, int initialValue, IntConsumer changedListener) {
		super(x, y, width, height, Component.empty(), normalized(initialValue));
		this.label = label;
		this.changedListener = changedListener;
		this.intValue = clamp(initialValue);
		updateMessage();
	}

	public int intValue() {
		return intValue;
	}

	public void setIntValue(int value) {
		this.intValue = clamp(value);
		this.value = normalized(this.intValue);
		updateMessage();
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(label + ": " + intValue));
	}

	@Override
	protected void applyValue() {
		intValue = clamp((int) Math.round(value * 255.0D));
		if (changedListener != null) {
			changedListener.accept(intValue);
		}
	}

	private static double normalized(int value) {
		return clamp(value) / 255.0D;
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(255, value));
	}
}
