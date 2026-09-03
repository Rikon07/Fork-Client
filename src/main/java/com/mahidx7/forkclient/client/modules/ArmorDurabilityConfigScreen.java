package com.mahidx7.forkclient.client.modules;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.ForkClientController.WidgetState;
import com.mahidx7.forkclient.client.hud.ArmorDurabilityHudComponent;
import com.mahidx7.forkclient.client.hud.ArmorDurabilityHudComponent.Settings;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Configuration screen for the Armor Durability HUD. Replicates the layout and
 * behavior of the original mod's config screen ("Armour Durability Settings"):
 * sliders for position/scale/spacing, checkboxes for the visible pieces, a live
 * scaled preview, and Save/Reset/Cancel actions. Appearance edits a working copy
 * of {@link Settings}; the X/Y position edits the registered HUD widget position.
 */
public final class ArmorDurabilityConfigScreen extends Screen {
	private static final int TEXT_PRIMARY   = 0xFFFFFF;
	private static final int TEXT_SECONDARY = 0xB0B0B0;
	private static final int TEXT_ACCENT    = 0x7DC8FF;
	private static final int STATUS_SAVED   = 0x7DFF9A;
	private static final int STATUS_DIRTY   = 0xFFD37D;

	private static final int DEFAULT_X = 12;
	private static final int DEFAULT_Y = 216;

	private final Screen parent;
	private final Settings persistedSettings;
	private final int persistedX;
	private final int persistedY;

	private Settings working;
	private int workingX;
	private int workingY;
	private boolean showPreview;

	private DynamicSlider hudXSlider;
	private DynamicSlider hudYSlider;

	public ArmorDurabilityConfigScreen(Screen parent) {
		super(Component.translatable("gui.armour_durability.config.title"));
		this.parent = parent;
		this.showPreview = true;
		this.persistedSettings = ArmorDurabilityHudComponent.loadSettings();
		this.working = copyOf(this.persistedSettings);

		WidgetState widget = ForkClientController.INSTANCE.getWidgetById(ArmorDurabilityHudComponent.MODULE_ID);
		this.persistedX = widget != null ? widget.x() : DEFAULT_X;
		this.persistedY = widget != null ? widget.y() : DEFAULT_Y;
		this.workingX = this.persistedX;
		this.workingY = this.persistedY;
	}

