package com.reign.itemlore.mixin.client;

import com.reign.itemlore.access.AnvilLoreScreenBridge;
import com.reign.itemlore.access.RecipeViewerArea;
import com.reign.itemlore.client.ColorValueSlider;
import com.reign.itemlore.client.ColorWheelWidget;
import com.reign.itemlore.lore.LoreMarkupParser;
import com.reign.itemlore.lore.ParseResult;
import com.reign.itemlore.net.ServerboundAnvilLoreUpdatePayload;
import com.reign.itemlore.net.ServerboundAnvilNameUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.input.CharacterEvent;
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
	private static final int itemLore$PREFERRED_PANEL_WIDTH = 210;
	@Unique
	private static final int itemLore$MIN_PANEL_WIDTH = 188;
	@Unique
	private static final int itemLore$PANEL_GAP = 10;
	@Unique
	private static final int itemLore$SCREEN_MARGIN = 6;
	@Unique
	private static final int itemLore$PANEL_PADDING = 7;
	@Unique
	private static final int itemLore$PANEL_HEIGHT = 232;
	@Unique
	private static final int itemLore$SEND_DELAY_TICKS = 6;
	@Unique
	private static final int itemLore$LORE_BUTTON_WIDTH = 44;
	@Unique
	private static final int itemLore$LORE_BUTTON_HEIGHT = 16;
	@Unique
	private static final int itemLore$SMALL_BUTTON_SIZE = 16;
	@Unique
	private static final int itemLore$COLOR_SWATCH_SIZE = 12;

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
	private EditBox itemLore$hexInput;
	@Unique
	private ColorWheelWidget itemLore$colorWheelWidget;
	@Unique
	private Button itemLore$redDownButton;
	@Unique
	private Button itemLore$redUpButton;
	@Unique
	private Button itemLore$greenDownButton;
	@Unique
	private Button itemLore$greenUpButton;
	@Unique
	private Button itemLore$blueDownButton;
	@Unique
	private Button itemLore$blueUpButton;
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
	private boolean itemLore$draggingLoreSelection = false;
	@Unique
	private boolean itemLore$colorWheelOpen = false;
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
	private boolean itemLore$suppressHexInput = false;
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
	private int itemLore$colorSwatchX;
	@Unique
	private int itemLore$colorSwatchY;
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
		itemLore$closeButton = Button.builder(Component.literal("×"), button -> itemLore$setPanelOpen(false))
				.bounds(0, 0, itemLore$SMALL_BUTTON_SIZE, itemLore$SMALL_BUTTON_SIZE)
				.build();
		addRenderableWidget(itemLore$toggleButton);
		addRenderableWidget(itemLore$helpButton);
		addRenderableWidget(itemLore$closeButton);

		itemLore$redSlider = new ColorValueSlider(0, 0, 100, 12, "R", itemLore$activeRed(), value -> itemLore$setActiveColorComponent(0, value));
		itemLore$greenSlider = new ColorValueSlider(0, 0, 100, 12, "G", itemLore$activeGreen(), value -> itemLore$setActiveColorComponent(1, value));
		itemLore$blueSlider = new ColorValueSlider(0, 0, 100, 12, "B", itemLore$activeBlue(), value -> itemLore$setActiveColorComponent(2, value));
		itemLore$hexInput = new EditBox(font, 0, 0, 54, 12, Component.translatable("gui.item_lore.hex_input"));
		itemLore$hexInput.setMaxLength(7);
		itemLore$hexInput.setValue(itemLore$currentHex());
		itemLore$hexInput.setResponder(this::itemLore$onHexInputChanged);
		itemLore$redDownButton = Button.builder(Component.literal("<"), button -> itemLore$adjustActiveColorComponent(0, -1))
				.bounds(0, 0, 12, 12)
				.build();
		itemLore$redUpButton = Button.builder(Component.literal(">"), button -> itemLore$adjustActiveColorComponent(0, 1))
				.bounds(0, 0, 12, 12)
				.build();
		itemLore$greenDownButton = Button.builder(Component.literal("<"), button -> itemLore$adjustActiveColorComponent(1, -1))
				.bounds(0, 0, 12, 12)
				.build();
		itemLore$greenUpButton = Button.builder(Component.literal(">"), button -> itemLore$adjustActiveColorComponent(1, 1))
				.bounds(0, 0, 12, 12)
				.build();
		itemLore$blueDownButton = Button.builder(Component.literal("<"), button -> itemLore$adjustActiveColorComponent(2, -1))
				.bounds(0, 0, 12, 12)
				.build();
		itemLore$blueUpButton = Button.builder(Component.literal(">"), button -> itemLore$adjustActiveColorComponent(2, 1))
				.bounds(0, 0, 12, 12)
				.build();
		addRenderableWidget(itemLore$redSlider);
		addRenderableWidget(itemLore$greenSlider);
		addRenderableWidget(itemLore$blueSlider);
		addRenderableWidget(itemLore$hexInput);
		addRenderableWidget(itemLore$redDownButton);
		addRenderableWidget(itemLore$redUpButton);
		addRenderableWidget(itemLore$greenDownButton);
		addRenderableWidget(itemLore$greenUpButton);
		addRenderableWidget(itemLore$blueDownButton);
		addRenderableWidget(itemLore$blueUpButton);

		itemLore$firstColorButton = Button.builder(Component.empty(), button -> itemLore$selectColorSlot(0))
				.bounds(0, 0, 60, 18)
				.build();
		itemLore$secondColorButton = Button.builder(Component.empty(), button -> itemLore$selectColorSlot(1))
				.bounds(0, 0, 60, 18)
				.build();
		itemLore$copyHexButton = Button.builder(Component.empty(), button -> itemLore$copyCurrentHex())
				.bounds(0, 0, 60, 18)
				.build();
		itemLore$insertColorButton = Button.builder(Component.translatable("gui.item_lore.insert_color"), button -> itemLore$insertMarkupTemplate("<c " + itemLore$currentHex() + ">", "</c>"))
				.bounds(0, 0, 60, 18)
				.build();
		itemLore$insertGradientButton = Button.builder(Component.translatable("gui.item_lore.insert_gradient"), button -> itemLore$insertMarkupTemplate("<gr " + itemLore$firstHex() + " " + itemLore$secondHex() + ">", "</gr>"))
				.bounds(0, 0, 60, 18)
				.build();
		itemLore$formatBoldButton = Button.builder(Component.translatable("gui.item_lore.format_bold").withStyle(ChatFormatting.BOLD), button -> itemLore$insertMarkupTemplate("<b>", "</b>"))
				.bounds(0, 0, 20, 16)
				.build();
		itemLore$formatItalicButton = Button.builder(Component.translatable("gui.item_lore.format_italic").withStyle(ChatFormatting.ITALIC), button -> itemLore$insertMarkupTemplate("<i>", "</i>"))
				.bounds(0, 0, 20, 16)
				.build();
		itemLore$formatUnderlineButton = Button.builder(Component.translatable("gui.item_lore.format_underline").withStyle(ChatFormatting.UNDERLINE), button -> itemLore$insertMarkupTemplate("<underlined>", "</underlined>"))
				.bounds(0, 0, 20, 16)
				.build();
		itemLore$formatStrikeButton = Button.builder(Component.translatable("gui.item_lore.format_strike").withStyle(ChatFormatting.STRIKETHROUGH), button -> itemLore$insertMarkupTemplate("<st>", "</st>"))
				.bounds(0, 0, 20, 16)
				.build();
		itemLore$formatObfuscatedButton = Button.builder(Component.translatable("gui.item_lore.format_obfuscated"), button -> itemLore$insertMarkupTemplate("<obf>", "</obf>"))
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

		itemLore$colorWheelWidget = new ColorWheelWidget(0, 0, itemLore$currentColor(), this::itemLore$setActiveColor);
		addRenderableWidget(itemLore$colorWheelWidget);

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
		int titleY = itemLore$panelY + 10;

		Component title = Component.translatable("gui.item_lore.title");
		graphics.text(font, title, textX, titleY, itemLore$VANILLA_TEXT, false);

		Component count = Component.translatable(
				"gui.item_lore.symbols",
				itemLore$parseResult.visibleCodePoints(),
				LoreMarkupParser.MAX_VISIBLE_CODEPOINTS
		);
		int countColor = itemLore$parseResult.isSuccess() ? itemLore$VANILLA_TEXT_MUTED : itemLore$VANILLA_TEXT_ERROR;
		int countX = textX + font.width(title) + 10;
		if (itemLore$helpButton != null) {
			int maxCountX = itemLore$helpButton.getX() - 4 - font.width(count);
			countX = Math.min(countX, Math.max(textX, maxCountX));
		}
		graphics.text(font, count, countX, titleY, countColor, false);

		int colorTitleY = itemLore$firstColorButton.getY() - 14;
		Component colorTitle = Component.translatable("gui.item_lore.color_helper");
		graphics.text(font, colorTitle, textX, colorTitleY, itemLore$VANILLA_TEXT, false);

		int previewColor = 0xFF000000 | itemLore$currentColor();
		Component previewWord = Component.translatable("gui.item_lore.color_preview");
		int previewX = textX + font.width(colorTitle) + 8;
		graphics.text(font, previewWord, previewX, colorTitleY, previewColor, false);

		graphics.fill(itemLore$colorSwatchX, itemLore$colorSwatchY, itemLore$colorSwatchX + itemLore$COLOR_SWATCH_SIZE, itemLore$colorSwatchY + itemLore$COLOR_SWATCH_SIZE, itemLore$VANILLA_PANEL_SHADOW);
		graphics.fill(itemLore$colorSwatchX + 1, itemLore$colorSwatchY + 1, itemLore$colorSwatchX + itemLore$COLOR_SWATCH_SIZE - 1, itemLore$colorSwatchY + itemLore$COLOR_SWATCH_SIZE - 1, previewColor);

		if (itemLore$helpButton != null && itemLore$isInWidgetBounds(mouseX, mouseY, itemLore$helpButton)) {
			int tooltipX = Math.min(mouseX, Math.max(0, width - 340));
			graphics.setComponentTooltipForNextFrame(font, itemLore$helpTooltipLines(), tooltipX, mouseY);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void itemLore$routeKeysToLoreEditor(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (itemLore$hexInput != null && itemLore$hexInput.visible && itemLore$hexInput.isFocused() && itemLore$hexInput.keyPressed(event)) {
			cir.setReturnValue(true);
			return;
		}

		if (itemLore$routeKeyToColorSlider(event)) {
			cir.setReturnValue(true);
			return;
		}

		if (itemLore$editor != null && itemLore$editor.visible && itemLore$editor.isFocused() && itemLore$editor.keyPressed(event)) {
			cir.setReturnValue(true);
		}
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (itemLore$hexInput != null && itemLore$hexInput.visible && itemLore$hexInput.isFocused() && itemLore$hexInput.charTyped(event)) {
			return true;
		}

		if (itemLore$routeCharToColorSlider(event)) {
			return true;
		}

		return super.charTyped(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && !itemLore$isInColorSliderBounds(event.x(), event.y())) {
			itemLore$unfocusColorSliders();
		}

		if (name != null && event.button() == 0 && itemLore$isInWidgetBounds(event.x(), event.y(), name)) {
			itemLore$insertTargetName = true;
			if (itemLore$editor != null) {
				itemLore$editor.setFocused(false);
			}
			return super.mouseClicked(event, doubleClick);
		}

		if (itemLore$editor != null && itemLore$editor.visible && itemLore$panelOpen && event.button() == 0) {
			if (itemLore$colorWheelOpen && itemLore$colorWheelWidget != null && itemLore$colorWheelWidget.visible && itemLore$colorWheelWidget.mouseClicked(event, doubleClick)) {
				return true;
			}

			if (itemLore$isInColorSwatchBounds(event.x(), event.y())) {
				itemLore$colorWheelOpen = !itemLore$colorWheelOpen;
				itemLore$layoutLoreWidgets();
				return true;
			}

			if (itemLore$colorWheelOpen && itemLore$colorWheelWidget != null && !itemLore$colorWheelWidget.isMouseOver(event.x(), event.y())) {
				itemLore$colorWheelOpen = false;
				itemLore$layoutLoreWidgets();
			}

			if (itemLore$isInEditorBounds(event.x(), event.y())) {
				itemLore$insertTargetName = false;
				itemLore$draggingLoreSelection = true;
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
			itemLore$draggingLoreSelection = false;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (itemLore$colorWheelWidget != null && itemLore$colorWheelWidget.visible && itemLore$colorWheelWidget.mouseDragged(event, dragX, dragY)) {
			return true;
		}

		if (itemLore$draggingLoreSelection
				&& itemLore$editor != null
				&& itemLore$editor.visible
				&& itemLore$panelOpen
				&& event.button() == 0) {
			itemLore$insertTargetName = false;
			itemLore$focusLoreEditor();
			itemLore$editor.mouseDragged(event, dragX, dragY);
			return true;
		}

		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (itemLore$colorWheelWidget != null && itemLore$colorWheelWidget.visible && itemLore$colorWheelWidget.mouseReleased(event)) {
			return true;
		}

		if (itemLore$draggingLoreSelection && event.button() == 0) {
			itemLore$draggingLoreSelection = false;
			if (itemLore$editor != null && itemLore$editor.visible) {
				itemLore$editor.mouseReleased(event);
			}
			return true;
		}

		return super.mouseReleased(event);
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

	@Override
	public List<RecipeViewerArea> itemLore$getRecipeViewerExclusionAreas() {
		if (!itemLore$serverSupportsUi()) {
			return List.of();
		}

		if (itemLore$panelOpen && itemLore$editor != null && itemLore$editor.visible) {
			return List.of(new RecipeViewerArea(itemLore$panelX, itemLore$panelY, itemLore$panelWidth, itemLore$panelHeight));
		}

		if (itemLore$toggleButton != null && itemLore$toggleButton.visible) {
			return List.of(new RecipeViewerArea(itemLore$toggleButton.getX(), itemLore$toggleButton.getY(), itemLore$toggleButton.getWidth(), itemLore$toggleButton.getHeight()));
		}

		return List.of();
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

		itemLore$panelHeight = Math.max(imageHeight, itemLore$PANEL_HEIGHT);
		itemLore$panelY = Math.max(itemLore$SCREEN_MARGIN, Math.min(topPos - Math.max(0, (itemLore$panelHeight - imageHeight) / 2), height - itemLore$panelHeight - itemLore$SCREEN_MARGIN));

		int textX = itemLore$panelX + itemLore$PANEL_PADDING;
		int contentWidth = itemLore$panelWidth - 2 * itemLore$PANEL_PADDING;
		int editorY = itemLore$panelY + 26;
		int editorHeight = 50;

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

		int colorTitleY = editorY + editorHeight + 5;
		int hexInputWidth = 55;
		int rightEdge = itemLore$panelX + itemLore$panelWidth - itemLore$PANEL_PADDING;
		itemLore$positionWidget(itemLore$hexInput, rightEdge - hexInputWidth, colorTitleY - 2, hexInputWidth, 14);
		itemLore$colorSwatchX = itemLore$hexInput.getX() - itemLore$COLOR_SWATCH_SIZE - 4;
		itemLore$colorSwatchY = itemLore$hexInput.getY() + Math.max(0, (itemLore$hexInput.getHeight() - itemLore$COLOR_SWATCH_SIZE) / 2);
		itemLore$layoutColorWheelWidget();

		int colorButtonsY = colorTitleY + 15;
		int columnGap = 6;
		int leftColumnWidth = (contentWidth - columnGap) / 2;
		int rightColumnX = textX + leftColumnWidth + columnGap;
		int rightColumnWidth = contentWidth - leftColumnWidth - columnGap;
		itemLore$positionWidget(itemLore$firstColorButton, textX, colorButtonsY, leftColumnWidth, 18);
		itemLore$positionWidget(itemLore$secondColorButton, rightColumnX, colorButtonsY, rightColumnWidth, 18);

		int sliderY = colorButtonsY + 22;
		int rowHeight = 18;
		int rowGap = 2;
		int arrowWidth = 12;
		int arrowGap = 2;
		int sliderWidth = Math.max(40, leftColumnWidth - 2 * arrowWidth - 2 * arrowGap);
		itemLore$layoutSliderRow(itemLore$redDownButton, itemLore$redSlider, itemLore$redUpButton, textX, sliderY, arrowWidth, arrowGap, sliderWidth, rowHeight);
		itemLore$layoutSliderRow(itemLore$greenDownButton, itemLore$greenSlider, itemLore$greenUpButton, textX, sliderY + rowHeight + rowGap, arrowWidth, arrowGap, sliderWidth, rowHeight);
		itemLore$layoutSliderRow(itemLore$blueDownButton, itemLore$blueSlider, itemLore$blueUpButton, textX, sliderY + 2 * (rowHeight + rowGap), arrowWidth, arrowGap, sliderWidth, rowHeight);

		itemLore$positionWidget(itemLore$copyHexButton, rightColumnX, sliderY, rightColumnWidth, rowHeight);
		itemLore$positionWidget(itemLore$insertColorButton, rightColumnX, sliderY + rowHeight + rowGap, rightColumnWidth, rowHeight);
		itemLore$positionWidget(itemLore$insertGradientButton, rightColumnX, sliderY + 2 * (rowHeight + rowGap), rightColumnWidth, rowHeight);

		int formatY = sliderY + 3 * (rowHeight + rowGap) + 2;
		int formatGap = 4;
		int formatRowOneA = 45;
		int formatRowOneB = 47;
		int formatRowOneC = contentWidth - formatRowOneA - formatRowOneB - 2 * formatGap;
		itemLore$positionWidget(itemLore$formatBoldButton, textX, formatY, formatRowOneA, 17);
		itemLore$positionWidget(itemLore$formatItalicButton, textX + formatRowOneA + formatGap, formatY, formatRowOneB, 17);
		itemLore$positionWidget(itemLore$formatUnderlineButton, textX + formatRowOneA + formatGap + formatRowOneB + formatGap, formatY, formatRowOneC, 17);

		int formatRowTwoY = formatY + 20;
		int formatRowTwoA = (contentWidth - formatGap) / 2;
		itemLore$positionWidget(itemLore$formatStrikeButton, textX, formatRowTwoY, formatRowTwoA, 17);
		itemLore$positionWidget(itemLore$formatObfuscatedButton, textX + formatRowTwoA + formatGap, formatRowTwoY, contentWidth - formatRowTwoA - formatGap, 17);

	}

	@Unique
	private void itemLore$layoutToggleButton(boolean visible) {
		if (itemLore$toggleButton == null) {
			return;
		}

		int x = leftPos + imageWidth + itemLore$PANEL_GAP;
		int y = name != null
				? name.getY() + 4 - itemLore$LORE_BUTTON_HEIGHT / 2
				: topPos + 24;
		if (x + itemLore$LORE_BUTTON_WIDTH > width - itemLore$SCREEN_MARGIN) {
			x = leftPos + imageWidth - itemLore$LORE_BUTTON_WIDTH - 6;
		}
		itemLore$positionWidget(itemLore$toggleButton, x, y, itemLore$LORE_BUTTON_WIDTH, itemLore$LORE_BUTTON_HEIGHT);
		itemLore$toggleButton.visible = visible;
		itemLore$toggleButton.active = visible;
	}

	@Unique
	private void itemLore$layoutSliderRow(Button downButton, ColorValueSlider slider, Button upButton, int x, int y, int arrowWidth, int gap, int sliderWidth, int rowHeight) {
		itemLore$positionWidget(downButton, x, y, arrowWidth, rowHeight);
		itemLore$positionWidget(slider, x + arrowWidth + gap, y, sliderWidth, rowHeight);
		itemLore$positionWidget(upButton, x + arrowWidth + gap + sliderWidth + gap, y, arrowWidth, rowHeight);
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
	private void itemLore$positionWidget(EditBox widget, int x, int y, int widgetWidth, int widgetHeight) {
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
	private void itemLore$positionWidget(ColorWheelWidget widget, int x, int y, int widgetWidth, int widgetHeight) {
		if (widget == null) {
			return;
		}
		widget.setX(x);
		widget.setY(y);
		widget.setWidth(widgetWidth);
		widget.setHeight(widgetHeight);
	}

	@Unique
	private void itemLore$layoutColorWheelWidget() {
		if (itemLore$colorWheelWidget == null) {
			return;
		}

		int popupX = itemLore$colorSwatchX + itemLore$COLOR_SWATCH_SIZE - ColorWheelWidget.POPUP_WIDTH;
		int popupY = itemLore$colorSwatchY + itemLore$COLOR_SWATCH_SIZE + 4;
		popupX = Math.max(itemLore$SCREEN_MARGIN, Math.min(popupX, width - ColorWheelWidget.POPUP_WIDTH - itemLore$SCREEN_MARGIN));
		if (popupY + ColorWheelWidget.POPUP_HEIGHT > height - itemLore$SCREEN_MARGIN) {
			popupY = itemLore$colorSwatchY - ColorWheelWidget.POPUP_HEIGHT - 4;
		}
		popupY = Math.max(itemLore$SCREEN_MARGIN, Math.min(popupY, height - ColorWheelWidget.POPUP_HEIGHT - itemLore$SCREEN_MARGIN));
		itemLore$positionWidget(itemLore$colorWheelWidget, popupX, popupY, ColorWheelWidget.POPUP_WIDTH, ColorWheelWidget.POPUP_HEIGHT);
	}

	@Unique
	private void itemLore$setLoreWidgetsVisible(boolean visible) {
		if (!visible) {
			itemLore$draggingLoreSelection = false;
			itemLore$colorWheelOpen = false;
			if (itemLore$colorWheelWidget != null) {
				itemLore$colorWheelWidget.stopDragging();
			}
		}
		itemLore$editor.visible = visible;
		itemLore$editor.active = visible;
		if (itemLore$hexInput != null) {
			itemLore$hexInput.visible = visible;
			itemLore$hexInput.active = visible;
		}

		itemLore$setWidgetVisible(itemLore$helpButton, visible);
		itemLore$setWidgetVisible(itemLore$closeButton, visible);
		itemLore$setWidgetVisible(itemLore$redDownButton, visible);
		itemLore$setWidgetVisible(itemLore$redUpButton, visible);
		itemLore$setWidgetVisible(itemLore$greenDownButton, visible);
		itemLore$setWidgetVisible(itemLore$greenUpButton, visible);
		itemLore$setWidgetVisible(itemLore$blueDownButton, visible);
		itemLore$setWidgetVisible(itemLore$blueUpButton, visible);
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
		if (itemLore$colorWheelWidget != null) {
			itemLore$colorWheelWidget.visible = visible && itemLore$colorWheelOpen;
			itemLore$colorWheelWidget.active = visible && itemLore$colorWheelOpen;
			itemLore$colorWheelWidget.setColor(itemLore$currentColor());
			if (!itemLore$colorWheelWidget.visible) {
				itemLore$colorWheelWidget.stopDragging();
			}
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
		return panelWidth >= itemLore$MIN_PANEL_WIDTH
				&& width >= combinedWidth + 2 * itemLore$SCREEN_MARGIN
				&& height >= Math.max(imageHeight, itemLore$PANEL_HEIGHT) + 2 * itemLore$SCREEN_MARGIN;
	}

	@Unique
	private void itemLore$setPanelOpen(boolean open) {
		if (open && !itemLore$canOpenPanel()) {
			return;
		}
		itemLore$panelOpen = open;
		itemLore$draggingLoreSelection = false;
		itemLore$colorWheelOpen = false;
		if (itemLore$colorWheelWidget != null) {
			itemLore$colorWheelWidget.stopDragging();
		}
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
	private void itemLore$unfocusColorSliders() {
		Object focused = getFocused();
		if (focused == itemLore$redSlider || focused == itemLore$greenSlider || focused == itemLore$blueSlider) {
			setFocused(null);
		}
		if (itemLore$redSlider != null) {
			itemLore$redSlider.setFocused(false);
		}
		if (itemLore$greenSlider != null) {
			itemLore$greenSlider.setFocused(false);
		}
		if (itemLore$blueSlider != null) {
			itemLore$blueSlider.setFocused(false);
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
		value = Math.max(0, Math.min(255, value));
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
		itemLore$updateHexInput();
	}

	@Unique
	private void itemLore$adjustActiveColorComponent(int component, int amount) {
		int current = switch (component) {
			case 0 -> itemLore$activeRed();
			case 1 -> itemLore$activeGreen();
			default -> itemLore$activeBlue();
		};
		itemLore$setActiveColorComponent(component, current + amount);
		itemLore$syncSlidersToActiveColor();
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
		itemLore$updateHexInput();
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
		if (itemLore$colorWheelWidget != null) {
			itemLore$colorWheelWidget.setColor(itemLore$currentColor());
		}
	}

	@Unique
	private void itemLore$onHexInputChanged(String value) {
		if (itemLore$suppressHexInput) {
			return;
		}

		int color = itemLore$parseHexInput(value);
		if (color < 0) {
			return;
		}

		itemLore$setActiveColor(color);
	}

	@Unique
	private void itemLore$setActiveColor(int rgb) {
		rgb &= 0xFFFFFF;
		if (itemLore$activeColorSlot == 0) {
			itemLore$pickerOneRed = (rgb >> 16) & 0xFF;
			itemLore$pickerOneGreen = (rgb >> 8) & 0xFF;
			itemLore$pickerOneBlue = rgb & 0xFF;
		} else {
			itemLore$pickerTwoRed = (rgb >> 16) & 0xFF;
			itemLore$pickerTwoGreen = (rgb >> 8) & 0xFF;
			itemLore$pickerTwoBlue = rgb & 0xFF;
		}
		itemLore$syncSlidersToActiveColor();
	}

	@Unique
	private void itemLore$updateHexInput() {
		if (itemLore$hexInput == null) {
			return;
		}

		String hex = itemLore$currentHex();
		if (!hex.equalsIgnoreCase(itemLore$hexInput.getValue())) {
			itemLore$suppressHexInput = true;
			itemLore$hexInput.setValue(hex);
			itemLore$suppressHexInput = false;
		}
	}

	@Unique
	private int itemLore$parseHexInput(String value) {
		if (value == null) {
			return -1;
		}

		String text = value.trim();
		if (text.startsWith("#")) {
			text = text.substring(1);
		}

		if (text.length() != 6) {
			return -1;
		}

		for (int i = 0; i < text.length(); i++) {
			if (Character.digit(text.charAt(i), 16) < 0) {
				return -1;
			}
		}

		return Integer.parseInt(text, 16);
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
	private boolean itemLore$routeKeyToColorSlider(KeyEvent event) {
		return itemLore$routeKeyToColorSlider(itemLore$redSlider, event)
				|| itemLore$routeKeyToColorSlider(itemLore$greenSlider, event)
				|| itemLore$routeKeyToColorSlider(itemLore$blueSlider, event);
	}

	@Unique
	private boolean itemLore$routeKeyToColorSlider(ColorValueSlider slider, KeyEvent event) {
		return slider != null && slider.visible && slider.isFocused() && slider.keyPressed(event);
	}

	@Unique
	private boolean itemLore$routeCharToColorSlider(CharacterEvent event) {
		return itemLore$routeCharToColorSlider(itemLore$redSlider, event)
				|| itemLore$routeCharToColorSlider(itemLore$greenSlider, event)
				|| itemLore$routeCharToColorSlider(itemLore$blueSlider, event);
	}

	@Unique
	private boolean itemLore$routeCharToColorSlider(ColorValueSlider slider, CharacterEvent event) {
		return slider != null && slider.visible && slider.isFocused() && slider.charTyped(event);
	}

	@Unique
	private List<Component> itemLore$helpTooltipLines() {
		return List.of(
				Component.translatable("gui.item_lore.help.color_intro"),
				Component.translatable("gui.item_lore.help.color_example"),
				Component.translatable("gui.item_lore.help.gradient_intro"),
				Component.translatable("gui.item_lore.help.gradient_example"),
				Component.translatable("gui.item_lore.help.nesting_intro"),
				Component.translatable("gui.item_lore.help.nesting_wrong_a"),
				Component.translatable("gui.item_lore.help.nesting_wrong_b"),
				Component.translatable("gui.item_lore.help.nesting_right_a"),
				Component.translatable("gui.item_lore.help.nesting_right_b"),
				Component.translatable("gui.item_lore.help.nesting_right_gradient"),
				Component.translatable("gui.item_lore.help.colors"),
				Component.translatable("gui.item_lore.help.sliders"),
				Component.translatable("gui.item_lore.help.slider_keys"),
				Component.translatable("gui.item_lore.help.format_a"),
				Component.translatable("gui.item_lore.help.format_b"),
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
	private boolean itemLore$isInColorSwatchBounds(double mouseX, double mouseY) {
		return itemLore$isInBounds(mouseX, mouseY, itemLore$colorSwatchX, itemLore$colorSwatchY, itemLore$COLOR_SWATCH_SIZE, itemLore$COLOR_SWATCH_SIZE);
	}

	@Unique
	private boolean itemLore$isInColorSliderBounds(double mouseX, double mouseY) {
		return itemLore$isInColorSliderBounds(itemLore$redSlider, mouseX, mouseY)
				|| itemLore$isInColorSliderBounds(itemLore$greenSlider, mouseX, mouseY)
				|| itemLore$isInColorSliderBounds(itemLore$blueSlider, mouseX, mouseY);
	}

	@Unique
	private boolean itemLore$isInColorSliderBounds(ColorValueSlider slider, double mouseX, double mouseY) {
		return slider != null
				&& slider.visible
				&& itemLore$isInBounds(mouseX, mouseY, slider.getX(), slider.getY(), slider.getWidth(), slider.getHeight());
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
