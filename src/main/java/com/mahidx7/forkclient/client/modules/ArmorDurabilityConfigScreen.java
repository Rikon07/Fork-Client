package com.mahidx7.forkclient.client.modules;

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

/**
 * Configuration screen for the Armor Durability HUD. Edit a working copy of the
 * settings with sliders, checkboxes and a live preview, then save it back to
 * the controller (persisted with the rest of the config). Positioning is left
 * to the HUD editor, so this screen only exposes appearance options.
 */
public final class ArmorDurabilityConfigScreen extends Screen {
	private static final int PANEL_W = 500;
	private static final int PANEL_H = 330;

	// Fork Client palette (mirrors ForkClickGuiScreen).
	private static final int BG             = 0xFF0A0D14;
	private static final int PANEL          = 0xFF0E1219;
	private static final int PANEL_HOVER    = 0xFF141922;
	private static final int BORDER         = 0x22FFFFFF;
	private static final int ACCENT         = 0xFF2BB5C8;
	private static final int TEXT_PRIMARY   = 0xFFFFFFFF;
	private static final int TEXT_SECONDARY = 0xFFB0B8C4;

	private final Screen parent;
	private Settings working;

	private ScaleSliderWidget scaleSlider;
	private SpacingSliderWidget spacingSlider;

	public ArmorDurabilityConfigScreen(Screen parent) {
		super(Component.translatable("screen.fork-client.armor_durability.title"));
		this.parent = parent;
		this.working = ArmorDurabilityHudComponent.loadSettings();
	}

	@Override
	protected void init() {
		int panelX = this.width / 2 - PANEL_W / 2;
		int panelY = this.height / 2 - PANEL_H / 2;
		int lx = panelX + 24;

		this.scaleSlider = this.addRenderableWidget(
				new ScaleSliderWidget(lx, panelY + 92, 210, 20, this.working));
		this.spacingSlider = this.addRenderableWidget(
				new SpacingSliderWidget(lx, panelY + 120, 210, 20, this.working));
		this.addRenderableWidget(Checkbox.builder(
				Component.translatable("screen.fork-client.armor_durability.horizontal_layout"), this.font)
				.pos(lx, panelY + 62)
				.selected(this.working.horizontalLayout)
				.onValueChange((checkbox, value) -> this.working.horizontalLayout = value)
				.build());

		this.addRenderableWidget(Checkbox.builder(
				Component.translatable("screen.fork-client.armor_durability.show_helmet"), this.font)
				.pos(lx, panelY + 156)
				.selected(this.working.showHelmet)
				.onValueChange((checkbox, value) -> this.working.showHelmet = value)
				.build());
		this.addRenderableWidget(Checkbox.builder(
				Component.translatable("screen.fork-client.armor_durability.show_chestplate"), this.font)
				.pos(lx + 130, panelY + 156)
				.selected(this.working.showChestplate)
				.onValueChange((checkbox, value) -> this.working.showChestplate = value)
				.build());
		this.addRenderableWidget(Checkbox.builder(
				Component.translatable("screen.fork-client.armor_durability.show_leggings"), this.font)
				.pos(lx, panelY + 182)
				.selected(this.working.showLeggings)
				.onValueChange((checkbox, value) -> this.working.showLeggings = value)
				.build());
		this.addRenderableWidget(Checkbox.builder(
				Component.translatable("screen.fork-client.armor_durability.show_boots"), this.font)
				.pos(lx + 130, panelY + 182)
				.selected(this.working.showBoots)
				.onValueChange((checkbox, value) -> this.working.showBoots = value)
				.build());
		this.addRenderableWidget(Checkbox.builder(
				Component.translatable("screen.fork-client.armor_durability.show_elytra"), this.font)
				.pos(lx, panelY + 208)
				.selected(this.working.showElytra)
				.onValueChange((checkbox, value) -> this.working.showElytra = value)
				.build());
		this.addRenderableWidget(Checkbox.builder(
				Component.translatable("screen.fork-client.armor_durability.show_turtle_shell"), this.font)
				.pos(lx + 130, panelY + 208)
				.selected(this.working.showTurtleShell)
				.onValueChange((checkbox, value) -> this.working.showTurtleShell = value)
				.build());

		int startX = panelX + 30;
		int buttonY = panelY + PANEL_H - 36;
		this.addRenderableWidget(Button.builder(
				Component.translatable("screen.fork-client.armor_durability.save"),
				button -> this.saveAndClose()).bounds(startX, buttonY, 140, 20).build());
		this.addRenderableWidget(Button.builder(
				Component.translatable("screen.fork-client.armor_durability.reset"),
				button -> this.resetToDefaults()).bounds(startX + 150, buttonY, 140, 20).build());
		this.addRenderableWidget(Button.builder(
				Component.translatable("screen.fork-client.armor_durability.done"),
				button -> this.onClose()).bounds(startX + 300, buttonY, 140, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);

		int panelX = this.width / 2 - PANEL_W / 2;
		int panelY = this.height / 2 - PANEL_H / 2;

		context.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, PANEL);
		context.outline(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BORDER);

		context.centeredText(this.font,
				Component.translatable("screen.fork-client.armor_durability.title"),
				this.width / 2, panelY + 18, TEXT_PRIMARY);
		context.centeredText(this.font,
				Component.translatable("screen.fork-client.armor_durability.subtitle"),
				this.width / 2, panelY + 34, TEXT_SECONDARY);

		// Preview box.
		int boxX = panelX + 260;
		int boxY = panelY + 62;
		int boxW = PANEL_W - 280;
		int boxH = 200;
		context.centeredText(this.font,
				Component.translatable("screen.fork-client.armor_durability.preview_label"),
				boxX + boxW / 2, boxY - 12, TEXT_SECONDARY);
		context.fill(boxX, boxY, boxX + boxW, boxY + boxH, BG);
		context.outline(boxX, boxY, boxX + boxW, boxY + boxH, BORDER);

		context.enableScissor(boxX, boxY, boxX + boxW, boxY + boxH);
		ArmorDurabilityHudComponent.renderPreview(context, this.minecraft, boxX + 10, boxY + 10, this.working);
		context.disableScissor();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		this.extractTransparentBackground(context);
	}

