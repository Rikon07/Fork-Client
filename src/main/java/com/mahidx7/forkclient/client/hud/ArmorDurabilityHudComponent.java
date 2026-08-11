package com.mahidx7.forkclient.client.hud;

import com.mahidx7.forkclient.client.ForkClientController;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Armor Durability HUD. Renders each equipped armor piece as a 16x16 item icon
 * followed by its durability percentage, color coded by how worn the piece is.
 * Behavior mirrors the "Armour Durability" mod (spunkyinsaan), folded into the
 * Fork Client widget system. Options are stored through the controller's
 * per-module settings so they persist alongside the rest of the config.
 */
public final class ArmorDurabilityHudComponent {
	public static final String MODULE_ID = "armor_durability";

	public static final String SETTING_SCALE = "hud_scale";
	public static final String SETTING_SPACING = "item_spacing";
	public static final String SETTING_HELMET = "show_helmet";
	public static final String SETTING_CHESTPLATE = "show_chestplate";
	public static final String SETTING_LEGGINGS = "show_leggings";
	public static final String SETTING_BOOTS = "show_boots";
	public static final String SETTING_ELYTRA = "show_elytra";
	public static final String SETTING_TURTLE_SHELL = "show_turtle_shell";
	public static final String SETTING_HORIZONTAL = "horizontal_layout";

	public static final float DEFAULT_SCALE = 1.0F;
	public static final float DEFAULT_SPACING = 2.0F;

	/** Hard limits used by the config screen and validation. */
	public static final float MIN_SCALE = 0.1F;
	public static final float MAX_SCALE = 3.0F;
	public static final int MIN_SPACING = 0;
	public static final int MAX_SPACING = 20;

	private static final int ICON_SIZE = 16;
	private static final int HORIZONTAL_PITCH = 46;
	private static final int VERTICAL_PITCH = 16;

	// Durability color stops (matches the source mod's palette).
	private static final int COLOR_CRITICAL = 0xFFFF5555;
	private static final int COLOR_RED = 0xFF5555;
	private static final int COLOR_YELLOW = 0xFFFF55;
	private static final int COLOR_GREEN = 0x55FF55;

	private ArmorDurabilityHudComponent() {
	}

	/**
	 * Mutable snapshot of every user-facing option. The HUD reads one of these
	 * and the config screen edits a working copy before saving it back.
	 */
	public static final class Settings {
		public float scale = DEFAULT_SCALE;
		public int itemSpacing = Math.round(DEFAULT_SPACING);
		public boolean showHelmet = true;
		public boolean showChestplate = true;
		public boolean showLeggings = true;
		public boolean showBoots = true;
		public boolean showElytra = true;
		public boolean showTurtleShell = true;
		public boolean horizontalLayout = false;
	}

	public record Dims(int width, int height) {
	}

	/** One rendered armor piece: the stack and the slot it occupies. */
	public static final class ArmorItem {
		public final ItemStack stack;
		public final EquipmentSlot slot;

		ArmorItem(ItemStack stack, EquipmentSlot slot) {
			this.stack = stack;
			this.slot = slot;
		}
	}

	/** Loads the persisted options for the HUD from the controller. */
	public static Settings loadSettings() {
		ForkClientController ctrl = ForkClientController.INSTANCE;
		Settings s = new Settings();
		s.scale = ctrl.getModuleFloatSetting(MODULE_ID, SETTING_SCALE, DEFAULT_SCALE);
		s.itemSpacing = Math.round(ctrl.getModuleFloatSetting(MODULE_ID, SETTING_SPACING, DEFAULT_SPACING));
		s.showHelmet = getBool(ctrl, SETTING_HELMET, true);
		s.showChestplate = getBool(ctrl, SETTING_CHESTPLATE, true);
		s.showLeggings = getBool(ctrl, SETTING_LEGGINGS, true);
		s.showBoots = getBool(ctrl, SETTING_BOOTS, true);
		s.showElytra = getBool(ctrl, SETTING_ELYTRA, true);
		s.showTurtleShell = getBool(ctrl, SETTING_TURTLE_SHELL, true);
		s.horizontalLayout = getBool(ctrl, SETTING_HORIZONTAL, false);
		return s;
	}

