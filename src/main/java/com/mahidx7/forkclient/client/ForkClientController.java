package com.mahidx7.forkclient.client;

import com.mahidx7.forkclient.ForkClient;
import com.mahidx7.forkclient.client.hud.ArmorDurabilityHudComponent;
import com.mahidx7.forkclient.client.hud.ArrayListHudComponent;
import com.mahidx7.forkclient.client.modules.ArmorDurabilityConfigScreen;
import com.mahidx7.forkclient.client.modules.building.BlockMeasurementModule;
import com.mahidx7.forkclient.client.modules.building.BlockPaletteModule;
import com.mahidx7.forkclient.client.modules.building.BlueprintPreviewModule;
import com.mahidx7.forkclient.client.modules.building.BuildHeightModule;
import com.mahidx7.forkclient.client.modules.building.ChunkBorderModule;
import com.mahidx7.forkclient.client.modules.building.GridOverlayModule;
import com.mahidx7.forkclient.client.modules.building.MaterialCalculatorModule;
import com.mahidx7.forkclient.client.modules.building.ShapePreviewModule;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
// GLFW removed in 26.3 (SDL migration) - using InputConstants instead

public final class ForkClientController {
	public static final ForkClientController INSTANCE = new ForkClientController();

	private static final int PANEL_BACKGROUND = 0xCC1B212A;
	private static final int PANEL_OUTLINE = 0xFF55D0FF;
	private static final int PANEL_OUTLINE_HOVER = 0xFF8EE7FF;
	private static final int PANEL_INNER = 0xFF474E4A;
	private static final int PANEL_DISABLED = 0xFF7A807A;
	private static final int TEXT_PRIMARY = 0xFFFFFFFF;
	private static final int TEXT_MUTED = 0xFFE1E6DE;
	private static final int TEXT_DISABLED = 0xFF9FA59E;
	private static final int ENABLED_ACCENT = 0xFF33D17A;
	private static final int DISABLED_ACCENT = 0xFF9AA5B1;
	private static final int[] WAYPOINT_COLORS = {
		0xFF2196F3, 0xFF4CAF50, 0xFFF44336, 0xFFFF9800,
		0xFF9C27B0, 0xFFFFEB3B, 0xFF00BCD4, 0xFFE91E63
	};
	private static final long AUTO_CHAT_INTERVAL_MS = 180000L;
	private static final long AUTO_CHAT_JOIN_DELAY_MS = 12000L;
	private static final List<String> AUTO_CHAT_ROTATION = List.of(
		"glhf",
		"good luck everyone",
		"have a great game"
	);
	private static final Method INPUT_IS_KEY_DOWN_INT = resolveIsKeyDownMethod();

	private static Method resolveIsKeyDownMethod() {
		try {
			return InputConstants.class.getMethod("isKeyDown", int.class);
		} catch (NoSuchMethodException ignored) {
			try {
				return InputConstants.class.getMethod("isKeyDown", Window.class, int.class);
			} catch (NoSuchMethodException e) {
				throw new IllegalStateException("InputConstants.isKeyDown is unavailable in this Minecraft version", e);
			}
		}
	}