	@Override
	public void onClose() {
		Minecraft client = this.minecraft;
		if (client != null) {
			client.gui.setScreen(this.parent);
		}
	}

	private void saveAndClose() {
		ArmorDurabilityHudComponent.saveSettings(this.working);
		this.onClose();
	}

	private void resetToDefaults() {
		this.working = new Settings();
		this.clearWidgets();
		this.init();
	}

	private static final class ScaleSliderWidget extends AbstractSliderButton {
		private final Settings settings;

		private ScaleSliderWidget(int x, int y, int width, int height, Settings settings) {
			super(x, y, width, height, Component.empty(),
					(settings.scale - ArmorDurabilityHudComponent.MIN_SCALE)
							/ (ArmorDurabilityHudComponent.MAX_SCALE - ArmorDurabilityHudComponent.MIN_SCALE));
			this.settings = settings;
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.translatable("screen.fork-client.armor_durability.scale",
					String.format(Locale.ROOT, "%.1f", this.scaleValue())));
		}

		@Override
		protected void applyValue() {
			this.settings.scale = this.scaleValue();
			this.updateMessage();
		}

		private float scaleValue() {
			return ArmorDurabilityHudComponent.MIN_SCALE
					+ (float) this.value * (ArmorDurabilityHudComponent.MAX_SCALE - ArmorDurabilityHudComponent.MIN_SCALE);
		}
	}

	private static final class SpacingSliderWidget extends AbstractSliderButton {
		private final Settings settings;

		private SpacingSliderWidget(int x, int y, int width, int height, Settings settings) {
			super(x, y, width, height, Component.empty(),
					(double) (settings.itemSpacing - ArmorDurabilityHudComponent.MIN_SPACING)
							/ (ArmorDurabilityHudComponent.MAX_SPACING - ArmorDurabilityHudComponent.MIN_SPACING));
			this.settings = settings;
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.translatable("screen.fork-client.armor_durability.spacing",
					this.spacingValue()));
		}

		@Override
		protected void applyValue() {
			this.settings.itemSpacing = this.spacingValue();
			this.updateMessage();
		}

		private int spacingValue() {
			return ArmorDurabilityHudComponent.MIN_SPACING
					+ (int) Math.round(this.value * (ArmorDurabilityHudComponent.MAX_SPACING - ArmorDurabilityHudComponent.MIN_SPACING));
		}
	}
}
