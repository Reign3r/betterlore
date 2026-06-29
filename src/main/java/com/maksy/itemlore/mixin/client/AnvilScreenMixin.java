package com.maksy.itemlore.mixin.client;

import com.maksy.itemlore.access.AnvilLoreScreenBridge;
import com.maksy.itemlore.client.ColorValueSlider;
import com.maksy.itemlore.lore.LoreMarkupParser;
import com.maksy.itemlore.lore.ParseResult;
import com.maksy.itemlore.net.ServerboundAnvilLoreUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin extends ItemCombinerScreen<AnvilMenu> implements AnvilLoreScreenBridge {
	@Unique
	private static final int itemLore$PREFERRED_PANEL_WIDTH = 190;
	@Unique
	private static final int itemLore$MIN_PANEL_WIDTH = 166;
	@Unique
	private static final int itemLore$PANEL_GAP = 6;
	@Unique
	private static final int itemLore$SCREEN_MARGIN = 6;
	@Unique
	private static final int itemLore$PANEL_PADDING = 7;
	@Unique
	private static final int itemLore$SEND_DELAY_TICKS = 6;

	@Unique
	private static final int itemLore$VANILLA_PANEL = 0xFFC6C6C6;
	@Unique
	private static final int itemLore$VANILLA_PANEL_LIGHT = 0xFFFFFFFF;
	@Unique
	private static final int itemLore$VANILLA_PANEL_DARK = 0xFF555555;
	@Unique
	private static final int itemLore$VANILLA_PANEL_SHADOW = 0xFF000000;
	@Unique
	private static final int itemLore$VANILLA_TEXT = 0xFF404040;
	@Unique
	private static final int itemLore$VANILLA_TEXT_MUTED = 0xFF666666;
	@Unique
	private static final int itemLore$VANILLA_TEXT_ERROR = 0xFFFF5555;

	@Shadow
	private EditBox name;

	@Unique
	private MultiLineEditBox itemLore$editor;
	@Unique
	private ColorValueSlider itemLore$redSlider;
	@Unique
	private ColorValueSlider itemLore$greenSlider;
	@Unique
	private ColorValueSlider itemLore$blueSlider;
	@Unique
	private Button itemLore$copyHexButton;
	@Unique
	private Button itemLore$insertColorButton;
	@Unique
	private Button itemLore$insertGradientButton;
	@Unique
	private int itemLore$sessionId = 0;
	@Unique
	private String itemLore$raw = "";
	@Unique
	private ParseResult itemLore$parseResult = LoreMarkupParser.parse("");
	@Unique
	private boolean itemLore$dirty = false;
	@Unique
	private boolean itemLore$suppressListener = false;
	@Unique
	private int itemLore$sendDelay = 0;
	@Unique
	private int itemLore$panelX;
	@Unique
	private int itemLore$panelY;
	@Unique
	private int itemLore$panelWidth;
	@Unique
	private int itemLore$panelHeight;
	@Unique
	private int itemLore$pickerRed = 255;
	@Unique
	private int itemLore$pickerGreen = 102;
	@Unique
	private int itemLore$pickerBlue = 0;

	private AnvilScreenMixin(AnvilMenu menu, Inventory inventory, Component title, Identifier menuResource) {
		super(menu, inventory, title, menuResource);
	}

	@Inject(method = "subInit", at = @At("HEAD"))
	private void itemLore$reserveRightSidePanelSpace(CallbackInfo ci) {
		int panelWidth = itemLore$preferredPanelWidthForScreen();
		int combinedWidth = imageWidth + itemLore$PANEL_GAP + panelWidth;

		if (panelWidth >= itemLore$MIN_PANEL_WIDTH) {
			leftPos = Math.max(itemLore$SCREEN_MARGIN, (width - combinedWidth) / 2);
		}
	}

	@Inject(method = "subInit", at = @At("TAIL"))
	private void itemLore$initLoreWidgets(CallbackInfo ci) {
		itemLore$editor = MultiLineEditBox.builder()
				.setX(0)
				.setY(0)
				.setPlaceholder(Component.translatable("gui.item_lore.placeholder"))
				.build(font, 120, 58, Component.translatable("gui.item_lore.editor"));

		// Do not use MultiLineEditBox#setCharacterLimit here: it renders a raw
		// "0 / 4096" counter, which is an implementation detail rather than the
		// player-facing lore limit. The server still enforces the raw packet cap,
		// and itemLore$onEditorChanged clamps oversized client input before send.
		itemLore$editor.setLineLimit(LoreMarkupParser.MAX_LINES);
		itemLore$editor.setValueListener(this::itemLore$onEditorChanged);
		addRenderableWidget(itemLore$editor);

		itemLore$redSlider = new ColorValueSlider(0, 0, 100, 12, "R", itemLore$pickerRed, value -> {
			itemLore$pickerRed = value;
			itemLore$updateColorButtons();
		});
		itemLore$greenSlider = new ColorValueSlider(0, 0, 100, 12, "G", itemLore$pickerGreen, value -> {
			itemLore$pickerGreen = value;
			itemLore$updateColorButtons();
		});
		itemLore$blueSlider = new ColorValueSlider(0, 0, 100, 12, "B", itemLore$pickerBlue, value -> {
			itemLore$pickerBlue = value;
			itemLore$updateColorButtons();
		});
		addRenderableWidget(itemLore$redSlider);
		addRenderableWidget(itemLore$greenSlider);
		addRenderableWidget(itemLore$blueSlider);

		itemLore$copyHexButton = Button.builder(Component.empty(), button -> itemLore$copyCurrentHex())
				.bounds(0, 0, 60, 18)
				.build();
		itemLore$insertColorButton = Button.builder(Component.translatable("gui.item_lore.insert_color"), button -> itemLore$appendToLore("[color:" + itemLore$currentHex() + "][/color]"))
				.bounds(0, 0, 60, 18)
				.build();
		itemLore$insertGradientButton = Button.builder(Component.translatable("gui.item_lore.insert_gradient"), button -> itemLore$appendToLore("[gradient:" + itemLore$currentHex() + "][/gradient:" + itemLore$currentHex() + "]"))
				.bounds(0, 0, 60, 18)
				.build();
		addRenderableWidget(itemLore$copyHexButton);
		addRenderableWidget(itemLore$insertColorButton);
		addRenderableWidget(itemLore$insertGradientButton);

		itemLore$setEditorValue(itemLore$raw, false);
		itemLore$layoutLoreWidgets();
		itemLore$updateColorButtons();
	}

	@Inject(method = "containerTick", at = @At("TAIL"))
	private void itemLore$tickLoreSender(CallbackInfo ci) {
		if (itemLore$sendDelay > 0) {
			itemLore$sendDelay--;
		}

		if (itemLore$dirty && itemLore$sendDelay <= 0 && ClientPlayNetworking.canSend(ServerboundAnvilLoreUpdatePayload.TYPE)) {
			ClientPlayNetworking.send(new ServerboundAnvilLoreUpdatePayload(menu.containerId, itemLore$sessionId, itemLore$raw));
			itemLore$dirty = false;
		}
	}

	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void itemLore$extractLorePanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (itemLore$editor == null || !itemLore$editor.visible) {
			return;
		}

		itemLore$drawVanillaPanel(graphics, itemLore$panelX, itemLore$panelY, itemLore$panelWidth, itemLore$panelHeight);

		int textX = itemLore$panelX + itemLore$PANEL_PADDING;
		int contentWidth = itemLore$panelWidth - 2 * itemLore$PANEL_PADDING;
		int titleY = itemLore$panelY + 7;

		graphics.text(font, Component.translatable("gui.item_lore.title"), textX, titleY, itemLore$VANILLA_TEXT, false);

		Component count = Component.translatable(
				"gui.item_lore.symbols",
				itemLore$parseResult.visibleCodePoints(),
				LoreMarkupParser.MAX_VISIBLE_CODEPOINTS
		);
		int countColor = itemLore$parseResult.isSuccess() ? itemLore$VANILLA_TEXT_MUTED : itemLore$VANILLA_TEXT_ERROR;
		graphics.text(font, count, textX, titleY + 11, countColor, false);

		Component cost = Component.translatable("gui.item_lore.cost");
		graphics.text(font, cost, itemLore$panelX + itemLore$panelWidth - itemLore$PANEL_PADDING - font.width(cost), titleY + 11, itemLore$VANILLA_TEXT_MUTED, false);

		int infoY = itemLore$editor.getBottom() + 5;
		if (!itemLore$parseResult.isSuccess()) {
			graphics.textWithWordWrap(font, Component.literal(itemLore$parseResult.errorMessage()), textX, infoY, contentWidth, itemLore$VANILLA_TEXT_ERROR, false);
		} else {
			graphics.textWithWordWrap(font, Component.translatable("gui.item_lore.hint"), textX, infoY, contentWidth, itemLore$VANILLA_TEXT_MUTED, false);
		}

		int colorTitleY = itemLore$redSlider.getY() - 13;
		Component colorTitle = Component.translatable("gui.item_lore.color_helper");
		graphics.text(font, colorTitle, textX, colorTitleY, itemLore$VANILLA_TEXT, false);

		int previewColor = 0xFF000000 | itemLore$currentColor();
		Component previewWord = Component.translatable("gui.item_lore.color_preview");
		int swatchY = colorTitleY - 2;
		int previewX = textX + font.width(colorTitle) + 8;
		int swatchX = previewX + font.width(previewWord) + 4;
		int rightEdge = itemLore$panelX + itemLore$panelWidth - itemLore$PANEL_PADDING;
		if (swatchX + 12 > rightEdge) {
			swatchX = rightEdge - 12;
			previewX = Math.max(textX, swatchX - 4 - font.width(previewWord));
		}
		graphics.text(font, previewWord, previewX, colorTitleY, previewColor, false);
		graphics.fill(swatchX, swatchY, swatchX + 12, swatchY + 12, itemLore$VANILLA_PANEL_SHADOW);
		graphics.fill(swatchX + 1, swatchY + 1, swatchX + 11, swatchY + 11, previewColor);

		Component hex = Component.translatable("gui.item_lore.hex", itemLore$currentHex());
		graphics.text(font, hex, textX, itemLore$blueSlider.getBottom() + 4, itemLore$VANILLA_TEXT_MUTED, false);
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void itemLore$routeKeysToLoreEditor(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (itemLore$editor != null && itemLore$editor.visible && itemLore$editor.isFocused() && itemLore$editor.keyPressed(event)) {
			cir.setReturnValue(true);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (itemLore$editor != null && itemLore$editor.visible && event.button() == 0) {
			if (itemLore$isInEditorBounds(event.x(), event.y())) {
				itemLore$focusLoreEditor();
				itemLore$editor.mouseClicked(event, doubleClick);
				return true;
			}

			if (itemLore$isInPanelBounds(event.x(), event.y())) {
				itemLore$unfocusTextFields();
				if (super.mouseClicked(event, doubleClick)) {
					return true;
				}
				return true;
			}

			itemLore$editor.setFocused(false);
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void itemLore$acceptServerLoreState(int containerId, int sessionId, String rawLoreMarkup) {
		if (containerId != menu.containerId) {
			return;
		}

		if (itemLore$sessionId != sessionId || !itemLore$dirty) {
			itemLore$sessionId = sessionId;
			itemLore$setEditorValue(rawLoreMarkup, false);
			itemLore$dirty = false;
			itemLore$sendDelay = 0;
			return;
		}

		itemLore$sessionId = sessionId;
	}

	@Unique
	private void itemLore$onEditorChanged(String value) {
		if (itemLore$suppressListener) {
			return;
		}

		String trimmed = itemLore$limitRawInput(value == null ? "" : value);
		if (!trimmed.equals(value)) {
			itemLore$setEditorValue(trimmed, true);
			itemLore$sendDelay = itemLore$SEND_DELAY_TICKS;
			return;
		}

		itemLore$raw = trimmed;
		itemLore$parseResult = LoreMarkupParser.parse(trimmed);
		itemLore$dirty = true;
		itemLore$sendDelay = itemLore$SEND_DELAY_TICKS;
	}

	@Unique
	private void itemLore$setEditorValue(String value, boolean dirty) {
		itemLore$raw = itemLore$limitRawInput(value == null ? "" : value);
		itemLore$parseResult = LoreMarkupParser.parse(itemLore$raw);
		itemLore$dirty = dirty;

		if (itemLore$editor != null) {
			itemLore$suppressListener = true;
			itemLore$editor.setValue(itemLore$raw);
			itemLore$suppressListener = false;
		}
	}

	@Unique
	private String itemLore$limitRawInput(String value) {
		return value.length() <= LoreMarkupParser.MAX_RAW_CHARS
				? value
				: value.substring(0, LoreMarkupParser.MAX_RAW_CHARS);
	}

	@Unique
	private void itemLore$layoutLoreWidgets() {
		if (itemLore$editor == null) {
			return;
		}

		itemLore$panelX = leftPos + imageWidth + itemLore$PANEL_GAP;
		int availableRight = width - itemLore$panelX - itemLore$SCREEN_MARGIN;
		itemLore$panelWidth = Math.min(itemLore$PREFERRED_PANEL_WIDTH, Math.max(0, availableRight));
		boolean visible = itemLore$panelWidth >= itemLore$MIN_PANEL_WIDTH;
		itemLore$setLoreWidgetsVisible(visible);
		if (!visible) {
			return;
		}

		itemLore$panelHeight = Math.min(height - 2 * itemLore$SCREEN_MARGIN, Math.max(imageHeight, 232));
		itemLore$panelY = Math.max(itemLore$SCREEN_MARGIN, Math.min(topPos - Math.max(0, (itemLore$panelHeight - imageHeight) / 2), height - itemLore$panelHeight - itemLore$SCREEN_MARGIN));

		int textX = itemLore$panelX + itemLore$PANEL_PADDING;
		int contentWidth = itemLore$panelWidth - 2 * itemLore$PANEL_PADDING;
		int editorY = itemLore$panelY + 31;
		int editorHeight = 58;

		// Avoid AbstractWidget#setRectangle here. Its argument order is easy to get
		// wrong across mapping changes, and a wrong order turns the editor into a
		// huge invisible/clickable rectangle over the vanilla anvil slots.
		itemLore$editor.setX(textX);
		itemLore$editor.setY(editorY);
		itemLore$editor.setWidth(contentWidth);
		itemLore$editor.setHeight(editorHeight);

		int sliderY = editorY + editorHeight + 43;
		itemLore$positionWidget(itemLore$redSlider, textX, sliderY, contentWidth, 12);
		itemLore$positionWidget(itemLore$greenSlider, textX, sliderY + 15, contentWidth, 12);
		itemLore$positionWidget(itemLore$blueSlider, textX, sliderY + 30, contentWidth, 12);

		int buttonY = sliderY + 58;
		int smallButtonWidth = (contentWidth - 4) / 2;
		itemLore$positionWidget(itemLore$copyHexButton, textX, buttonY, smallButtonWidth, 18);
		itemLore$positionWidget(itemLore$insertColorButton, textX + smallButtonWidth + 4, buttonY, contentWidth - smallButtonWidth - 4, 18);
		itemLore$positionWidget(itemLore$insertGradientButton, textX, buttonY + 21, contentWidth, 18);
	}

	@Unique
	private void itemLore$positionWidget(Button widget, int x, int y, int widgetWidth, int widgetHeight) {
		widget.setX(x);
		widget.setY(y);
		widget.setWidth(widgetWidth);
		widget.setHeight(widgetHeight);
	}

	@Unique
	private void itemLore$positionWidget(ColorValueSlider widget, int x, int y, int widgetWidth, int widgetHeight) {
		widget.setX(x);
		widget.setY(y);
		widget.setWidth(widgetWidth);
		widget.setHeight(widgetHeight);
	}

	@Unique
	private void itemLore$setLoreWidgetsVisible(boolean visible) {
		itemLore$editor.visible = visible;
		itemLore$editor.active = visible;
		if (itemLore$redSlider != null) {
			itemLore$redSlider.visible = visible;
			itemLore$redSlider.active = visible;
			itemLore$greenSlider.visible = visible;
			itemLore$greenSlider.active = visible;
			itemLore$blueSlider.visible = visible;
			itemLore$blueSlider.active = visible;
			itemLore$copyHexButton.visible = visible;
			itemLore$copyHexButton.active = visible;
			itemLore$insertColorButton.visible = visible;
			itemLore$insertColorButton.active = visible;
			itemLore$insertGradientButton.visible = visible;
			itemLore$insertGradientButton.active = visible;
		}
	}

	@Unique
	private int itemLore$preferredPanelWidthForScreen() {
		int available = width - imageWidth - itemLore$PANEL_GAP - 2 * itemLore$SCREEN_MARGIN;
		return Math.min(itemLore$PREFERRED_PANEL_WIDTH, Math.max(0, available));
	}

	@Unique
	private void itemLore$focusLoreEditor() {
		setFocused(itemLore$editor);
		itemLore$editor.setFocused(true);
		if (name != null) {
			name.setFocused(false);
		}
	}

	@Unique
	private void itemLore$unfocusTextFields() {
		itemLore$editor.setFocused(false);
		if (name != null) {
			name.setFocused(false);
		}
	}

	@Unique
	private int itemLore$currentColor() {
		return ((itemLore$pickerRed & 0xFF) << 16) | ((itemLore$pickerGreen & 0xFF) << 8) | (itemLore$pickerBlue & 0xFF);
	}

	@Unique
	private String itemLore$currentHex() {
		return LoreMarkupParser.formatHex(itemLore$currentColor());
	}

	@Unique
	private void itemLore$updateColorButtons() {
		if (itemLore$copyHexButton != null) {
			itemLore$copyHexButton.setMessage(Component.translatable("gui.item_lore.copy_hex", itemLore$currentHex()));
		}
	}

	@Unique
	private void itemLore$copyCurrentHex() {
		if (minecraft != null) {
			minecraft.keyboardHandler.setClipboard(itemLore$currentHex());
		}
	}

	@Unique
	private void itemLore$appendToLore(String insertion) {
		String separator = itemLore$raw.isEmpty() || itemLore$raw.endsWith("\n") ? "" : "\n";
		itemLore$setEditorValue(itemLore$raw + separator + insertion, true);
		itemLore$sendDelay = itemLore$SEND_DELAY_TICKS;
		itemLore$focusLoreEditor();
	}

	@Unique
	private void itemLore$drawVanillaPanel(GuiGraphicsExtractor graphics, int x, int y, int panelWidth, int panelHeight) {
		graphics.fill(x, y, x + panelWidth, y + panelHeight, itemLore$VANILLA_PANEL);

		graphics.fill(x, y, x + panelWidth, y + 1, itemLore$VANILLA_PANEL_LIGHT);
		graphics.fill(x, y, x + 1, y + panelHeight, itemLore$VANILLA_PANEL_LIGHT);
		graphics.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, itemLore$VANILLA_PANEL_SHADOW);
		graphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, itemLore$VANILLA_PANEL_SHADOW);

		graphics.fill(x + 1, y + 1, x + panelWidth - 1, y + 2, itemLore$VANILLA_PANEL_DARK);
		graphics.fill(x + 1, y + 1, x + 2, y + panelHeight - 1, itemLore$VANILLA_PANEL_DARK);
	}

	@Unique
	private boolean itemLore$isInEditorBounds(double mouseX, double mouseY) {
		return itemLore$isInBounds(mouseX, mouseY, itemLore$editor.getX(), itemLore$editor.getY(), itemLore$editor.getWidth(), itemLore$editor.getHeight());
	}

	@Unique
	private boolean itemLore$isInPanelBounds(double mouseX, double mouseY) {
		return itemLore$isInBounds(mouseX, mouseY, itemLore$panelX, itemLore$panelY, itemLore$panelWidth, itemLore$panelHeight);
	}

	@Unique
	private boolean itemLore$isInBounds(double mouseX, double mouseY, int x, int y, int boundsWidth, int boundsHeight) {
		return mouseX >= x && mouseX < x + boundsWidth && mouseY >= y && mouseY < y + boundsHeight;
	}
}
