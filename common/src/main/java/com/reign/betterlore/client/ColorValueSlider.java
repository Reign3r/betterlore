package com.reign.betterlore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
//? if >=1.21.9 {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
//? } else {
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
//? }
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

public class ColorValueSlider extends AbstractSliderButton {
	private final String label;
	private final IntConsumer changedListener;
	private int intValue;
	private String typedDigits = "";

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

	//? if >=1.21.9 {
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isPaste()) {
			String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
			if (setIntValueFromText(clipboard)) {
				return true;
			}
		}

		if (event.isLeft()) {
			adjustFromKeyboard(-1);
			return true;
		}

		if (event.isRight()) {
			adjustFromKeyboard(1);
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		int codepoint = event.codepoint();
		if (codepoint >= '0' && codepoint <= '9') {
			appendTypedDigit(codepoint - '0');
			return true;
		}

		typedDigits = "";
		return super.charTyped(event);
	}
	//? } else {
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (Screen.isPaste(keyCode)) {
			String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
			if (setIntValueFromText(clipboard)) {
				return true;
			}
		}

		if (keyCode == GLFW.GLFW_KEY_LEFT) {
			adjustFromKeyboard(-1);
			return true;
		}

		if (keyCode == GLFW.GLFW_KEY_RIGHT) {
			adjustFromKeyboard(1);
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char character, int modifiers) {
		if (character >= '0' && character <= '9') {
			appendTypedDigit(character - '0');
			return true;
		}

		typedDigits = "";
		return super.charTyped(character, modifiers);
	}
	//? }

	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
		if (!focused) {
			typedDigits = "";
		}
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(label + ": " + intValue));
	}

	@Override
	protected void applyValue() {
		typedDigits = "";
		intValue = clamp((int) Math.round(value * 255.0D));
		if (changedListener != null) {
			changedListener.accept(intValue);
		}
	}

	private void adjustFromKeyboard(int amount) {
		typedDigits = "";
		setIntValueFromUser(intValue + amount);
	}

	private void appendTypedDigit(int digit) {
		if (typedDigits.length() >= 3) {
			typedDigits = "";
		}
		typedDigits += digit;
		setIntValueFromUser(Integer.parseInt(typedDigits));
	}

	private boolean setIntValueFromText(String raw) {
		if (raw == null) {
			return false;
		}

		String text = raw.trim();
		if (text.isEmpty() || text.length() > 3) {
			return false;
		}

		for (int i = 0; i < text.length(); i++) {
			if (!Character.isDigit(text.charAt(i))) {
				return false;
			}
		}

		typedDigits = text;
		setIntValueFromUser(Integer.parseInt(text));
		return true;
	}

	private void setIntValueFromUser(int value) {
		int oldValue = intValue;
		setIntValue(value);
		if (changedListener != null && intValue != oldValue) {
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