	private static boolean isKeyDownCompat(int keyCode) {
		try {
			if (INPUT_IS_KEY_DOWN_INT.getParameterCount() == 1) {
				return (boolean) INPUT_IS_KEY_DOWN_INT.invoke(null, keyCode);
			}
			Window window = Minecraft.getInstance().getWindow();
			return (boolean) INPUT_IS_KEY_DOWN_INT.invoke(null, window, keyCode);
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	// ── Performance & caching state ────────────────────────────────────────
	// Version counters let cheap checks decide when cached data is stale,
	// so expensive work (string building, font measurement, block lookups)
	// happens at most once per client tick or per state change instead of
	// once per rendered frame.
	private long layoutVersion;
	private final Map<String, WidgetLayout> widgetLayoutCache = new HashMap<>();
	private long moduleStateGeneration;
	private long enabledTitlesGeneration = -1;
	private List<String> cachedEnabledTitles = List.of();

	private static final long MINIMAP_CACHE_MS = 1000L;
	private static final int MINIMAP_GRID = 64;
	private static final int MINIMAP_SIZE = 64;
	private static final int MINIMAP_MAX_SURFACE_SCAN = 4;
	private static final int MAP_PARCHMENT = 0xFFD5C8A8;
	private static final int MAP_FRAME_DARK = 0xFF3A2410;
	private static final int MAP_FRAME_LIGHT = 0xFFE8D8B8;
	private static final int MINIMAP_BTN_SIZE = 14;
	private static final int MINIMAP_BTN_MARGIN = 4;
	private static final int MINIMAP_BTN_GAP = 2;
	private long minimapCacheTime;
	private int minimapCacheX;
	private int minimapCacheY;
	private int minimapCacheZ;
	private int minimapCacheZoom = 1;
	private Level minimapCacheLevel;
	private int[] minimapCacheColors;
	private boolean minimapZoomClickDown;
	private final ArrayList<net.minecraft.world.entity.Entity> minimapEntityBuffer = new ArrayList<>();

	private final List<ModuleDefinition> modules = new ArrayList<>();
	private final Map<String, ModuleDefinition> modulesById = new LinkedHashMap<>();
	private final Map<String, Boolean> moduleStates = new LinkedHashMap<>();
	private final List<WidgetState> widgets = new ArrayList<>();
	private final Map<String, WidgetState> widgetsById = new LinkedHashMap<>();
	private final Deque<Notification> notifications = new ArrayDeque<>();
	private final Deque<Long> leftClicks = new ArrayDeque<>();
	private final Deque<Long> rightClicks = new ArrayDeque<>();

	private boolean initialized;
	private KeyMapping.Category keyCategory;
	private KeyMapping openGuiKey;
	private KeyMapping openHudEditorKey;
	private KeyMapping openArmorConfigKey;
	private KeyMapping zoomKey;
	private KeyMapping freelookKey;
	private KeyMapping hideHudKey;
	private KeyMapping cameraPathKey;
	private KeyMapping brightnessToggleKey;
	private KeyMapping brightnessRaiseKey;
	private KeyMapping brightnessLowerKey;
	private ModuleCategory selectedCategory = ModuleCategory.HUD;
	private Path configPath;

	// ── Brightness Plus state ────────────────────────────────────────────
	public static final String MODULE_BRIGHTNESS_PLUS = "brightness_plus";
	private static final double BRIGHTNESS_PLUS_DEFAULT = 0.0;
	private static final double BRIGHTNESS_PLUS_DEFAULT_VALUE = 1.0;
	private static final double BRIGHTNESS_PLUS_MIN = -1.0;
	private static final double BRIGHTNESS_PLUS_MAX = 12.0;
	private static final double BRIGHTNESS_PLUS_STEP = 0.1;
	private double brightnessPlusValue = BRIGHTNESS_PLUS_DEFAULT_VALUE;
	private double brightnessPlusMin = BRIGHTNESS_PLUS_MIN;
	private double brightnessPlusMax = BRIGHTNESS_PLUS_MAX;
	private double brightnessPlusStep = BRIGHTNESS_PLUS_STEP;

	private boolean zoomApplied;
	private int storedFov;
	private float freelookYaw;
	private float freelookPitch;
	private float freelookOriginalYaw;
	private float freelookOriginalPitch;
	private double lastFreelookMouseX;
	private double lastFreelookMouseY;
	private boolean freelooking;
	private boolean toggleSneakLatched;
	private boolean lastAttackDown;
	private boolean lastUseDown;
	private Vec3 lastPlayerPos = Vec3.ZERO;
	private boolean firstSpeedTick = true;
	private double horizontalSpeed;
	private boolean noWeatherActive;
	private float savedRainLevel;
	private float savedThunderLevel;
	private String savedDimension;
	private String lastSessionKey = "";
	private boolean autoGgSent;
	private long nextAutoChatMessageAt;
	private int autoChatMessageIndex;
	private int comboCount;
	private long lastComboHitTime;
	private double lastReach;
	private int comboTargetId = -1;
	private int comboTargetHurtTime;
	private long timerStartMs;
	private long timerElapsedMs;
	private boolean timerRunning;
	private String searchQuery = "";
	private String selectedProfile = "Default";
	private final List<String> profiles = new ArrayList<>(List.of("Default", "UHC", "Hypixel", "Arena PvP"));
	private GuiTab selectedTopTab = GuiTab.MODS;
	private final Map<String, Boolean> settingsStates = new LinkedHashMap<>();
	private final Map<String, Map<String, Float>> moduleFloatSettings = new LinkedHashMap<>();
	private final Map<String, Map<String, String>> moduleStringSettings = new LinkedHashMap<>();
	private final List<Waypoint> waypoints = new ArrayList<>();
	private boolean renderingClientHud;
	private final List<String> recentAlerts = new ArrayList<>();
	private static final int MAX_RECENT_ALERTS = 50;

	// ── Content Creation state ───────────────────────────────────────────
	private float cinematicYaw;
	private float cinematicPitch;
	private boolean cinematicActive;
	private boolean hudHidden;
	private boolean timelapseRunning;
	private long timelapseStartMs;
	private boolean timelapsePositionLocked;
	private int timelapseCompassLock = -1;
	private boolean cameraPathRecording;
	private boolean cameraPathPlaying;
	private int cameraPathIndex;
	private long cameraPathLastTick;
	private final List<float[]> cameraPathPoints = new ArrayList<>();

	// ── Building Helpers state ───────────────────────────────────────────

	// ── Multiplayer Utilities state ──────────────────────────────────────
	private final List<Integer> pingHistory = new ArrayList<>();
	private static final int PING_HISTORY_MAX = 200;
	private final List<String> friendList = new ArrayList<>();
	private final List<String[]> serverBookmarks = new ArrayList<>();
	private final List<String[]> playerNotes = new ArrayList<>();
	private final List<String> quickMessages = new ArrayList<>(List.of("Hello", "GG", "Thanks", "GLHF"));
	private long lastTpsSampleMs;
	private long lastTpsGameTime;
	private boolean[] quickMessageKeyWasDown = new boolean[9];
	private float estimatedTps = 20.0F;

	public static final Set<String> MOVEMENT_MODULES = Set.of(
		"auto_sprint",
		"toggle_sneak",
		"inventory_walk",
		"anti_knockback"
	);

	public boolean isRenderingClientHud() {
		return this.renderingClientHud;
	}

	public void addRecentAlert(String message) {
		this.recentAlerts.add(0, message);
		while (this.recentAlerts.size() > MAX_RECENT_ALERTS) {
			this.recentAlerts.remove(this.recentAlerts.size() - 1);
		}
	}

	public List<String> getRecentAlerts() {
		return this.recentAlerts;
	}

	private ForkClientController() {
	}

	public void initialize() {
		if (this.initialized) {
			return;
		}

		this.keyCategory = KeyMapping.Category.register(ForkClient.id("general"));
		registerModules();
		registerWidgets();
		registerKeyMappings();
		loadConfig();

		ClientTickEvents.START_CLIENT_TICK.register(this::preTickMovementModules);
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
		HudElement hudElement = this::renderHud;
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ForkClient.id("fork_overlay"), hudElement);

		HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, original -> (extractor, deltaTracker) -> {
			if (!isModuleEnabled("boss_bar_overhaul") || !FeaturePermissions.canUseRendering()) {
				original.extractRenderState(extractor, deltaTracker);
				return;
			}
			extractor.pose().pushMatrix();
			float guiW = extractor.guiWidth();
			extractor.pose().translate(guiW / 2.0F, 0.0F);
			extractor.pose().scale(0.85F, 0.85F);
			extractor.pose().translate(-guiW / 2.0F, 0.0F);
			original.extractRenderState(extractor, deltaTracker);
			extractor.pose().popMatrix();
		});

		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, original -> (extractor, deltaTracker) -> {
			if (!isModuleEnabled("scoreboard_widget") || !FeaturePermissions.canUseRendering()) {
				original.extractRenderState(extractor, deltaTracker);
				return;
			}
			extractor.pose().pushMatrix();
			extractor.pose().translate(-10.0F, 10.0F);
			original.extractRenderState(extractor, deltaTracker);
			extractor.pose().popMatrix();
		});

		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (extractor, deltaTracker) -> {
			if (!isModuleEnabled("custom_crosshair") || !FeaturePermissions.canUseRendering()) {
				original.extractRenderState(extractor, deltaTracker);
			}
			// When custom crosshair is enabled, suppress vanilla and draw our own
			// in renderHud() via the fork_overlay HUD element.
		});

		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player == null || client.level == null) return;

			GridOverlayModule.render(client);
			ChunkBorderModule.render(client);
			ShapePreviewModule.render(client);
			BlueprintPreviewModule.render(client);
		});

		this.initialized = true;
	}

	private void registerModules() {
		addModule(MODULE_BRIGHTNESS_PLUS, "Brightness Plus", ModuleCategory.VISUAL, "Set brightness beyond default levels with enhanced controls. B toggles, = and - adjust.", false);
		addModule("zoom", "Zoom", ModuleCategory.VISUAL, "Zooms while the dedicated key is held.", true);
		addModule("time_changer", "Time Changer", ModuleCategory.VISUAL, "Pins the client world clock to noon.", false);
		addModule("block_outline", "Block Outline", ModuleCategory.VISUAL, "Forces block outline rendering while looking at blocks.", true);
		addModule("no_hurt_camera", "No Hurt Camera", ModuleCategory.VISUAL, "Disables the hurt camera tilt effect.", true);
		addModule("custom_crosshair", "Custom Crosshair", ModuleCategory.VISUAL, "Draws a stylized custom crosshair overlay.", true);
		addModule("freelook", "Freelook", ModuleCategory.VISUAL, "Hold a key to look around without turning your player.", false);
		addModule("clean_view", "Clean View", ModuleCategory.VISUAL, "Hides your own name tag in third person.", false);
		addModule("no_weather", "No Weather", ModuleCategory.VISUAL, "Forces clear weather on the client side.", false);
		addModule("low_shield", "Low Shield", ModuleCategory.VISUAL, "Lowers the shield model in first person to improve visibility.", false);
		addModule("low_fire", "Low Fire", ModuleCategory.VISUAL, "Shrinks the fire overlay so it does not block your view.", false);
		addModule("motion_blur_plus", "Motion Blur Plus", ModuleCategory.VISUAL, "Adds a motion blur trail to the rendered frame. Configure via /motionblurplus.", false);
		addModule("entity_culling", "Entity Culling", ModuleCategory.VISUAL, "Prevents rendering entities hidden behind solid blocks using async path-tracing. Boosts FPS in dense areas.", false);

		addModule("auto_sprint", "Auto Sprint", ModuleCategory.MOVEMENT, "Keeps sprint held while you are in-game.", false);
		addModule("toggle_sneak", "Toggle Sneak", ModuleCategory.MOVEMENT, "Turns sneak into a toggle.", false);
		addModule("inventory_walk", "Inventory Walk", ModuleCategory.MOVEMENT, "Allows movement while your inventory is open.", false);

		addModule("auto_gg", "Auto GG", ModuleCategory.UTILITY, "Sends gg once after you die in multiplayer.", false);
		addModule("auto_chat_messages", "Auto Chat Messages", ModuleCategory.UTILITY, "Sends friendly chat messages on a timer while connected.", false);
		addModule("auto_tool", "Auto Tool", ModuleCategory.UTILITY, "Auto-switches to the best tool for the block you are mining.", false);
		addModule("chat_timestamps", "Chat Timestamps", ModuleCategory.UTILITY, "Adds timestamps in front of chat messages.", false);
		addModule("notifications", "Notifications", ModuleCategory.UTILITY, "Shows module toggle notifications.", true);
		addModule("module_toggle_alerts", "Module Toggle Alerts", ModuleCategory.UTILITY, "Shows richer alerts when modules or widgets are toggled.", true);
		addModule("clock_widget", "Clock Widget", ModuleCategory.HUD, "Displays the current world time.", true);
		addModule("coords_widget", "Coordinates Widget", ModuleCategory.HUD, "Displays current coordinates.", true);
		addModule("performance_widget", "Performance Widget", ModuleCategory.HUD, "Displays FPS, ping, and speed.", true);
		addModule("fps_widget", "FPS Counter", ModuleCategory.HUD, "Displays current frames per second.", true);
		addModule("ping_widget", "Ping Display", ModuleCategory.HUD, "Displays current server latency.", true);
		addModule("memory_widget", "Memory Usage", ModuleCategory.HUD, "Displays current Java memory usage.", true);
		addModule("direction_widget", "Direction HUD", ModuleCategory.HUD, "Displays the direction you are facing.", true);
		addModule("biome_widget", "Biome Display", ModuleCategory.HUD, "Displays the biome at your position.", true);
		addModule("armor_durability", "Armor Durability", ModuleCategory.HUD, "Displays armor items with durability percentages.", true);
		addModule("saturation_widget", "Saturation Display", ModuleCategory.HUD, "Displays hunger and saturation.", true);
		addModule("day_counter_widget", "Day Counter", ModuleCategory.HUD, "Displays the current world day.", true);
		addModule("server_widget", "Server Widget", ModuleCategory.HUD, "Displays server or singleplayer status.", true);
		addModule("keystrokes_widget", "Keystrokes Widget", ModuleCategory.HUD, "Displays movement and mouse input.", true);
		addModule("cps_widget", "CPS Widget", ModuleCategory.HUD, "Displays left and right clicks per second.", true);
		addModule("array_list_widget", "Array List Widget", ModuleCategory.HUD, "Displays enabled modules.", true);
		addModule("potion_status_widget", "Potion Status", ModuleCategory.HUD, "Lists current buffs and debuffs with timers.", true);
		addModule("combo_widget", "Combo Counter", ModuleCategory.COMBAT, "Tracks consecutive PvP hits.", false);
		addModule("reach_widget", "Reach Display", ModuleCategory.COMBAT, "Shows your reach distance to two decimals.", false);
		addModule("anti_knockback", "Anti-Knockback", ModuleCategory.MOVEMENT, "Reduces knockback from attacks and explosions. Use sliders to set horizontal and vertical reduction.", false);
		addModule("item_counter_widget", "Item Counter", ModuleCategory.HUD, "Tracks specific items in your inventory.", true);
		addModule("pack_display_widget", "Pack Display", ModuleCategory.HUD, "Shows the active resource pack name.", false);
		addModule("minimap_widget", "MiniMap", ModuleCategory.HUD, "A top-down radar minimap of nearby terrain.", false);
		addModule("timers_widget", "Timers & Stopwatches", ModuleCategory.HUD, "On-screen stopwatch and countdown timer.", false);
		addModule("boss_bar_overhaul", "BossBar Overhaul", ModuleCategory.VISUAL, "Scale and customize vanilla boss health bars.", false);
		addModule("scoreboard_widget", "Scoreboard Customization", ModuleCategory.HUD, "Reposition and clean up vanilla scoreboards.", false);

		// ── Content Creation ──────────────────────────────────────────────
		addModule("cinematic_camera", "Cinematic Camera", ModuleCategory.CONTENT, "Smooth free camera movement with adjustable speed and mouse smoothing.", false);
		addModule("cinematic_presets", "Cinematic Presets", ModuleCategory.CONTENT, "Configurable camera presets: slow pan, orbit, dolly, zoom, fly-through.", false);
		addModule("screenshot_mode", "Screenshot Mode", ModuleCategory.CONTENT, "Hides HUD elements for clean screenshots.", false);
		addModule("hide_hud_hotkey", "Hide HUD Hotkey", ModuleCategory.CONTENT, "Toggle every HUD element with a configurable hotkey.", false);
		addModule("timelapse_helper", "Timelapse Helper", ModuleCategory.CONTENT, "Assists with recording timelapses: camera speed, position lock, compass lock.", false);
		addModule("camera_path_recorder", "Camera Path Recorder", ModuleCategory.CONTENT, "Record, save, load, and replay camera paths with interpolation.", false);

		// ── Building Helpers ──────────────────────────────────────────────
		addModule("block_palette_viewer", "Block Palette Viewer", ModuleCategory.BUILDING, "Display selected building palette with color groups and favorites.", false);
		addModule("material_calculator", "Material Calculator", ModuleCategory.BUILDING, "Calculate required materials for a build with category breakdowns.", false);
		addModule("grid_overlay", "Grid Overlay", ModuleCategory.BUILDING, "Render an optional build grid: block, chunk, vertical, and horizontal.", false);
		addModule("chunk_border_viewer", "Chunk Border Viewer", ModuleCategory.BUILDING, "Customizable chunk border rendering with color and thickness options.", false);
		addModule("build_height_indicator", "Build Height Indicator", ModuleCategory.BUILDING, "Display current Y level, build limit, and remaining height.", false);
		addModule("shape_preview", "Circle / Sphere Preview", ModuleCategory.BUILDING, "Render ghost previews for circles, spheres, cylinders, and domes.", false);
		addModule("blueprint_preview", "Blueprint Preview", ModuleCategory.BUILDING, "Load and render blueprint files as transparent ghost blocks.", false);
		addModule("block_measurement", "Block Measurement", ModuleCategory.BUILDING, "Measure width, height, length, volume, and distance between two positions.", false);

		// ── Multiplayer Utilities ─────────────────────────────────────────
		addModule("friend_list", "Friend List", ModuleCategory.MULTIPLAYER, "Store friends locally with nicknames, colors, and online indicators.", false);
		addModule("party_hud", "Party HUD", ModuleCategory.MULTIPLAYER, "Display party members, health, distance, and status when available.", false);
		addModule("ping_graph", "Ping Graph", ModuleCategory.MULTIPLAYER, "Live ping display with average, min, max, and graph history.", false);
		addModule("server_tps_estimator", "Server TPS Estimator", ModuleCategory.MULTIPLAYER, "Estimate server TPS using client-observable timing. Labeled as estimate.", false);
		addModule("chat_filters", "Chat Filters", ModuleCategory.MULTIPLAYER, "Filter chat by keyword, regex, player, or spam detection.", false);
		addModule("quick_messages", "Quick Messages", ModuleCategory.MULTIPLAYER, "Configurable hotkeys for predefined chat messages.", false);
		addModule("player_notes", "Player Notes", ModuleCategory.MULTIPLAYER, "Store local notes about players with tags and trust labels.", false);
		addModule("server_bookmarks", "Server Bookmarks", ModuleCategory.MULTIPLAYER, "Save favorite servers with IP, port, notes, and ping history.", false);
	}

	private void registerWidgets() {
		addWidget("coords", "Coordinates", 12, 12, true);
		addWidget("fps", "FPS", 12, 28, true);
		addWidget("ping", "Ping", 12, 44, true);
		addWidget("memory", "Memory", 12, 60, true);
		addWidget("direction", "Direction", 12, 76, true);
		addWidget("biome", "Biome", 12, 104, true);
		addWidget("saturation", "Saturation", 12, 120, true);
		addWidget("day_counter", "Day Counter", 12, 148, true);
		addWidget("clock", "Clock", 12, 164, true);
		addWidget("server", "Server", 12, 180, true);
		addWidget("armor_durability", "Armor Durability", 12, 216, true);
		addWidget("keystrokes", "Keystrokes", 12, 272, true);
		addWidget("cps", "CPS", 104, 272, true);
		addWidget("performance", "Performance", 180, 12, true);
		addWidget("array_list", "Array List", 180, 64, true);
		addWidget("potion_status", "Potion Status", 12, 340, true);
		addWidget("combo", "Combo Counter", 180, 272, false);
		addWidget("reach", "Reach Display", 260, 272, false);
		addWidget("item_counter", "Item Counter", 180, 120, true);
		addWidget("pack_display", "Pack Display", 180, 160, false);
		addWidget("minimap", "MiniMap", 300, 12, false);
		addWidget("timers", "Timers", 180, 200, false);
		addWidget("scoreboard", "Scoreboard", 300, 64, false);
		addWidget("timelapse_info", "Timelapse", 400, 12, false);
		addWidget("build_height", "Build Height", 12, 400, false);
		addWidget("measurements", "Measurements", 400, 80, false);
		addWidget("ping_graph", "Ping Graph", 400, 140, false);
		addWidget("tps_display", "TPS Display", 400, 200, false);
		addWidget("palette", "Block Palette", 400, 260, false);
		addWidget("materials", "Material Calculator", 400, 300, false);
		addWidget("shape_preview", "Shape Preview", 12, 430, false);
		addWidget("blueprint_preview", "Blueprint Preview", 260, 430, false);
	}

	private void registerKeyMappings() {
		this.openGuiKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.fork-client.open_gui", InputConstants.KEY_RSHIFT, this.keyCategory)
		);
		this.openHudEditorKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.fork-client.open_hud_editor", InputConstants.KEY_H, this.keyCategory)
		);
		this.openArmorConfigKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.fork-client.open_armor_config", InputConstants.KEY_K, this.keyCategory)
		);
		this.zoomKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.fork-client.zoom", InputConstants.KEY_C, this.keyCategory)
		);
		this.freelookKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.fork-client.freelook", InputConstants.KEY_V, this.keyCategory)
		);
		this.hideHudKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.fork-client.hide_hud", InputConstants.KEY_F1, this.keyCategory)
		);
		this.cameraPathKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.fork-client.camera_path", InputConstants.KEY_J, this.keyCategory)
		);
		this.brightnessToggleKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.fork-client.brightness_toggle", InputConstants.KEY_B, this.keyCategory)
		);
		this.brightnessRaiseKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.fork-client.brightness_raise", InputConstants.KEY_EQUALS, this.keyCategory)
		);
		this.brightnessLowerKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.fork-client.brightness_lower", InputConstants.KEY_MINUS, this.keyCategory)
		);
	}

	private void addModule(String id, String title, ModuleCategory category, String description, boolean defaultEnabled) {
		ModuleDefinition definition = new ModuleDefinition(id, title, category, description, defaultEnabled);
		this.modules.add(definition);
		this.modulesById.put(id, definition);
		this.moduleStates.put(id, defaultEnabled);
	}

	private void addWidget(String id, String title, int x, int y, boolean enabled) {
		WidgetState widget = new WidgetState(id, title, x, y, enabled);
		this.widgets.add(widget);
		this.widgetsById.put(id, widget);
	}

	private void onClientTick(Minecraft client) {
		if (client == null) {
			return;
		}

		// Bump the HUD layout version so per-frame cached widget text/sizes are
		// rebuilt exactly once per tick (20 Hz). Widget data (player position,
		// FPS, ping, potion timers...) only changes at tick rate, so rendering
		// the cached result is visually identical to recomputing every frame.
		this.layoutVersion++;

		while (this.openGuiKey.consumeClick()) {
			toggleClickGui(client);
		}

		while (this.openHudEditorKey.consumeClick()) {
			toggleHudEditor(client);
		}

		while (this.openArmorConfigKey.consumeClick()) {
			openArmorConfig(client);
		}

		applyBrightnessPlus(client);
		applyZoom(client);
		applyFreelook(client);
		applyTimeChanger(client);
		applyMovementModules(client);
		applyNoWeather(client);
		applyAutoTool(client);
		applyInventoryWalk(client);
		updateClickCounters(client);
		updateMovementSpeed(client);
		updateCombatTracking(client);
		updateTimers();
		updateSessionState(client);
		handleUtilityModules(client);
		applyContentCreationModules(client);
		applyBuildingHelperModules(client);
		applyMultiplayerModules(client);
		pruneNotifications();
	}

	private void updateSessionState(Minecraft client) {
		String sessionKey = getSessionKey(client);
		if (!Objects.equals(this.lastSessionKey, sessionKey)) {
			this.lastSessionKey = sessionKey;
			this.autoGgSent = false;
			this.autoChatMessageIndex = 0;
			this.nextAutoChatMessageAt = System.currentTimeMillis() + AUTO_CHAT_JOIN_DELAY_MS;
			if (this.freelooking) {
				restoreFreelook(client);
			}
		}
	}

	private String getSessionKey(Minecraft client) {
		if (client.player == null || client.level == null) {
			return "menu";
		}

		ServerData server = client.getCurrentServer();
		if (server != null && server.ip != null && !server.ip.isBlank()) {
			return "server:" + server.ip.toLowerCase(Locale.ROOT);
		}

		return (client.hasSingleplayerServer() ? "singleplayer:" : "world:")
			+ client.level.dimension().toString().toLowerCase(Locale.ROOT);
	}

	private void handleUtilityModules(Minecraft client) {
		handleAutoGg(client);
		handleAutoChatMessages(client);
	}

	private void handleAutoGg(Minecraft client) {
		LocalPlayer player = client.player;
		if (!isModuleEnabled("auto_gg") || client.hasSingleplayerServer() || player == null || client.getConnection() == null) {
			return;
		}

		if (!FeaturePermissions.canUseUtilities()) {
			return;
		}

		if (player.getHealth() > 0.0F) {
			this.autoGgSent = false;
			return;
		}

		if (this.autoGgSent) {
			return;
		}

		client.getConnection().sendChat("gg");
		this.autoGgSent = true;
		pushUtilityNotification("Auto GG sent to chat.", ENABLED_ACCENT);
	}

	private void handleAutoChatMessages(Minecraft client) {
		if (!isModuleEnabled("auto_chat_messages") || client.hasSingleplayerServer() || client.player == null || client.getConnection() == null) {
			return;
		}

		if (!FeaturePermissions.canUseUtilities()) {
			return;
		}

		long now = System.currentTimeMillis();
		if (now < this.nextAutoChatMessageAt) {
			return;
		}

		String message = AUTO_CHAT_ROTATION.get(this.autoChatMessageIndex % AUTO_CHAT_ROTATION.size());
		this.autoChatMessageIndex++;
		this.nextAutoChatMessageAt = now + AUTO_CHAT_INTERVAL_MS;
		client.getConnection().sendChat(message);
		pushUtilityNotification("Auto chat sent: " + message, PANEL_OUTLINE);
	}

	private void applyContentCreationModules(Minecraft client) {
		if (client.player == null) return;

		// Hide HUD hotkey toggle
		while (this.hideHudKey.consumeClick()) {
			if (isModuleEnabled("hide_hud_hotkey")) {
				this.hudHidden = !this.hudHidden;
				pushUtilityNotification("HUD " + (this.hudHidden ? "hidden" : "shown"), PANEL_OUTLINE);
			}
		}

		// Camera path key toggle
		while (this.cameraPathKey.consumeClick()) {
			if (isModuleEnabled("camera_path_recorder")) {
				if (this.cameraPathRecording) {
					this.cameraPathRecording = false;
					pushUtilityNotification("Camera path recording stopped (" + this.cameraPathPoints.size() + " points)", ENABLED_ACCENT);
				} else {
					this.cameraPathPoints.clear();
					this.cameraPathRecording = true;
					this.cameraPathLastTick = System.currentTimeMillis();
					pushUtilityNotification("Camera path recording started", PANEL_OUTLINE);
				}
			}
		}

		// Camera path recording
		if (isModuleEnabled("camera_path_recorder") && this.cameraPathRecording && client.player != null) {
			long now = System.currentTimeMillis();
			float interval = getModuleFloatSetting("camera_path_recorder", "record_interval", 100.0F);
			if (now - this.cameraPathLastTick >= (long) interval) {
				LocalPlayer player = client.player;
				float speed = 0.0F;
				this.cameraPathPoints.add(new float[]{
					(float) player.getX(), (float) player.getY(), (float) player.getZ(),
					player.getYRot(), player.getXRot(), speed
				});
				this.cameraPathLastTick = now;
			}
		}

		// Camera path playback
		if (isModuleEnabled("camera_path_recorder") && this.cameraPathPlaying && !this.cameraPathPoints.isEmpty()) {
			long now = System.currentTimeMillis();
			float playbackSpeed = getModuleFloatSetting("camera_path_recorder", "playback_speed", 1.0F);
			long interval = (long) (100.0F / Math.max(0.1F, playbackSpeed));
			if (now - this.cameraPathLastTick >= interval) {
				if (this.cameraPathIndex < this.cameraPathPoints.size()) {
					float[] pt = this.cameraPathPoints.get(this.cameraPathIndex);
					client.player.setPos(pt[0], pt[1], pt[2]);
					client.player.setYRot(pt[3]);
					client.player.setXRot(pt[4]);
					this.cameraPathIndex++;
					this.cameraPathLastTick = now;
				} else {
					this.cameraPathPlaying = false;
					this.cameraPathIndex = 0;
					pushUtilityNotification("Camera path playback finished", ENABLED_ACCENT);
				}
			}
		}

		// Cinematic camera
		boolean cinematicOn = isModuleEnabled("cinematic_camera") && FeaturePermissions.canUseRendering();
		if (cinematicOn && !this.cinematicActive) {
			this.cinematicActive = true;
			this.cinematicYaw = client.player.getYRot();
			this.cinematicPitch = client.player.getXRot();
		} else if (!cinematicOn && this.cinematicActive) {
			this.cinematicActive = false;
		}
		if (cinematicOn) {
			float sensitivity = getModuleFloatSetting("cinematic_camera", "smoothing", 0.5F);
			LocalPlayer player = client.player;
			float yaw = player.getYRot();
			float pitch = player.getXRot();
			this.cinematicYaw += (yaw - this.cinematicYaw) * sensitivity;
			this.cinematicPitch += (pitch - this.cinematicPitch) * sensitivity;
			player.setYRot(this.cinematicYaw);
			player.setXRot(this.cinematicPitch);
		}

		// Timelapse helper
		if (isModuleEnabled("timelapse_helper")) {
			if (!this.timelapseRunning) {
				this.timelapseRunning = true;
				this.timelapseStartMs = System.currentTimeMillis();
			}
			LocalPlayer player = client.player;
			if (this.timelapsePositionLocked) {
				player.setDeltaMovement(0, 0, 0);
			}
			if (this.timelapseCompassLock >= 0) {
				float targetYaw = this.timelapseCompassLock * 90.0F;
				player.setYRot(targetYaw);
			}
		} else if (this.timelapseRunning) {
			this.timelapseRunning = false;
		}
	}

	private void applyBuildingHelperModules(Minecraft client) {
		BlockMeasurementModule.onTick(client);
	}

	private void applyMultiplayerModules(Minecraft client) {
		if (client.player == null || client.getConnection() == null) return;

		// Ping tracking
		if (isModuleEnabled("ping_graph")) {
			ServerData server = client.getCurrentServer();
			if (server != null) {
				int ping = Math.max(0, (int) server.ping);
				this.pingHistory.add(ping);
				while (this.pingHistory.size() > PING_HISTORY_MAX) {
					this.pingHistory.remove(0);
				}
			}
		}

		// TPS estimation based on world game time advancement rate
		if (isModuleEnabled("server_tps_estimator") && client.level != null) {
			long now = System.currentTimeMillis();
			long currentGameTime = client.level.getLevelData().getGameTime();
			if (this.lastTpsSampleMs > 0) {
				long elapsed = now - this.lastTpsSampleMs;
				if (elapsed >= 1000) {
					long gameTicksPassed = currentGameTime - this.lastTpsGameTime;
					if (elapsed > 0) {
						float tps = (float) gameTicksPassed * 1000.0F / (float) elapsed;
						this.estimatedTps = this.estimatedTps * 0.9F + tps * 0.1F;
						this.estimatedTps = Math.max(0.0F, Math.min(25.0F, this.estimatedTps));
					}
					this.lastTpsGameTime = currentGameTime;
					this.lastTpsSampleMs = now;
				}
			} else {
				this.lastTpsGameTime = currentGameTime;
				this.lastTpsSampleMs = now;
			}
		}

		// Quick messages via numpad keys (1-9) - TODO SDL migration: verify numpad keycodes sequential
		if (isModuleEnabled("quick_messages") && FeaturePermissions.canUseUtilities()) {
			for (int i = 0; i < Math.min(9, this.quickMessages.size()); i++) {
				int kpKey = switch (i) {
					case 0 -> InputConstants.KEY_NUMPAD1;
					case 1 -> InputConstants.KEY_NUMPAD2;
					case 2 -> InputConstants.KEY_NUMPAD3;
					case 3 -> InputConstants.KEY_NUMPAD4;
					case 4 -> InputConstants.KEY_NUMPAD5;
					case 5 -> InputConstants.KEY_NUMPAD6;
					case 6 -> InputConstants.KEY_NUMPAD7;
					case 7 -> InputConstants.KEY_NUMPAD8;
					case 8 -> InputConstants.KEY_NUMPAD9;
					default -> InputConstants.KEY_NUMPAD1;
				};
				boolean keyDown = isKeyDownCompat(kpKey);
				if (keyDown && !this.quickMessageKeyWasDown[i]) {
					String msg = this.quickMessages.get(i);
					if (client.getConnection() != null) {
						client.getConnection().sendChat(msg);
						pushUtilityNotification("Quick message sent: " + msg, ENABLED_ACCENT);
					}
				}
				this.quickMessageKeyWasDown[i] = keyDown;
			}
		}
	}

	private void toggleClickGui(Minecraft client) {
		if (client.gui.screen() instanceof ForkClickGuiScreen) {
			client.gui.setScreen(null);
			return;
		}

		if (client.gui.screen() instanceof ForkHudEditorScreen) {
			client.gui.setScreen(new ForkClickGuiScreen());
			return;
		}

		client.gui.setScreen(new ForkClickGuiScreen());
	}

	private void toggleHudEditor(Minecraft client) {
		if (client.gui.screen() instanceof ForkHudEditorScreen) {
			client.gui.setScreen(null);
			return;
		}

		client.gui.setScreen(new ForkHudEditorScreen());
	}

	/**
	 * Opens the Armor Durability config screen. Passes the current screen as the
	 * parent so the config screen can return to it, mirroring the behavior of the
	 * module's right-click action in the Click GUI.
	 */
	public void openArmorConfig(Minecraft client) {
		if (client == null || client.gui.screen() instanceof ArmorDurabilityConfigScreen) {
			return;
		}
		client.gui.setScreen(new ArmorDurabilityConfigScreen(client.gui.screen()));
	}

	/** Display name of the key currently bound to open the Armor Durability config. */
	public String getArmorConfigKeybindName() {
		return this.openArmorConfigKey == null
				? "K"
				: this.openArmorConfigKey.getTranslatedKeyMessage().getString();
	}

	public void openHudEditor(Minecraft client) {
		client.gui.setScreen(new ForkHudEditorScreen());
	}

	/**
	 * Consumes any pending click on the HUD editor hotkey (H) so that closing
	 * the editor from its own keyPressed handler doesn't immediately reopen it
	 * when the same click is picked up by END_CLIENT_TICK later in the frame.
	 */
	public void consumeHudEditorKeyClick() {
		this.openHudEditorKey.consumeClick();
	}

	private void applyBrightnessPlus(Minecraft client) {
		if (!FeaturePermissions.canUseRendering()) {
			return;
		}

		while (this.brightnessToggleKey.consumeClick()) {
			toggleBrightnessPlus(client);
		}
		while (this.brightnessRaiseKey.consumeClick()) {
			adjustBrightnessPlus(client, true);
		}
		while (this.brightnessLowerKey.consumeClick()) {
			adjustBrightnessPlus(client, false);
		}

		enforceBrightnessPlus(client);
	}

	private void toggleBrightnessPlus(Minecraft client) {
		boolean enabled = isModuleEnabled(MODULE_BRIGHTNESS_PLUS);
		if (enabled) {
			client.options.gamma().set(BRIGHTNESS_PLUS_DEFAULT);
			client.options.save();
		} else if (this.brightnessPlusValue <= 0.0) {
			changeBrightnessPlus(client, BRIGHTNESS_PLUS_MAX);
		} else {
			client.options.gamma().set(this.brightnessPlusValue);
			client.options.save();
		}
		toggleModule(MODULE_BRIGHTNESS_PLUS);
		showBrightnessPlusOverlay(client);
		saveConfig();
	}

	private void adjustBrightnessPlus(Minecraft client, boolean increase) {
		double step = this.brightnessPlusStep;
		if (isKeyDownCompat(InputConstants.KEY_LSHIFT)
				|| isKeyDownCompat(InputConstants.KEY_RSHIFT)) {
			step *= 5.0;
		}
		if (isKeyDownCompat(InputConstants.KEY_LCONTROL)
				|| isKeyDownCompat(InputConstants.KEY_RCONTROL)) {
			step *= 0.2;
		}
		double newValue = clampBrightness(increase ? this.brightnessPlusValue + step : this.brightnessPlusValue - step);
		changeBrightnessPlus(client, newValue);
		showBrightnessPlusOverlay(client);
		saveConfig();
	}

	private void changeBrightnessPlus(Minecraft client, double value) {
		this.brightnessPlusValue = clampBrightness(value);
		client.options.gamma().set(this.brightnessPlusValue);
		client.options.save();
	}

	private void enforceBrightnessPlus(Minecraft client) {
		double desired = isModuleEnabled(MODULE_BRIGHTNESS_PLUS) ? this.brightnessPlusValue : BRIGHTNESS_PLUS_DEFAULT;
		double current = client.options.gamma().get();
		if (Math.abs(current - desired) > 0.000001) {
			client.options.gamma().set(desired);
		}
	}

	private void showBrightnessPlusOverlay(Minecraft client) {
		if (client.player == null) {
			return;
		}
		long percent = Math.round(this.brightnessPlusValue * 100.0);
		client.player.sendOverlayMessage(
			Component.translatable("overlay.fork-client.brightness_change", percent).withStyle(ChatFormatting.GREEN)
		);
	}

	private double clampBrightness(double value) {
		return Math.max(this.brightnessPlusMin, Math.min(this.brightnessPlusMax, value));
	}

	public boolean isBrightnessPlusEnabled() {
		return isModuleEnabled(MODULE_BRIGHTNESS_PLUS) && FeaturePermissions.canUseRendering();
	}

	public double getBrightnessPlusValue() {
		return this.brightnessPlusValue;
	}

	private void applyZoom(Minecraft client) {
		boolean active = isModuleEnabled("zoom") && FeaturePermissions.canUseRendering() && this.zoomKey.isDown();

		if (active) {
			if (!this.zoomApplied) {
				this.storedFov = client.options.fov().get();
				this.zoomApplied = true;
			}

			if (client.options.fov().get() != 30) {
				client.options.fov().set(30);
			}
			return;
		}

		restoreZoom(client);
	}

	private void applyTimeChanger(Minecraft client) {
		if (isModuleEnabled("time_changer") && FeaturePermissions.canUseRendering() && client.level != null) {
			client.level.setTimeFromServer(6000L);
		}
	}

	private void preTickMovementModules(Minecraft client) {
		if (client == null) return;
		if (!FeaturePermissions.canUseMovement()) {
			if (this.toggleSneakLatched) {
				this.toggleSneakLatched = false;
			}
			return;
		}
		if (isModuleEnabled("toggle_sneak")) {
			while (client.options.keyShift.consumeClick()) {
				this.toggleSneakLatched = !this.toggleSneakLatched;
			}
		} else if (this.toggleSneakLatched) {
			this.toggleSneakLatched = false;
		}
	}

	private void applyMovementModules(Minecraft client) {
		// Auto-sprint is handled by ClientInputMixin which sets the Input.sprint flag.
		// No direct setSprinting() call here to avoid overriding the game's internal sprint
		// validation (hunger, suffocation, water checks).
	}

	private void applyNoWeather(Minecraft client) {
		if (client.level == null) {
			this.noWeatherActive = false;
			this.savedDimension = null;
			return;
		}
		if (isModuleEnabled("no_weather") && FeaturePermissions.canUseRendering()) {
			String currentDim = client.level.dimension().identifier().toString();
			if (!this.noWeatherActive || !currentDim.equals(this.savedDimension)) {
				this.savedRainLevel = client.level.getRainLevel(1.0F);
				this.savedThunderLevel = client.level.getThunderLevel(1.0F);
				this.savedDimension = currentDim;
				this.noWeatherActive = true;
			}
			client.level.setRainLevel(0.0F);
			client.level.setThunderLevel(0.0F);
		} else if (this.noWeatherActive) {
			client.level.setRainLevel(this.savedRainLevel);
			client.level.setThunderLevel(this.savedThunderLevel);
			this.noWeatherActive = false;
			this.savedDimension = null;
		}
	}

	private void applyAutoTool(Minecraft client) {
		if (!isModuleEnabled("auto_tool") || client.player == null || client.level == null) return;
		if (!FeaturePermissions.canUseUtilities()) return;
		if (!client.options.keyAttack.isDown()) return;
		var hitResult = client.hitResult;
		if (hitResult == null || hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return;
		var blockHit = (net.minecraft.world.phys.BlockHitResult) hitResult;
		var blockState = client.level.getBlockState(blockHit.getBlockPos());
		float bestSpeed = 0.0F;
		int bestSlot = -1;
		for (int i = 0; i < 9; i++) {
			ItemStack stack = client.player.getInventory().getItem(i);
			float speed = stack.getDestroySpeed(blockState);
			if (speed > bestSpeed) {
				bestSpeed = speed;
				bestSlot = i;
			}
		}
		if (bestSlot >= 0 && bestSpeed > 1.0F && bestSlot != client.player.getInventory().getSelectedSlot()) {
			client.player.getInventory().setSelectedSlot(bestSlot);
		}
	}

	private void applyInventoryWalk(Minecraft client) {
		if (!FeaturePermissions.canUseMovement() || !isModuleEnabled("inventory_walk") || client.player == null) return;
		if (client.gui.screen() == null || client.gui.screen() instanceof ForkClickGuiScreen
			|| client.gui.screen() instanceof ForkHudEditorScreen) return;
		// Minecraft clears KeyMapping states when a screen is open, so we read
		// the physical GLFW key state directly and apply movement manually.
		LocalPlayer player = client.player;
		// Window unused after SDL migration - isKeyDown(int) no longer needs window
		boolean forward = isKeyDownCompat(InputConstants.KEY_W);
		boolean back    = isKeyDownCompat(InputConstants.KEY_S);
		boolean left    = isKeyDownCompat(InputConstants.KEY_A);
		boolean right   = isKeyDownCompat(InputConstants.KEY_D);
		boolean jump    = isKeyDownCompat(InputConstants.KEY_SPACE);
		if (!forward && !back && !left && !right && !jump) return;

		float yRot = player.getYRot();
		float sin = (float) Math.sin(Math.toRadians(-yRot));
		float cos = (float) Math.cos(Math.toRadians(-yRot));
		float mx = 0.0F, mz = 0.0F;
		if (forward) mz += 1.0F;
		if (back)    mz -= 1.0F;
		if (left)    mx -= 1.0F;
		if (right)   mx += 1.0F;
		float len = (float) Math.sqrt(mx * mx + mz * mz);
		if (len > 0.0F) {
			mx /= len;
			mz /= len;
		}
		ForkClientController ctrl = ForkClientController.INSTANCE;
		float speed = (player.isSprinting() || ctrl.isModuleEnabled("auto_sprint")) ? 0.13F : 0.10F;
		Vec3 currentDelta = player.getDeltaMovement();
		player.setDeltaMovement(
			mz * sin * speed - mx * cos * speed,
			jump && player.onGround() ? 0.42F : currentDelta.y,
			mz * cos * speed + mx * sin * speed
		);
		// Jump impulse is applied above via setDeltaMovement; no hasImpulse field available
	}

	private void restoreZoom(Minecraft client) {
		if (!this.zoomApplied) {
			return;
		}

		client.options.fov().set(this.storedFov);
		this.zoomApplied = false;
	}

	private void applyFreelook(Minecraft client) {
		if (!isModuleEnabled("freelook") || !FeaturePermissions.canUseRendering()) {
			if (this.freelooking) {
				restoreFreelook(client);
			}
			return;
		}

		boolean keyDown = this.freelookKey.isDown();

		if (keyDown && !this.freelooking) {
			LocalPlayer player = client.player;
			if (player != null) {
				this.freelookOriginalYaw = player.getYRot();
				this.freelookOriginalPitch = player.getXRot();
				this.freelookYaw = this.freelookOriginalYaw;
				this.freelookPitch = this.freelookOriginalPitch;
				this.lastFreelookMouseX = client.mouseHandler.xpos();
				this.lastFreelookMouseY = client.mouseHandler.ypos();
				this.freelooking = true;
			}
		} else if (!keyDown && this.freelooking) {
			restoreFreelook(client);
		}

		if (this.freelooking && client.player != null) {
			// Keep the body fixed while freelooking so movement direction does not drift.
			client.player.setYRot(this.freelookOriginalYaw);
			client.player.setXRot(this.freelookOriginalPitch);
		}
	}

	private void restoreFreelook(Minecraft client) {
		if (!this.freelooking) {
			return;
		}
		LocalPlayer player = client.player;
		if (player != null) {
			player.setYRot(this.freelookOriginalYaw);
			player.setXRot(this.freelookOriginalPitch);
		}
		this.freelooking = false;
	}

	public boolean isFreelooking() {
		return this.freelooking;
	}

	public float getFreelookYaw() {
		return this.freelookYaw;
	}

	public float getFreelookPitch() {
		return this.freelookPitch;
	}

	public void updateFreelookMouse() {
		if (!this.freelooking) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		double mouseX = client.mouseHandler.xpos();
		double mouseY = client.mouseHandler.ypos();
		double dx = mouseX - this.lastFreelookMouseX;
		double dy = mouseY - this.lastFreelookMouseY;
		this.lastFreelookMouseX = mouseX;
		this.lastFreelookMouseY = mouseY;
		if (dx == 0.0D && dy == 0.0D) {
			return;
		}
		double sensitivity = client.options.sensitivity().get().doubleValue() * 0.6 + 0.2;
		sensitivity = sensitivity * sensitivity * sensitivity * 8.0 * 0.15;
		this.freelookYaw += (float) (dx * sensitivity);
		this.freelookPitch = (float) Mth.clamp(this.freelookPitch - (float) (dy * sensitivity), -90.0F, 90.0F);
	}

	private void updateClickCounters(Minecraft client) {
		long now = System.currentTimeMillis();

		boolean attackDown = client.options.keyAttack.isDown();
		if (attackDown && !this.lastAttackDown) {
			this.leftClicks.addLast(now);
		}
		this.lastAttackDown = attackDown;

		boolean useDown = client.options.keyUse.isDown();
		if (useDown && !this.lastUseDown) {
			this.rightClicks.addLast(now);
		}
		this.lastUseDown = useDown;

		pruneOldClicks(this.leftClicks, now);
		pruneOldClicks(this.rightClicks, now);
	}

	private void pruneOldClicks(Deque<Long> clicks, long now) {
		while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) {
			clicks.removeFirst();
		}
	}

	private void updateMovementSpeed(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			this.lastPlayerPos = Vec3.ZERO;
			this.firstSpeedTick = true;
			this.horizontalSpeed = 0.0D;
			return;
		}

		Vec3 current = player.position();
		if (this.firstSpeedTick) {
			this.lastPlayerPos = current;
			this.firstSpeedTick = false;
			this.horizontalSpeed = 0.0D;
			return;
		}

		this.horizontalSpeed = current.subtract(this.lastPlayerPos).horizontalDistance() * 20.0D;
		this.lastPlayerPos = current;
	}

	private void updateCombatTracking(Minecraft client) {
		if (client.player != null && client.hitResult instanceof EntityHitResult entityHitResult
			&& entityHitResult.getEntity() instanceof LivingEntity livingEntity) {
			if (livingEntity.getId() != this.comboTargetId) {
				this.comboTargetId = livingEntity.getId();
				this.comboTargetHurtTime = livingEntity.hurtTime;
				this.comboCount = 0;
				this.lastReach = 0.0D;
			}

			int previousHurtTime = this.comboTargetHurtTime;
			this.comboTargetHurtTime = livingEntity.hurtTime;

			if (livingEntity.hurtTime > previousHurtTime
				&& livingEntity.getLastHurtByPlayer() == client.player) {
				this.comboCount++;
				this.lastComboHitTime = System.currentTimeMillis();
				this.lastReach = client.player.getEyePosition().distanceTo(entityHitResult.getLocation());
			}
		} else if (client.hitResult instanceof EntityHitResult entityHitResult && client.player != null) {
			this.comboTargetId = entityHitResult.getEntity().getId();
			this.comboTargetHurtTime = 0;
		}

		if (this.comboCount > 0 && System.currentTimeMillis() - this.lastComboHitTime > 3000L) {
			this.comboCount = 0;
			this.lastReach = 0.0D;
		}
	}

	private void updateTimers() {
		if (isModuleEnabled("timers_widget")) {
			if (!this.timerRunning) {
				this.timerStartMs = System.currentTimeMillis();
				this.timerRunning = true;
			}
		} else {
			if (this.timerRunning) {
				this.timerElapsedMs += System.currentTimeMillis() - this.timerStartMs;
			}
			this.timerRunning = false;
		}
	}

	private void pruneNotifications() {
		long now = System.currentTimeMillis();
		while (!this.notifications.isEmpty() && this.notifications.peekFirst().expiresAt < now) {
			this.notifications.removeFirst();
		}
	}

	private void renderHud(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker) {
		this.renderingClientHud = true;
		try {
			Minecraft client = Minecraft.getInstance();
			if (client.gui.hud.isHidden() || this.hudHidden) {
				return;
			}

			if (isModuleEnabled("screenshot_mode")) {
				return;
			}

			// Frame-rate click handling for the minimap zoom buttons (the
			// cursor is grabbed in normal play, so this only fires when it is
			// free with no screen open).
			handleMinimapZoomClick(client);

			// Only layout/clamp widgets that are actually visible. Each widget's
			// text and size come from a tick-based cache so the per-frame cost of
			// the HUD loop is a map lookup instead of String.format + font
			// measurement for every widget on every frame.
			for (WidgetState widget : this.widgets) {
				if (!widget.enabled()) {
					continue;
				}

				WidgetLayout layout = getWidgetLayout(client, widget);
				clampWidgetToViewport(client, widget);
				renderWidget(extractor, client, widget, layout, false, false);
			}

			if (isModuleEnabled("custom_crosshair") && FeaturePermissions.canUseRendering()) {
				renderCustomCrosshair(extractor, client);
			}

			if (isModuleEnabled("notifications") || isModuleEnabled("module_toggle_alerts")) {
				renderNotifications(extractor, client);
			}

			renderWaypointBeacons(extractor, client);
		} finally {
			this.renderingClientHud = false;
		}
	}

	public void renderEditorPreview(GuiGraphicsExtractor extractor, double mouseX, double mouseY, WidgetState dragging) {
		this.renderingClientHud = true;
		try {
			Minecraft client = Minecraft.getInstance();
			for (WidgetState widget : this.widgets) {
				clampWidgetToViewport(client, widget);
				WidgetLayout layout = getWidgetLayout(client, widget);
				boolean hovered = isWithinWidget(client, widget, mouseX, mouseY);
				renderWidget(extractor, client, widget, layout, true, hovered || Objects.equals(widget, dragging));
			}

			Component hint = Component.literal("Left click to drag widgets. Right click toggles visibility.");
			extractor.fill(11, 11, 11 + client.font.width(hint) + 10, 11 + client.font.lineHeight + 8, 0xB8161C25);
			extractor.outline(11, 11, client.font.width(hint) + 10, client.font.lineHeight + 8, 0xFF8C7BFF);
			extractor.outline(13, 13, client.font.width(hint) + 6, client.font.lineHeight + 4, 0xFF39E6CB);
			extractor.text(client.font, hint, 16, 15, TEXT_PRIMARY, true);
		} finally {
			this.renderingClientHud = false;
		}
	}

	private void renderWidget(GuiGraphicsExtractor extractor, Minecraft client, WidgetState widget, WidgetLayout layout, boolean editorMode, boolean highlighted) {
		int x = widget.x();
		int y = widget.y();
		Size size = layout.size;

		int outline = highlighted ? PANEL_OUTLINE_HOVER : PANEL_OUTLINE;
		if (!widget.enabled()) {
			outline = PANEL_DISABLED;
		}

		if (!"array_list".equals(widget.id()) && !"minimap".equals(widget.id()) && !"armor_durability".equals(widget.id())) {
			int outerX = x - 3;
			int outerY = y - 3;
			int outerWidth = size.width + 6;
			int outerHeight = size.height + 6;
			if (widget.enabled()) {
				int glowColor = getCategoryColor(ModuleCategory.HUD);
				extractor.outline(outerX - 2, outerY - 2, outerWidth + 4, outerHeight + 4, withAlpha(glowColor, highlighted ? 0x66 : 0x38));
				extractor.outline(outerX - 1, outerY - 1, outerWidth + 2, outerHeight + 2, withAlpha(lighten(glowColor, 0.24F), highlighted ? 0xAA : 0x62));
			}
			extractor.fill(outerX, outerY, outerX + outerWidth, outerY + outerHeight, PANEL_BACKGROUND);
			extractor.outline(outerX, outerY, outerWidth, outerHeight, outline);
			extractor.outline(outerX + 2, outerY + 2, outerWidth - 4, outerHeight - 4, widget.enabled() ? lighten(outline, 0.22F) : PANEL_INNER);
			extractor.fill(outerX + 3, outerY + 3, outerX + outerWidth - 3, outerY + 7, widget.enabled() ? withAlpha(lighten(outline, 0.30F), 0x88) : 0x555F6761);
		}

		switch (widget.id()) {
			case "keystrokes" -> renderKeystrokes(extractor, client, x, y);
			case "array_list" -> renderArrayList(extractor, client, x, y, editorMode);
			case "minimap" -> renderMinimap(extractor, client, x, y, highlighted);
			case "armor_durability" -> ArmorDurabilityHudComponent.render(extractor, client, x, y, editorMode);
			default -> renderTextWidget(extractor, client, widget, x, y, editorMode, layout.lines);
		}
	}

	private void renderTextWidget(GuiGraphicsExtractor extractor, Minecraft client, WidgetState widget, int x, int y, boolean editorMode, List<CachedLine> lines) {
		int lineHeight = client.font.lineHeight + 2;

		if (editorMode) {
			extractor.text(client.font, Component.literal(widget.title()), x, y - client.font.lineHeight - 4, TEXT_MUTED, false);
		}

		// Reuse the cached Component for each line instead of allocating a new
		// Component.literal() every frame; text and width were computed once per
		// tick in the layout cache.
		for (int i = 0; i < lines.size(); i++) {
			CachedLine line = lines.get(i);
			extractor.text(client.font, line.component, x, y + (i * lineHeight), TEXT_PRIMARY, false);
		}
	}

	private void renderKeystrokes(GuiGraphicsExtractor extractor, Minecraft client, int x, int y) {
		drawKeyCell(extractor, client.font, x + 20, y, 18, 18, "W", client.options.keyUp.isDown());
		drawKeyCell(extractor, client.font, x, y + 20, 18, 18, "A", client.options.keyLeft.isDown());
		drawKeyCell(extractor, client.font, x + 20, y + 20, 18, 18, "S", client.options.keyDown.isDown());
		drawKeyCell(extractor, client.font, x + 40, y + 20, 18, 18, "D", client.options.keyRight.isDown());
		drawKeyCell(extractor, client.font, x, y + 42, 28, 18, "LMB", client.options.keyAttack.isDown());
		drawKeyCell(extractor, client.font, x + 30, y + 42, 28, 18, "RMB", client.options.keyUse.isDown());
	}

	private void drawKeyCell(GuiGraphicsExtractor extractor, Font font, int x, int y, int width, int height, String label, boolean active) {
		int fill = active ? 0xE1444FB8 : 0xCC27303A;
		int outline = active ? 0xFF8EE7FF : PANEL_DISABLED;
		extractor.fill(x, y, x + width, y + height, fill);
		extractor.outline(x, y, width, height, outline);
		extractor.outline(x + 2, y + 2, width - 4, height - 4, active ? 0xFFD7F8FF : PANEL_INNER);
		extractor.fill(x + 2, y + 2, x + width - 2, y + 5, active ? 0x889DEEFF : 0x444D545C);
		int textX = x + Math.max(2, (width - font.width(label)) / 2);
		int textY = y + Math.max(2, (height - font.lineHeight) / 2);
		extractor.text(font, Component.literal(label), textX, textY, TEXT_PRIMARY, false);
	}

	private void renderArrayList(GuiGraphicsExtractor extractor, Minecraft client, int x, int y, boolean editorMode) {
		if (editorMode) {
			WidgetState widget = this.widgetsById.get("array_list");
			if (widget != null) {
				ArrayListHudComponent.renderEditorPreview(extractor, client, widget);
			}
			return;
		}

		ArrayListHudComponent.render(extractor, client, x, y);
	}

	private void renderMinimap(GuiGraphicsExtractor extractor, Minecraft client, int x, int y, boolean highlighted) {
		WidgetState widget = this.widgetsById.get("minimap");
		boolean enabled = widget == null || widget.enabled();

		if (highlighted) {
			int glow = getCategoryColor(ModuleCategory.HUD);
			extractor.outline(x - 2, y - 2, MINIMAP_SIZE + 4, MINIMAP_SIZE + 4, withAlpha(glow, 0x66));
			extractor.outline(x - 1, y - 1, MINIMAP_SIZE + 2, MINIMAP_SIZE + 2, withAlpha(lighten(glow, 0.24F), 0xAA));
		}

		if (!enabled) {
			extractor.fill(x, y, x + MINIMAP_SIZE, y + MINIMAP_SIZE, PANEL_BACKGROUND);
			extractor.outline(x, y, MINIMAP_SIZE, MINIMAP_SIZE, PANEL_DISABLED);
			extractor.outline(x + 2, y + 2, MINIMAP_SIZE - 4, MINIMAP_SIZE - 4, PANEL_INNER);
			return;
		}

		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			extractor.fill(x, y, x + MINIMAP_SIZE, y + MINIMAP_SIZE, MAP_PARCHMENT);
			drawMapFrame(extractor, x, y);
			String noMap = "No Map";
			extractor.text(client.font, Component.literal(noMap),
				x + (MINIMAP_SIZE - client.font.width(noMap)) / 2,
				y + (MINIMAP_SIZE - client.font.lineHeight) / 2, TEXT_DISABLED, false);
			return;
		}

		int centerX = x + MINIMAP_SIZE / 2;
		int centerY = y + MINIMAP_SIZE / 2;
		int playerBlockX = (int) Math.floor(player.getX());
		int playerBlockY = (int) Math.floor(player.getY());
		int playerBlockZ = (int) Math.floor(player.getZ());

		// The block colors behind the minimap are cached and rebuilt only when
		// the player crosses a block boundary, the level changes, the zoom
		// changes, or after a short timeout. Sampling up to 64x64 columns with
		// getBlockState()+getMapColor() is the minimap's most expensive
		// operation, so it must not run on every frame.
		ensureMinimapGrid(client, playerBlockX, playerBlockY, playerBlockZ);

		extractor.enableScissor(x, y, x + MINIMAP_SIZE, y + MINIMAP_SIZE);
		extractor.fill(x, y, x + MINIMAP_SIZE, y + MINIMAP_SIZE, MAP_PARCHMENT);

		// North-up blit of the cached color grid. A 0 entry is a transparent
		// MapColor.NONE cell (air/unexplored), so parchment shows through.
		int[] colors = this.minimapCacheColors;
		if (colors != null) {
			for (int j = 0; j < MINIMAP_GRID; j++) {
				int rowBase = j * MINIMAP_GRID;
				int runStart = -1;
				int runColor = 0;
				for (int i = 0; i <= MINIMAP_GRID; i++) {
					int color = i < MINIMAP_GRID ? colors[rowBase + i] : -1;
					if (color != runColor) {
						if (runStart >= 0 && runColor != 0) {
							extractor.fill(x + runStart, y + j, x + i, y + j + 1, runColor);
						}
						runStart = i;
						runColor = color;
					}
				}
			}
		}

		drawMapFrame(extractor, x, y);
		drawMinimapPlayerArrow(extractor, centerX, centerY, player);

		float pxPerBlock = minimapPixelsPerBlock();
		var entities = client.level.entitiesForRendering();
		if (entities != null) {
			// Reuse a single buffer for the entity snapshot so the minimap does
			// not allocate a new ArrayList every frame.
			this.minimapEntityBuffer.clear();
			entities.forEach(this.minimapEntityBuffer::add);
			for (var entity : this.minimapEntityBuffer) {
				if (entity == player) continue;
				int epx = centerX + (int) Math.round((entity.getX() - player.getX()) * pxPerBlock);
				int epz = centerY + (int) Math.round((entity.getZ() - player.getZ()) * pxPerBlock);
				if (epx >= x && epx < x + MINIMAP_SIZE && epz >= y && epz < y + MINIMAP_SIZE) {
					extractor.fill(epx - 1, epz - 1, epx + 1, epz + 1, 0xFFFF4444);
				}
			}
		}

		drawMinimapZoomButtons(extractor, x, y);
		extractor.disableScissor();
	}

	private void drawMapFrame(GuiGraphicsExtractor extractor, int x, int y) {
		extractor.fill(x, y, x + MINIMAP_SIZE, y + 2, MAP_FRAME_DARK);
		extractor.fill(x, y + MINIMAP_SIZE - 2, x + MINIMAP_SIZE, y + MINIMAP_SIZE, MAP_FRAME_DARK);
		extractor.fill(x, y, x + 2, y + MINIMAP_SIZE, MAP_FRAME_DARK);
		extractor.fill(x + MINIMAP_SIZE - 2, y, x + MINIMAP_SIZE, y + MINIMAP_SIZE, MAP_FRAME_DARK);
		extractor.outline(x + 2, y + 2, MINIMAP_SIZE - 4, MINIMAP_SIZE - 4, MAP_FRAME_LIGHT);
	}

	private void drawMinimapPlayerArrow(GuiGraphicsExtractor extractor, int centerX, int centerY, LocalPlayer player) {
		// North-up map space: north points up, so the arrow direction is
		// (-sin(yaw), cos(yaw)) (east = right, south = down).
		float yawRad = (float) Math.toRadians(player.getVisualRotationYInDegrees());
		float dx = -Mth.sin(yawRad);
		float dy = Mth.cos(yawRad);

		int tipX = centerX + Math.round(dx * 3);
		int tipY = centerY + Math.round(dy * 3);
		int baseX = centerX - Math.round(dx * 2);
		int baseY = centerY - Math.round(dy * 2);
		int perpX = Math.round(-dy * 2);
		int perpY = Math.round(dx * 2);
		int bx1 = baseX - perpX;
		int by1 = baseY - perpY;
		int bx2 = baseX + perpX;
		int by2 = baseY + perpY;

		// Dark outline (triangle grown away from its centroid), then white fill.
		int centroidX = (tipX + bx1 + bx2) / 3;
		int centroidY = (tipY + by1 + by2) / 3;
		fillTriangle(extractor,
			growVertex(centroidX, tipX), growVertex(centroidY, tipY),
			growVertex(centroidX, bx1), growVertex(centroidY, by1),
			growVertex(centroidX, bx2), growVertex(centroidY, by2), 0xFF1A1A1A);
		fillTriangle(extractor, tipX, tipY, bx1, by1, bx2, by2, 0xFFFFFFFF);
	}

	private static int growVertex(int centroid, int value) {
		if (value > centroid) {
			return value + 1;
		}
		if (value < centroid) {
			return value - 1;
		}
		return value;
	}

	private void fillTriangle(GuiGraphicsExtractor extractor, int x1, int y1, int x2, int y2, int x3, int y3, int color) {
		int minY = Math.min(y1, Math.min(y2, y3));
		int maxY = Math.max(y1, Math.max(y2, y3));
		for (int yy = minY; yy <= maxY; yy++) {
			double y0 = yy + 0.5;
			double left = triangleEdgeIntersectionX(x1, y1, x2, y2, x3, y3, y0, true);
			double right = triangleEdgeIntersectionX(x1, y1, x2, y2, x3, y3, y0, false);
			if (right > left) {
				extractor.fill((int) Math.floor(left), yy, (int) Math.ceil(right), yy + 1, color);
			}
		}
	}

	private static double triangleEdgeIntersectionX(int x1, int y1, int x2, int y2, int x3, int y3, double y0, boolean leftmost) {
		double best = leftmost ? Double.MAX_VALUE : -Double.MAX_VALUE;
		best = triangleEdge(best, x1, y1, x2, y2, y0, leftmost);
		best = triangleEdge(best, x2, y2, x3, y3, y0, leftmost);
		best = triangleEdge(best, x3, y3, x1, y1, y0, leftmost);
		return best;
	}

	private static double triangleEdge(double best, int ax, int ay, int bx, int by, double y0, boolean leftmost) {
		if ((ay <= y0 && y0 < by) || (by <= y0 && y0 < ay)) {
			double x = ax + (bx - ax) * (y0 - ay) / (double) (by - ay);
			return leftmost ? Math.min(best, x) : Math.max(best, x);
		}
		return best;
	}

	private void drawMinimapZoomButtons(GuiGraphicsExtractor extractor, int x, int y) {
		int right = x + MINIMAP_SIZE - MINIMAP_BTN_MARGIN - MINIMAP_BTN_SIZE;
		int left = right - MINIMAP_BTN_GAP - MINIMAP_BTN_SIZE;
		int by = y + MINIMAP_BTN_MARGIN;
		drawMinimapZoomButton(extractor, right, by, true);
		drawMinimapZoomButton(extractor, left, by, false);
	}

	private void drawMinimapZoomButton(GuiGraphicsExtractor extractor, int bx, int by, boolean plus) {
		extractor.fill(bx, by, bx + MINIMAP_BTN_SIZE, by + MINIMAP_BTN_SIZE, 0xAA1B212A);
		extractor.outline(bx, by, MINIMAP_BTN_SIZE, MINIMAP_BTN_SIZE, 0xFF55D0FF);
		if (plus) {
			extractor.fill(bx + 6, by + 3, bx + 8, by + 11, 0xFFFFFFFF);
		}
		extractor.fill(bx + 3, by + 6, bx + 11, by + 8, 0xFFFFFFFF);
	}

	private boolean minimapButtonHit(int widgetX, int widgetY, double mouseX, double mouseY, boolean plus) {
		int right = widgetX + MINIMAP_SIZE - MINIMAP_BTN_MARGIN - MINIMAP_BTN_SIZE;
		int left = right - MINIMAP_BTN_GAP - MINIMAP_BTN_SIZE;
		int bx = plus ? right : left;
		int by = widgetY + MINIMAP_BTN_MARGIN;
		return mouseX >= bx && mouseX < bx + MINIMAP_BTN_SIZE
			&& mouseY >= by && mouseY < by + MINIMAP_BTN_SIZE;
	}

	private int getMinimapZoom() {
		float zoom = getModuleFloatSetting("minimap_widget", "zoom", 1.0F);
		if (zoom >= 4.0F) {
			return 4;
		}
		if (zoom >= 2.0F) {
			return 2;
		}
		return 1;
	}

	// Zoom radius in blocks: 16 / 32 / 64. Each grid pixel covers radius/32
	// blocks (0.5 / 1 / 2), so the 64x64 widget maps to a 2*radius square.
	private float minimapPixelsPerBlock() {
		return (16 << (getMinimapZoom() - 1)) / 32.0F;
	}

	private void handleMinimapZoomClick(Minecraft client) {
		WidgetState widget = this.widgetsById.get("minimap");
		if (widget == null || !widget.enabled()) {
			this.minimapZoomClickDown = false;
			return;
		}
		if (client.gui.screen() != null || client.player == null
			|| client.gui.hud.isHidden() || this.hudHidden
			|| client.mouseHandler.isMouseGrabbed()) {
			this.minimapZoomClickDown = false;
			return;
		}

		// Fire once per press edge so holding the button does not cycle zoom.
		boolean leftDown = isKeyDownCompat(InputConstants.MOUSE_BUTTON_LEFT);
		if (!leftDown || this.minimapZoomClickDown) {
			this.minimapZoomClickDown = leftDown;
			return;
		}
		this.minimapZoomClickDown = true;

		double mouseX = client.mouseHandler.getScaledXPos(client.getWindow());
		double mouseY = client.mouseHandler.getScaledYPos(client.getWindow());
		boolean plus = minimapButtonHit(widget.x(), widget.y(), mouseX, mouseY, true);
		boolean minus = minimapButtonHit(widget.x(), widget.y(), mouseX, mouseY, false);
		if (!plus && !minus) {
			return;
		}

		int zoom = getMinimapZoom();
		int next = plus ? (zoom >= 4 ? 1 : zoom * 2) : (zoom <= 1 ? 4 : zoom / 2);
		// Force the cached grid to rebuild on the next frame.
		this.minimapCacheZoom = -1;
		setModuleFloatSetting("minimap_widget", "zoom", next);
	}

	/**
	 * Rebuilds the cached minimap block-color grid only when the sampled block
	 * position, the level, the zoom, or the refresh timer changes. Sampling up
	 * to 4096 block columns with map-color lookup is the minimap's most
	 * expensive operation, so it must not run on every frame. The rebuild is
	 * allowed to run less often at higher zoom, where it is heavier.
	 */
	private void ensureMinimapGrid(Minecraft client, int px, int py, int pz) {
		int zoom = getMinimapZoom();
		long now = System.currentTimeMillis();
		long cacheMs = zoom > 1 ? 1500L : MINIMAP_CACHE_MS;
		if (this.minimapCacheColors != null
			&& this.minimapCacheLevel == client.level
			&& this.minimapCacheZoom == zoom
			&& this.minimapCacheX == px
			&& this.minimapCacheY == py
			&& this.minimapCacheZ == pz
			&& now - this.minimapCacheTime < cacheMs) {
			return;
		}

		float pxPerBlock = minimapPixelsPerBlock();
		int[] colors = new int[MINIMAP_GRID * MINIMAP_GRID];
		for (int j = 0; j < MINIMAP_GRID; j++) {
			int rowBase = j * MINIMAP_GRID;
			for (int i = 0; i < MINIMAP_GRID; i++) {
				double wx = px + (i - MINIMAP_GRID / 2 + 0.5) * pxPerBlock;
				double wz = pz + (j - MINIMAP_GRID / 2 + 0.5) * pxPerBlock;
				colors[rowBase + i] = sampleMinimapColor(client, wx, py, wz);
			}
		}

		this.minimapCacheColors = colors;
		this.minimapCacheLevel = client.level;
		this.minimapCacheZoom = zoom;
		this.minimapCacheX = px;
		this.minimapCacheY = py;
		this.minimapCacheZ = pz;
		this.minimapCacheTime = now;
	}

	/**
	 * Samples the vanilla packed map color for one map pixel: scans the block
	 * column around the player's Y for the first surface with a non-NONE map
	 * color, then picks a brightness by the surface's height relative to the
	 * player (above = lightest, level = mid, below = darker). Water columns
	 * darken with depth, approximating the vanilla map depth pattern.
	 */
	private int sampleMinimapColor(Minecraft client, double wx, int py, double wz) {
		int bx = (int) Math.floor(wx);
		int bz = (int) Math.floor(wz);
		MapColor material = MapColor.NONE;
		int materialY = py;
		int waterDepth = 0;

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = py + MINIMAP_MAX_SURFACE_SCAN; y >= py - MINIMAP_MAX_SURFACE_SCAN; y--) {
			pos.set(bx, y, bz);
			MapColor color;
			try {
				color = client.level.getBlockState(pos).getMapColor(client.level, pos);
			} catch (Exception e) {
				color = MapColor.NONE;
			}
			if (color == MapColor.NONE) {
				continue;
			}
			if (color == MapColor.WATER) {
				waterDepth++;
				continue;
			}
			material = color;
			materialY = y;
			break;
		}

		if (material == MapColor.NONE) {
			if (waterDepth == 0) {
				return 0;
			}
			material = MapColor.WATER;
		}

		MapColor.Brightness brightness;
		if (waterDepth > 0) {
			brightness = waterDepth >= 3 ? MapColor.Brightness.LOWEST : MapColor.Brightness.LOW;
		} else if (materialY > py) {
			brightness = MapColor.Brightness.HIGH;
		} else if (materialY < py) {
			brightness = MapColor.Brightness.LOW;
		} else {
			brightness = MapColor.Brightness.NORMAL;
		}
		return MapColor.getColorFromPackedId(material.id * 4 + brightness.id);
	}

	private void renderNotifications(GuiGraphicsExtractor extractor, Minecraft client) {
		if (this.notifications.isEmpty()) {
			return;
		}

		List<Notification> activeNotifications = new ArrayList<>(this.notifications);
		Collections.reverse(activeNotifications);

		int right = extractor.guiWidth() - 14;
		int y = 14;
		for (Notification notification : activeNotifications) {
			int width = client.font.width(notification.message()) + 14;
			int x = right - width;
			extractor.fill(x, y, right, y + client.font.lineHeight + 8, 0xE0161C25);
			extractor.outline(x, y, width, client.font.lineHeight + 8, notification.color());
			extractor.outline(x + 2, y + 2, width - 4, client.font.lineHeight + 4, 0xFF47504B);
			extractor.fill(x + 3, y + 3, right - 3, y + 6, withAlpha(lighten(notification.color(), 0.25F), 0x99));
			extractor.text(client.font, Component.literal(notification.message()), x + 7, y + 4, TEXT_PRIMARY, false);
			y += client.font.lineHeight + 12;
		}
	}

	private void renderWaypointBeacons(GuiGraphicsExtractor extractor, Minecraft client) {
		if (this.waypoints.isEmpty() || client.player == null || client.level == null) return;

		String playerDim = client.level.dimension().identifier().toString();
		int guiW = extractor.guiWidth();
		int guiH = extractor.guiHeight();
		int centerX = guiW / 2;
		int lineHeight = client.font.lineHeight + 4;
		int margin = 4;

		Camera camera = client.gameRenderer.mainCamera();
		Vec3 cameraPos = camera.position();
		Vec3 forward = new Vec3(camera.forwardVector()).normalize();
		Vec3 up = new Vec3(camera.upVector()).normalize();
		Vec3 right = forward.cross(up).normalize();

		double fov = client.options.fov().get();
		double fovRad = Math.toRadians(fov);
		double aspect = (double) guiW / guiH;
		double tanHalfFov = Math.tan(fovRad / 2.0);

		// Start just above the hotbar (below health/food/armor bars)
		int beaconY = guiH - 22 - lineHeight;

		for (int i = this.waypoints.size() - 1; i >= 0; i--) {
			Waypoint wp = this.waypoints.get(i);
			if (!wp.enabled() || !wp.dimension().equals(playerDim)) continue;

			double dist = distanceToWaypoint(wp);
			Vec3 waypointPos = new Vec3(wp.x() + 0.5, wp.y() + 0.5, wp.z() + 0.5);
			Vec3 toWaypoint = waypointPos.subtract(cameraPos);

			double camX = toWaypoint.dot(right);
			double camY = toWaypoint.dot(up);
			double camZ = toWaypoint.dot(forward);

			double absCamZ = Math.max(Math.abs(camZ), 0.01);

			double ndcX = camX / (absCamZ * aspect * tanHalfFov);
			double ndcY = camY / (absCamZ * tanHalfFov);

			boolean offScreen = Math.abs(ndcX) > 1.0 || Math.abs(ndcY) > 1.0;
			if (offScreen) {
				double scale = 1.0 / Math.max(Math.abs(ndcX), Math.abs(ndcY));
				ndcX *= scale;
				ndcY *= scale;
			}

			int dotX = (int) Math.round((ndcX * 0.5 + 0.5) * guiW);
			int dotY = (int) Math.round((-ndcY * 0.5 + 0.5) * guiH);

			if (offScreen) {
				dotX = Math.max(8, Math.min(guiW - 8, dotX));
				dotY = Math.max(8, Math.min(guiH - 8, dotY));
			}

			// Draw waypoint dot at exact projected position
			extractor.fill(dotX - 3, dotY - 3, dotX + 4, dotY + 4, wp.color() | 0xFF000000);
			extractor.fill(dotX - 2, dotY - 2, dotX + 3, dotY + 3, withAlpha(lighten(wp.color(), 0.4F), 0xEE));

			// Draw label above the hotbar, stacking upward
			String distStr = dist < 1000 ? String.format(Locale.ROOT, "%.0fm", dist) : String.format(Locale.ROOT, "%.1fkm", dist / 1000.0);
			String label = wp.name() + " " + distStr;
			int labelW = client.font.width(label) + 12;

			beaconY -= lineHeight;
			if (beaconY < margin) continue;
			int lx = Math.max(margin, Math.min(centerX - labelW / 2, guiW - labelW - margin));
			extractor.fill(lx, beaconY, lx + labelW, beaconY + lineHeight, 0xBB12192A);
			extractor.fill(lx, beaconY, lx + 3, beaconY + lineHeight, wp.color() | 0xFF000000);
			extractor.text(client.font, Component.literal(label), lx + 7, beaconY + 2, wp.color() | 0xFF000000, false);
		}
	}

	private void renderCustomCrosshair(GuiGraphicsExtractor extractor, Minecraft client) {
		if (client.player == null) {
			return;
		}

		int centerX = extractor.guiWidth() / 2;
		int centerY = extractor.guiHeight() / 2;
		int white = 0xFFFFFFFF;

		int gap = 2;
		int armLength = 4;
		int cornerSize = 3;

		extractor.fill(centerX - 1, centerY - gap - armLength - 1, centerX + 2, centerY - gap + 1, white);
		extractor.fill(centerX, centerY - gap - armLength, centerX + 1, centerY - gap, white);

		extractor.fill(centerX - 1, centerY + gap - 1, centerX + 2, centerY + gap + armLength + 1, white);
		extractor.fill(centerX, centerY + gap, centerX + 1, centerY + gap + armLength, white);

		extractor.fill(centerX - gap - armLength - 1, centerY - 1, centerX - gap + 1, centerY + 2, white);
		extractor.fill(centerX - gap - armLength, centerY, centerX - gap, centerY + 1, white);

		extractor.fill(centerX + gap - 1, centerY - 1, centerX + gap + armLength + 1, centerY + 2, white);
		extractor.fill(centerX + gap, centerY, centerX + gap + armLength, centerY + 1, white);

		int bx1 = centerX - gap - armLength;
		int by1 = centerY - gap - armLength;
		int bx2 = centerX + gap + armLength;
		int by2 = centerY + gap + armLength;

		extractor.fill(bx1, by1, bx1 + cornerSize, by1 + 1, white);
		extractor.fill(bx1, by1, bx1 + 1, by1 + cornerSize, white);

		extractor.fill(bx2 - cornerSize + 1, by1, bx2 + 1, by1 + 1, white);
		extractor.fill(bx2, by1, bx2 + 1, by1 + cornerSize, white);

		extractor.fill(bx1, by2, bx1 + cornerSize, by2 + 1, white);
		extractor.fill(bx1, by2 - cornerSize + 1, bx1 + 1, by2 + 1, white);

		extractor.fill(bx2 - cornerSize + 1, by2, bx2 + 1, by2 + 1, white);
		extractor.fill(bx2, by2 - cornerSize + 1, bx2 + 1, by2 + 1, white);
	}

	private void pushUtilityNotification(String message, int color) {
		if (isModuleEnabled("notifications")) {
			pushNotification(message, color, 3200L);
		}
	}

	private Size measureWidget(Minecraft client, WidgetState widget) {
		// All widget sizing flows through the tick-based layout cache so this
		// path (also used by hit-testing in the HUD editor) never re-runs the
		// expensive line-building/measurement code more than once per tick.
		return getWidgetLayout(client, widget).size;
	}

	/**
	 * Returns the cached layout (text lines + size) for a widget, rebuilding it
	 * lazily once per client tick. Text lines, their measured pixel widths, and
	 * the {@link Component} used for rendering are all reused across frames
	 * while unchanged, which removes per-frame String.format, font measurement
	 * and Component allocations from the HUD hot path.
	 */
	private WidgetLayout getWidgetLayout(Minecraft client, WidgetState widget) {
		WidgetLayout cached = this.widgetLayoutCache.get(widget.id());
		if (cached != null && cached.version == this.layoutVersion) {
			return cached;
		}
		WidgetLayout fresh = buildWidgetLayout(client, widget, cached);
		this.widgetLayoutCache.put(widget.id(), fresh);
		return fresh;
	}

	private WidgetLayout buildWidgetLayout(Minecraft client, WidgetState widget, WidgetLayout old) {
		int lineHeight = client.font.lineHeight + 2;

		// Fixed-size widgets never need their lines measured; returning an empty
		// line list means their renderer (keystrokes/minimap) does the work.
		return switch (widget.id()) {
			case "keystrokes" -> new WidgetLayout(this.layoutVersion, List.of(), new Size(58, 60));
			case "minimap" -> new WidgetLayout(this.layoutVersion, List.of(), new Size(64, 64));
			case "timers" -> new WidgetLayout(this.layoutVersion, buildCachedLines(client, getWidgetLines(client, widget), old), new Size(80, 2 * lineHeight));
			case "timelapse_info" -> new WidgetLayout(this.layoutVersion, buildCachedLines(client, getWidgetLines(client, widget), old), new Size(120, 2 * lineHeight));
			case "build_height" -> new WidgetLayout(this.layoutVersion, buildCachedLines(client, getWidgetLines(client, widget), old), new Size(120, 3 * lineHeight));
			case "measurements" -> new WidgetLayout(this.layoutVersion, buildCachedLines(client, getWidgetLines(client, widget), old), new Size(100, 3 * lineHeight));
			case "ping_graph" -> new WidgetLayout(this.layoutVersion, buildCachedLines(client, getWidgetLines(client, widget), old), new Size(100, 4 * lineHeight));
			case "tps_display" -> new WidgetLayout(this.layoutVersion, buildCachedLines(client, getWidgetLines(client, widget), old), new Size(100, 2 * lineHeight));
			case "potion_status" -> {
				LocalPlayer player = client.player;
				int effectCount = player != null ? player.getActiveEffects().size() : 0;
				yield new WidgetLayout(this.layoutVersion, buildCachedLines(client, getWidgetLines(client, widget), old),
					new Size(80, Math.max(lineHeight, (effectCount + 1) * lineHeight)));
			}
			case "armor_durability" -> {
				int itemCount = ArmorDurabilityHudComponent.collectItemCount(client.player);
				if (itemCount < 1) {
					itemCount = ArmorDurabilityHudComponent.getPreviewItemCount();
				}
				ArmorDurabilityHudComponent.Dims dims = ArmorDurabilityHudComponent.scaledDimensions(client, itemCount);
				yield new WidgetLayout(this.layoutVersion, List.of(), new Size(dims.width(), dims.height()));
			}
			case "item_counter" -> {
				LocalPlayer player = client.player;
				List<String> lines = getWidgetLines(client, widget);
				int linesCount = player != null ? lines.size() : 1;
				yield new WidgetLayout(this.layoutVersion, buildCachedLines(client, lines, old),
					new Size(90, Math.max(1, linesCount) * lineHeight));
			}
			case "array_list" -> {
				List<String> enabledNames = getEnabledModuleTitles();
				yield new WidgetLayout(this.layoutVersion, List.of(), new Size(
					ArrayListHudComponent.calculateWidth(client, enabledNames),
					ArrayListHudComponent.calculateHeight(client, enabledNames)
				));
			}
			default -> {
				List<String> lines = getWidgetLines(client, widget);
				List<CachedLine> cachedLines = buildCachedLines(client, lines, old);
				int width = 0;
				for (CachedLine line : cachedLines) {
					width = Math.max(width, line.width);
				}
				int height = cachedLines.size() * lineHeight;
				yield new WidgetLayout(this.layoutVersion, cachedLines, new Size(width + 2, Math.max(height, client.font.lineHeight)));
			}
		};
	}

	/**
	 * Converts freshly computed widget line strings into reusable cached lines,
	 * reusing the previous frame's measured width/Component when the text is
	 * unchanged so no allocation or font measurement happens for static lines.
	 */
	private List<CachedLine> buildCachedLines(Minecraft client, List<String> lines, WidgetLayout old) {
		List<CachedLine> oldLines = old == null ? List.of() : old.lines;
		List<CachedLine> result = new ArrayList<>(lines.size());
		for (int i = 0; i < lines.size(); i++) {
			String text = lines.get(i);
			if (i < oldLines.size() && text.equals(oldLines.get(i).text)) {
				result.add(oldLines.get(i));
			} else {
				result.add(new CachedLine(text, client.font.width(text)));
			}
		}
		return result;
	}

	private List<String> getWidgetLines(Minecraft client, WidgetState widget) {
		LocalPlayer player = client.player;
		ServerData server = client.getCurrentServer();

		return switch (widget.id()) {
			case "coords" -> player == null
				? List.of("XYZ -- / -- / --")
				: List.of(String.format(Locale.ROOT, "XYZ %.1f / %.1f / %.1f", player.getX(), player.getY(), player.getZ()));
			case "fps" -> List.of("FPS " + client.getFps());
			case "ping" -> List.of("Ping " + getPingText(server, client));
			case "memory" -> List.of(getMemoryUsageText());
			case "direction" -> player == null
				? List.of("Facing --", "Axis --")
				: List.of("Facing " + getFacingText(player), "Axis " + getFacingAxis(player));
			case "biome" -> player == null
				? List.of("Biome Unknown")
				: List.of("Biome " + getBiomeText(player));
			case "saturation" -> player == null
				? List.of("Hunger --", "Saturation --")
				: List.of(
					"Hunger " + player.getFoodData().getFoodLevel(),
					String.format(Locale.ROOT, "Saturation %.1f", player.getFoodData().getSaturationLevel())
				);
			case "day_counter" -> client.level == null ? List.of("Day --") : List.of("Day " + getDayCount(client));
			case "performance" -> List.of(
				"FPS " + client.getFps(),
				"Ping " + getPingText(server, client),
				String.format(Locale.ROOT, "Speed %.2f b/s", this.horizontalSpeed)
			);
			case "clock" -> client.level == null ? List.of("Time --:--") : List.of("Time " + formatWorldTime(client));
			case "server" -> List.of(getServerTitle(server, client), getServerSubtitle(server, client));
			case "cps" -> List.of("CPS " + this.leftClicks.size() + " | " + this.rightClicks.size());
			case "potion_status" -> player == null ? List.of("No Effects") : getPotionLines(player);
			case "combo" -> List.of("Combo " + this.comboCount);
			case "reach" -> this.lastReach > 0.0D
				? List.of(String.format(Locale.ROOT, "Reach %.2f", this.lastReach))
				: List.of("Reach --");
			case "item_counter" -> player == null ? List.of("Items --") : getItemCounterLines(player);
			case "pack_display" -> List.of("Pack " + getActivePackName(client));
			case "minimap" -> List.of("MiniMap");
			case "timers" -> {
				long elapsed = this.timerRunning
					? this.timerElapsedMs + (System.currentTimeMillis() - this.timerStartMs)
					: this.timerElapsedMs;
				yield List.of(
					"Timer " + formatTimerMs(elapsed),
					"Gen " + formatGeneratorTimer()
				);
			}
			case "timelapse_info" -> {
				if (!this.timelapseRunning) {
					yield List.of("Timelapse --", "Status Idle");
				}
				long elapsed = System.currentTimeMillis() - this.timelapseStartMs;
				String lockInfo = this.timelapsePositionLocked ? "Locked" : "Free";
				yield List.of(
					"Timelapse " + formatTimerMs(elapsed),
					"Status " + lockInfo + (this.timelapseCompassLock >= 0 ? " N" + this.timelapseCompassLock : "")
				);
			}
			case "build_height" -> BuildHeightModule.getLines(player);
			case "measurements" -> BlockMeasurementModule.getLines();
			case "ping_graph" -> {
				if (this.pingHistory.isEmpty()) {
					yield List.of("Ping --", "Avg --", "Min --", "Max --");
				}
				int last = this.pingHistory.get(this.pingHistory.size() - 1);
				int sum = 0, min = Integer.MAX_VALUE, max = 0;
				for (int p : this.pingHistory) {
					sum += p;
					min = Math.min(min, p);
					max = Math.max(max, p);
				}
				int avg = sum / this.pingHistory.size();
				yield List.of(
					"Ping " + last + "ms",
					"Avg " + avg + "ms",
					"Min " + min + "ms",
					"Max " + max + "ms"
				);
			}
			case "tps_display" -> {
				yield List.of(
					String.format(Locale.ROOT, "TPS %.1f", this.estimatedTps),
					"Estimate only"
				);
			}
			case "palette" -> BlockPaletteModule.getLines(client);
			case "materials" -> MaterialCalculatorModule.getLines();
			case "shape_preview" -> ShapePreviewModule.getLines();
			case "blueprint_preview" -> BlueprintPreviewModule.getLines();
			default -> List.of(widget.title());
		};
	}

	private String getMemoryUsageText() {
		Runtime runtime = Runtime.getRuntime();
		long usedBytes = runtime.totalMemory() - runtime.freeMemory();
		long maxBytes = runtime.maxMemory();
		return String.format(Locale.ROOT, "Memory %d / %d MB", bytesToMiB(usedBytes), bytesToMiB(maxBytes));
	}

	private List<String> getPotionLines(LocalPlayer player) {
		List<String> lines = new ArrayList<>();
		for (MobEffectInstance effect : player.getActiveEffects()) {
			String rawKey = effect.getEffect().unwrapKey()
				.map(key -> extractKeyPath(key.toString()))
				.orElse("Unknown");
			String name = formatIdentifier(rawKey);
			int ticks = effect.getDuration();
			if (ticks > 20000000) {
				lines.add(name + " **:**");
			} else {
				int seconds = ticks / 20;
				int minutes = seconds / 60;
				seconds = seconds % 60;
				lines.add(String.format(Locale.ROOT, "%s %d:%02d", name, minutes, seconds));
			}
		}
		if (lines.isEmpty()) {
			lines.add("No Effects");
		}
		return lines;
	}

	private List<String> getItemCounterLines(LocalPlayer player) {
		String[][] tracked = {
			{"golden_apple", "GApple"},
			{"enchanted_golden_apple", "EGApple"},
			{"ender_pearl", "Pearls"},
			{"arrow", "Arrows"},
			{"totem_of_undying", "Totems"}
		};
		List<String> lines = new ArrayList<>();
		for (String[] entry : tracked) {
			int count = 0;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(entry[0])) {
					count += stack.getCount();
				}
			}
			if (count > 0) {
				lines.add(entry[1] + ": " + count);
			}
		}
		if (lines.isEmpty()) {
			lines.add("No tracked items");
		}
		return lines;
	}

	private String getActivePackName(Minecraft client) {
		try {
			var selected = client.getResourcePackRepository().getSelectedPacks();
			for (var pack : selected) {
				String id = pack.getId();
				if (!"vanilla".equals(id) && !"fabric".equals(id) && !id.startsWith("file/")) {
					return pack.getTitle().getString();
				}
			}
			for (var pack : selected) {
				String id = pack.getId();
				if (!"vanilla".equals(id) && !"fabric".equals(id)) {
					String name = pack.getTitle().getString();
					if (name != null && !name.isBlank()) {
						return name;
					}
				}
			}
		} catch (Exception e) {
			ForkClient.LOGGER.debug("Failed to get active resource pack name", e);
		}
		return "Default";
	}

	private String formatTimerMs(long elapsedMs) {
		long totalSeconds = elapsedMs / 1000L;
		long minutes = totalSeconds / 60L;
		long seconds = totalSeconds % 60L;
		long tenths = (elapsedMs % 1000L) / 100L;
		return String.format(Locale.ROOT, "%02d:%02d.%d", minutes, seconds, tenths);
	}

	private String formatGeneratorTimer() {
		long elapsed = (System.currentTimeMillis() / 1000L) % 40L;
		long remaining = 40L - elapsed;
		return String.format(Locale.ROOT, "%ds", remaining);
	}

	private long bytesToMiB(long bytes) {
		return bytes / (1024L * 1024L);
	}

	private String getFacingText(LocalPlayer player) {
		return switch (Math.floorMod(Math.round(player.getVisualRotationYInDegrees() / 90.0F), 4)) {
			case 0 -> "South";
			case 1 -> "West";
			case 2 -> "North";
			default -> "East";
		};
	}

	private String getFacingAxis(LocalPlayer player) {
		return switch (Math.floorMod(Math.round(player.getVisualRotationYInDegrees() / 90.0F), 4)) {
			case 0 -> "+Z";
			case 1 -> "-X";
			case 2 -> "-Z";
			default -> "+X";
		};
	}

	private String getBiomeText(LocalPlayer player) {
		return player.level()
			.getBiome(player.blockPosition())
			.unwrapKey()
			.map(key -> formatIdentifier(extractKeyPath(key.toString())))
			.orElse("Unknown");
	}

	private String extractKeyPath(String keyText) {
		String path = keyText;
		int slash = path.lastIndexOf('/');
		if (slash >= 0) {
			path = path.substring(slash + 1);
		}
		int colon = path.lastIndexOf(':');
		if (colon >= 0) {
			path = path.substring(colon + 1);
		}
		int bracket = path.indexOf(']');
		if (bracket >= 0) {
			path = path.substring(0, bracket);
		}
		return path.trim();
	}

	private String formatIdentifier(String value) {
		if (value == null || value.isBlank()) {
			return "Unknown";
		}

		String[] parts = value.split("_");
		StringBuilder builder = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (builder.length() > 0) {
				builder.append(' ');
			}
			builder.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				builder.append(part.substring(1));
			}
		}
		return builder.isEmpty() ? "Unknown" : builder.toString();
	}

	private String getPingText(ServerData server, Minecraft client) {
		if (client.hasSingleplayerServer() || server == null) {
			return "Local";
		}

		return Math.max(0L, server.ping) + "ms";
	}

	private long getDayCount(Minecraft client) {
		return client.level == null ? 0L : client.level.getLevelData().getGameTime() / 24000L;
	}

	private String formatWorldTime(Minecraft client) {
		if (client.level == null) {
			return "--:--";
		}

		long dayTime = client.level.getLevelData().getGameTime() % 24000L;
		long hours = (dayTime / 1000L + 6L) % 24L;
		long minutes = (dayTime % 1000L) * 60L / 1000L;
		return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
	}

	private String getServerTitle(ServerData server, Minecraft client) {
		if (client.hasSingleplayerServer()) {
			return "Singleplayer";
		}
		if (server == null) {
			return "Main Menu";
		}
		return server.name == null || server.name.isBlank() ? "Multiplayer" : server.name;
	}

	private String getServerSubtitle(ServerData server, Minecraft client) {
		if (client.hasSingleplayerServer()) {
			return "Integrated world";
		}
		if (server == null) {
			return "Not connected";
		}
		return server.ip == null || server.ip.isBlank() ? "Unknown endpoint" : server.ip;
	}

	private List<String> getEnabledModuleTitles() {
		// Cache the sorted list of enabled module titles; it only changes when a
		// module is toggled (generation counter), so this avoids re-running the
		// stream/filter/sort pipeline once per widget per frame (the array list
		// widget alone requested it up to three times per frame).
		if (this.enabledTitlesGeneration != this.moduleStateGeneration) {
			this.cachedEnabledTitles = this.modules.stream()
				.filter(module -> isModuleEnabled(module.id()))
				.filter(module -> module.category() != ModuleCategory.HUD)
				.map(ModuleDefinition::title)
				.sorted(Comparator.comparingInt(String::length).reversed())
				.toList();
			this.enabledTitlesGeneration = this.moduleStateGeneration;
		}
		return this.cachedEnabledTitles;
	}

	public List<String> enabledModuleTitles() {
		return getEnabledModuleTitles();
	}

	public List<ModuleCategory> categories() {
		return List.of(ModuleCategory.values());
	}

	public ModuleCategory selectedCategory() {
		return this.selectedCategory;
	}

	public void selectCategory(ModuleCategory category) {
		this.selectedCategory = category;
		saveConfig();
	}

	public List<ModuleDefinition> allModules() {
		return Collections.unmodifiableList(this.modules);
	}

	public List<ModuleDefinition> allModulesFiltered() {
		String query = this.searchQuery.toLowerCase(Locale.ROOT);
		if (query.isEmpty()) return this.modules;
		return this.modules.stream()
			.filter(module -> module.title().toLowerCase(Locale.ROOT).contains(query)
				|| module.id().toLowerCase(Locale.ROOT).contains(query))
			.toList();
	}

	public String getSearchQuery() {
		return this.searchQuery;
	}

	public void setSearchQuery(String query) {
		this.searchQuery = query == null ? "" : query;
	}

	public String getSelectedProfile() {
		return this.selectedProfile;
	}

	public void setSelectedProfile(String profile) {
		this.selectedProfile = profile;
		saveConfig();
	}

	public List<String> getProfiles() {
		return Collections.unmodifiableList(this.profiles);
	}

	public void addProfile(String name) {
		if (!this.profiles.contains(name)) {
			this.profiles.add(name);
		}
		this.selectedProfile = name;
		saveConfig();
	}

	public GuiTab selectedTopTab() {
		return this.selectedTopTab;
	}

	public void selectTopTab(GuiTab tab) {
		this.selectedTopTab = tab;
	}

	public boolean isSettingOn(String id) {
		return this.settingsStates.getOrDefault(id, false);
	}

	public void toggleSetting(String id) {
		this.settingsStates.put(id, !this.settingsStates.getOrDefault(id, false));
		saveConfig();
	}

	// ==================== WAYPOINTS ====================

	public List<Waypoint> getWaypoints() {
		return Collections.unmodifiableList(this.waypoints);
	}

	public void addWaypointAtPlayer() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) return;
		String name = "Waypoint " + (this.waypoints.size() + 1);
		int x = (int) Math.floor(client.player.getX());
		int y = (int) Math.floor(client.player.getY());
		int z = (int) Math.floor(client.player.getZ());
		String dim = client.level.dimension().identifier().toString();
		int color = WAYPOINT_COLORS[this.waypoints.size() % WAYPOINT_COLORS.length];
		this.waypoints.add(new Waypoint(name, x, y, z, dim, color, true));
		pushNotification("Waypoint added: " + name + " (" + x + ", " + y + ", " + z + ")", color, 3000L);
		saveConfig();
	}

	public void addWaypoint(String name, int x, int y, int z, String dimension, int color) {
		this.waypoints.add(new Waypoint(name, x, y, z, dimension, color, true));
		saveConfig();
	}

	public void removeWaypoint(int index) {
		if (index >= 0 && index < this.waypoints.size()) {
			Waypoint removed = this.waypoints.remove(index);
			pushNotification("Waypoint removed: " + removed.name(), 0xFFF44336, 2500L);
			saveConfig();
		}
	}

	public void toggleWaypoint(int index) {
		if (index >= 0 && index < this.waypoints.size()) {
			Waypoint wp = this.waypoints.get(index);
			this.waypoints.set(index, wp.withEnabled(!wp.enabled()));
			saveConfig();
		}
	}

	public double distanceToWaypoint(Waypoint wp) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return -1;
		double dx = wp.x() - client.player.getX();
		double dy = wp.y() - client.player.getY();
		double dz = wp.z() - client.player.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	// ==================== FRIEND LIST ====================

	public List<String> getFriendList() {
		return Collections.unmodifiableList(this.friendList);
	}

	public void addFriend(String name) {
		if (!this.friendList.contains(name)) {
			this.friendList.add(name);
			pushNotification("Added friend: " + name, ENABLED_ACCENT, 2500L);
			saveConfig();
		}
	}

	public void removeFriend(String name) {
		if (this.friendList.remove(name)) {
			pushNotification("Removed friend: " + name, 0xFFF44336, 2500L);
			saveConfig();
		}
	}

	// ==================== SERVER BOOKMARKS ====================

	public List<String[]> getServerBookmarks() {
		return Collections.unmodifiableList(this.serverBookmarks);
	}

	public void addServerBookmark(String name, String ip, String port, String notes) {
		this.serverBookmarks.add(new String[]{name, ip, port, notes});
		pushNotification("Bookmarked: " + name, ENABLED_ACCENT, 2500L);
		saveConfig();
	}

	public void removeServerBookmark(int index) {
		if (index >= 0 && index < this.serverBookmarks.size()) {
			String[] removed = this.serverBookmarks.remove(index);
			pushNotification("Removed bookmark: " + removed[0], 0xFFF44336, 2500L);
			saveConfig();
		}
	}

	// ==================== PLAYER NOTES ====================

	public List<String[]> getPlayerNotes() {
		return Collections.unmodifiableList(this.playerNotes);
	}

	public void addPlayerNote(String player, String text, String tags) {
		this.playerNotes.add(new String[]{player, text, tags});
		pushNotification("Note added for: " + player, ENABLED_ACCENT, 2500L);
		saveConfig();
	}

	public void removePlayerNote(int index) {
		if (index >= 0 && index < this.playerNotes.size()) {
			String[] removed = this.playerNotes.remove(index);
			pushNotification("Note removed: " + removed[0], 0xFFF44336, 2500L);
			saveConfig();
		}
	}

	// ==================== QUICK MESSAGES ====================

	public List<String> getQuickMessages() {
		return Collections.unmodifiableList(this.quickMessages);
	}

	public void addQuickMessage(String message) {
		if (this.quickMessages.size() < 9) {
			this.quickMessages.add(message);
			saveConfig();
		}
	}

	public void removeQuickMessage(int index) {
		if (index >= 0 && index < this.quickMessages.size()) {
			this.quickMessages.remove(index);
			saveConfig();
		}
	}

	// ==================== TIMELAPSE ====================

	public boolean isTimelapseRunning() {
		return this.timelapseRunning;
	}

	public void toggleTimelapsePositionLock() {
		this.timelapsePositionLocked = !this.timelapsePositionLocked;
		pushUtilityNotification("Position " + (this.timelapsePositionLocked ? "locked" : "unlocked"), PANEL_OUTLINE);
	}

	public void setTimelapseCompassLock(int direction) {
		this.timelapseCompassLock = this.timelapseCompassLock == direction ? -1 : direction;
		String label = this.timelapseCompassLock >= 0 ? "N" + this.timelapseCompassLock : "Free";
		pushUtilityNotification("Compass: " + label, PANEL_OUTLINE);
	}

	// ==================== CAMERA PATH ====================

	public boolean isCameraPathRecording() {
		return this.cameraPathRecording;
	}

	public void startCameraPathPlayback() {
		if (!this.cameraPathPoints.isEmpty() && !this.cameraPathRecording) {
			this.cameraPathPlaying = true;
			this.cameraPathIndex = 0;
			this.cameraPathLastTick = System.currentTimeMillis();
			pushUtilityNotification("Camera path playback started", PANEL_OUTLINE);
		}
	}

	public void stopCameraPathPlayback() {
		this.cameraPathPlaying = false;
		this.cameraPathIndex = 0;
	}

	public List<float[]> getCameraPathPoints() {
		return Collections.unmodifiableList(this.cameraPathPoints);
	}

	// ==================== MULTIPLAYER ====================

	public List<Integer> getPingHistory() {
		return Collections.unmodifiableList(this.pingHistory);
	}

	public float getEstimatedTps() {
		return this.estimatedTps;
	}

	public boolean isHudHidden() {
		return this.hudHidden;
	}

	public void toggleHudHidden() {
		this.hudHidden = !this.hudHidden;
	}

	public WidgetState findWidgetAt(double mouseX, double mouseY) {
		Minecraft client = Minecraft.getInstance();
		for (int i = this.widgets.size() - 1; i >= 0; i--) {
			WidgetState widget = this.widgets.get(i);
			if (isWithinWidget(client, widget, mouseX, mouseY)) {
				return widget;
			}
		}
		return null;
	}

	private boolean isWithinWidget(Minecraft client, WidgetState widget, double mouseX, double mouseY) {
		Size size = measureWidget(client, widget);
		return mouseX >= widget.x() - 3
			&& mouseX < widget.x() + size.width + 3
			&& mouseY >= widget.y() - 3
			&& mouseY < widget.y() + size.height + 3;
	}

	public boolean isModuleEnabled(String id) {
		return this.moduleStates.getOrDefault(id, false);
	}

	public KeyMapping.Category getKeyCategory() {
		return this.keyCategory;
	}

	public float getModuleFloatSetting(String moduleId, String settingId, float defaultValue) {
		Map<String, Float> settings = this.moduleFloatSettings.get(moduleId);
		if (settings == null) {
			return defaultValue;
		}
		return settings.getOrDefault(settingId, defaultValue);
	}

	public void setModuleFloatSetting(String moduleId, String settingId, float value) {
		this.moduleFloatSettings.computeIfAbsent(moduleId, k -> new LinkedHashMap<>()).put(settingId, value);
		saveConfig();
	}

	public String getModuleStringSetting(String moduleId, String settingId, String defaultValue) {
		Map<String, String> settings = this.moduleStringSettings.get(moduleId);
		if (settings == null) {
			return defaultValue;
		}
		return settings.getOrDefault(settingId, defaultValue);
	}

	public void setModuleStringSetting(String moduleId, String settingId, String value) {
		this.moduleStringSettings.computeIfAbsent(moduleId, k -> new LinkedHashMap<>()).put(settingId, value);
		saveConfig();
	}

	public WidgetState getWidgetById(String id) {
		return this.widgetsById.get(id);
	}

	public void toggleModule(String id) {
		boolean enabled = !isModuleEnabled(id);
		ModuleDefinition def = this.modulesById.get(id);

		if (enabled && MOVEMENT_MODULES.contains(id) && !FeaturePermissions.canUseMovement()) {
			String msg = FeaturePermissions.getDeniedMessage(FeaturePermissions.PermissionFlag.MOVEMENT).getString();
			pushNotification(msg, 0xFFFF6B6B, 4000L);
			addRecentAlert("Movement modifications blocked - server has not opted in");
			return;
		}

		if (enabled && def != null && def.category() == ModuleCategory.VISUAL && !FeaturePermissions.canUseRendering()) {
			String msg = FeaturePermissions.getDeniedMessage(FeaturePermissions.PermissionFlag.RENDERING).getString();
			pushNotification(msg, 0xFFFF6B6B, 4000L);
			addRecentAlert("Rendering modifications blocked - server has not opted in");
			return;
		}

		if (enabled && def != null && def.category() == ModuleCategory.UTILITY && !FeaturePermissions.canUseUtilities()) {
			String msg = FeaturePermissions.getDeniedMessage(FeaturePermissions.PermissionFlag.UTILITY).getString();
			pushNotification(msg, 0xFFFF6B6B, 4000L);
			addRecentAlert("Utility modifications blocked - server has not opted in");
			return;
		}

		if (enabled && def != null && def.category() == ModuleCategory.CONTENT && !FeaturePermissions.canUseRendering()) {
			String msg = FeaturePermissions.getDeniedMessage(FeaturePermissions.PermissionFlag.RENDERING).getString();
			pushNotification(msg, 0xFFFF6B6B, 4000L);
			addRecentAlert("Content creation features blocked - server has not opted in");
			return;
		}

		if (enabled && def != null && def.category() == ModuleCategory.BUILDING && !FeaturePermissions.canUseRendering()) {
			String msg = FeaturePermissions.getDeniedMessage(FeaturePermissions.PermissionFlag.RENDERING).getString();
			pushNotification(msg, 0xFFFF6B6B, 4000L);
			addRecentAlert("Building helpers blocked - server has not opted in");
			return;
		}

		if (enabled && def != null && def.category() == ModuleCategory.MULTIPLAYER && !FeaturePermissions.canUseUtilities()) {
			String msg = FeaturePermissions.getDeniedMessage(FeaturePermissions.PermissionFlag.UTILITY).getString();
			pushNotification(msg, 0xFFFF6B6B, 4000L);
			addRecentAlert("Multiplayer utilities blocked - server has not opted in");
			return;
		}

		this.moduleStates.put(id, enabled);
		markModuleStatesChanged();

		if (!enabled && "toggle_sneak".equals(id)) {
			this.toggleSneakLatched = false;
		}

		if (!enabled && MODULE_BRIGHTNESS_PLUS.equals(id)) {
			Minecraft client = Minecraft.getInstance();
			if (client != null) {
				enforceBrightnessPlus(client);
			}
		}

		if (!enabled && "zoom".equals(id)) {
			restoreZoom(Minecraft.getInstance());
		}

		if (!enabled && "freelook".equals(id)) {
			restoreFreelook(Minecraft.getInstance());
		}

		if (enabled && "auto_chat_messages".equals(id)) {
			this.autoChatMessageIndex = 0;
			this.nextAutoChatMessageAt = System.currentTimeMillis() + AUTO_CHAT_JOIN_DELAY_MS;
		}

		applyWidgetModuleState(id, enabled);

		if (isModuleEnabled("module_toggle_alerts")) {
			notifyModuleToggle(id, enabled);
		}

		ModuleDefinition module = this.modulesById.get(id);
		String title = module == null ? id : module.title();
		addRecentAlert(title + (enabled ? " Enabled" : " Disabled"));

		saveConfig();
	}

	public void toggleWidget(WidgetState widget) {
		widget.setEnabled(!widget.enabled());
		String moduleId = getWidgetModuleId(widget.id());
		if (moduleId != null) {
			this.moduleStates.put(moduleId, widget.enabled());
			markModuleStatesChanged();
		}
		// Re-clamp immediately when shown so a widget enabled after a window
		// resize always appears inside the viewport (matches old per-frame clamp).
		if (widget.enabled()) {
			clampWidgetToViewport(Minecraft.getInstance(), widget);
		}
		addRecentAlert(widget.title() + (widget.enabled() ? " Shown" : " Hidden"));
		if (isModuleEnabled("module_toggle_alerts")) {
			pushNotification(
				"[HUD] " + widget.title() + (widget.enabled() ? " shown" : " hidden"),
				widget.enabled() ? PANEL_OUTLINE : DISABLED_ACCENT,
				3000L
			);
		}
		saveConfig();
	}

	public void moveWidget(WidgetState widget, int x, int y) {
		widget.setX(x);
		widget.setY(y);
		clampWidgetToViewport(Minecraft.getInstance(), widget);
	}

	public void pushNotification(String message, int color) {
		pushNotification(message, color, 2400L);
	}

	public void pushNotification(String message, int color, long durationMs) {
		while (this.notifications.size() >= 10) {
			long now = System.currentTimeMillis();
			boolean removedExpired = false;
			var iter = this.notifications.iterator();
			while (iter.hasNext()) {
				if (iter.next().expiresAt < now) {
					iter.remove();
					removedExpired = true;
					break;
				}
			}
			if (!removedExpired) {
				this.notifications.removeFirst();
			}
		}
		this.notifications.addLast(new Notification(message, color, System.currentTimeMillis() + durationMs));
	}

	private void notifyModuleToggle(String id, boolean enabled) {
		ModuleDefinition module = this.modulesById.get(id);
		String title = module == null ? id : module.title();
		String scope = module == null ? "Module" : module.category().label();
		int color = enabled ? getCategoryColor(module == null ? ModuleCategory.UTILITY : module.category()) : DISABLED_ACCENT;
		pushNotification("[" + scope + "] " + title + (enabled ? " enabled" : " disabled"), color, 3200L);
	}

	private int getCategoryColor(ModuleCategory category) {
		return switch (category) {
			case HUD -> 0xFF55D0FF;
			case VISUAL -> 0xFFFF74D4;
			case MOVEMENT -> 0xFF7CFF74;
			case UTILITY -> 0xFFFFD45E;
			case COMBAT -> 0xFFFF6B6B;
			case CONTENT -> 0xFFCF6BEF;
			case BUILDING -> 0xFFEFC86B;
			case MULTIPLAYER -> 0xFF6BD1EF;
		};
	}

	private static int withAlpha(int rgb, int alpha) {
		return (rgb & 0x00FFFFFF) | (alpha << 24);
	}

	private static int lighten(int color, float amount) {
		int a = (color >>> 24) & 0xFF;
		int r = (color >>> 16) & 0xFF;
		int g = (color >>> 8) & 0xFF;
		int b = color & 0xFF;
		r += Math.round((255 - r) * amount);
		g += Math.round((255 - g) * amount);
		b += Math.round((255 - b) * amount);
		return (a << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
	}

	public void saveConfig() {
		syncModuleStatesToWidgets();
		if (this.configPath == null) {
			Minecraft client = Minecraft.getInstance();
			this.configPath = client.gameDirectory.toPath().resolve("config").resolve("fork-client.properties");
		}

		Properties properties = new Properties();
		properties.setProperty("selected_category", this.selectedCategory.name());

		for (ModuleDefinition module : this.modules) {
			properties.setProperty("module." + module.id(), Boolean.toString(isModuleEnabled(module.id())));
		}

		for (WidgetState widget : this.widgets) {
			properties.setProperty("widget." + widget.id() + ".x", Integer.toString(widget.x()));
			properties.setProperty("widget." + widget.id() + ".y", Integer.toString(widget.y()));
			properties.setProperty("widget." + widget.id() + ".enabled", Boolean.toString(widget.enabled()));
		}

		for (var entry : this.settingsStates.entrySet()) {
			properties.setProperty("setting." + entry.getKey(), Boolean.toString(entry.getValue()));
		}

		for (var moduleEntry : this.moduleFloatSettings.entrySet()) {
			for (var settingEntry : moduleEntry.getValue().entrySet()) {
				properties.setProperty("float." + moduleEntry.getKey() + "." + settingEntry.getKey(),
					Float.toString(settingEntry.getValue()));
			}
		}

		for (var moduleEntry : this.moduleStringSettings.entrySet()) {
			for (var settingEntry : moduleEntry.getValue().entrySet()) {
				properties.setProperty("str." + moduleEntry.getKey() + "." + settingEntry.getKey(),
					settingEntry.getValue());
			}
		}

		properties.setProperty("waypoint.count", Integer.toString(this.waypoints.size()));
		for (int i = 0; i < this.waypoints.size(); i++) {
			Waypoint wp = this.waypoints.get(i);
			String prefix = "waypoint." + i + ".";
			properties.setProperty(prefix + "name", wp.name());
			properties.setProperty(prefix + "x", Integer.toString(wp.x()));
			properties.setProperty(prefix + "y", Integer.toString(wp.y()));
			properties.setProperty(prefix + "z", Integer.toString(wp.z()));
			properties.setProperty(prefix + "dimension", wp.dimension());
			properties.setProperty(prefix + "color", Integer.toString(wp.color()));
			properties.setProperty(prefix + "enabled", Boolean.toString(wp.enabled()));
		}

		// ── Friend list ─────────────────────────────────────────────────
		properties.setProperty("friend.count", Integer.toString(this.friendList.size()));
		for (int i = 0; i < this.friendList.size(); i++) {
			properties.setProperty("friend." + i, this.friendList.get(i));
		}

		// ── Server bookmarks ────────────────────────────────────────────
		properties.setProperty("bookmark.count", Integer.toString(this.serverBookmarks.size()));
		for (int i = 0; i < this.serverBookmarks.size(); i++) {
			String[] bm = this.serverBookmarks.get(i);
			String prefix = "bookmark." + i + ".";
			properties.setProperty(prefix + "name", bm.length > 0 ? bm[0] : "");
			properties.setProperty(prefix + "ip", bm.length > 1 ? bm[1] : "");
			properties.setProperty(prefix + "port", bm.length > 2 ? bm[2] : "25565");
			properties.setProperty(prefix + "notes", bm.length > 3 ? bm[3] : "");
		}

		// ── Player notes ────────────────────────────────────────────────
		properties.setProperty("note.count", Integer.toString(this.playerNotes.size()));
		for (int i = 0; i < this.playerNotes.size(); i++) {
			String[] note = this.playerNotes.get(i);
			String prefix = "note." + i + ".";
			properties.setProperty(prefix + "player", note.length > 0 ? note[0] : "");
			properties.setProperty(prefix + "text", note.length > 1 ? note[1] : "");
			properties.setProperty(prefix + "tags", note.length > 2 ? note[2] : "");
		}

		// ── Quick messages ──────────────────────────────────────────────
		properties.setProperty("quickmsg.count", Integer.toString(this.quickMessages.size()));
		for (int i = 0; i < this.quickMessages.size(); i++) {
			properties.setProperty("quickmsg." + i, this.quickMessages.get(i));
		}

		// ── Block palette ───────────────────────────────────────────────
		java.util.List<String[]> paletteData = BlockPaletteModule.savePalette();
		properties.setProperty("palette.count", Integer.toString(paletteData.size()));
		for (int i = 0; i < paletteData.size(); i++) {
			String[] entry = paletteData.get(i);
			String prefix = "palette." + i + ".";
			properties.setProperty(prefix + "block", entry.length > 0 ? entry[0] : "");
			properties.setProperty(prefix + "group", entry.length > 1 ? entry[1] : "");
			properties.setProperty(prefix + "fav", entry.length > 2 ? entry[2] : "false");
		}

		// ── Brightness Plus ─────────────────────────────────────────────
		properties.setProperty("brightness_plus.value", Double.toString(this.brightnessPlusValue));
		properties.setProperty("brightness_plus.min", Double.toString(this.brightnessPlusMin));
		properties.setProperty("brightness_plus.max", Double.toString(this.brightnessPlusMax));
		properties.setProperty("brightness_plus.step", Double.toString(this.brightnessPlusStep));

		try {
			Files.createDirectories(this.configPath.getParent());
			Path tempPath = this.configPath.resolveSibling(this.configPath.getFileName() + ".tmp");
			try (OutputStream stream = Files.newOutputStream(tempPath)) {
				properties.store(stream, "Fork Client settings");
			}
			try {
				Files.move(tempPath, this.configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(tempPath, this.configPath, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			ForkClient.LOGGER.error("Failed to save Fork Client config", exception);
		}
	}

	private void loadConfig() {
		Minecraft client = Minecraft.getInstance();
		this.configPath = client.gameDirectory.toPath().resolve("config").resolve("fork-client.properties");

		if (!Files.exists(this.configPath)) {
			saveConfig();
			return;
		}

		Properties properties = new Properties();
		try (InputStream stream = Files.newInputStream(this.configPath)) {
			properties.load(stream);
		} catch (IOException exception) {
			ForkClient.LOGGER.error("Failed to load Fork Client config", exception);
			return;
		}

		String category = properties.getProperty("selected_category");
		if (category != null) {
			try {
				this.selectedCategory = ModuleCategory.valueOf(category);
			} catch (IllegalArgumentException ignored) {
				this.selectedCategory = ModuleCategory.HUD;
			}
		}

		for (ModuleDefinition module : this.modules) {
			String value = properties.getProperty("module." + module.id());
			if (value != null) {
				this.moduleStates.put(module.id(), Boolean.parseBoolean(value));
			}
		}

		for (WidgetState widget : this.widgets) {
			String x = properties.getProperty("widget." + widget.id() + ".x");
			String y = properties.getProperty("widget." + widget.id() + ".y");
			String enabled = properties.getProperty("widget." + widget.id() + ".enabled");

			if (x != null) {
				widget.setX(parseInt(x, widget.x()));
			}
			if (y != null) {
				widget.setY(parseInt(y, widget.y()));
			}
			if (enabled != null) {
				widget.setEnabled(Boolean.parseBoolean(enabled));
			}

			clampWidgetToViewport(client, widget);
		}

		for (String key : properties.stringPropertyNames()) {
			if (key.startsWith("setting.")) {
				String settingId = key.substring("setting.".length());
				this.settingsStates.put(settingId, Boolean.parseBoolean(properties.getProperty(key)));
			}
		}

		for (String key : properties.stringPropertyNames()) {
			if (key.startsWith("float.")) {
				String remainder = key.substring("float.".length());
				int dot = remainder.indexOf('.');
				if (dot > 0) {
					String moduleId = remainder.substring(0, dot);
					String settingId = remainder.substring(dot + 1);
					try {
						float value = Float.parseFloat(properties.getProperty(key));
						this.moduleFloatSettings.computeIfAbsent(moduleId, k -> new LinkedHashMap<>()).put(settingId, value);
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}

		for (String key : properties.stringPropertyNames()) {
			if (key.startsWith("str.")) {
				String remainder = key.substring("str.".length());
				int dot = remainder.indexOf('.');
				if (dot > 0) {
					String moduleId = remainder.substring(0, dot);
					String settingId = remainder.substring(dot + 1);
					String value = properties.getProperty(key);
					if (value != null) {
						this.moduleStringSettings.computeIfAbsent(moduleId, k -> new LinkedHashMap<>()).put(settingId, value);
					}
				}
			}
		}

		String wpCountStr = properties.getProperty("waypoint.count");
		if (wpCountStr != null) {
			int wpCount = parseInt(wpCountStr, 0);
			this.waypoints.clear();
			for (int i = 0; i < wpCount; i++) {
				String prefix = "waypoint." + i + ".";
				String name = properties.getProperty(prefix + "name", "Waypoint " + (i + 1));
				int wx = parseInt(properties.getProperty(prefix + "x", "0"), 0);
				int wy = parseInt(properties.getProperty(prefix + "y", "64"), 64);
				int wz = parseInt(properties.getProperty(prefix + "z", "0"), 0);
				String dim = properties.getProperty(prefix + "dimension", "minecraft:overworld");
				int color = parseInt(properties.getProperty(prefix + "color", Integer.toString(WAYPOINT_COLORS[i % WAYPOINT_COLORS.length])),
					WAYPOINT_COLORS[i % WAYPOINT_COLORS.length]);
				boolean enabled = Boolean.parseBoolean(properties.getProperty(prefix + "enabled", "true"));
				this.waypoints.add(new Waypoint(name, wx, wy, wz, dim, color, enabled));
			}
		}

		// ── Friend list ─────────────────────────────────────────────────
		String friendCountStr = properties.getProperty("friend.count");
		if (friendCountStr != null) {
			int count = parseInt(friendCountStr, 0);
			this.friendList.clear();
			for (int i = 0; i < count; i++) {
				String name = properties.getProperty("friend." + i);
				if (name != null && !name.isBlank()) {
					this.friendList.add(name);
				}
			}
		}

		// ── Server bookmarks ────────────────────────────────────────────
		String bookmarkCountStr = properties.getProperty("bookmark.count");
		if (bookmarkCountStr != null) {
			int count = parseInt(bookmarkCountStr, 0);
			this.serverBookmarks.clear();
			for (int i = 0; i < count; i++) {
				String prefix = "bookmark." + i + ".";
				String name = properties.getProperty(prefix + "name", "");
				String ip = properties.getProperty(prefix + "ip", "");
				String port = properties.getProperty(prefix + "port", "25565");
				String notes = properties.getProperty(prefix + "notes", "");
				this.serverBookmarks.add(new String[]{name, ip, port, notes});
			}
		}

		// ── Player notes ────────────────────────────────────────────────
		String noteCountStr = properties.getProperty("note.count");
		if (noteCountStr != null) {
			int count = parseInt(noteCountStr, 0);
			this.playerNotes.clear();
			for (int i = 0; i < count; i++) {
				String prefix = "note." + i + ".";
				String player = properties.getProperty(prefix + "player", "");
				String text = properties.getProperty(prefix + "text", "");
				String tags = properties.getProperty(prefix + "tags", "");
				this.playerNotes.add(new String[]{player, text, tags});
			}
		}

		// ── Quick messages ──────────────────────────────────────────────
		String quickmsgCountStr = properties.getProperty("quickmsg.count");
		if (quickmsgCountStr != null) {
			int count = parseInt(quickmsgCountStr, 0);
			this.quickMessages.clear();
			for (int i = 0; i < count; i++) {
				String msg = properties.getProperty("quickmsg." + i);
				if (msg != null && !msg.isBlank()) {
					this.quickMessages.add(msg);
				}
			}
		}

		// ── Brightness Plus ─────────────────────────────────────────────
		this.brightnessPlusMin = parseDouble(properties.getProperty("brightness_plus.min"), this.brightnessPlusMin);
		this.brightnessPlusMax = parseDouble(properties.getProperty("brightness_plus.max"), this.brightnessPlusMax);
		this.brightnessPlusStep = parseDouble(properties.getProperty("brightness_plus.step"), this.brightnessPlusStep);
		this.brightnessPlusValue = clampBrightness(parseDouble(properties.getProperty("brightness_plus.value"), this.brightnessPlusValue));

		// ── Block palette ───────────────────────────────────────────────
		java.util.List<String[]> paletteData = new java.util.ArrayList<>();
		String paletteCountStr = properties.getProperty("palette.count");
		if (paletteCountStr != null) {
			int count = parseInt(paletteCountStr, 0);
			for (int i = 0; i < count; i++) {
				String prefix = "palette." + i + ".";
				String block = properties.getProperty(prefix + "block", "");
				String group = properties.getProperty(prefix + "group", "Other");
				String fav = properties.getProperty(prefix + "fav", "false");
				if (!block.isBlank()) {
					paletteData.add(new String[]{block, group, fav});
				}
			}
		}
		BlockPaletteModule.loadPalette(paletteData);

		syncWidgetBackedModules();
	}

	private void syncWidgetBackedModules() {
		for (WidgetState widget : this.widgets) {
			String moduleId = getWidgetModuleId(widget.id());
			this.moduleStates.put(moduleId, widget.enabled());
		}
		markModuleStatesChanged();
	}

	/**
	 * Marks the cached enabled-module title list as stale. Called whenever the
	 * module state map changes so the array list widget refreshes immediately
	 * without re-sorting on every frame.
	 */
	private void markModuleStatesChanged() {
		this.moduleStateGeneration++;
	}

	private void syncModuleStatesToWidgets() {
		for (WidgetState widget : this.widgets) {
			String moduleId = getWidgetModuleId(widget.id());
			widget.setEnabled(isModuleEnabled(moduleId));
		}
	}

	private static final Map<String, String> WIDGET_TO_MODULE;
	private static final Map<String, String> MODULE_TO_WIDGET;

	static {
		Map<String, String> w2m = new LinkedHashMap<>();
		w2m.put("timelapse_info", "timelapse_helper");
		w2m.put("build_height", "build_height_indicator");
		w2m.put("measurements", "block_measurement");
		w2m.put("ping_graph", "ping_graph");
		w2m.put("tps_display", "server_tps_estimator");
		w2m.put("armor_durability", "armor_durability");
		w2m.put("palette", "block_palette_viewer");
		w2m.put("materials", "material_calculator");
		w2m.put("shape_preview", "shape_preview");
		w2m.put("blueprint_preview", "blueprint_preview");
		WIDGET_TO_MODULE = Collections.unmodifiableMap(w2m);

		Map<String, String> m2w = new LinkedHashMap<>();
		for (var entry : WIDGET_TO_MODULE.entrySet()) {
			m2w.put(entry.getValue(), entry.getKey());
		}
		MODULE_TO_WIDGET = Collections.unmodifiableMap(m2w);
	}

	private static String getWidgetModuleId(String widgetId) {
		if (WIDGET_TO_MODULE.containsKey(widgetId)) {
			return WIDGET_TO_MODULE.get(widgetId);
		}
		return widgetId + "_widget";
	}

	private void applyWidgetModuleState(String moduleId, boolean enabled) {
		String widgetId = getModuleWidgetId(moduleId);
		if (widgetId == null) {
			return;
		}

		WidgetState widget = this.widgetsById.get(widgetId);
		if (widget != null) {
			widget.setEnabled(enabled);
			markModuleStatesChanged();
			if (enabled) {
				clampWidgetToViewport(Minecraft.getInstance(), widget);
			}
		}
	}

	private static String getModuleWidgetId(String moduleId) {
		if (moduleId != null && MODULE_TO_WIDGET.containsKey(moduleId)) {
			return MODULE_TO_WIDGET.get(moduleId);
		}
		if (moduleId != null && moduleId.endsWith("_widget")) {
			return moduleId.substring(0, moduleId.length() - "_widget".length());
		}
		return null;
	}

	private void clampWidgetToViewport(Minecraft client, WidgetState widget) {
		if (client == null || client.font == null) {
			return;
		}

		if (client.getWindow().getGuiScaledWidth() <= 0 || client.getWindow().getGuiScaledHeight() <= 0) {
			return;
		}

		Size size = measureWidget(client, widget);
		int maxX = Math.max(4, client.getWindow().getGuiScaledWidth() - size.width - 4);
		int maxY = Math.max(4, client.getWindow().getGuiScaledHeight() - size.height - 4);
		widget.setX(clamp(widget.x(), 4, maxX));
		widget.setY(clamp(widget.y(), 4, maxY));
	}

	private int parseInt(String value, int fallback) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private double parseDouble(String value, double fallback) {
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException | NullPointerException ignored) {
			return fallback;
		}
	}

	public boolean isToggleSneakLatched() {
		return this.toggleSneakLatched;
	}

	private int clamp(int value, int min, int max) {
		return Math.clamp(value, min, max);
	}

	public record ModuleDefinition(String id, String title, ModuleCategory category, String description, boolean defaultEnabled) {
	}

	public enum ModuleCategory {
		HUD("HUD"),
		VISUAL("Visual"),
		MOVEMENT("Movement"),
		UTILITY("Utility"),
		COMBAT("Combat"),
		CONTENT("Content Creation"),
		BUILDING("Building Helpers"),
		MULTIPLAYER("Multiplayer Utilities");

		private final String label;

		ModuleCategory(String label) {
			this.label = label;
		}

		public String label() {
			return this.label;
		}
	}

	public enum GuiTab {
		MODS("Mods"),
		SETTINGS("Settings"),
		WAYPOINTS("Waypoints");

		private final String label;

		GuiTab(String label) {
			this.label = label;
		}

		public String label() {
			return this.label;
		}
	}

	public static final class WidgetState {
		private final String id;
		private final String title;
		private int x;
		private int y;
		private boolean enabled;

		public WidgetState(String id, String title, int x, int y, boolean enabled) {
			this.id = id;
			this.title = title;
			this.x = x;
			this.y = y;
			this.enabled = enabled;
		}

		public String id() {
			return this.id;
		}

		public String title() {
			return this.title;
		}

		public int x() {
			return this.x;
		}

		public int y() {
			return this.y;
		}

		public boolean enabled() {
			return this.enabled;
		}

		public void setX(int x) {
			this.x = x;
		}

		public void setY(int y) {
			this.y = y;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
	}

	private record Notification(String message, int color, long expiresAt) {
	}

	private record Size(int width, int height) {
	}

	/**
	 * Cached rendered text line for a HUD widget. Holding the measured width and
	 * a ready-to-draw {@link Component} lets the render loop skip font layout
	 * and object allocation entirely while the underlying string is unchanged.
	 */
	private static final class CachedLine {
		final String text;
		final int width;
		final Component component;

		CachedLine(String text, int width) {
			this.text = text;
			this.width = width;
			this.component = Component.literal(text);
		}
	}

	/**
	 * Cached layout (text lines + bounding size) for one HUD widget, valid for a
	 * single layout version (one client tick). Rebuilt lazily on the first frame
	 * of each new tick and reused for every frame until the next tick.
	 */
	private static final class WidgetLayout {
		final long version;
		final List<CachedLine> lines;
		final Size size;

		WidgetLayout(long version, List<CachedLine> lines, Size size) {
			this.version = version;
			this.lines = lines;
			this.size = size;
		}
	}

	public record Waypoint(String name, int x, int y, int z, String dimension, int color, boolean enabled) {
		public Waypoint withEnabled(boolean newEnabled) {
			return new Waypoint(this.name, this.x, this.y, this.z, this.dimension, this.color, newEnabled);
		}

		public String dimensionLabel() {
			return switch (this.dimension) {
				case "minecraft:overworld" -> "Overworld";
				case "minecraft:the_nether" -> "Nether";
				case "minecraft:the_end" -> "The End";
				default -> {
					String path = this.dimension;
					int colon = path.lastIndexOf(':');
					if (colon >= 0) path = path.substring(colon + 1);
					yield path.length() > 1
						? path.substring(0, 1).toUpperCase() + path.substring(1)
						: path.toUpperCase();
				}
			};
		}
	}
}
