package com.reign.betterlore.mixin.client;

import com.reign.betterlore.access.AnvilLoreScreenBridge;
import com.reign.betterlore.access.RecipeViewerArea;
import com.reign.betterlore.client.ColorValueSlider;
import com.reign.betterlore.client.ColorWheelWidget;
import com.reign.betterlore.lore.LoreMarkupParser;
import com.reign.betterlore.lore.ParseResult;
import com.reign.betterlore.client.net.ClientAnvilLoreNetworking;
import com.reign.betterlore.net.ServerboundAnvilLoreUpdatePayload;
import com.reign.betterlore.net.ServerboundAnvilNameUpdatePayload;
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
	private static final int betterLore$PREFERRED_PANEL_WIDTH = 210;
	@Unique
	private static final int betterLore$MIN_PANEL_WIDTH = 188;
	@Unique
	private static final int betterLore$PANEL_GAP = 10;
	@Unique
	private static final int betterLore$SCREEN_MARGIN = 6;
	@Unique
	private static final int betterLore$PANEL_PADDING = 7;
	@Unique
	private static final int betterLore$PANEL_HEIGHT = 232;
	@Unique
	private static final int betterLore$SEND_DELAY_TICKS = 6;
	@Unique
	private static final int betterLore$LORE_BUTTON_WIDTH = 44;
	@Unique
	private static final int betterLore$LORE_BUTTON_HEIGHT = 16;
	@Unique
	private static final int betterLore$SMALL_BUTTON_SIZE = 16;
	@Unique
	private static final int betterLore$COLOR_SWATCH_SIZE = 12;

	@Unique
	private static final int betterLore$VANILLA_PANEL = 0xFFC6C6C6;
	@Unique
	private static final int betterLore$VANILLA_PANEL_LIGHT = 0xFFFFFFFF;
	@Unique
	private static final int betterLore$VANILLA_PANEL_DARK = 0xFF555555;
	@Unique
	private static final int betterLore$VANILLA_PANEL_SHADOW = 0xFF3A3A3A;
	@Unique
	private static final int betterLore$VANILLA_TEXT = 0xFF404040;
	@Unique
	private static final int betterLore$VANILLA_TEXT_MUTED = 0xFF666666;
	@Unique
	private static final int betterLore$VANILLA_TEXT_ERROR = 0xFFFF5555;

	@Shadow
	private EditBox name;

	@Unique
	private MultiLineEditBox betterLore$editor;
	@Unique
	private ColorValueSlider betterLore$redSlider;
	@Unique
	private ColorValueSlider betterLore$greenSlider;
	@Unique
	private ColorValueSlider betterLore$blueSlider;
	@Unique
	private EditBox betterLore$hexInput;
	@Unique
	private ColorWheelWidget betterLore$colorWheelWidget;
	@Unique
	private Button betterLore$redDownButton;
	@Unique
	private Button betterLore$redUpButton;
	@Unique
	private Button betterLore$greenDownButton;
	@Unique
	private Button betterLore$greenUpButton;
	@Unique
	private Button betterLore$blueDownButton;
	@Unique
	private Button betterLore$blueUpButton;
	@Unique
	private Button betterLore$toggleButton;
	@Unique
	private Button betterLore$helpButton;
	@Unique
	private Button betterLore$closeButton;
	@Unique
	private Button betterLore$firstColorButton;
	@Unique
	private Button betterLore$secondColorButton;
	@Unique
	private Button betterLore$copyHexButton;
	@Unique
	private Button betterLore$insertColorButton;
	@Unique
	private Button betterLore$insertGradientButton;
	@Unique
	private Button betterLore$formatBoldButton;
	@Unique
	private Button betterLore$formatItalicButton;
	@Unique
	private Button betterLore$formatUnderlineButton;
	@Unique
	private Button betterLore$formatStrikeButton;
	@Unique
	private Button betterLore$formatObfuscatedButton;

	@Unique
	private boolean betterLore$panelOpen = false;
	@Unique
	private boolean betterLore$draggingLoreSelection = false;
	@Unique
	private boolean betterLore$colorWheelOpen = false;
	@Unique
	private boolean betterLore$insertTargetName = false;
	@Unique
	private int betterLore$sessionId = 0;
	@Unique
	private String betterLore$raw = "";
	@Unique
	private ParseResult betterLore$parseResult = LoreMarkupParser.parse("");
	@Unique
	private boolean betterLore$dirty = false;
	@Unique
	private boolean betterLore$suppressListener = false;
	@Unique
	private boolean betterLore$suppressHexInput = false;
	@Unique
	private int betterLore$sendDelay = 0;
	@Unique
	private int betterLore$panelX;
	@Unique
	private int betterLore$panelY;
	@Unique
	private int betterLore$panelWidth;
	@Unique
	private int betterLore$panelHeight;
	@Unique
	private int betterLore$colorSwatchX;
	@Unique
	private int betterLore$colorSwatchY;
	@Unique
	private int betterLore$pickerOneRed = 255;
	@Unique
	private int betterLore$pickerOneGreen = 102;
	@Unique
	private int betterLore$pickerOneBlue = 0;
	@Unique
	private int betterLore$pickerTwoRed = 170;
	@Unique
	private int betterLore$pickerTwoGreen = 18;
	@Unique
	private int betterLore$pickerTwoBlue = 8;
	@Unique
	private int betterLore$activeColorSlot = 0;
	@Unique
	private String betterLore$lastObservedName = "";
	@Unique
	private String betterLore$lastSentName = "";
	@Unique
	private boolean betterLore$nameDirty = false;
	@Unique
	private boolean betterLore$suppressNameSync = false;
	@Unique
	private int betterLore$nameSendDelay = 0;
	@Unique
	private int betterLore$nameStateGraceTicks = 0;

	private AnvilScreenMixin(AnvilMenu menu, Inventory inventory, Component title, Identifier menuResource) {
		super(menu, inventory, title, menuResource);
	}

	@Inject(method = "subInit", at = @At("HEAD"))
	private void betterLore$reserveRightSidePanelSpace(CallbackInfo ci) {
		if (!betterLore$panelOpen || !betterLore$serverSupportsUi()) {
			return;
		}

		int panelWidth = betterLore$preferredPanelWidthForScreen();
		int combinedWidth = imageWidth + betterLore$PANEL_GAP + panelWidth;
		if (panelWidth >= betterLore$MIN_PANEL_WIDTH && width >= combinedWidth + 2 * betterLore$SCREEN_MARGIN) {
			leftPos = Math.max(betterLore$SCREEN_MARGIN, (width - combinedWidth) / 2);
		}
	}

	@Inject(method = "subInit", at = @At("TAIL"))
	private void betterLore$initLoreWidgets(CallbackInfo ci) {
		betterLore$editor = MultiLineEditBox.builder()
				.setX(0)
				.setY(0)
				.setPlaceholder(Component.translatable("gui.better_lore.placeholder"))
				.build(font, 120, 58, Component.translatable("gui.better_lore.editor"));

		// Do not use MultiLineEditBox#setCharacterLimit here: it renders a raw
		// "0 / 4096" counter, which is an implementation detail rather than the
		// player-facing lore limit. The server still enforces the raw packet cap,
		// and betterLore$onEditorChanged clamps oversized client input before send.
		betterLore$editor.setLineLimit(LoreMarkupParser.MAX_LINES);
		betterLore$editor.setValueListener(this::betterLore$onEditorChanged);
		addRenderableWidget(betterLore$editor);

		betterLore$toggleButton = Button.builder(Component.translatable("gui.better_lore.button"), button -> betterLore$setPanelOpen(true))
				.bounds(0, 0, betterLore$LORE_BUTTON_WIDTH, betterLore$LORE_BUTTON_HEIGHT)
				.build();
		betterLore$helpButton = Button.builder(Component.literal("?"), button -> {})
				.bounds(0, 0, betterLore$SMALL_BUTTON_SIZE, betterLore$SMALL_BUTTON_SIZE)
				.build();
		betterLore$closeButton = Button.builder(Component.literal("×"), button -> betterLore$setPanelOpen(false))
				.bounds(0, 0, betterLore$SMALL_BUTTON_SIZE, betterLore$SMALL_BUTTON_SIZE)
				.build();
		addRenderableWidget(betterLore$toggleButton);
		addRenderableWidget(betterLore$helpButton);
		addRenderableWidget(betterLore$closeButton);

		betterLore$redSlider = new ColorValueSlider(0, 0, 100, 12, "R", betterLore$activeRed(), value -> betterLore$setActiveColorComponent(0, value));
		betterLore$greenSlider = new ColorValueSlider(0, 0, 100, 12, "G", betterLore$activeGreen(), value -> betterLore$setActiveColorComponent(1, value));
		betterLore$blueSlider = new ColorValueSlider(0, 0, 100, 12, "B", betterLore$activeBlue(), value -> betterLore$setActiveColorComponent(2, value));
		betterLore$hexInput = new EditBox(font, 0, 0, 54, 12, Component.translatable("gui.better_lore.hex_input"));
		betterLore$hexInput.setMaxLength(7);
		betterLore$hexInput.setValue(betterLore$currentHex());
		betterLore$hexInput.setResponder(this::betterLore$onHexInputChanged);
		betterLore$redDownButton = Button.builder(Component.literal("<"), button -> betterLore$adjustActiveColorComponent(0, -1))
				.bounds(0, 0, 12, 12)
				.build();
		betterLore$redUpButton = Button.builder(Component.literal(">"), button -> betterLore$adjustActiveColorComponent(0, 1))
				.bounds(0, 0, 12, 12)
				.build();
		betterLore$greenDownButton = Button.builder(Component.literal("<"), button -> betterLore$adjustActiveColorComponent(1, -1))
				.bounds(0, 0, 12, 12)
				.build();
		betterLore$greenUpButton = Button.builder(Component.literal(">"), button -> betterLore$adjustActiveColorComponent(1, 1))
				.bounds(0, 0, 12, 12)
				.build();
		betterLore$blueDownButton = Button.builder(Component.literal("<"), button -> betterLore$adjustActiveColorComponent(2, -1))
				.bounds(0, 0, 12, 12)
				.build();
		betterLore$blueUpButton = Button.builder(Component.literal(">"), button -> betterLore$adjustActiveColorComponent(2, 1))
				.bounds(0, 0, 12, 12)
				.build();
		addRenderableWidget(betterLore$redSlider);
		addRenderableWidget(betterLore$greenSlider);
		addRenderableWidget(betterLore$blueSlider);
		addRenderableWidget(betterLore$hexInput);
		addRenderableWidget(betterLore$redDownButton);
		addRenderableWidget(betterLore$redUpButton);
		addRenderableWidget(betterLore$greenDownButton);
		addRenderableWidget(betterLore$greenUpButton);
		addRenderableWidget(betterLore$blueDownButton);
		addRenderableWidget(betterLore$blueUpButton);

		betterLore$firstColorButton = Button.builder(Component.empty(), button -> betterLore$selectColorSlot(0))
				.bounds(0, 0, 60, 18)
				.build();
		betterLore$secondColorButton = Button.builder(Component.empty(), button -> betterLore$selectColorSlot(1))
				.bounds(0, 0, 60, 18)
				.build();
		betterLore$copyHexButton = Button.builder(Component.empty(), button -> betterLore$copyCurrentHex())
				.bounds(0, 0, 60, 18)
				.build();
		betterLore$insertColorButton = Button.builder(Component.translatable("gui.better_lore.insert_color"), button -> betterLore$insertMarkupTemplate("<c " + betterLore$currentHex() + ">", "</c>"))
				.bounds(0, 0, 60, 18)
				.build();
		betterLore$insertGradientButton = Button.builder(Component.translatable("gui.better_lore.insert_gradient"), button -> betterLore$insertMarkupTemplate("<gr " + betterLore$firstHex() + " " + betterLore$secondHex() + ">", "</gr>"))
				.bounds(0, 0, 60, 18)
				.build();
		betterLore$formatBoldButton = Button.builder(Component.translatable("gui.better_lore.format_bold").withStyle(ChatFormatting.BOLD), button -> betterLore$insertMarkupTemplate("<b>", "</b>"))
				.bounds(0, 0, 20, 16)
				.build();
		betterLore$formatItalicButton = Button.builder(Component.translatable("gui.better_lore.format_italic").withStyle(ChatFormatting.ITALIC), button -> betterLore$insertMarkupTemplate("<i>", "</i>"))
				.bounds(0, 0, 20, 16)
				.build();
		betterLore$formatUnderlineButton = Button.builder(Component.translatable("gui.better_lore.format_underline").withStyle(ChatFormatting.UNDERLINE), button -> betterLore$insertMarkupTemplate("<underlined>", "</underlined>"))
				.bounds(0, 0, 20, 16)
				.build();
		betterLore$formatStrikeButton = Button.builder(Component.translatable("gui.better_lore.format_strike").withStyle(ChatFormatting.STRIKETHROUGH), button -> betterLore$insertMarkupTemplate("<st>", "</st>"))
				.bounds(0, 0, 20, 16)
				.build();
		betterLore$formatObfuscatedButton = Button.builder(Component.translatable("gui.better_lore.format_obfuscated"), button -> betterLore$insertMarkupTemplate("<obf>", "</obf>"))
				.bounds(0, 0, 20, 16)
				.build();
		addRenderableWidget(betterLore$firstColorButton);
		addRenderableWidget(betterLore$secondColorButton);
		addRenderableWidget(betterLore$copyHexButton);
		addRenderableWidget(betterLore$insertColorButton);
		addRenderableWidget(betterLore$insertGradientButton);
		addRenderableWidget(betterLore$formatBoldButton);
		addRenderableWidget(betterLore$formatItalicButton);
		addRenderableWidget(betterLore$formatUnderlineButton);
		addRenderableWidget(betterLore$formatStrikeButton);
		addRenderableWidget(betterLore$formatObfuscatedButton);

		betterLore$colorWheelWidget = new ColorWheelWidget(0, 0, betterLore$currentColor(), this::betterLore$setActiveColor);
		addRenderableWidget(betterLore$colorWheelWidget);

		betterLore$setEditorValue(betterLore$raw, false);
		betterLore$syncSlidersToActiveColor();
		betterLore$applyAnvilLayout();
	}

	@Inject(method = "containerTick", at = @At("TAIL"))
	private void betterLore$tickLoreSender(CallbackInfo ci) {
		if (!betterLore$serverSupportsUi()) {
			if (betterLore$panelOpen) {
				betterLore$setPanelOpen(false);
			} else {
				betterLore$layoutLoreWidgets();
			}
			return;
		}

		betterLore$tickNameSender();

		if (betterLore$sendDelay > 0) {
			betterLore$sendDelay--;
		}

		if (betterLore$dirty && betterLore$sendDelay <= 0 && ClientAnvilLoreNetworking.canSendLoreUpdate()) {
			ClientAnvilLoreNetworking.sendLoreUpdate(new ServerboundAnvilLoreUpdatePayload(menu.containerId, betterLore$sessionId, betterLore$raw));
			betterLore$dirty = false;
		}
	}

	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void betterLore$extractLorePanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (betterLore$editor == null || !betterLore$editor.visible || !betterLore$panelOpen) {
			return;
		}

		betterLore$drawVanillaPanel(graphics, betterLore$panelX, betterLore$panelY, betterLore$panelWidth, betterLore$panelHeight);

		int textX = betterLore$panelX + betterLore$PANEL_PADDING;
		int contentWidth = betterLore$panelWidth - 2 * betterLore$PANEL_PADDING;
		int titleY = betterLore$panelY + 10;

		Component title = Component.translatable("gui.better_lore.title");
		graphics.text(font, title, textX, titleY, betterLore$VANILLA_TEXT, false);

		Component count = Component.translatable(
				"gui.better_lore.symbols",
				betterLore$parseResult.visibleCodePoints(),
				LoreMarkupParser.MAX_VISIBLE_CODEPOINTS
		);
		int countColor = betterLore$parseResult.isSuccess() ? betterLore$VANILLA_TEXT_MUTED : betterLore$VANILLA_TEXT_ERROR;
		int countX = textX + font.width(title) + 10;
		if (betterLore$helpButton != null) {
			int maxCountX = betterLore$helpButton.getX() - 4 - font.width(count);
			countX = Math.min(countX, Math.max(textX, maxCountX));
		}
		graphics.text(font, count, countX, titleY, countColor, false);

		int colorTitleY = betterLore$firstColorButton.getY() - 14;
		Component colorTitle = Component.translatable("gui.better_lore.color_helper");
		graphics.text(font, colorTitle, textX, colorTitleY, betterLore$VANILLA_TEXT, false);

		int previewColor = 0xFF000000 | betterLore$currentColor();
		Component previewWord = Component.translatable("gui.better_lore.color_preview");
		int previewX = textX + font.width(colorTitle) + 8;
		graphics.text(font, previewWord, previewX, colorTitleY, previewColor, false);

		graphics.fill(betterLore$colorSwatchX, betterLore$colorSwatchY, betterLore$colorSwatchX + betterLore$COLOR_SWATCH_SIZE, betterLore$colorSwatchY + betterLore$COLOR_SWATCH_SIZE, betterLore$VANILLA_PANEL_SHADOW);
		graphics.fill(betterLore$colorSwatchX + 1, betterLore$colorSwatchY + 1, betterLore$colorSwatchX + betterLore$COLOR_SWATCH_SIZE - 1, betterLore$colorSwatchY + betterLore$COLOR_SWATCH_SIZE - 1, previewColor);

		if (betterLore$helpButton != null && betterLore$isInWidgetBounds(mouseX, mouseY, betterLore$helpButton)) {
			int tooltipX = Math.min(mouseX, Math.max(0, width - 340));
			graphics.setComponentTooltipForNextFrame(font, betterLore$helpTooltipLines(), tooltipX, mouseY);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void betterLore$routeKeysToLoreEditor(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (betterLore$hexInput != null && betterLore$hexInput.visible && betterLore$hexInput.isFocused() && betterLore$hexInput.keyPressed(event)) {
			cir.setReturnValue(true);
			return;
		}

		if (betterLore$routeKeyToColorSlider(event)) {
			cir.setReturnValue(true);
			return;
		}

		if (betterLore$editor != null && betterLore$editor.visible && betterLore$editor.isFocused() && betterLore$editor.keyPressed(event)) {
			cir.setReturnValue(true);
		}
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (betterLore$hexInput != null && betterLore$hexInput.visible && betterLore$hexInput.isFocused() && betterLore$hexInput.charTyped(event)) {
			return true;
		}

		if (betterLore$routeCharToColorSlider(event)) {
			return true;
		}

		return super.charTyped(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && !betterLore$isInColorSliderBounds(event.x(), event.y())) {
			betterLore$unfocusColorSliders();
		}

		if (name != null && event.button() == 0 && betterLore$isInWidgetBounds(event.x(), event.y(), name)) {
			betterLore$insertTargetName = true;
			if (betterLore$editor != null) {
				betterLore$editor.setFocused(false);
			}
			return super.mouseClicked(event, doubleClick);
		}

		if (betterLore$editor != null && betterLore$editor.visible && betterLore$panelOpen && event.button() == 0) {
			if (betterLore$colorWheelOpen && betterLore$colorWheelWidget != null && betterLore$colorWheelWidget.visible && betterLore$colorWheelWidget.mouseClicked(event, doubleClick)) {
				return true;
			}

			if (betterLore$isInColorSwatchBounds(event.x(), event.y())) {
				betterLore$colorWheelOpen = !betterLore$colorWheelOpen;
				betterLore$layoutLoreWidgets();
				return true;
			}

			if (betterLore$colorWheelOpen && betterLore$colorWheelWidget != null && !betterLore$colorWheelWidget.isMouseOver(event.x(), event.y())) {
				betterLore$colorWheelOpen = false;
				betterLore$layoutLoreWidgets();
			}

			if (betterLore$isInEditorBounds(event.x(), event.y())) {
				betterLore$insertTargetName = false;
				betterLore$draggingLoreSelection = true;
				betterLore$focusLoreEditor();
				betterLore$editor.mouseClicked(event, doubleClick);
				return true;
			}

			if (betterLore$isInPanelBounds(event.x(), event.y())) {
				if (super.mouseClicked(event, doubleClick)) {
					return true;
				}
				return true;
			}

			betterLore$editor.setFocused(false);
			betterLore$draggingLoreSelection = false;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (betterLore$colorWheelWidget != null && betterLore$colorWheelWidget.visible && betterLore$colorWheelWidget.mouseDragged(event, dragX, dragY)) {
			return true;
		}

		if (betterLore$draggingLoreSelection
				&& betterLore$editor != null
				&& betterLore$editor.visible
				&& betterLore$panelOpen
				&& event.button() == 0) {
			betterLore$insertTargetName = false;
			betterLore$focusLoreEditor();
			betterLore$editor.mouseDragged(event, dragX, dragY);
			return true;
		}

		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (betterLore$colorWheelWidget != null && betterLore$colorWheelWidget.visible && betterLore$colorWheelWidget.mouseReleased(event)) {
			return true;
		}

		if (betterLore$draggingLoreSelection && event.button() == 0) {
			betterLore$draggingLoreSelection = false;
			if (betterLore$editor != null && betterLore$editor.visible) {
				betterLore$editor.mouseReleased(event);
			}
			return true;
		}

		return super.mouseReleased(event);
	}

	@Override
	public void betterLore$acceptServerLoreState(int containerId, int sessionId, String rawLoreMarkup, String rawNameMarkup) {
		if (containerId != menu.containerId) {
			return;
		}

		if (betterLore$sessionId != sessionId || !betterLore$dirty) {
			betterLore$sessionId = sessionId;
			betterLore$setEditorValue(rawLoreMarkup, false);
			betterLore$dirty = false;
			betterLore$sendDelay = 0;
		} else {
			betterLore$sessionId = sessionId;
		}

		betterLore$acceptServerNameState(rawNameMarkup);
	}

	@Override
	public List<RecipeViewerArea> betterLore$getRecipeViewerExclusionAreas() {
		if (!betterLore$serverSupportsUi()) {
			return List.of();
		}

		if (betterLore$panelOpen && betterLore$editor != null && betterLore$editor.visible) {
			return List.of(new RecipeViewerArea(betterLore$panelX, betterLore$panelY, betterLore$panelWidth, betterLore$panelHeight));
		}

		if (betterLore$toggleButton != null && betterLore$toggleButton.visible) {
			return List.of(new RecipeViewerArea(betterLore$toggleButton.getX(), betterLore$toggleButton.getY(), betterLore$toggleButton.getWidth(), betterLore$toggleButton.getHeight()));
		}

		return List.of();
	}

	@Unique
	private void betterLore$configureNameField() {
		if (name == null) {
			return;
		}

		if (betterLore$serverSupportsUi()) {
			name.setMaxLength(LoreMarkupParser.MAX_RAW_CHARS);
		}
		name.setX(leftPos + 62);
		name.setY(topPos + 24);
		name.setWidth(103);
		betterLore$lastObservedName = betterLore$limitRawInput(name.getValue());
		betterLore$lastSentName = betterLore$lastObservedName;
	}

	@Unique
	private void betterLore$tickNameSender() {
		if (name == null || betterLore$suppressNameSync || !ClientAnvilLoreNetworking.canSendNameUpdate()) {
			return;
		}

		String current = betterLore$limitRawInput(name.getValue());
		if (!current.equals(name.getValue())) {
			betterLore$suppressNameSync = true;
			name.setValue(current);
			betterLore$suppressNameSync = false;
		}

		if (betterLore$nameStateGraceTicks > 0) {
			betterLore$lastObservedName = current;
			betterLore$nameStateGraceTicks--;
			return;
		}

		if (!current.equals(betterLore$lastObservedName)) {
			betterLore$lastObservedName = current;
			betterLore$nameDirty = true;
			betterLore$nameSendDelay = betterLore$SEND_DELAY_TICKS;
		}

		if (betterLore$nameSendDelay > 0) {
			betterLore$nameSendDelay--;
		}

		if (betterLore$nameDirty && betterLore$nameSendDelay <= 0) {
			if (!current.equals(betterLore$lastSentName)) {
				ClientAnvilLoreNetworking.sendNameUpdate(new ServerboundAnvilNameUpdatePayload(menu.containerId, betterLore$sessionId, current));
				betterLore$lastSentName = current;
			}
			betterLore$nameDirty = false;
		}
	}

	@Unique
	private void betterLore$acceptServerNameState(String rawNameMarkup) {
		if (name == null || !betterLore$serverSupportsUi()) {
			return;
		}

		String safeRawName = betterLore$limitRawInput(rawNameMarkup == null ? "" : rawNameMarkup);
		boolean forceCustomNameSync = false;
		betterLore$suppressNameSync = true;
		if (!safeRawName.isEmpty()) {
			name.setValue(safeRawName);
			betterLore$lastObservedName = safeRawName;
			// Force one custom name sync so the server can correct vanilla's raw-tag rename preview.
			betterLore$lastSentName = "";
			forceCustomNameSync = true;
		} else {
			betterLore$lastObservedName = betterLore$limitRawInput(name.getValue());
			betterLore$lastSentName = "";
		}
		betterLore$nameDirty = forceCustomNameSync;
		betterLore$nameSendDelay = 0;
		betterLore$nameStateGraceTicks = forceCustomNameSync ? 0 : 2;
		betterLore$suppressNameSync = false;
	}

	@Unique
	private void betterLore$onEditorChanged(String value) {
		if (betterLore$suppressListener) {
			return;
		}

		String trimmed = betterLore$limitRawInput(value == null ? "" : value);
		if (!trimmed.equals(value)) {
			betterLore$setEditorValue(trimmed, true);
			betterLore$sendDelay = betterLore$SEND_DELAY_TICKS;
			return;
		}

		betterLore$raw = trimmed;
		betterLore$parseResult = LoreMarkupParser.parse(trimmed);
		betterLore$dirty = true;
		betterLore$sendDelay = betterLore$SEND_DELAY_TICKS;
	}

	@Unique
	private void betterLore$setEditorValue(String value, boolean dirty) {
		betterLore$raw = betterLore$limitRawInput(value == null ? "" : value);
		betterLore$parseResult = LoreMarkupParser.parse(betterLore$raw);
		betterLore$dirty = dirty;

		if (betterLore$editor != null) {
			betterLore$suppressListener = true;
			betterLore$editor.setValue(betterLore$raw);
			betterLore$suppressListener = false;
		}
	}

	@Unique
	private String betterLore$limitRawInput(String value) {
		return value.length() <= LoreMarkupParser.MAX_RAW_CHARS
				? value
				: value.substring(0, LoreMarkupParser.MAX_RAW_CHARS);
	}

	@Unique
	private void betterLore$applyAnvilLayout() {
		leftPos = (width - imageWidth) / 2;
		if (betterLore$panelOpen && betterLore$serverSupportsUi()) {
			int panelWidth = betterLore$preferredPanelWidthForScreen();
			int combinedWidth = imageWidth + betterLore$PANEL_GAP + panelWidth;
			if (panelWidth >= betterLore$MIN_PANEL_WIDTH && width >= combinedWidth + 2 * betterLore$SCREEN_MARGIN) {
				leftPos = Math.max(betterLore$SCREEN_MARGIN, (width - combinedWidth) / 2);
			}
		}
		betterLore$configureNameField();
		betterLore$layoutLoreWidgets();
	}

	@Unique
	private void betterLore$layoutLoreWidgets() {
		if (betterLore$editor == null) {
			return;
		}

		boolean supported = betterLore$serverSupportsUi();
		boolean panelCanOpen = betterLore$canOpenPanel();
		if (betterLore$panelOpen && !panelCanOpen) {
			betterLore$panelOpen = false;
		}
		betterLore$layoutToggleButton(supported && !betterLore$panelOpen && panelCanOpen);

		betterLore$panelX = leftPos + imageWidth + betterLore$PANEL_GAP;
		int availableRight = width - betterLore$panelX - betterLore$SCREEN_MARGIN;
		betterLore$panelWidth = Math.min(betterLore$PREFERRED_PANEL_WIDTH, Math.max(0, availableRight));
		boolean visible = betterLore$panelOpen && panelCanOpen && betterLore$panelWidth >= betterLore$MIN_PANEL_WIDTH;
		betterLore$setLoreWidgetsVisible(visible);
		if (!visible) {
			return;
		}

		betterLore$panelHeight = Math.max(imageHeight, betterLore$PANEL_HEIGHT);
		betterLore$panelY = Math.max(betterLore$SCREEN_MARGIN, Math.min(topPos - Math.max(0, (betterLore$panelHeight - imageHeight) / 2), height - betterLore$panelHeight - betterLore$SCREEN_MARGIN));

		int textX = betterLore$panelX + betterLore$PANEL_PADDING;
		int contentWidth = betterLore$panelWidth - 2 * betterLore$PANEL_PADDING;
		int editorY = betterLore$panelY + 26;
		int editorHeight = 50;

		// Avoid AbstractWidget#setRectangle here. Its argument order is easy to get
		// wrong across mapping changes, and a wrong order turns the editor into a
		// huge invisible/clickable rectangle over the vanilla anvil slots.
		betterLore$editor.setX(textX);
		betterLore$editor.setY(editorY);
		betterLore$editor.setWidth(contentWidth);
		betterLore$editor.setHeight(editorHeight);

		int topButtonY = betterLore$panelY + 5;
		betterLore$positionWidget(betterLore$closeButton, betterLore$panelX + betterLore$panelWidth - betterLore$PANEL_PADDING - betterLore$SMALL_BUTTON_SIZE, topButtonY, betterLore$SMALL_BUTTON_SIZE, betterLore$SMALL_BUTTON_SIZE);
		betterLore$positionWidget(betterLore$helpButton, betterLore$closeButton.getX() - betterLore$SMALL_BUTTON_SIZE - 3, topButtonY, betterLore$SMALL_BUTTON_SIZE, betterLore$SMALL_BUTTON_SIZE);

		int colorTitleY = editorY + editorHeight + 5;
		int hexInputWidth = 55;
		int rightEdge = betterLore$panelX + betterLore$panelWidth - betterLore$PANEL_PADDING;
		betterLore$positionWidget(betterLore$hexInput, rightEdge - hexInputWidth, colorTitleY - 2, hexInputWidth, 14);
		betterLore$colorSwatchX = betterLore$hexInput.getX() - betterLore$COLOR_SWATCH_SIZE - 4;
		betterLore$colorSwatchY = betterLore$hexInput.getY() + Math.max(0, (betterLore$hexInput.getHeight() - betterLore$COLOR_SWATCH_SIZE) / 2);
		betterLore$layoutColorWheelWidget();

		int colorButtonsY = colorTitleY + 15;
		int columnGap = 6;
		int leftColumnWidth = (contentWidth - columnGap) / 2;
		int rightColumnX = textX + leftColumnWidth + columnGap;
		int rightColumnWidth = contentWidth - leftColumnWidth - columnGap;
		betterLore$positionWidget(betterLore$firstColorButton, textX, colorButtonsY, leftColumnWidth, 18);
		betterLore$positionWidget(betterLore$secondColorButton, rightColumnX, colorButtonsY, rightColumnWidth, 18);

		int sliderY = colorButtonsY + 22;
		int rowHeight = 18;
		int rowGap = 2;
		int arrowWidth = 12;
		int arrowGap = 2;
		int sliderWidth = Math.max(40, leftColumnWidth - 2 * arrowWidth - 2 * arrowGap);
		betterLore$layoutSliderRow(betterLore$redDownButton, betterLore$redSlider, betterLore$redUpButton, textX, sliderY, arrowWidth, arrowGap, sliderWidth, rowHeight);
		betterLore$layoutSliderRow(betterLore$greenDownButton, betterLore$greenSlider, betterLore$greenUpButton, textX, sliderY + rowHeight + rowGap, arrowWidth, arrowGap, sliderWidth, rowHeight);
		betterLore$layoutSliderRow(betterLore$blueDownButton, betterLore$blueSlider, betterLore$blueUpButton, textX, sliderY + 2 * (rowHeight + rowGap), arrowWidth, arrowGap, sliderWidth, rowHeight);

		betterLore$positionWidget(betterLore$copyHexButton, rightColumnX, sliderY, rightColumnWidth, rowHeight);
		betterLore$positionWidget(betterLore$insertColorButton, rightColumnX, sliderY + rowHeight + rowGap, rightColumnWidth, rowHeight);
		betterLore$positionWidget(betterLore$insertGradientButton, rightColumnX, sliderY + 2 * (rowHeight + rowGap), rightColumnWidth, rowHeight);

		int formatY = sliderY + 3 * (rowHeight + rowGap) + 2;
		int formatGap = 4;
		int formatRowOneA = 45;
		int formatRowOneB = 47;
		int formatRowOneC = contentWidth - formatRowOneA - formatRowOneB - 2 * formatGap;
		betterLore$positionWidget(betterLore$formatBoldButton, textX, formatY, formatRowOneA, 17);
		betterLore$positionWidget(betterLore$formatItalicButton, textX + formatRowOneA + formatGap, formatY, formatRowOneB, 17);
		betterLore$positionWidget(betterLore$formatUnderlineButton, textX + formatRowOneA + formatGap + formatRowOneB + formatGap, formatY, formatRowOneC, 17);

		int formatRowTwoY = formatY + 20;
		int formatRowTwoA = (contentWidth - formatGap) / 2;
		betterLore$positionWidget(betterLore$formatStrikeButton, textX, formatRowTwoY, formatRowTwoA, 17);
		betterLore$positionWidget(betterLore$formatObfuscatedButton, textX + formatRowTwoA + formatGap, formatRowTwoY, contentWidth - formatRowTwoA - formatGap, 17);

	}

	@Unique
	private void betterLore$layoutToggleButton(boolean visible) {
		if (betterLore$toggleButton == null) {
			return;
		}

		int x = leftPos + imageWidth + betterLore$PANEL_GAP;
		int y = name != null
				? name.getY() + 4 - betterLore$LORE_BUTTON_HEIGHT / 2
				: topPos + 24;
		if (x + betterLore$LORE_BUTTON_WIDTH > width - betterLore$SCREEN_MARGIN) {
			x = leftPos + imageWidth - betterLore$LORE_BUTTON_WIDTH - 6;
		}
		betterLore$positionWidget(betterLore$toggleButton, x, y, betterLore$LORE_BUTTON_WIDTH, betterLore$LORE_BUTTON_HEIGHT);
		betterLore$toggleButton.visible = visible;
		betterLore$toggleButton.active = visible;
	}

	@Unique
	private void betterLore$layoutSliderRow(Button downButton, ColorValueSlider slider, Button upButton, int x, int y, int arrowWidth, int gap, int sliderWidth, int rowHeight) {
		betterLore$positionWidget(downButton, x, y, arrowWidth, rowHeight);
		betterLore$positionWidget(slider, x + arrowWidth + gap, y, sliderWidth, rowHeight);
		betterLore$positionWidget(upButton, x + arrowWidth + gap + sliderWidth + gap, y, arrowWidth, rowHeight);
	}

	@Unique
	private void betterLore$positionWidget(Button widget, int x, int y, int widgetWidth, int widgetHeight) {
		if (widget == null) {
			return;
		}
		widget.setX(x);
		widget.setY(y);
		widget.setWidth(widgetWidth);
		widget.setHeight(widgetHeight);
	}

	@Unique
	private void betterLore$positionWidget(EditBox widget, int x, int y, int widgetWidth, int widgetHeight) {
		if (widget == null) {
			return;
		}
		widget.setX(x);
		widget.setY(y);
		widget.setWidth(widgetWidth);
		widget.setHeight(widgetHeight);
	}

	@Unique
	private void betterLore$positionWidget(ColorValueSlider widget, int x, int y, int widgetWidth, int widgetHeight) {
		if (widget == null) {
			return;
		}
		widget.setX(x);
		widget.setY(y);
		widget.setWidth(widgetWidth);
		widget.setHeight(widgetHeight);
	}

	@Unique
	private void betterLore$positionWidget(ColorWheelWidget widget, int x, int y, int widgetWidth, int widgetHeight) {
		if (widget == null) {
			return;
		}
		widget.setX(x);
		widget.setY(y);
		widget.setWidth(widgetWidth);
		widget.setHeight(widgetHeight);
	}

	@Unique
	private void betterLore$layoutColorWheelWidget() {
		if (betterLore$colorWheelWidget == null) {
			return;
		}

		int popupX = betterLore$colorSwatchX + betterLore$COLOR_SWATCH_SIZE - ColorWheelWidget.POPUP_WIDTH;
		int popupY = betterLore$colorSwatchY + betterLore$COLOR_SWATCH_SIZE + 4;
		popupX = Math.max(betterLore$SCREEN_MARGIN, Math.min(popupX, width - ColorWheelWidget.POPUP_WIDTH - betterLore$SCREEN_MARGIN));
		if (popupY + ColorWheelWidget.POPUP_HEIGHT > height - betterLore$SCREEN_MARGIN) {
			popupY = betterLore$colorSwatchY - ColorWheelWidget.POPUP_HEIGHT - 4;
		}
		popupY = Math.max(betterLore$SCREEN_MARGIN, Math.min(popupY, height - ColorWheelWidget.POPUP_HEIGHT - betterLore$SCREEN_MARGIN));
		betterLore$positionWidget(betterLore$colorWheelWidget, popupX, popupY, ColorWheelWidget.POPUP_WIDTH, ColorWheelWidget.POPUP_HEIGHT);
	}

	@Unique
	private void betterLore$setLoreWidgetsVisible(boolean visible) {
		if (!visible) {
			betterLore$draggingLoreSelection = false;
			betterLore$colorWheelOpen = false;
			if (betterLore$colorWheelWidget != null) {
				betterLore$colorWheelWidget.stopDragging();
			}
		}
		betterLore$editor.visible = visible;
		betterLore$editor.active = visible;
		if (betterLore$hexInput != null) {
			betterLore$hexInput.visible = visible;
			betterLore$hexInput.active = visible;
		}

		betterLore$setWidgetVisible(betterLore$helpButton, visible);
		betterLore$setWidgetVisible(betterLore$closeButton, visible);
		betterLore$setWidgetVisible(betterLore$redDownButton, visible);
		betterLore$setWidgetVisible(betterLore$redUpButton, visible);
		betterLore$setWidgetVisible(betterLore$greenDownButton, visible);
		betterLore$setWidgetVisible(betterLore$greenUpButton, visible);
		betterLore$setWidgetVisible(betterLore$blueDownButton, visible);
		betterLore$setWidgetVisible(betterLore$blueUpButton, visible);
		betterLore$setWidgetVisible(betterLore$firstColorButton, visible);
		betterLore$setWidgetVisible(betterLore$secondColorButton, visible);
		betterLore$setWidgetVisible(betterLore$copyHexButton, visible);
		betterLore$setWidgetVisible(betterLore$insertColorButton, visible);
		betterLore$setWidgetVisible(betterLore$insertGradientButton, visible);
		betterLore$setWidgetVisible(betterLore$formatBoldButton, visible);
		betterLore$setWidgetVisible(betterLore$formatItalicButton, visible);
		betterLore$setWidgetVisible(betterLore$formatUnderlineButton, visible);
		betterLore$setWidgetVisible(betterLore$formatStrikeButton, visible);
		betterLore$setWidgetVisible(betterLore$formatObfuscatedButton, visible);
		if (betterLore$redSlider != null) {
			betterLore$redSlider.visible = visible;
			betterLore$redSlider.active = visible;
			betterLore$greenSlider.visible = visible;
			betterLore$greenSlider.active = visible;
			betterLore$blueSlider.visible = visible;
			betterLore$blueSlider.active = visible;
		}
		if (betterLore$colorWheelWidget != null) {
			betterLore$colorWheelWidget.visible = visible && betterLore$colorWheelOpen;
			betterLore$colorWheelWidget.active = visible && betterLore$colorWheelOpen;
			betterLore$colorWheelWidget.setColor(betterLore$currentColor());
			if (!betterLore$colorWheelWidget.visible) {
				betterLore$colorWheelWidget.stopDragging();
			}
		}
	}

	@Unique
	private void betterLore$setWidgetVisible(Button widget, boolean visible) {
		if (widget == null) {
			return;
		}
		widget.visible = visible;
		widget.active = visible;
	}

	@Unique
	private int betterLore$preferredPanelWidthForScreen() {
		int available = width - imageWidth - betterLore$PANEL_GAP - 2 * betterLore$SCREEN_MARGIN;
		return Math.min(betterLore$PREFERRED_PANEL_WIDTH, Math.max(0, available));
	}

	@Unique
	private boolean betterLore$serverSupportsUi() {
		return ClientAnvilLoreNetworking.canSendLoreUpdate()
				&& ClientAnvilLoreNetworking.canSendNameUpdate();
	}


	@Unique
	private boolean betterLore$canOpenPanel() {
		if (!betterLore$serverSupportsUi()) {
			return false;
		}
		int panelWidth = betterLore$preferredPanelWidthForScreen();
		int combinedWidth = imageWidth + betterLore$PANEL_GAP + panelWidth;
		return panelWidth >= betterLore$MIN_PANEL_WIDTH
				&& width >= combinedWidth + 2 * betterLore$SCREEN_MARGIN
				&& height >= Math.max(imageHeight, betterLore$PANEL_HEIGHT) + 2 * betterLore$SCREEN_MARGIN;
	}

	@Unique
	private void betterLore$setPanelOpen(boolean open) {
		if (open && !betterLore$canOpenPanel()) {
			return;
		}
		betterLore$panelOpen = open;
		betterLore$draggingLoreSelection = false;
		betterLore$colorWheelOpen = false;
		if (betterLore$colorWheelWidget != null) {
			betterLore$colorWheelWidget.stopDragging();
		}
		if (!open) {
			betterLore$unfocusTextFields();
		} else {
			betterLore$insertTargetName = false;
		}
		betterLore$applyAnvilLayout();
	}

	@Unique
	private void betterLore$focusLoreEditor() {
		setFocused(betterLore$editor);
		betterLore$editor.setFocused(true);
		if (name != null) {
			name.setFocused(false);
		}
	}

	@Unique
	private void betterLore$unfocusTextFields() {
		if (betterLore$editor != null) {
			betterLore$editor.setFocused(false);
		}
		if (name != null) {
			name.setFocused(false);
		}
	}

	@Unique
	private void betterLore$unfocusColorSliders() {
		Object focused = getFocused();
		if (focused == betterLore$redSlider || focused == betterLore$greenSlider || focused == betterLore$blueSlider) {
			setFocused(null);
		}
		if (betterLore$redSlider != null) {
			betterLore$redSlider.setFocused(false);
		}
		if (betterLore$greenSlider != null) {
			betterLore$greenSlider.setFocused(false);
		}
		if (betterLore$blueSlider != null) {
			betterLore$blueSlider.setFocused(false);
		}
	}

	@Unique
	private int betterLore$firstColor() {
		return ((betterLore$pickerOneRed & 0xFF) << 16) | ((betterLore$pickerOneGreen & 0xFF) << 8) | (betterLore$pickerOneBlue & 0xFF);
	}

	@Unique
	private int betterLore$secondColor() {
		return ((betterLore$pickerTwoRed & 0xFF) << 16) | ((betterLore$pickerTwoGreen & 0xFF) << 8) | (betterLore$pickerTwoBlue & 0xFF);
	}

	@Unique
	private int betterLore$currentColor() {
		return betterLore$activeColorSlot == 0 ? betterLore$firstColor() : betterLore$secondColor();
	}

	@Unique
	private String betterLore$firstHex() {
		return LoreMarkupParser.formatHex(betterLore$firstColor());
	}

	@Unique
	private String betterLore$secondHex() {
		return LoreMarkupParser.formatHex(betterLore$secondColor());
	}

	@Unique
	private String betterLore$currentHex() {
		return LoreMarkupParser.formatHex(betterLore$currentColor());
	}

	@Unique
	private int betterLore$activeRed() {
		return betterLore$activeColorSlot == 0 ? betterLore$pickerOneRed : betterLore$pickerTwoRed;
	}

	@Unique
	private int betterLore$activeGreen() {
		return betterLore$activeColorSlot == 0 ? betterLore$pickerOneGreen : betterLore$pickerTwoGreen;
	}

	@Unique
	private int betterLore$activeBlue() {
		return betterLore$activeColorSlot == 0 ? betterLore$pickerOneBlue : betterLore$pickerTwoBlue;
	}

	@Unique
	private void betterLore$setActiveColorComponent(int component, int value) {
		value = Math.max(0, Math.min(255, value));
		if (betterLore$activeColorSlot == 0) {
			if (component == 0) {
				betterLore$pickerOneRed = value;
			} else if (component == 1) {
				betterLore$pickerOneGreen = value;
			} else {
				betterLore$pickerOneBlue = value;
			}
		} else {
			if (component == 0) {
				betterLore$pickerTwoRed = value;
			} else if (component == 1) {
				betterLore$pickerTwoGreen = value;
			} else {
				betterLore$pickerTwoBlue = value;
			}
		}
		betterLore$updateColorButtons();
		betterLore$updateHexInput();
	}

	@Unique
	private void betterLore$adjustActiveColorComponent(int component, int amount) {
		int current = switch (component) {
			case 0 -> betterLore$activeRed();
			case 1 -> betterLore$activeGreen();
			default -> betterLore$activeBlue();
		};
		betterLore$setActiveColorComponent(component, current + amount);
		betterLore$syncSlidersToActiveColor();
	}

	@Unique
	private void betterLore$selectColorSlot(int slot) {
		betterLore$activeColorSlot = slot == 1 ? 1 : 0;
		betterLore$syncSlidersToActiveColor();
		betterLore$updateColorButtons();
	}

	@Unique
	private void betterLore$syncSlidersToActiveColor() {
		if (betterLore$redSlider == null) {
			return;
		}
		betterLore$redSlider.setIntValue(betterLore$activeRed());
		betterLore$greenSlider.setIntValue(betterLore$activeGreen());
		betterLore$blueSlider.setIntValue(betterLore$activeBlue());
		betterLore$updateColorButtons();
		betterLore$updateHexInput();
	}

	@Unique
	private void betterLore$updateColorButtons() {
		if (betterLore$copyHexButton != null) {
			betterLore$copyHexButton.setMessage(Component.translatable("gui.better_lore.copy_hex", betterLore$currentHex()));
		}
		if (betterLore$firstColorButton != null) {
			betterLore$firstColorButton.setMessage(Component.translatable(betterLore$activeColorSlot == 0 ? "gui.better_lore.color_one_active" : "gui.better_lore.color_one", betterLore$firstHex()));
		}
		if (betterLore$secondColorButton != null) {
			betterLore$secondColorButton.setMessage(Component.translatable(betterLore$activeColorSlot == 1 ? "gui.better_lore.color_two_active" : "gui.better_lore.color_two", betterLore$secondHex()));
		}
		if (betterLore$colorWheelWidget != null) {
			betterLore$colorWheelWidget.setColor(betterLore$currentColor());
		}
	}

	@Unique
	private void betterLore$onHexInputChanged(String value) {
		if (betterLore$suppressHexInput) {
			return;
		}

		int color = betterLore$parseHexInput(value);
		if (color < 0) {
			return;
		}

		betterLore$setActiveColor(color);
	}

	@Unique
	private void betterLore$setActiveColor(int rgb) {
		rgb &= 0xFFFFFF;
		if (betterLore$activeColorSlot == 0) {
			betterLore$pickerOneRed = (rgb >> 16) & 0xFF;
			betterLore$pickerOneGreen = (rgb >> 8) & 0xFF;
			betterLore$pickerOneBlue = rgb & 0xFF;
		} else {
			betterLore$pickerTwoRed = (rgb >> 16) & 0xFF;
			betterLore$pickerTwoGreen = (rgb >> 8) & 0xFF;
			betterLore$pickerTwoBlue = rgb & 0xFF;
		}
		betterLore$syncSlidersToActiveColor();
	}

	@Unique
	private void betterLore$updateHexInput() {
		if (betterLore$hexInput == null) {
			return;
		}

		String hex = betterLore$currentHex();
		if (!hex.equalsIgnoreCase(betterLore$hexInput.getValue())) {
			betterLore$suppressHexInput = true;
			betterLore$hexInput.setValue(hex);
			betterLore$suppressHexInput = false;
		}
	}

	@Unique
	private int betterLore$parseHexInput(String value) {
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
	private void betterLore$copyCurrentHex() {
		if (minecraft != null) {
			minecraft.keyboardHandler.setClipboard(betterLore$currentHex());
		}
	}

	@Unique
	private void betterLore$insertMarkupTemplate(String openingTag, String closingTag) {
		betterLore$insertAtTextCursor(openingTag + closingTag, openingTag.length());
	}

	@Unique
	private void betterLore$insertAtTextCursor(String insertion, int cursorOffset) {
		if (betterLore$insertTargetName && name != null) {
			int cursor = name.getCursorPosition();
			name.insertText(insertion);
			int target = Math.min(name.getValue().length(), cursor + Math.max(0, Math.min(cursorOffset, insertion.length())));
			name.setCursorPosition(target);
			String current = betterLore$limitRawInput(name.getValue());
			if (!current.equals(name.getValue())) {
				betterLore$suppressNameSync = true;
				name.setValue(current);
				betterLore$suppressNameSync = false;
			}
			betterLore$lastObservedName = betterLore$limitRawInput(name.getValue());
			betterLore$nameDirty = true;
			betterLore$nameSendDelay = betterLore$SEND_DELAY_TICKS;
			name.setFocused(true);
			if (betterLore$editor != null) {
				betterLore$editor.setFocused(false);
			}
			return;
		}

		if (betterLore$editor == null) {
			return;
		}

		betterLore$insertTargetName = false;
		MultilineTextField textField = ((MultiLineEditBoxAccessor) betterLore$editor).betterLore$getTextField();
		int cursor = textField.cursor();
		textField.insertText(insertion);
		int target = Math.min(textField.value().length(), cursor + Math.max(0, Math.min(cursorOffset, insertion.length())));
		textField.seekCursor(Whence.ABSOLUTE, target);
		String current = betterLore$limitRawInput(betterLore$editor.getValue());
		if (!current.equals(betterLore$editor.getValue())) {
			betterLore$setEditorValue(current, true);
		} else {
			betterLore$raw = current;
			betterLore$parseResult = LoreMarkupParser.parse(current);
			betterLore$dirty = true;
		}
		betterLore$sendDelay = betterLore$SEND_DELAY_TICKS;
		betterLore$focusLoreEditor();
	}

	@Unique
	private boolean betterLore$routeKeyToColorSlider(KeyEvent event) {
		return betterLore$routeKeyToColorSlider(betterLore$redSlider, event)
				|| betterLore$routeKeyToColorSlider(betterLore$greenSlider, event)
				|| betterLore$routeKeyToColorSlider(betterLore$blueSlider, event);
	}

	@Unique
	private boolean betterLore$routeKeyToColorSlider(ColorValueSlider slider, KeyEvent event) {
		return slider != null && slider.visible && slider.isFocused() && slider.keyPressed(event);
	}

	@Unique
	private boolean betterLore$routeCharToColorSlider(CharacterEvent event) {
		return betterLore$routeCharToColorSlider(betterLore$redSlider, event)
				|| betterLore$routeCharToColorSlider(betterLore$greenSlider, event)
				|| betterLore$routeCharToColorSlider(betterLore$blueSlider, event);
	}

	@Unique
	private boolean betterLore$routeCharToColorSlider(ColorValueSlider slider, CharacterEvent event) {
		return slider != null && slider.visible && slider.isFocused() && slider.charTyped(event);
	}

	@Unique
	private List<Component> betterLore$helpTooltipLines() {
		return List.of(
				Component.translatable("gui.better_lore.help.color_intro"),
				Component.translatable("gui.better_lore.help.color_example"),
				Component.translatable("gui.better_lore.help.gradient_intro"),
				Component.translatable("gui.better_lore.help.gradient_example"),
				Component.translatable("gui.better_lore.help.nesting_intro"),
				Component.translatable("gui.better_lore.help.nesting_wrong_a"),
				Component.translatable("gui.better_lore.help.nesting_wrong_b"),
				Component.translatable("gui.better_lore.help.nesting_right_a"),
				Component.translatable("gui.better_lore.help.nesting_right_b"),
				Component.translatable("gui.better_lore.help.nesting_right_gradient"),
				Component.translatable("gui.better_lore.help.colors"),
				Component.translatable("gui.better_lore.help.sliders"),
				Component.translatable("gui.better_lore.help.slider_keys"),
				Component.translatable("gui.better_lore.help.format_a"),
				Component.translatable("gui.better_lore.help.format_b"),
				Component.translatable("gui.better_lore.help.name")
		);
	}

	@Unique
	private void betterLore$drawVanillaPanel(GuiGraphicsExtractor graphics, int x, int y, int panelWidth, int panelHeight) {
		graphics.fill(x, y, x + panelWidth, y + panelHeight, betterLore$VANILLA_PANEL_SHADOW);
		graphics.fill(x + 1, y + 1, x + panelWidth - 1, y + panelHeight - 1, betterLore$VANILLA_PANEL);

		graphics.fill(x + 1, y + 1, x + panelWidth - 1, y + 2, betterLore$VANILLA_PANEL_LIGHT);
		graphics.fill(x + 1, y + 1, x + 2, y + panelHeight - 1, betterLore$VANILLA_PANEL_LIGHT);
		graphics.fill(x + panelWidth - 2, y + 1, x + panelWidth - 1, y + panelHeight - 1, betterLore$VANILLA_PANEL_DARK);
		graphics.fill(x + 1, y + panelHeight - 2, x + panelWidth - 1, y + panelHeight - 1, betterLore$VANILLA_PANEL_DARK);
	}

	@Unique
	private boolean betterLore$isInColorSwatchBounds(double mouseX, double mouseY) {
		return betterLore$isInBounds(mouseX, mouseY, betterLore$colorSwatchX, betterLore$colorSwatchY, betterLore$COLOR_SWATCH_SIZE, betterLore$COLOR_SWATCH_SIZE);
	}

	@Unique
	private boolean betterLore$isInColorSliderBounds(double mouseX, double mouseY) {
		return betterLore$isInColorSliderBounds(betterLore$redSlider, mouseX, mouseY)
				|| betterLore$isInColorSliderBounds(betterLore$greenSlider, mouseX, mouseY)
				|| betterLore$isInColorSliderBounds(betterLore$blueSlider, mouseX, mouseY);
	}

	@Unique
	private boolean betterLore$isInColorSliderBounds(ColorValueSlider slider, double mouseX, double mouseY) {
		return slider != null
				&& slider.visible
				&& betterLore$isInBounds(mouseX, mouseY, slider.getX(), slider.getY(), slider.getWidth(), slider.getHeight());
	}

	@Unique
	private boolean betterLore$isInEditorBounds(double mouseX, double mouseY) {
		return betterLore$isInBounds(mouseX, mouseY, betterLore$editor.getX(), betterLore$editor.getY(), betterLore$editor.getWidth(), betterLore$editor.getHeight());
	}

	@Unique
	private boolean betterLore$isInPanelBounds(double mouseX, double mouseY) {
		return betterLore$isInBounds(mouseX, mouseY, betterLore$panelX, betterLore$panelY, betterLore$panelWidth, betterLore$panelHeight);
	}

	@Unique
	private boolean betterLore$isInWidgetBounds(double mouseX, double mouseY, Button widget) {
		return widget != null && betterLore$isInBounds(mouseX, mouseY, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
	}

	@Unique
	private boolean betterLore$isInWidgetBounds(double mouseX, double mouseY, EditBox widget) {
		return widget != null && betterLore$isInBounds(mouseX, mouseY, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
	}

	@Unique
	private boolean betterLore$isInBounds(double mouseX, double mouseY, int x, int y, int boundsWidth, int boundsHeight) {
		return mouseX >= x && mouseX < x + boundsWidth && mouseY >= y && mouseY < y + boundsHeight;
	}
}