	@Override
	protected void init() {
		this.clampHudPositionToPreviewBounds();

		int centerX = this.width / 2;
		int leftX = centerX - 180;
		int secondColX = centerX + 10;
		int sliderWidth = 170;
		int sliderHeight = 20;
		int rowGap = 28;
		int checkboxGap = 26;
		int y = 48;

		this.hudXSlider = this.addRenderableWidget(new HudXSlider(leftX, y, sliderWidth, sliderHeight));
		this.hudYSlider = this.addRenderableWidget(new HudYSlider(secondColX, y, sliderWidth, sliderHeight));

		y += checkboxGap;
		this.addRenderableWidget(new ScaleSlider(leftX, y, 360, sliderHeight));

		y += checkboxGap;
		this.addRenderableWidget(new SpacingSlider(leftX, y, 360, sliderHeight));

		y += rowGap;
		this.addRenderableWidget(this.buildCheckbox("Horizontal Layout", leftX, y,
				(checkbox, value) -> {
					this.working.horizontalLayout = value;
					this.refreshPositionSliders();
				}, this.working.horizontalLayout));

		y += checkboxGap;
		this.addRenderableWidget(this.buildCheckbox("Helmet", leftX, y,
				(checkbox, value) -> {
					this.working.showHelmet = value;
					this.refreshPositionSliders();
				}, this.working.showHelmet));
		this.addRenderableWidget(this.buildCheckbox("Chestplate", leftX + 120, y,
				(checkbox, value) -> {
					this.working.showChestplate = value;
					this.refreshPositionSliders();
				}, this.working.showChestplate));
		this.addRenderableWidget(this.buildCheckbox("Leggings", leftX + 250, y,
				(checkbox, value) -> {
					this.working.showLeggings = value;
					this.refreshPositionSliders();
				}, this.working.showLeggings));

		y += 24;
		this.addRenderableWidget(this.buildCheckbox("Boots", leftX, y,
				(checkbox, value) -> {
					this.working.showBoots = value;
					this.refreshPositionSliders();
				}, this.working.showBoots));
		this.addRenderableWidget(this.buildCheckbox("Elytra", leftX + 120, y,
				(checkbox, value) -> {
					this.working.showElytra = value;
					this.refreshPositionSliders();
				}, this.working.showElytra));
		this.addRenderableWidget(this.buildCheckbox("Turtle Shell", leftX + 250, y,
				(checkbox, value) -> {
					this.working.showTurtleShell = value;
					this.refreshPositionSliders();
				}, this.working.showTurtleShell));

		y += 34;
		this.addRenderableWidget(Button.builder(this.getPreviewButtonLabel(),
				button -> {
					this.showPreview = !this.showPreview;
					button.setMessage(this.getPreviewButtonLabel());
				}).bounds(centerX - 75, y, 150, 20).build());

		int buttonWidth = 92;
		int gap = 12;
		int buttonY = this.height - 42;
		int totalWidth = buttonWidth * 3 + gap * 2;
		int saveX = centerX - totalWidth / 2;

		this.addRenderableWidget(Button.builder(Component.literal("Save"),
				button -> this.saveAndClose()).bounds(saveX, buttonY, buttonWidth, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Reset"),
				button -> this.resetToDefaults()).bounds(saveX + buttonWidth + gap, buttonY, buttonWidth, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Cancel"),
				button -> this.closeWithoutSaving()).bounds(saveX + 2 * (buttonWidth + gap), buttonY, buttonWidth, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);

		int centerX = this.width / 2;
		int leftX = centerX - 180;

		context.centeredText(this.font, this.title, centerX, 18, TEXT_PRIMARY);
		context.centeredText(this.font,
				Component.literal("Simple layout with live preview and cleaner spacing."),
				centerX, 30, TEXT_SECONDARY);

		this.drawSectionHeader(context, leftX, 40, "Position & Scale");
		this.drawSectionHeader(context, leftX, 120, "Display Options");

		context.text(this.font, "Visible pieces: " + this.getVisiblePieceCount(), leftX, 192, TEXT_SECONDARY, false);
		context.text(this.font, "Current layout: " + (this.working.horizontalLayout ? "Horizontal" : "Vertical"),
				leftX + 190, 192, TEXT_SECONDARY, false);

		boolean dirty = this.hasUnsavedChanges();
		int statusColor = dirty ? STATUS_DIRTY : STATUS_SAVED;
		context.centeredText(this.font,
				Component.literal(dirty ? "Unsaved changes" : "All changes saved"),
				centerX, this.height - 58, statusColor);

		if (this.showPreview) {
			this.renderArmorPreview(context);
		} else {
			context.centeredText(this.font, Component.literal("Preview is hidden"), centerX, 224, TEXT_SECONDARY);
		}
	}

	@Override
	public void onClose() {
		this.closeWithoutSaving();
	}

	private void drawSectionHeader(GuiGraphicsExtractor context, int x, int y, String label) {
		context.text(this.font, label, x, y, TEXT_ACCENT, false);
	}

	private void renderArmorPreview(GuiGraphicsExtractor context) {
		ArmorDurabilityHudComponent.renderPreview(context, this.minecraft, this.workingX, this.workingY, this.working);
		context.text(this.font, "Preview",
				Math.max(6, this.workingX - 6), Math.max(6, this.workingY - 14), TEXT_PRIMARY, true);
	}

	private Checkbox buildCheckbox(String label, int x, int y, Checkbox.OnValueChange onValueChange, boolean selected) {
		return Checkbox.builder(Component.literal(label), this.font)
				.pos(x, y)
				.selected(selected)
				.onValueChange(onValueChange)
				.build();
	}

	private Component getPreviewButtonLabel() {
		return Component.literal(this.showPreview ? "Preview: ON" : "Preview: OFF");
	}

	private static String formatScale(float scale) {
		return String.format(Locale.ROOT, "%.1fx", scale);
	}

	private int getVisiblePieceCount() {
		int count = 0;
		if (this.working.showHelmet) count++;
		if (this.working.showChestplate) count++;
		if (this.working.showLeggings) count++;
		if (this.working.showBoots) count++;
		return count;
	}

	private int getPreviewItemCount() {
		return Math.max(1, this.getVisiblePieceCount());
	}

	private int getMaxHudX() {
		ArmorDurabilityHudComponent.Dims dims = ArmorDurabilityHudComponent.scaledDimensions(this.minecraft, this.working, this.getPreviewItemCount());
		return Math.max(0, this.width - dims.width());
	}

	private int getMaxHudY() {
		ArmorDurabilityHudComponent.Dims dims = ArmorDurabilityHudComponent.scaledDimensions(this.minecraft, this.working, this.getPreviewItemCount());
		return Math.max(0, this.height - dims.height());
	}

	private void clampHudPositionToPreviewBounds() {
		this.workingX = Math.clamp(this.workingX, 0, this.getMaxHudX());
		this.workingY = Math.clamp(this.workingY, 0, this.getMaxHudY());
	}

	private void refreshPositionSliders() {
		this.clampHudPositionToPreviewBounds();
		if (this.hudXSlider != null) {
			this.hudXSlider.setSliderValue(normalize(this.workingX, this.getMaxHudX()));
		}
		if (this.hudYSlider != null) {
			this.hudYSlider.setSliderValue(normalize(this.workingY, this.getMaxHudY()));
		}
	}

	private static double normalize(int value, int max) {
		if (max <= 0) {
			return 0.0;
		}
		return Mth.clamp((double) value / max, 0.0, 1.0);
	}

	private static int denormalize(double value, int max) {
		if (max <= 0) {
			return 0;
		}
		return (int) Math.round(Mth.clamp(value, 0.0, 1.0) * max);
	}

	private void saveAndClose() {
		this.clampHudPositionToPreviewBounds();
		copyFrom(this.working, this.persistedSettings);
		ArmorDurabilityHudComponent.saveSettings(this.working);

		WidgetState widget = ForkClientController.INSTANCE.getWidgetById(ArmorDurabilityHudComponent.MODULE_ID);
		if (widget != null) {
			ForkClientController.INSTANCE.moveWidget(widget, this.workingX, this.workingY);
		}
		ForkClientController.INSTANCE.saveConfig();
		this.closeWithoutSaving();
	}

	private void closeWithoutSaving() {
		Minecraft client = this.minecraft;
		if (client != null) {
			client.gui.setScreen(this.parent);
		}
	}

	private void resetToDefaults() {
		this.working = new Settings();
		this.workingX = DEFAULT_X;
		this.workingY = DEFAULT_Y;
		this.clearWidgets();
		this.init();
	}

	private boolean hasUnsavedChanges() {
		return this.persistedSettings.scale != this.working.scale
				|| this.persistedSettings.itemSpacing != this.working.itemSpacing
				|| this.persistedSettings.showHelmet != this.working.showHelmet
				|| this.persistedSettings.showChestplate != this.working.showChestplate
				|| this.persistedSettings.showLeggings != this.working.showLeggings
				|| this.persistedSettings.showBoots != this.working.showBoots
				|| this.persistedSettings.showElytra != this.working.showElytra
				|| this.persistedSettings.showTurtleShell != this.working.showTurtleShell
				|| this.persistedSettings.horizontalLayout != this.working.horizontalLayout
				|| this.persistedX != this.workingX
				|| this.persistedY != this.workingY;
	}

	private static Settings copyOf(Settings source) {
		Settings copy = new Settings();
		copyFrom(source, copy);
		return copy;
	}

	private static void copyFrom(Settings source, Settings target) {
		target.scale = source.scale;
		target.itemSpacing = source.itemSpacing;
		target.showHelmet = source.showHelmet;
		target.showChestplate = source.showChestplate;
		target.showLeggings = source.showLeggings;
		target.showBoots = source.showBoots;
		target.showElytra = source.showElytra;
		target.showTurtleShell = source.showTurtleShell;
		target.horizontalLayout = source.horizontalLayout;
	}

	private abstract static class DynamicSlider extends AbstractSliderButton {
		private DynamicSlider(int x, int y, int width, int height, Component message, double value) {
			super(x, y, width, height, message, value);
		}

		public void setSliderValue(double value) {
			this.value = Mth.clamp(value, 0.0, 1.0);
			this.updateMessage();
		}
	}

	private final class HudXSlider extends DynamicSlider {
		private HudXSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Component.literal("X Position: 0"),
					normalize(ArmorDurabilityConfigScreen.this.workingX, ArmorDurabilityConfigScreen.this.getMaxHudX()));
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			int value = denormalize(this.value, ArmorDurabilityConfigScreen.this.getMaxHudX());
			ArmorDurabilityConfigScreen.this.workingX = value;
			this.setMessage(Component.literal("X Position: " + value));
		}