	/** Persists the given options to the controller, clamping out-of-range values. */
	public static void saveSettings(Settings s) {
		ForkClientController ctrl = ForkClientController.INSTANCE;
		float scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, s.scale));
		int spacing = Math.max(MIN_SPACING, Math.min(MAX_SPACING, s.itemSpacing));
		ctrl.setModuleFloatSetting(MODULE_ID, SETTING_SCALE, scale);
		ctrl.setModuleFloatSetting(MODULE_ID, SETTING_SPACING, spacing);
		ctrl.setModuleStringSetting(MODULE_ID, SETTING_HELMET, Boolean.toString(s.showHelmet));
		ctrl.setModuleStringSetting(MODULE_ID, SETTING_CHESTPLATE, Boolean.toString(s.showChestplate));
		ctrl.setModuleStringSetting(MODULE_ID, SETTING_LEGGINGS, Boolean.toString(s.showLeggings));
		ctrl.setModuleStringSetting(MODULE_ID, SETTING_BOOTS, Boolean.toString(s.showBoots));
		ctrl.setModuleStringSetting(MODULE_ID, SETTING_ELYTRA, Boolean.toString(s.showElytra));
		ctrl.setModuleStringSetting(MODULE_ID, SETTING_TURTLE_SHELL, Boolean.toString(s.showTurtleShell));
		ctrl.setModuleStringSetting(MODULE_ID, SETTING_HORIZONTAL, Boolean.toString(s.horizontalLayout));
	}

	/**
	 * Renders the durability HUD anchored at (x, y). The caller supplies a
	 * screen-space position; the configured scale is applied around that point,
	 * exactly like the source mod does with its hudScale.
	 */
	public static void render(GuiGraphicsExtractor extractor, Minecraft client, int x, int y, boolean editorMode) {
		render(extractor, client, x, y, loadSettings(), editorMode);
	}

	/** Renders the HUD using an explicit settings snapshot (used for live previews). */
	public static void render(GuiGraphicsExtractor extractor, Minecraft client, int x, int y, Settings settings,
			boolean editorMode) {
		if (settings == null) {
			settings = new Settings();
		}

		List<ArmorItem> items = editorMode ? sampleItems(settings) : collectItems(client.player, settings);
		if (items.isEmpty()) {
			return;
		}

		float scale = settings.scale;

		extractor.pose().pushMatrix();
		extractor.pose().scale(scale, scale);

		int drawX = (int) (x / scale);
		int drawY = (int) (y / scale);

		renderArmorItems(extractor, client.font, items, drawX, drawY, settings.itemSpacing, settings.horizontalLayout, scale);

		extractor.pose().popMatrix();

		if (editorMode) {
			extractor.text(client.font, Component.literal("Armor Durability"), x, y - client.font.lineHeight - 4, 0xFFE1E6DE, false);
		}
	}

	/** Number of armor pieces that would currently be drawn for the player. */
	public static int collectItemCount(Player player) {
		if (player == null) {
			return 0;
		}
		return collectItems(player, loadSettings()).size();
	}

	/** Number of preview pieces shown in the HUD editor (never below one). */
	public static int getPreviewItemCount() {
		return Math.max(1, sampleItems(loadSettings()).size());
	}

	/** Scaled widget dimensions for {@code itemCount} armor pieces. */
	public static Dims scaledDimensions(Minecraft client, int itemCount) {
		return scaledDimensions(client, loadSettings(), itemCount);
	}

	/** Scaled widget dimensions for an explicit settings snapshot. */
	public static Dims scaledDimensions(Minecraft client, Settings settings, int itemCount) {
		if (settings == null) {
			settings = new Settings();
		}

		int width = settings.horizontalLayout
			? itemCount * HORIZONTAL_PITCH + Math.max(0, itemCount - 1) * settings.itemSpacing
			: HORIZONTAL_PITCH;
		int height = settings.horizontalLayout
			? VERTICAL_PITCH
			: itemCount * VERTICAL_PITCH + Math.max(0, itemCount - 1) * settings.itemSpacing;

		return new Dims(Math.round(width * settings.scale), Math.round(height * settings.scale));
	}

	private static List<ArmorItem> collectItems(Player player, Settings settings) {
		List<ArmorItem> items = new ArrayList<>();

		if (player == null) {
			return items;
		}

		ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
		if (!head.isEmpty() && head.isDamageableItem()) {
			if (head.is(Items.TURTLE_HELMET)) {
				if (settings.showTurtleShell) {
					items.add(new ArmorItem(head, EquipmentSlot.HEAD));
				}
			} else if (settings.showHelmet) {
				items.add(new ArmorItem(head, EquipmentSlot.HEAD));
			}
		}

		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!chest.isEmpty() && chest.isDamageableItem()) {
			if (chest.is(Items.ELYTRA)) {
				if (settings.showElytra) {
					items.add(new ArmorItem(chest, EquipmentSlot.CHEST));
				}
			} else if (settings.showChestplate) {
				items.add(new ArmorItem(chest, EquipmentSlot.CHEST));
			}
		}

		if (settings.showLeggings) {
			ItemStack stack = player.getItemBySlot(EquipmentSlot.LEGS);
			if (!stack.isEmpty() && stack.isDamageableItem()) {
				items.add(new ArmorItem(stack, EquipmentSlot.LEGS));
			}
		}

		if (settings.showBoots) {
			ItemStack stack = player.getItemBySlot(EquipmentSlot.FEET);
			if (!stack.isEmpty() && stack.isDamageableItem()) {
				items.add(new ArmorItem(stack, EquipmentSlot.FEET));
			}
		}

		return items;
	}

	/** Renders the sample armor preview with the given settings (used by the config screen). */
	public static void renderPreview(GuiGraphicsExtractor extractor, Minecraft client, int x, int y, Settings settings) {
		if (settings == null) {
			settings = new Settings();
		}

		List<ArmorItem> items = sampleItems(settings);
		if (items.isEmpty()) {
			return;
		}

		float scale = settings.scale;
		extractor.pose().pushMatrix();
		extractor.pose().scale(scale, scale);
		renderArmorItems(extractor, client.font, items, (int) (x / scale), (int) (y / scale),
				settings.itemSpacing, settings.horizontalLayout, scale);
		extractor.pose().popMatrix();
	}

	/** Number of sample preview pieces for the given settings. */
	public static int sampleItemCount(Settings settings) {
		return sampleItems(settings == null ? new Settings() : settings).size();
	}

	/** Sample diamond armor used for the HUD editor / config screen preview. */
	private static List<ArmorItem> sampleItems(Settings settings) {
		List<ArmorItem> items = new ArrayList<>();
		if (settings.showHelmet) {
			items.add(new ArmorItem(createSampleArmor(Items.DIAMOND_HELMET, 85), EquipmentSlot.HEAD));
		}
		if (settings.showChestplate) {
			items.add(new ArmorItem(createSampleArmor(Items.DIAMOND_CHESTPLATE, 45), EquipmentSlot.CHEST));
		}
		if (settings.showLeggings) {
			items.add(new ArmorItem(createSampleArmor(Items.DIAMOND_LEGGINGS, 15), EquipmentSlot.LEGS));
		}
		if (settings.showBoots) {
			items.add(new ArmorItem(createSampleArmor(Items.DIAMOND_BOOTS, 92), EquipmentSlot.FEET));
		}
		return items;
	}

	private static ItemStack createSampleArmor(net.minecraft.world.item.Item item, int percent) {
		ItemStack stack = new ItemStack(item);
		if (stack.isDamageableItem()) {
			int maxDamage = stack.getMaxDamage();
			int damage = Math.max(0, Math.min(maxDamage - 1, maxDamage - (int) ((percent / 100.0) * maxDamage)));
			stack.setDamageValue(damage);
		}
		return stack;
	}

	private static void renderArmorItems(GuiGraphicsExtractor extractor, Font font, List<ArmorItem> items,
			int baseX, int baseY, int spacing, boolean horizontal, float scale) {
		for (int i = 0; i < items.size(); i++) {
			ArmorItem armorItem = items.get(i);
			int x = baseX;
			int y = baseY;

			if (horizontal) {
				x += Math.round(i * (HORIZONTAL_PITCH + spacing) / scale);
			} else {
				y += Math.round(i * (VERTICAL_PITCH + spacing) / scale);
			}

			ItemStack stack = armorItem.stack;
			extractor.item(stack, x, y);

			int maxDamage = stack.getMaxDamage();
			int damage = stack.getDamageValue();
			int percent = (int) Math.round(((maxDamage - damage) / (double) maxDamage) * 100.0);
			int color = getDurabilityColor(percent);

			extractor.text(font, percent + "%", x + ICON_SIZE + 2, y + 3, color, true);
		}
	}

	private static int getDurabilityColor(int percent) {
		percent = Math.max(0, Math.min(100, percent));

		if (percent <= 20) {
			return COLOR_CRITICAL;
		}

		if (percent <= 50) {
			return interpolateColor(COLOR_RED, COLOR_YELLOW, (percent - 20) / 30.0F);
		}

		return interpolateColor(COLOR_YELLOW, COLOR_GREEN, (percent - 50) / 50.0F);
	}

	private static int interpolateColor(int from, int to, float t) {
		t = Math.max(0.0F, Math.min(1.0F, t));

		int fromR = (from >> 16) & 0xFF;
		int fromG = (from >> 8) & 0xFF;
		int fromB = from & 0xFF;

		int toR = (to >> 16) & 0xFF;
		int toG = (to >> 8) & 0xFF;
		int toB = to & 0xFF;

		int r = (int) (fromR + (toR - fromR) * t);
		int g = (int) (fromG + (toG - fromG) * t);
		int b = (int) (fromB + (toB - fromB) * t);

		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static boolean getBool(ForkClientController ctrl, String setting, boolean defaultValue) {
		return Boolean.parseBoolean(ctrl.getModuleStringSetting(MODULE_ID, setting, Boolean.toString(defaultValue)));
	}
}
