package com.maksy.itemlore.mixin.client;

import com.maksy.itemlore.access.AnvilLoreScreenBridge;
import com.maksy.itemlore.client.ColorValueSlider;
import com.maksy.itemlore.lore.LoreMarkupParser;
import com.maksy.itemlore.lore.ParseResult;
import com.maksy.itemlore.net.ServerboundAnvilLoreUpdatePayload;
import com.maksy.itemlore.net.ServerboundAnvilNameUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
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

import java.util.List;

@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin extends ItemCombinerScreen<AnvilMenu> implements AnvilLoreScreenBridge {
	@Unique
	private static final int itemLore$PREFERRED_PANEL_WIDTH = 190;
	@Unique
	private static final int itemLore$MIN_PANEL_WIDTH = 166;
	@Unique
	private static final int itemLore$PANEL_GAP = 10;
	@Unique
	private static final int itemLore$SCREEN_MARGIN = 6;
	@Unique
	private static final int itemLore$PANEL_PADDING = 7;
	@Unique
	private static final int itemLore$SEND_DELAY_TICKS = 6;
	@Unique
	private static final int itemLore$LORE_BUTTON_WIDTH = 44;
	@Unique
	private static final int itemLore$LORE_BUTTON_HEIGHT = 18;
	@Unique
	private static final int itemLore$SMALL_BUTTON_SIZE = 13;

	@Unique
	private static final int itemLore$VANILLA_PANEL = 0xFFC6C6C6;
	@Unique
	private static final int itemLore$VANILLA_PANEL_LIGHT = 0xFFFFFFFF;
	@Unique
	private static final int itemLore$VANILLA_PANEL_DARK = 0xFF555555;
	@Unique
	private static final int itemLore$VANILLA_PANEL_SHADOW = 0xFF3A3A3A;
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
	private Button itemLore$toggleButton;
	@Unique
	private Button itemLore$helpButton;
	@Unique
	private Button itemLore$closeButton;
	@Unique
	private Button itemLore$firstColorButton;
	@Unique
	private Button itemLore$secondColorButton;
	@Unique
	private Button itemLore$copyHexButton;
	@Unique
	private Button itemLore$insertColorButton;
	@Unique
	private Button itemLore$insertGradientButton;
	@Unique
	private Button itemLore$formatBoldButton;
	@Unique
	private Button itemLore$formatItalicButton;
	@Unique
	private Button itemLore$formatUnderlineButton;
	@Unique
	private Button itemLore$formatStrikeButton;
	@Unique
	private Button itemLore$formatObfuscatedButton;

	@Unique
	private boolean itemLore$panelOpen = false;
	@Unique
	private boolean itemLore$insertTargetName = false;
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
	private int itemLore$pickerOneRed = 255;
	@Unique
	private int itemLore$pickerOneGreen = 102;
	@Unique
	private int itemLore$pickerOneBlue = 0;
	@Unique
	private int itemLore$pickerTwoRed = 170;
	@Unique
	private int itemLore$pickerTwoGreen = 18;
	@Unique
	private int itemLore$pickerTwoBlue = 8;
	@Unique
	private int itemLore$activeColorSlot = 0;
	@Unique
	private String itemLore$lastObservedName = "";
	@Unique
	private String itemLore$lastSentName = "";
	@Unique
	private boolean itemLore$nameDirty = false;
	@Unique
	private boolean itemLore$suppressNameSync = false;
	@Unique
	private int itemLore$nameSendDelay = 0;
	@Unique
	private int itemLore$nameStateGraceTicks = 0;

	private AnvilScreenMixin(AnvilMenu menu, Inventory inventory, Component title, Identifier menuResource) {
		super(menu, inventory, title, menuResource);
	}

	@Inject(method = "subInit", at = @At("HEAD"))
	private void itemLore$reserveRightSidePanelSpace(CallbackInfo ci) {
		if (!itemLore$panelOpen || !itemLore$serverSupportsUi()) {
			return;
		}

		int panelWidth = itemLore$preferredPanelWidthForScreen();
		int combinedWidth = imageWidth + itemLore$PANEL_GAP + panelWidth;
		if (panelWidth >= itemLore$MIN_PANEL_WIDTH && width >= combinedWidth + 2 * itemLore$SCREEN_MARGIN) {
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

		itemLore$toggleButton = Button.builder(Component.translatable("gui.item_lore.button"), button -> itemLore$setPanelOpen(true))
				.bounds(0, 0, itemLore$LORE_BUTTON_WIDTH, itemLore$LORE_BUTTON_HEIGHT)
				.build();
		itemLore$helpButton = Button.builder(Component.literal("?"), button -> {})
				.bounds(0, 0, itemLore$SMALL_BUTTON_SIZE, itemLore$SMALL_BUTTON_SIZE)
				.build();
		itemLore$closeButton = Button.builder(Component.literal("X"), button -> itemLore$setPanelOpen(false))
				.bounds(0, 0, itemLore$SMALL_BUTTON_SIZE, itemLore$SMALL_BUTTON_SIZE)
				.build();
		addRenderableWidget(itemLore$toggleButton);
		addRenderableWidget(itemLore$helpButton);
		addRenderableWidget(itemLore$closeButton);

		itemLore$redSlider = new ColorValueSlider(0, 0, 100, 12, "R", itemLore$activeRed(), value -> itemLore$setActiveColorComponent(0, value));
		itemLore$greenSlider = new ColorValueSlider(0, 0, 100, 12, "G", itemLore$activeGreen(), value -> itemLore$setActiveColorComponent(1, value));
		itemLore$blueSlider = new ColorValueSlider(0, 0, 100, 12, "B", itemLore$activeBlue(), value -> itemLore$setActiveColorComponent(2, value));
		addRenderableWidget(itemLore$redSlider);
		addRenderableWidget(itemLore$greenSlider);
		addRenderableWidget(itemLore$blueSlider);

		itemLore$firstColorButton = Button.builder(Component.empty(), button -> itemLore$selectColorSlot(0))
				.bounds(0, 0, 60, 18)
				.build();
		itemLore$secondColorButton = Button.builder(Component.empty(), button -> itemLore$selectColorSlot(1))
				.bounds(0, 0, 60, 18)
				.build();
		itemLore$copyHexButton = Button.builder(Component.empty(), button -> itemLore$copyCurrentHex())
				.bounds(0, 0, 60, 18)
				.build();
		itemLore$insertColorButton = Button.builder(Component.translatable("gui.item_lore.insert_color"), button -> itemLore$insertMarkupTemplate("[color:" + itemLore$firstHex() + "]", "[/color]"))
				.bounds(0, 0, 60, 18)
				.build();
		itemLore$insertGradientButton = Button.builder(Component.translatable("gui.item_lore.insert_gradient"), button -> itemLore$insertMarkupTemplate("[gradient:" + itemLore$firstHex() + "]", "[/gradient:" + itemLore$secondHex() + "]"))
				.bounds(0, 0, 60, 18)
				.build();
		itemLore$formatBoldButton = Button.builder(Component.translatable("gui.item_lore.format_bold"), button -> itemLore$insertMarkupTemplate("[b]", "[/b]"))
				.bounds(0, 0, 20, 16)
				.build();
		itemLore$formatItalicButton = Button.builder(Component.translatable("gui.item_lore.format_italic"), button -> itemLore$insertMarkupTemplate("[i]", "[/i]"))
				.bounds(0, 0, 20, 16)
				.build();
		itemLore$formatUnderlineButton = Button.builder(Component.translatable("gui.item_lore.format_underline"), button -> itemLore$insertMarkupTemplate("[u]", "[/u]"))
				.bounds(0, 0, 20, 16)
				.build();
		itemLore$formatStrikeButton = Button.builder(Component.translatable("gui.item_lore.format_strike"), button -> itemLore$insertMarkupTemplate("[s]", "[/s]"))
				.bounds(0, 0, 20, 16)
				.build();
		itemLore$formatObfuscatedButton = Button.builder(Component.translatable("gui.item_lore.format_obfuscated"), button -> itemLore$insertMarkupTemplate("[o]", "[/o]"))
				.bounds(0, 0, 20, 16)
				.build();
		addRenderableWidget(itemLore$firstColorButton);
		addRenderableWidget(itemLore$secondColorButton);
		addRenderableWidget(itemLore$copyHexButton);
		addRenderableWidget(itemLore$insertColorButton);
		addRenderableWidget(itemLore$insertGradientButton);
		addRenderableWidget(itemLore$formatBoldButton);
		addRenderableWidget(itemLore$formatItalicButton);
		addRenderableWidget(itemLore$formatUnderlineButton);
		addRenderableWidget(itemLore$formatStrikeButton);
		addRenderableWidget(itemLore$formatObfuscatedButton);

		itemLore$setEditorValue(itemLore$raw, false);
		itemLore$syncSlidersToActiveColor();
		itemLore$applyAnvilLayout();
	}

	@Inject(method = "containerTick", at = @At("TAIL"))
	private void itemLore$tickLoreSender(CallbackInfo ci) {
		if (!itemLore$serverSupportsUi()) {
			if (itemLore$panelOpen) {
				itemLore$setPanelOpen(false);
			} else {
				itemLore$layoutLoreWidgets();
			}
			return;
		}

		itemLore$tickNameSender();

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
		if (itemLore$editor == null || !itemLore$editor.visible || !itemLore$panelOpen) {
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

		if (!itemLore$parseResult.isSuccess()) {
			graphics.textWithWordWrap(font, Component.literal(itemLore$parseResult.errorMessage()), textX, itemLore$editor.getBottom() + 5, contentWidth, itemLore$VANILLA_TEXT_ERROR, false);
		}

		int colorTitleY = itemLore$firstColorButton.getY() - 13;
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

		if (itemLore$helpButton != null && itemLore$isInWidgetBounds(mouseX, mouseY, itemLore$helpButton)) {
			graphics.setComponentTooltipForNextFrame(font, itemLore$helpTooltipLines(), mouseX, mouseY);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void itemLore$routeKeysToLoreEditor(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (itemLore$editor != null && itemLore$editor.visible && itemLore$editor.isFocused() && itemLore$editor.keyPressed(event)) {
			cir.setReturnValue(true);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (name != null && event.button() == 0 && itemLore$isInWidgetBounds(event.x(), event.y(), name)) {
			itemLore$insertTargetName = true;
			if (itemLore$editor != null) {
				itemLore$editor.setFocused(false);
			}
			return super.mouseClicked(event, doubleClick);
		}

		if (itemLore$editor != null && itemLore$editor.visible && itemLore$panelOpen && event.button() == 0) {
			if (itemLore$isInEditorBounds(event.x(), event.y())) {
				itemLore$insertTargetName = false;
				itemLore$focusLoreEditor();
				itemLore$editor.mouseClicked(event, doubleClick);
				return true;
			}

			if (itemLore$isInPanelBounds(event.x(), event.y())) {
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
	public void itemLore$acceptServerLoreState(int containerId, int sessionId, String rawLoreMarkup, String rawNameMarkup) {
		if (containerId != menu.containerId) {
			return;
		}

		if (itemLore$sessionId != sessionId || !itemLore$dirty) {
			itemLore$sessionId = sessionId;
			itemLore$setEditorValue(rawLoreMarkup, false);
			itemLore$dirty = false;
			itemLore$sendDelay = 0;
		} else {
			itemLore$sessionId = sessionId;
		}

		itemLore$acceptServerNameState(rawNameMarkup);
	}

	@Unique
	private void itemLore$configureNameField() {
		if (name == null) {
			return;
		}

		if (itemLore$serverSupportsUi()) {
			name.setMaxLength(LoreMarkupParser.MAX_RAW_CHARS);
		}
		name.setX(leftPos + 62);
		name.setY(topPos + 24);
		name.setWidth(103);
		itemLore$lastObservedName = itemLore$limitRawInput(name.getValue());
		itemLore$lastSentName = itemLore$lastObservedName;
	}

	@Unique
	private void itemLore$tickNameSender() {
		if (name == null || itemLore$suppressNameSync || !ClientPlayNetworking.canSend(ServerboundAnvilNameUpdatePayload.TYPE)) {
			return;
		}

		String current = itemLore$limitRawInput(name.getValue());
		if (!current.equals(name.getValue())) {
			itemLore$suppressNameSync = true;
			name.setValue(current);
			itemLore$suppressNameSync = false;
		}

		if (itemLore$nameStateGraceTicks > 0) {
			itemLore$lastObservedName = current;
			itemLore$nameStateGraceTicks--;
			return;
		}

		if (!current.equals(itemLore$lastObservedName)) {
			itemLore$lastObservedName = current;
			itemLore$nameDirty = true;
			itemLore$nameSendDelay = itemLore$SEND_DELAY_TICKS;
		}

		if (itemLore$nameSendDelay > 0) {
			itemLore$nameSendDelay--;
		}

		if (itemLore$nameDirty && itemLore$nameSendDelay <= 0) {
			if (!current.equals(itemLore$lastSentName)) {
				ClientPlayNetworking.send(new ServerboundAnvilNameUpdatePayload(menu.containerId, itemLore$sessionId, current));
				itemLore$lastSentName = current;
			}
			itemLore$nameDirty = false;
		}
	}

	@Unique
	private void itemLore$acceptServerNameState(String rawNameMarkup) {
		if (name == null || !itemLore$serverSupportsUi()) {
			return;
		}

		String safeRawName = itemLore$limitRawInput(rawNameMarkup == null ? "" : rawNameMarkup);
		boolean forceCustomNameSync = false;
		itemLore$suppressNameSync = true;
		if (!safeRawName.isEmpty()) {
			name.setValue(safeRawName);
			itemLore$lastObservedName = safeRawName;
			// Force one custom name sync so the server can correct vanilla's raw-tag rename preview.
			itemLore$lastSentName = "";
			forceCustomNameSync = true;
		} else {
			itemLore$lastObservedName = itemLore$limitRawInput(name.getValue());
			itemLore$lastSentName = "";
		}
		itemLore$nameDirty = forceCustomNameSync;
		itemLore$nameSendDelay = 0;
		itemLore$nameStateGraceTicks = forceCustomNameSync ? 0 : 2;
		itemLore$suppressNameSync = false;
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
	private void itemLore$applyAnvilLayout() {
		leftPos = (width - imageWidth) / 2;
		if (itemLore$panelOpen && itemLore$serverSupportsUi()) {
			int panelWidth = itemLore$preferredPanelWidthForScreen();
			int combinedWidth = imageWidth + itemLore$PANEL_GAP + panelWidth;
			if (panelWidth >= itemLore$MIN_PANEL_WIDTH && width >= combinedWidth + 2 * itemLore$SCREEN_MARGIN) {
				leftPos = Math.max(itemLore$SCREEN_MARGIN, (width - combinedWidth) / 2);
			}
		}
		itemLore$configureNameField();
		itemLore$layoutLoreWidgets();
	}

	@Unique
	private void itemLore$layoutLoreWidgets() {
		if (itemLore$editor == null) {
			return;
		}

		boolean supported = itemLore$serverSupportsUi();
		boolean panelCanOpen = itemLore$canOpenPanel();
		if (itemLore$panelOpen && !panelCanOpen) {
			itemLore$panelOpen = false;
		}
		itemLore$layoutToggleButton(supported && !itemLore$panelOpen && panelCanOpen);

		itemLore$panelX = leftPos + imageWidth + itemLore$PANEL_GAP;
		int availableRight = width - itemLore$panelX - itemLore$SCREEN_MARGIN;
		itemLore$panelWidth = Math.min(itemLore$PREFERRED_PANEL_WIDTH, Math.max(0, availableRight));
		boolean visible = itemLore$panelOpen && panelCanOpen && itemLore$panelWidth >= itemLore$MIN_PANEL_WIDTH;
		itemLore$setLoreWidgetsVisible(visible);
		if (!visible) {
			return;
		}

		itemLore$panelHeight = Math.min(height - 2 * itemLore$SCREEN_MARGIN, Math.max(imageHeight, 264));
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

		int topButtonY = itemLore$panelY + 5;
		itemLore$positionWidget(itemLore$closeButton, itemLore$panelX + itemLore$panelWidth - itemLore$PANEL_PADDING - itemLore$SMALL_BUTTON_SIZE, topButtonY, itemLore$SMALL_BUTTON_SIZE, itemLore$SMALL_BUTTON_SIZE);
		itemLore$positionWidget(itemLore$helpButton, itemLore$closeButton.getX() - itemLore$SMALL_BUTTON_SIZE - 3, topButtonY, itemLore$SMALL_BUTTON_SIZE, itemLore$SMALL_BUTTON_SIZE);

		int colorButtonsY = editorY + editorHeight + 28;
		int halfButtonWidth = (contentWidth - 4) / 2;
		itemLore$positionWidget(itemLore$firstColorButton, textX, colorButtonsY, halfButtonWidth, 18);
		itemLore$positionWidget(itemLore$secondColorButton, textX + halfButtonWidth + 4, colorButtonsY, contentWidth - halfButtonWidth - 4, 18);

		int sliderY = colorButtonsY + 22;
		itemLore$positionWidget(itemLore$redSlider, textX, sliderY, contentWidth, 12);
		itemLore$positionWidget(itemLore$greenSlider, textX, sliderY + 15, contentWidth, 12);
		itemLore$positionWidget(itemLore$blueSlider, textX, sliderY + 30, contentWidth, 12);

		int buttonY = sliderY + 47;
		itemLore$positionWidget(itemLore$copyHexButton, textX, buttonY, halfButtonWidth, 18);
		itemLore$positionWidget(itemLore$insertColorButton, textX + halfButtonWidth + 4, buttonY, contentWidth - halfButtonWidth - 4, 18);
		itemLore$positionWidget(itemLore$insertGradientButton, textX, buttonY + 21, contentWidth, 18);

		int formatY = buttonY + 43;
		int formatGap = 3;
		int formatWidth = (contentWidth - 4 * formatGap) / 5;
		itemLore$positionWidget(itemLore$formatBoldButton, textX, formatY, formatWidth, 16);
		itemLore$positionWidget(itemLore$formatItalicButton, textX + (formatWidth + formatGap), formatY, formatWidth, 16);
		itemLore$positionWidget(itemLore$formatUnderlineButton, textX + 2 * (formatWidth + formatGap), formatY, formatWidth, 16);
		itemLore$positionWidget(itemLore$formatStrikeButton, textX + 3 * (formatWidth + formatGap), formatY, formatWidth, 16);
		itemLore$positionWidget(itemLore$formatObfuscatedButton, textX + 4 * (formatWidth + formatGap), formatY, contentWidth - 4 * (formatWidth + formatGap), 16);
	}

	@Unique
	private void itemLore$layoutToggleButton(boolean visible) {
		if (itemLore$toggleButton == null) {
			return;
		}

		int x = leftPos + imageWidth + 4;
		int y = topPos + 23;
		if (x + itemLore$LORE_BUTTON_WIDTH > width - itemLore$SCREEN_MARGIN) {
			x = leftPos + imageWidth - itemLore$LORE_BUTTON_WIDTH - 6;
		}
		itemLore$positionWidget(itemLore$toggleButton, x, y, itemLore$LORE_BUTTON_WIDTH, itemLore$LORE_BUTTON_HEIGHT);
		itemLore$toggleButton.visible = visible;
		itemLore$toggleButton.active = visible;
	}

	@Unique
	private void itemLore$positionWidget(Button widget, int x, int y, int widgetWidth, int widgetHeight) {
		if (widget == null) {
			return;
		}
		widget.setX(x);
		widget.setY(y);
		widget.setWidth(widgetWidth);
		widget.setHeight(widgetHeight);
	}

	@Unique
	private void itemLore$positionWidget(ColorValueSlider widget, int x, int y, int widgetWidth, int widgetHeight) {
		if (widget == null) {
			return;
		}
		widget.setX(x);
		widget.setY(y);
		widget.setWidth(widgetWidth);
		widget.setHeight(widgetHeight);
	}

	@Unique
	private void itemLore$setLoreWidgetsVisible(boolean visible) {
		itemLore$editor.visible = visible;
		itemLore$editor.active = visible;
		itemLore$setWidgetVisible(itemLore$helpButton, visible);
		itemLore$setWidgetVisible(itemLore$closeButton, visible);
		itemLore$setWidgetVisible(itemLore$firstColorButton, visible);
		itemLore$setWidgetVisible(itemLore$secondColorButton, visible);
		itemLore$setWidgetVisible(itemLore$copyHexButton, visible);
		itemLore$setWidgetVisible(itemLore$insertColorButton, visible);
		itemLore$setWidgetVisible(itemLore$insertGradientButton, visible);
		itemLore$setWidgetVisible(itemLore$formatBoldButton, visible);
		itemLore$setWidgetVisible(itemLore$formatItalicButton, visible);
		itemLore$setWidgetVisible(itemLore$formatUnderlineButton, visible);
		itemLore$setWidgetVisible(itemLore$formatStrikeButton, visible);
		itemLore$setWidgetVisible(itemLore$formatObfuscatedButton, visible);
		if (itemLore$redSlider != null) {
			itemLore$redSlider.visible = visible;
			itemLore$redSlider.active = visible;
			itemLore$greenSlider.visible = visible;
			itemLore$greenSlider.active = visible;
			itemLore$blueSlider.visible = visible;
			itemLore$blueSlider.active = visible;
		}
	}

	@Unique
	private void itemLore$setWidgetVisible(Button widget, boolean visible) {
		if (widget == null) {
			return;
		}
		widget.visible = visible;
		widget.active = visible;
	}

	@Unique
	private int itemLore$preferredPanelWidthForScreen() {
		int available = width - imageWidth - itemLore$PANEL_GAP - 2 * itemLore$SCREEN_MARGIN;
		return Math.min(itemLore$PREFERRED_PANEL_WIDTH, Math.max(0, available));
	}

	@Unique
	private boolean itemLore$serverSupportsUi() {
		return ClientPlayNetworking.canSend(ServerboundAnvilLoreUpdatePayload.TYPE)
				&& ClientPlayNetworking.canSend(ServerboundAnvilNameUpdatePayload.TYPE);
	}


	@Unique
	private boolean itemLore$canOpenPanel() {
		if (!itemLore$serverSupportsUi()) {
			return false;
		}
		int panelWidth = itemLore$preferredPanelWidthForScreen();
		int combinedWidth = imageWidth + itemLore$PANEL_GAP + panelWidth;
		return panelWidth >= itemLore$MIN_PANEL_WIDTH && width >= combinedWidth + 2 * itemLore$SCREEN_MARGIN;
	}

	@Unique
	private void itemLore$setPanelOpen(boolean open) {
		if (open && !itemLore$canOpenPanel()) {
			return;
		}
		itemLore$panelOpen = open;
		if (!open) {
			itemLore$unfocusTextFields();
		} else {
			itemLore$insertTargetName = false;
		}
		itemLore$applyAnvilLayout();
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
		if (itemLore$editor != null) {
			itemLore$editor.setFocused(false);
		}
		if (name != null) {
			name.setFocused(false);
		}
	}

	@Unique
	private int itemLore$firstColor() {
		return ((itemLore$pickerOneRed & 0xFF) << 16) | ((itemLore$pickerOneGreen & 0xFF) << 8) | (itemLore$pickerOneBlue & 0xFF);
	}

	@Unique
	private int itemLore$secondColor() {
		return ((itemLore$pickerTwoRed & 0xFF) << 16) | ((itemLore$pickerTwoGreen & 0xFF) << 8) | (itemLore$pickerTwoBlue & 0xFF);
	}

	@Unique
	private int itemLore$currentColor() {
		return itemLore$activeColorSlot == 0 ? itemLore$firstColor() : itemLore$secondColor();
	}

	@Unique
	private String itemLore$firstHex() {
		return LoreMarkupParser.formatHex(itemLore$firstColor());
	}

	@Unique
	private String itemLore$secondHex() {
		return LoreMarkupParser.formatHex(itemLore$secondColor());
	}

	@Unique
	private String itemLore$currentHex() {
		return LoreMarkupParser.formatHex(itemLore$currentColor());
	}

	@Unique
	private int itemLore$activeRed() {
		return itemLore$activeColorSlot == 0 ? itemLore$pickerOneRed : itemLore$pickerTwoRed;
	}

	@Unique
	private int itemLore$activeGreen() {
		return itemLore$activeColorSlot == 0 ? itemLore$pickerOneGreen : itemLore$pickerTwoGreen;
	}

	@Unique
	private int itemLore$activeBlue() {
		return itemLore$activeColorSlot == 0 ? itemLore$pickerOneBlue : itemLore$pickerTwoBlue;
	}

	@Unique
	private void itemLore$setActiveColorComponent(int component, int value) {
		if (itemLore$activeColorSlot == 0) {
			if (component == 0) {
				itemLore$pickerOneRed = value;
			} else if (component == 1) {
				itemLore$pickerOneGreen = value;
			} else {
				itemLore$pickerOneBlue = value;
			}
		} else {
			if (component == 0) {
				itemLore$pickerTwoRed = value;
			} else if (component == 1) {
				itemLore$pickerTwoGreen = value;
			} else {
				itemLore$pickerTwoBlue = value;
			}
		}
		itemLore$updateColorButtons();
	}

	@Unique
	private void itemLore$selectColorSlot(int slot) {
		itemLore$activeColorSlot = slot == 1 ? 1 : 0;
		itemLore$syncSlidersToActiveColor();
		itemLore$updateColorButtons();
	}

	@Unique
	private void itemLore$syncSlidersToActiveColor() {
		if (itemLore$redSlider == null) {
			return;
		}
		itemLore$redSlider.setIntValue(itemLore$activeRed());
		itemLore$greenSlider.setIntValue(itemLore$activeGreen());
		itemLore$blueSlider.setIntValue(itemLore$activeBlue());
		itemLore$updateColorButtons();
	}

	@Unique
	private void itemLore$updateColorButtons() {
		if (itemLore$copyHexButton != null) {
			itemLore$copyHexButton.setMessage(Component.translatable("gui.item_lore.copy_hex", itemLore$currentHex()));
		}
		if (itemLore$firstColorButton != null) {
			itemLore$firstColorButton.setMessage(Component.translatable(itemLore$activeColorSlot == 0 ? "gui.item_lore.color_one_active" : "gui.item_lore.color_one", itemLore$firstHex()));
		}
		if (itemLore$secondColorButton != null) {
			itemLore$secondColorButton.setMessage(Component.translatable(itemLore$activeColorSlot == 1 ? "gui.item_lore.color_two_active" : "gui.item_lore.color_two", itemLore$secondHex()));
		}
	}

	@Unique
	private void itemLore$copyCurrentHex() {
		if (minecraft != null) {
			minecraft.keyboardHandler.setClipboard(itemLore$currentHex());
		}
	}

	@Unique
	private void itemLore$insertMarkupTemplate(String openingTag, String closingTag) {
		itemLore$insertAtTextCursor(openingTag + closingTag, openingTag.length());
	}

	@Unique
	private void itemLore$insertAtTextCursor(String insertion, int cursorOffset) {
		if (itemLore$insertTargetName && name != null) {
			int cursor = name.getCursorPosition();
			name.insertText(insertion);
			int target = Math.min(name.getValue().length(), cursor + Math.max(0, Math.min(cursorOffset, insertion.length())));
			name.setCursorPosition(target);
			String current = itemLore$limitRawInput(name.getValue());
			if (!current.equals(name.getValue())) {
				itemLore$suppressNameSync = true;
				name.setValue(current);
				itemLore$suppressNameSync = false;
			}
			itemLore$lastObservedName = itemLore$limitRawInput(name.getValue());
			itemLore$nameDirty = true;
			itemLore$nameSendDelay = itemLore$SEND_DELAY_TICKS;
			name.setFocused(true);
			if (itemLore$editor != null) {
				itemLore$editor.setFocused(false);
			}
			return;
		}

		if (itemLore$editor == null) {
			return;
		}

		itemLore$insertTargetName = false;
		MultilineTextField textField = ((MultiLineEditBoxAccessor) itemLore$editor).itemLore$getTextField();
		int cursor = textField.cursor();
		textField.insertText(insertion);
		int target = Math.min(textField.value().length(), cursor + Math.max(0, Math.min(cursorOffset, insertion.length())));
		textField.seekCursor(Whence.ABSOLUTE, target);
		String current = itemLore$limitRawInput(itemLore$editor.getValue());
		if (!current.equals(itemLore$editor.getValue())) {
			itemLore$setEditorValue(current, true);
		} else {
			itemLore$raw = current;
			itemLore$parseResult = LoreMarkupParser.parse(current);
			itemLore$dirty = true;
		}
		itemLore$sendDelay = itemLore$SEND_DELAY_TICKS;
		itemLore$focusLoreEditor();
	}

	@Unique
	private List<Component> itemLore$helpTooltipLines() {
		return List.of(
				Component.translatable("gui.item_lore.help.color"),
				Component.translatable("gui.item_lore.help.gradient"),
				Component.translatable("gui.item_lore.help.format"),
				Component.translatable("gui.item_lore.help.name")
		);
	}

	@Unique
	private void itemLore$drawVanillaPanel(GuiGraphicsExtractor graphics, int x, int y, int panelWidth, int panelHeight) {
		graphics.fill(x, y, x + panelWidth, y + panelHeight, itemLore$VANILLA_PANEL_SHADOW);
		graphics.fill(x + 1, y + 1, x + panelWidth - 1, y + panelHeight - 1, itemLore$VANILLA_PANEL);

		graphics.fill(x + 1, y + 1, x + panelWidth - 1, y + 2, itemLore$VANILLA_PANEL_LIGHT);
		graphics.fill(x + 1, y + 1, x + 2, y + panelHeight - 1, itemLore$VANILLA_PANEL_LIGHT);
		graphics.fill(x + panelWidth - 2, y + 1, x + panelWidth - 1, y + panelHeight - 1, itemLore$VANILLA_PANEL_DARK);
		graphics.fill(x + 1, y + panelHeight - 2, x + panelWidth - 1, y + panelHeight - 1, itemLore$VANILLA_PANEL_DARK);
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
	private boolean itemLore$isInWidgetBounds(double mouseX, double mouseY, Button widget) {
		return widget != null && itemLore$isInBounds(mouseX, mouseY, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
	}

	@Unique
	private boolean itemLore$isInWidgetBounds(double mouseX, double mouseY, EditBox widget) {
		return widget != null && itemLore$isInBounds(mouseX, mouseY, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
	}

	@Unique
	private boolean itemLore$isInBounds(double mouseX, double mouseY, int x, int y, int boundsWidth, int boundsHeight) {
		return mouseX >= x && mouseX < x + boundsWidth && mouseY >= y && mouseY < y + boundsHeight;
	}
}