		@Override
		protected void applyValue() {
			ArmorDurabilityConfigScreen.this.workingX = denormalize(this.value, ArmorDurabilityConfigScreen.this.getMaxHudX());
		}
	}

	private final class HudYSlider extends DynamicSlider {
		private HudYSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Component.literal("Y Position: 0"),
					normalize(ArmorDurabilityConfigScreen.this.workingY, ArmorDurabilityConfigScreen.this.getMaxHudY()));
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			int value = denormalize(this.value, ArmorDurabilityConfigScreen.this.getMaxHudY());
			ArmorDurabilityConfigScreen.this.workingY = value;
			this.setMessage(Component.literal("Y Position: " + value));
		}

		@Override
		protected void applyValue() {
			ArmorDurabilityConfigScreen.this.workingY = denormalize(this.value, ArmorDurabilityConfigScreen.this.getMaxHudY());
		}
	}

	private final class ScaleSlider extends DynamicSlider {
		private ScaleSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Component.literal("HUD Scale: 0.0x"),
					(ArmorDurabilityConfigScreen.this.working.scale - 0.1) / 2.9);
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			float scale = (float) (0.1 + this.value * 2.9);
			ArmorDurabilityConfigScreen.this.working.scale = scale;
			this.setMessage(Component.literal("HUD Scale: " + formatScale(scale)));
			ArmorDurabilityConfigScreen.this.refreshPositionSliders();
		}

		@Override
		protected void applyValue() {
			ArmorDurabilityConfigScreen.this.working.scale = (float) (0.1 + this.value * 2.9);
			ArmorDurabilityConfigScreen.this.refreshPositionSliders();
		}
	}

	private final class SpacingSlider extends DynamicSlider {
		private SpacingSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Component.literal("Item Spacing: 0"),
					(double) ArmorDurabilityConfigScreen.this.working.itemSpacing / 20.0);
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			int spacing = (int) Math.round(this.value * 20.0);
			ArmorDurabilityConfigScreen.this.working.itemSpacing = spacing;
			this.setMessage(Component.literal("Item Spacing: " + spacing));
			ArmorDurabilityConfigScreen.this.refreshPositionSliders();
		}

		@Override
		protected void applyValue() {
			ArmorDurabilityConfigScreen.this.working.itemSpacing = (int) Math.round(this.value * 20.0);
			ArmorDurabilityConfigScreen.this.refreshPositionSliders();
		}
	}
}
