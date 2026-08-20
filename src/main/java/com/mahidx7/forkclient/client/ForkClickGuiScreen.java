package com.mahidx7.forkclient.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mahidx7.forkclient.ForkClient;
import com.mahidx7.forkclient.client.hud.ArmorDurabilityHudComponent;
import com.mahidx7.forkclient.client.modules.ArmorDurabilityConfigScreen;
import com.mahidx7.forkclient.client.modules.MotionBlurPlusModule;
import org.lwjgl.glfw.GLFW;

public class ForkClickGuiScreen extends Screen {

	// ── Colour palette ────────────────────────────────────────────────────────
	private static final int BG             = 0xFF0A0D14;
	private static final int PANEL          = 0xFF0E1219;
	private static final int PANEL_HOVER    = 0xFF141922;
	private static final int SIDEBAR_BG     = 0xFF0C0F18;
	private static final int HEADER_BG      = 0xFF090C13;
	private static final int BORDER         = 0x22FFFFFF;
	private static final int BORDER_HOVER   = 0x442196F3;
	private static final int SEPARATOR      = 0x14FFFFFF;
	private static final int ACCENT         = 0xFF2BB5C8;   // cyan/teal
	private static final int ACCENT_DIM     = 0x1A2BB5C8;
	private static final int TEXT_PRIMARY   = 0xFFFFFFFF;
	private static final int TEXT_SECONDARY = 0xFFB0B8C4;
	private static final int TEXT_DIM       = 0xFF6B7280;
	private static final int TEXT_ACCENT    = 0xFF2BB5C8;
	private static final int CHECK_OFF      = 0xFF1A1F2E;
	private static final int CHECK_BORDER   = 0xFF2E3550;
	private static final int ONLINE_GREEN   = 0xFF33D17A;

	// ── Layout constants ──────────────────────────────────────────────────────
	private static final int HEADER_H       = 44;
	private static final int SIDEBAR_W      = 120;
	private static final int RIGHT_W        = 170;
	private static final int COL_GAP        = 8;
	private static final int COLS_PER_ROW   = 3;
	private static final int COL_PAD        = 10;   // padding before first column
	private static final int CAT_HDR_H      = 30;
	private static final int CAT_TOP_PAD    = 10;   // space above each column's header
	private static final int MOD_ROW_H      = 44;
	private static final int MOD_GAP        = 2;
	private static final int TOGGLE_W       = 28;
	private static final int TOGGLE_H       = 14;
	private static final int KNOB_SIZE      = 10;
	private static final int SIDEBAR_ITEM_H = 34;

	// ── Sidebar entries ───────────────────────────────────────────────────────
	private static final String[][] SIDEBAR_ITEMS = {
		{"M", "MODULES",     "MODULES"},
		{"W", "WAYPOINTS",   "WAYPOINTS"},
		{"S", "SETTINGS",    "SETTINGS"},
		{"P", "PERFORMANCE", "PERFORMANCE"},
		{"V", "VISUALS",     "VISUALS"},
		{">", "MACROS",      "MACROS"},
		{"R", "RESOURCES",   "RESOURCES"},
	};

	// ── State ─────────────────────────────────────────────────────────────────
	private double mx, my;
	/** Current pixel scroll target (snaps to this). */
	private int scrollOffset;
	/** Interpolated scroll position used for rendering (smooth). */
	private float smoothScrollOffset;
	private int maxScroll;
	private int rightScrollOffset;
	private float rightSmoothScrollOffset;
	private int rightMaxScroll;
	private boolean searchFocused;
	private String sidebarSelection = "MODULES";

	public ForkClickGuiScreen() {
		super(Component.literal("Fork Client"));
	}

	@Override
	protected void init() {
		super.init();
		ForkClientController ctrl = ForkClientController.INSTANCE;
		this.sidebarSelection = switch (ctrl.selectedTopTab()) {
			case SETTINGS  -> "SETTINGS";
			case WAYPOINTS -> "WAYPOINTS";
			default        -> "MODULES";
		};
	}

	// ── Helpers shared between render and click ───────────────────────────────

	/** Returns the X coordinate of the left edge of the content area (after sidebar). */
	private int contentLeft()  { return SIDEBAR_W; }
	/** Returns the X coordinate of the right edge of the content area (before right panel). */
	private int contentRight() { return this.width - RIGHT_W; }
	/** Returns the Y coordinate of the top edge of the content area (below header). */
	private int contentTop()   { return HEADER_H; }
	/** Returns the Y coordinate of the bottom edge of the content area. */
	private int contentBot()   { return this.height; }
	/** Returns the pixel-rounded scroll offset for use in rendering/clicking. */
	private int renderOff()    { return (int) Math.round(this.smoothScrollOffset); }

	// ── Main render entry ─────────────────────────────────────────────────────
	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		this.mx = mouseX;
		this.my = mouseY;

		// Background fill
		g.fill(0, 0, this.width, this.height, BG);

		// Advance smooth scroll
		this.smoothScrollOffset += (this.scrollOffset - this.smoothScrollOffset) * 0.25f;
		if (Math.abs(this.smoothScrollOffset - this.scrollOffset) < 0.5f) {
			this.smoothScrollOffset = this.scrollOffset;
		}
		this.rightSmoothScrollOffset += (this.rightScrollOffset - this.rightSmoothScrollOffset) * 0.25f;
		if (Math.abs(this.rightSmoothScrollOffset - this.rightScrollOffset) < 0.5f) {
			this.rightSmoothScrollOffset = this.rightScrollOffset;
		}

		renderHeader(g);
		renderSidebar(g);
		renderRightPanel(g);
		renderContent(g);
	}

	private void renderContent(GuiGraphicsExtractor g) {
		int cl   = contentLeft();
		int cr   = contentRight();
		int ct   = contentTop();
		int cb   = contentBot();
		int areaW = cr - cl;
		int areaH = cb - ct;
		int off   = renderOff();

		ForkClientController ctrl = ForkClientController.INSTANCE;

		g.enableScissor(cl, ct, cr, cb);

		int contentH;
		switch (sidebarSelection) {
			case "MODULES"   -> contentH = renderModuleColumns(g, ctrl, cl, ct - off, areaW);
			case "SETTINGS"  -> contentH = renderSettingsArea(g, ctrl, cl + 12, ct + 12 - off, areaW - 24);
			case "WAYPOINTS" -> contentH = renderWaypointArea(g, ctrl, cl + 12, ct + 12 - off, areaW - 24);
			default          -> {
				contentH = 0;
				g.text(this.font, Component.literal("Coming soon..."), cl + 24, ct + 24, TEXT_DIM, false);
			}
		}

		this.maxScroll = Math.max(0, contentH - areaH + 16);
		this.scrollOffset = clamp(this.scrollOffset, 0, this.maxScroll);

		g.disableScissor();
	}

	// ── Header ────────────────────────────────────────────────────────────────
	private void renderHeader(GuiGraphicsExtractor g) {
		g.fill(0, 0, this.width, HEADER_H, HEADER_BG);
		g.fill(0, HEADER_H - 1, this.width, HEADER_H, SEPARATOR);

		// "F" icon box (top-left corner)
		int iconSz = 28;
		int iconX  = 10;
		int iconY  = (HEADER_H - iconSz) / 2;
		g.fill(iconX, iconY, iconX + iconSz, iconY + iconSz, 0xFF141B2E);
		g.outline(iconX, iconY, iconSz, iconSz, ACCENT);
		String fLabel = "F";
		g.text(this.font, Component.literal(fLabel),
			iconX + (iconSz - this.font.width(fLabel)) / 2,
			iconY + (iconSz - this.font.lineHeight) / 2,
			TEXT_PRIMARY, false);

		// "FORKCLIENT" branding
		int logoX  = iconX + iconSz + 10;
		int logoY  = (HEADER_H - this.font.lineHeight) / 2;
		String logoA = "FORK";
		String logoB = "CLIENT";
		g.text(this.font, Component.literal(logoA), logoX, logoY, TEXT_PRIMARY, false);
		g.text(this.font, Component.literal(logoB), logoX + this.font.width(logoA), logoY, ACCENT, false);

		// Search bar
		int searchW = 200;
		int searchH = 22;
		int searchX = contentRight() - searchW - 12;
		int searchY = 2;
		renderSearchBar(g, searchX, searchY, searchW, searchH);

		// X (clear) button
		int btnSz = 22;
		int btnX  = searchX + searchW + 4;
		int btnY  = searchY;
		boolean btnHov = contains(btnX, btnY, btnSz, btnSz, this.mx, this.my);
		g.fill(btnX, btnY, btnX + btnSz, btnY + btnSz, btnHov ? 0x33FF5252 : 0x14FF5252);
		g.outline(btnX, btnY, btnSz, btnSz, btnHov ? 0xFFFF5252 : 0xFF2A3040);
		String xLabel = "X";
		g.text(this.font, Component.literal(xLabel),
			btnX + (btnSz - this.font.width(xLabel)) / 2,
			btnY + (btnSz - this.font.lineHeight) / 2,
			0xFFFF5252, false);
	}

	private void renderSearchBar(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		boolean focused = this.searchFocused;
		g.fill(x, y, x + w, y + h, 0xFF0E1118);
		g.outline(x, y, w, h, focused ? ACCENT : CHECK_BORDER);
		ForkClientController ctrl = ForkClientController.INSTANCE;
		String query   = ctrl.getSearchQuery();
		String display = query.isEmpty() && !focused ? "Search..." : query + (focused ? "|" : "");
		display = trimToWidth(display, w - 16);
		g.text(this.font, Component.literal(display),
			x + 8, y + (h - this.font.lineHeight) / 2,
			query.isEmpty() && !focused ? TEXT_DIM : TEXT_PRIMARY, false);
	}

	// ── Sidebar ───────────────────────────────────────────────────────────────
	private void renderSidebar(GuiGraphicsExtractor g) {
		g.fill(0, HEADER_H, SIDEBAR_W, this.height, SIDEBAR_BG);
		g.fill(SIDEBAR_W - 1, HEADER_H, SIDEBAR_W, this.height, SEPARATOR);

		int y = HEADER_H + 8;
		for (String[] item : SIDEBAR_ITEMS) {
			String key   = item[0];
			String id    = item[1];
			String label = item[2];

			boolean active  = id.equals(this.sidebarSelection);
			boolean hovered = contains(0, y, SIDEBAR_W - 1, SIDEBAR_ITEM_H, this.mx, this.my);
			// Items with dedicated screens are fully lit; stub items are dimmed
			boolean implemented = id.equals("MODULES") || id.equals("WAYPOINTS") || id.equals("SETTINGS");

			if (active) {
				g.fill(0, y, SIDEBAR_W - 1, y + SIDEBAR_ITEM_H, ACCENT_DIM);
				g.fill(0, y, 3, y + SIDEBAR_ITEM_H, ACCENT);
			} else if (hovered) {
				g.fill(0, y, SIDEBAR_W - 1, y + SIDEBAR_ITEM_H, 0x08FFFFFF);
			}

			// Letter badge
			int kW = this.font.width(key) + 6;
			int kH = this.font.lineHeight + 4;
			int kX = 10;
			int kY = y + (SIDEBAR_ITEM_H - kH) / 2;
			g.fill(kX, kY, kX + kW, kY + kH, active ? ACCENT_DIM : 0x0DFFFFFF);
			g.outline(kX, kY, kW, kH, active ? ACCENT : 0xFF2A3040);
			g.text(this.font, Component.literal(key),
				kX + (kW - this.font.width(key)) / 2,
				kY + (kH - this.font.lineHeight) / 2,
				active ? TEXT_ACCENT : TEXT_DIM, false);

			// Label
			g.text(this.font, Component.literal(label),
				kX + kW + 8,
				y + (SIDEBAR_ITEM_H - this.font.lineHeight) / 2,
				active ? TEXT_PRIMARY : (implemented ? TEXT_SECONDARY : TEXT_DIM), false);

			y += SIDEBAR_ITEM_H + 2;
		}

		// Bottom separator + Edit HUD Layout button
		g.fill(8, this.height - 52, SIDEBAR_W - 8, this.height - 51, SEPARATOR);

		int btnY  = this.height - 44;
		int btnH  = 28;
		int btnX  = 8;
		int btnW  = SIDEBAR_W - 16;
		boolean btnHov = contains(btnX, btnY, btnW, btnH, this.mx, this.my);
		g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHov ? 0x252196F3 : 0x152196F3);
		g.outline(btnX, btnY, btnW, btnH, ACCENT);
		String editLabel = "Edit HUD Layout";
		g.text(this.font, Component.literal(editLabel),
			btnX + (btnW - this.font.width(editLabel)) / 2,
			btnY + (btnH - this.font.lineHeight) / 2,
			TEXT_ACCENT, false);
	}

	// ── Right panel ───────────────────────────────────────────────────────────
	private void renderRightPanel(GuiGraphicsExtractor g) {
		int rx = this.width - RIGHT_W;
		g.fill(rx, HEADER_H, rx + 1, this.height, SEPARATOR);

		ForkClientController ctrl = ForkClientController.INSTANCE;
		Minecraft mc = Minecraft.getInstance();

		int y = HEADER_H + 12;

		// ── Player card box ────────────────────────────────────────────────
		{
			int boxX = rx + 4;
			int boxW = RIGHT_W - 8;

			int avatarSz = 28;
			int lineH = this.font.lineHeight;
			int contentH = avatarSz + 16;
			int boxH = 10 + contentH + 10;

			g.fill(boxX, y, boxX + boxW, y + boxH, PANEL);
			g.outline(boxX, y, boxW, boxH, BORDER);

			int cx = boxX + 10;
			int cy = y + 10;

			g.fill(cx, cy, cx + avatarSz, cy + avatarSz, 0xFF141B2E);
			g.outline(cx, cy, avatarSz, avatarSz, ACCENT);
			String initial = "M";
			try {
				String gn = mc.getUser().getName();
				if (gn != null && !gn.isEmpty()) initial = String.valueOf(gn.charAt(0)).toUpperCase(Locale.ROOT);
			} catch (Exception e) {
				ForkClient.LOGGER.warn("Failed to get player name initial", e);
			}
			g.text(this.font, Component.literal(initial),
				cx + (avatarSz - this.font.width(initial)) / 2,
				cy + (avatarSz - this.font.lineHeight) / 2,
				TEXT_PRIMARY, false);

			String playerName = "Player";
			try {
				String gn = mc.getUser().getName();
				if (gn != null && !gn.isEmpty()) playerName = gn;
			} catch (Exception e) {
				ForkClient.LOGGER.warn("Failed to get player name", e);
			}
			boolean online = mc.player != null;
			int textX = cx + avatarSz + 8;
			g.text(this.font, Component.literal(playerName), textX, cy + 3, TEXT_PRIMARY, false);

			int dotY = cy + 3 + lineH + 4;
			int dotSize = 6;
			int dotTop = dotY + (lineH - dotSize) / 2;
			g.fill(textX, dotTop, textX + dotSize, dotTop + dotSize, online ? ONLINE_GREEN : TEXT_DIM);
			g.text(this.font, Component.literal(online ? "ONLINE" : "OFFLINE"),
				textX + dotSize + 3, dotY, online ? ONLINE_GREEN : TEXT_DIM, false);

			y = y + boxH + 8;
		}

		int scrollableTop = y;
		int scrollableH = this.height - scrollableTop;
		int rightOff = (int) Math.round(this.rightSmoothScrollOffset);

		g.enableScissor(rx + 1, scrollableTop, this.width, this.height);

		int contentY = scrollableTop - rightOff;

		// ── Active Modules box ─────────────────────────────────────────────
		{
			int boxX = rx + 4;
			int boxW = RIGHT_W - 8;
			int cx    = boxX + 10;
			int cw    = boxW - 20;

			int innerH = 0;
			innerH += this.font.lineHeight + 6;
			int activeCount = 0;
			for (ForkClientController.ModuleDefinition mod : ctrl.allModules()) {
				if (!ctrl.isModuleEnabled(mod.id())) continue;
				if (mod.category() == ForkClientController.ModuleCategory.HUD) continue;
				innerH += this.font.lineHeight + 6;
				activeCount++;
			}
			if (activeCount == 0) {
				innerH += this.font.lineHeight + 6;
			}
			int boxH = 10 + innerH + 10;

			int boxY = contentY;
			g.fill(boxX, boxY, boxX + boxW, boxY + boxH, PANEL);
			g.outline(boxX, boxY, boxW, boxH, BORDER);

			int cy = boxY + 10;
			g.text(this.font, Component.literal("ACTIVE MODULES"), cx, cy, TEXT_SECONDARY, false);
			cy += this.font.lineHeight + 6;

			activeCount = 0;
			for (ForkClientController.ModuleDefinition mod : ctrl.allModules()) {
				if (!ctrl.isModuleEnabled(mod.id())) continue;
				if (mod.category() == ForkClientController.ModuleCategory.HUD) continue;

				int rowH = this.font.lineHeight + 4;
				boolean rowHov = contains(cx, cy, cw, rowH, this.mx, this.my);
				if (rowHov) g.fill(cx, cy, cx + cw, cy + rowH, 0x08FFFFFF);
				String title = trimToWidth(mod.title(), cw - this.font.width(">") - 6);
				g.text(this.font, Component.literal(title), cx + 2, cy + 2, TEXT_PRIMARY, false);
				g.text(this.font, Component.literal(">"), cx + cw - this.font.width(">") - 2, cy + 2, TEXT_ACCENT, false);
				cy += rowH + 2;
				activeCount++;
			}
			if (activeCount == 0) {
				g.text(this.font, Component.literal("None active"), cx + 2, cy + 2, TEXT_DIM, false);
			}

			contentY = boxY + boxH + 8;
		}

		// ── Recent Alerts box ──────────────────────────────────────────────
		{
			int boxX = rx + 4;
			int boxW = RIGHT_W - 8;
			int cx    = boxX + 10;
			int cw    = boxW - 20;

			int innerH = 0;
			innerH += this.font.lineHeight + 6;
			List<String> alerts = ctrl.getRecentAlerts();
			if (alerts.isEmpty()) {
				innerH += this.font.lineHeight + 4;
			} else {
				innerH += alerts.size() * (this.font.lineHeight + 4);
			}
			int boxH = 10 + innerH + 10;

			int boxY = contentY;
			g.fill(boxX, boxY, boxX + boxW, boxY + boxH, PANEL);
			g.outline(boxX, boxY, boxW, boxH, BORDER);

			int cy = boxY + 10;
			g.text(this.font, Component.literal("RECENT ALERTS"), cx, cy, TEXT_SECONDARY, false);
			cy += this.font.lineHeight + 6;

			if (alerts.isEmpty()) {
				g.text(this.font, Component.literal("No recent activity"), cx + 2, cy + 2, TEXT_DIM, false);
			} else {
				for (int i = 0; i < alerts.size(); i++) {
					g.text(this.font, Component.literal(trimToWidth(alerts.get(i), cw - 4)),
						cx + 2, cy + 2, TEXT_SECONDARY, false);
					cy += this.font.lineHeight + 4;
				}
			}

			int contentEnd = boxY + boxH;
			this.rightMaxScroll = Math.max(0, contentEnd - scrollableTop + rightOff - scrollableH + 16);
			this.rightScrollOffset = clamp(this.rightScrollOffset, 0, this.rightMaxScroll);
		}

		g.disableScissor();
	}

	// ── Module columns ────────────────────────────────────────────────────────
	/**
	 * Renders all category columns side-by-side starting at (areaLeft+COL_PAD, startY+CAT_TOP_PAD).
	 * Scroll is vertical — all columns scroll together.
	 * Returns the maximum bottom-Y reached (used to compute maxScroll).
	 */
	private int renderModuleColumns(GuiGraphicsExtractor g, ForkClientController ctrl,
			int areaLeft, int startY, int areaW) {

		List<ForkClientController.ModuleDefinition> allMods = ctrl.allModulesFiltered();

		// Collect non-empty categories in order
		List<ForkClientController.ModuleCategory> activeCats = new ArrayList<>();
		List<List<ForkClientController.ModuleDefinition>> catModsList = new ArrayList<>();
		for (ForkClientController.ModuleCategory cat : ForkClientController.ModuleCategory.values()) {
			List<ForkClientController.ModuleDefinition> catMods = allMods.stream()
				.filter(m -> m.category() == cat).toList();
			if (catMods.isEmpty()) continue;
			activeCats.add(cat);
			catModsList.add(catMods);
		}

		if (activeCats.isEmpty()) return 0;

		// Pre-compute column heights
		int[] colHeights = new int[activeCats.size()];
		for (int i = 0; i < activeCats.size(); i++) {
			colHeights[i] = CAT_TOP_PAD + CAT_HDR_H + 2 + catModsList.get(i).size() * (MOD_ROW_H + MOD_GAP);
		}

		// Wrap columns into rows — COLS_PER_ROW per row
		int colsPerRow = COLS_PER_ROW;
		int colW = Math.max(140, (areaW - COL_PAD - (colsPerRow - 1) * COL_GAP) / colsPerRow);
		int colStep = colW + COL_GAP;

		int renderY = startY;
		int catIdx = 0;
		while (catIdx < activeCats.size()) {
			int colsInThisRow = Math.min(colsPerRow, activeCats.size() - catIdx);
			int rowMaxHeight = 0;
			for (int col = 0; col < colsInThisRow; col++) {
				rowMaxHeight = Math.max(rowMaxHeight, colHeights[catIdx + col]);
			}

			int baseX = areaLeft + COL_PAD;
			for (int col = 0; col < colsInThisRow; col++) {
				int idx = catIdx + col;
				renderCategoryColumn(g, ctrl, activeCats.get(idx), catModsList.get(idx),
					baseX + col * colStep, renderY, colW);
			}

			renderY += rowMaxHeight;
			catIdx += colsInThisRow;
		}

		return renderY - startY;
	}

	/**
	 * Renders one category column.
	 * Column layout (Y-axis): startY + CAT_TOP_PAD → header (CAT_HDR_H) → modules
	 * Returns the Y coordinate of the bottom pixel drawn.
	 */
	private int renderCategoryColumn(GuiGraphicsExtractor g, ForkClientController ctrl,
			ForkClientController.ModuleCategory cat,
			List<ForkClientController.ModuleDefinition> catMods,
			int x, int startY, int colW) {

		int accent = catColor(cat);
		int y = startY + CAT_TOP_PAD;

		// Category header bar
		g.fill(x, y, x + colW, y + CAT_HDR_H, 0xFF0C0F18);
		g.fill(x, y, x + 3, y + CAT_HDR_H, accent);                 // left accent strip
		String hdrText = catDisplayLabel(cat).toUpperCase(Locale.ROOT);
		g.text(this.font, Component.literal(hdrText),
			x + 10, y + (CAT_HDR_H - this.font.lineHeight) / 2, accent, false);
		String badge = catMods.size() + " mods";
		g.text(this.font, Component.literal(badge),
			x + colW - this.font.width(badge) - 6,
			y + (CAT_HDR_H - this.font.lineHeight) / 2, TEXT_DIM, false);
		// bottom accent line
		g.fill(x, y + CAT_HDR_H - 1, x + colW, y + CAT_HDR_H, withAlpha(accent, 0x44));

		y += CAT_HDR_H + 2;

		// Module rows
		for (ForkClientController.ModuleDefinition mod : catMods) {
			renderModuleCard(g, ctrl, mod, x, y, colW);
			y += MOD_ROW_H + MOD_GAP;
		}

		return y;
	}

	private void renderModuleCard(GuiGraphicsExtractor g, ForkClientController ctrl,
			ForkClientController.ModuleDefinition mod, int x, int y, int w) {
		boolean enabled = ctrl.isModuleEnabled(mod.id());
		boolean hovered = contains(x, y, w, MOD_ROW_H, this.mx, this.my);

		g.fill(x, y, x + w, y + MOD_ROW_H, hovered ? PANEL_HOVER : PANEL);
		g.outline(x, y, w, MOD_ROW_H, hovered ? BORDER_HOVER : BORDER);

		// Enabled accent strip (left edge, inside border)
		if (enabled) {
			g.fill(x + 1, y + 4, x + 3, y + MOD_ROW_H - 4, catColor(mod.category()));
		}

		// Toggle switch
		int togX = x + 10;
		int togY = y + (MOD_ROW_H - TOGGLE_H) / 2;
		renderToggleSwitch(g, togX, togY, enabled, catColor(mod.category()));

		// Text
		int textX    = togX + TOGGLE_W + 8;
		int maxTextW = w - (textX - x) - 6;
		g.text(this.font, Component.literal(trimToWidth(mod.title(), maxTextW)),
			textX, y + 8, enabled ? TEXT_PRIMARY : TEXT_SECONDARY, false);
		g.text(this.font, Component.literal(trimToWidth(mod.description(), maxTextW)),
			textX, y + 8 + this.font.lineHeight + 3, TEXT_DIM, false);

		// Config keybind hint for modules with a dedicated settings screen
		if (ArmorDurabilityHudComponent.MODULE_ID.equals(mod.id())) {
			String hint = "[" + ctrl.getArmorConfigKeybindName() + "] open settings";
			g.text(this.font, Component.literal(hint),
				textX, y + 8 + this.font.lineHeight * 2 + 5, ACCENT, false);
		}
		if (MotionBlurPlusModule.MODULE_ID.equals(mod.id())) {
			String hint = "[" + MotionBlurPlusModule.getKeybindName() + "] open settings";
			g.text(this.font, Component.literal(hint),
				textX, y + 8 + this.font.lineHeight * 2 + 5, ACCENT, false);
		}
	}

	/** Renders a toggle switch at (x, y) with a white indicator knob.
	 *  Left = disabled, Right = enabled. */
	private void renderToggleSwitch(GuiGraphicsExtractor g, int x, int y, boolean enabled, int accentColor) {
		int bg = enabled ? accentColor : CHECK_OFF;
		int border = enabled ? accentColor : CHECK_BORDER;
		g.fill(x, y, x + TOGGLE_W, y + TOGGLE_H, bg);
		g.outline(x, y, TOGGLE_W, TOGGLE_H, border);

		// White indicator knob
		int knobX = enabled ? x + TOGGLE_W - KNOB_SIZE - 2 : x + 2;
		int knobY = y + (TOGGLE_H - KNOB_SIZE) / 2;
		g.fill(knobX, knobY, knobX + KNOB_SIZE, knobY + KNOB_SIZE, 0xFFFFFFFF);
	}

	// ── Settings area ─────────────────────────────────────────────────────────
	/** Returns height of rendered content (for scroll computation). */
	private int renderSettingsArea(GuiGraphicsExtractor g, ForkClientController ctrl,
			int x, int y, int w) {
		int startY = y;
		g.text(this.font, Component.literal("SETTINGS"), x, y, TEXT_PRIMARY, true);

		String[][] settings = {
			{"notifications",        "Notifications"},
			{"module_toggle_alerts", "Module Toggle Alerts"},
		};
		String optionCountText = settings.length + " options";
		g.text(this.font, Component.literal(optionCountText),
			x + w - this.font.width(optionCountText), y, ACCENT, false);
		y += this.font.lineHeight + 12;
		g.fill(x, y, x + w, y + 1, SEPARATOR);
		y += 9;

		int rowH = 36;
		for (String[] s : settings) {
			boolean on = ctrl.isModuleEnabled(s[0]);
			boolean hov = contains(x, y, w, rowH, this.mx, this.my);
			g.fill(x, y, x + w, y + rowH, hov ? PANEL_HOVER : PANEL);
			g.outline(x, y, w, rowH, hov ? BORDER_HOVER : BORDER);
			g.text(this.font, Component.literal(s[1]), x + 12, y + (rowH - this.font.lineHeight) / 2, TEXT_PRIMARY, false);
			renderToggleSwitch(g, x + w - TOGGLE_W - 12, y + (rowH - TOGGLE_H) / 2, on, ACCENT);
			y += rowH + 2;
		}
		return y - startY;
	}

	// ── Waypoint area ─────────────────────────────────────────────────────────
	/** Returns height of rendered content (for scroll computation). */
	private int renderWaypointArea(GuiGraphicsExtractor g, ForkClientController ctrl,
			int x, int y, int w) {
		int startY = y;
		List<ForkClientController.Waypoint> waypoints = ctrl.getWaypoints();
		g.text(this.font, Component.literal("WAYPOINTS"), x, y, TEXT_PRIMARY, true);
		String badge = waypoints.size() + " waypoint" + (waypoints.size() == 1 ? "" : "s");
		g.text(this.font, Component.literal(badge), x + w - this.font.width(badge), y, ACCENT, false);
		y += this.font.lineHeight + 6;

		// "Add Waypoint" button
		int addW = 130;
		int addX = x + w - addW;
		boolean addHov = contains(addX, y, addW, 18, this.mx, this.my);
		g.fill(addX, y, addX + addW, y + 18, addHov ? 0x332196F3 : 0x1A2196F3);
		g.outline(addX, y, addW, 18, 0x442196F3);
		g.text(this.font, Component.literal("+ Add Waypoint"),
			addX + (addW - this.font.width("+ Add Waypoint")) / 2,
			y + (18 - this.font.lineHeight) / 2, ACCENT, false);
		y += 26;

		g.fill(x, y, x + w, y + 1, SEPARATOR);
		y += 8;

		if (waypoints.isEmpty()) {
			int rowH = 44;
			g.fill(x, y, x + w, y + rowH, PANEL);
			g.outline(x, y, w, rowH, BORDER);
			g.text(this.font, Component.literal("No waypoints configured."), x + 12, y + 10, TEXT_DIM, false);
			g.text(this.font, Component.literal("Click the button above to add one."), x + 12, y + 24, TEXT_DIM, false);
			y += rowH + 4;
		} else {
			for (ForkClientController.Waypoint wp : waypoints) {
				int rowH = 40;
				boolean hov = contains(x, y, w, rowH, this.mx, this.my);
				g.fill(x, y, x + w, y + rowH, hov ? PANEL_HOVER : PANEL);
				g.outline(x, y, w, rowH, hov ? BORDER_HOVER : BORDER);
				g.fill(x + 1, y + 4, x + 3, y + rowH - 4, wp.enabled() ? wp.color() : TEXT_DIM);
				g.text(this.font, Component.literal(wp.name()), x + 14, y + 4,
					wp.enabled() ? TEXT_PRIMARY : TEXT_SECONDARY, false);
				String coords = wp.x() + ", " + wp.y() + ", " + wp.z();
				g.text(this.font, Component.literal(coords), x + 14, y + 20, TEXT_DIM, false);
				// Delete button
				int delSz = 16;
				int delX  = x + w - delSz - 10;
				int delY  = y + (rowH - delSz) / 2;
				boolean delHov = contains(delX, delY, delSz, delSz, this.mx, this.my);
				g.fill(delX, delY, delX + delSz, delY + delSz, delHov ? 0x33FF5252 : 0x14FF5252);
				g.outline(delX, delY, delSz, delSz, delHov ? 0xFFFF5252 : 0xFF2A3040);
				// Draw a small "X" in the delete button
				String delLabel = "x";
				g.text(this.font, Component.literal(delLabel),
					delX + (delSz - this.font.width(delLabel)) / 2,
					delY + (delSz - this.font.lineHeight) / 2,
					delHov ? 0xFFFF5252 : 0xFFB0B8C4, false);
				y += rowH + 4;
			}
		}
		return y - startY;
	}

	// ── Mouse events ──────────────────────────────────────────────────────────
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT && event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;
		boolean rightClick = event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
		double ex = event.x(), ey = event.y();
		ForkClientController ctrl = ForkClientController.INSTANCE;
		this.searchFocused = false;

		// ── Search bar ────────────────────────────────────────────────────
		int searchW = 200;
		int searchH = 22;
		int searchX = contentRight() - searchW - 12;
		int searchY = 2;
		if (contains(searchX, searchY, searchW, searchH, ex, ey)) {
			this.searchFocused = true;
			return true;
		}

		// ── X (clear-search) button ───────────────────────────────────────
		int btnSz = 22;
		int btnX  = searchX + searchW + 4;
		if (contains(btnX, searchY, btnSz, btnSz, ex, ey)) {
			ctrl.setSearchQuery("");
			this.scrollOffset = 0;
			this.smoothScrollOffset = 0;
			return true;
		}

		// ── Sidebar items ─────────────────────────────────────────────────
		int sy = HEADER_H + 8;
		for (String[] item : SIDEBAR_ITEMS) {
			if (contains(0, sy, SIDEBAR_W - 1, SIDEBAR_ITEM_H, ex, ey)) {
				String id = item[1];
				this.sidebarSelection = id;
				this.scrollOffset = 0;
				this.smoothScrollOffset = 0;
				switch (id) {
					case "SETTINGS"  -> ctrl.selectTopTab(ForkClientController.GuiTab.SETTINGS);
					case "WAYPOINTS" -> ctrl.selectTopTab(ForkClientController.GuiTab.WAYPOINTS);
					default          -> ctrl.selectTopTab(ForkClientController.GuiTab.MODS);
				}
				return true;
			}
			sy += SIDEBAR_ITEM_H + 2;
		}

		// ── Edit HUD Layout button ────────────────────────────────────────
		if (contains(8, this.height - 44, SIDEBAR_W - 16, 28, ex, ey)) {
			ctrl.openHudEditor(this.minecraft);
			return true;
		}

		// ── Module cards ──────────────────────────────────────────────────
		if (sidebarSelection.equals("MODULES")) {
			int cl     = contentLeft();
			int areaW  = contentRight() - cl;
			int off    = renderOff();
			int startY = contentTop() - off;

			List<ForkClientController.ModuleDefinition> allMods = ctrl.allModulesFiltered();

			// Collect non-empty categories (same order as renderModuleColumns)
			List<ForkClientController.ModuleCategory> activeCats = new ArrayList<>();
			List<List<ForkClientController.ModuleDefinition>> catModsList = new ArrayList<>();
			for (ForkClientController.ModuleCategory cat : ForkClientController.ModuleCategory.values()) {
				List<ForkClientController.ModuleDefinition> catMods = allMods.stream()
					.filter(m -> m.category() == cat).toList();
				if (catMods.isEmpty()) continue;
				activeCats.add(cat);
				catModsList.add(catMods);
			}

			if (activeCats.isEmpty()) return false;

			// Pre-compute column heights
			int[] colHeights = new int[activeCats.size()];
			for (int i = 0; i < activeCats.size(); i++) {
				colHeights[i] = CAT_TOP_PAD + CAT_HDR_H + 2 + catModsList.get(i).size() * (MOD_ROW_H + MOD_GAP);
			}

			// Same wrapping layout as renderModuleColumns — COLS_PER_ROW per row
			int colsPerRow = COLS_PER_ROW;
			int colW = Math.max(140, (areaW - COL_PAD - (colsPerRow - 1) * COL_GAP) / colsPerRow);
			int colStep = colW + COL_GAP;

			int renderY = startY;
			int catIdx = 0;
			while (catIdx < activeCats.size()) {
				int colsInThisRow = Math.min(colsPerRow, activeCats.size() - catIdx);
				int rowMaxHeight = 0;
				for (int col = 0; col < colsInThisRow; col++) {
					rowMaxHeight = Math.max(rowMaxHeight, colHeights[catIdx + col]);
				}

				int baseX = cl + COL_PAD;
				for (int col = 0; col < colsInThisRow; col++) {
					int idx = catIdx + col;
					int colX = baseX + col * colStep;
					int modY = renderY + CAT_TOP_PAD + CAT_HDR_H + 2;
					for (ForkClientController.ModuleDefinition mod : catModsList.get(idx)) {
						if (contains(colX, modY, colW, MOD_ROW_H, ex, ey)) {
							if (rightClick) {
								this.openModuleConfig(mod.id());
							} else {
								ctrl.toggleModule(mod.id());
							}
							return true;
						}
						modY += MOD_ROW_H + MOD_GAP;
					}
				}

				renderY += rowMaxHeight;
				catIdx += colsInThisRow;
			}
		}

		// ── Settings rows ─────────────────────────────────────────────────
		if (sidebarSelection.equals("SETTINGS")) {
			int x   = contentLeft() + 12;
			int w   = contentRight() - x - 12;
			int off = renderOff();
			// Mirror the Y layout from renderSettingsArea
			int ry  = contentTop() + 12 - off      // base y passed to renderSettingsArea
				+ this.font.lineHeight + 12         // title height
				+ 1 + 8;                            // separator + gap
			String[] ids = {"notifications", "module_toggle_alerts"};
			int rowH = 36;
			for (String id : ids) {
				if (contains(x, ry, w, rowH, ex, ey)) {
					ctrl.toggleModule(id);
					return true;
				}
				ry += rowH + 2;
			}
		}

		// ── Waypoint rows ─────────────────────────────────────────────────
		if (sidebarSelection.equals("WAYPOINTS")) {
			int x    = contentLeft() + 12;
			int w    = contentRight() - x - 12;
			int off  = renderOff();
			int base = contentTop() + 12 - off;   // base y passed to renderWaypointArea

			// Add waypoint button
			int titleH = this.font.lineHeight + 6;
			int addBtnY = base + titleH;
			int addW = 130;
			int addX = x + w - addW;
			if (contains(addX, addBtnY, addW, 18, ex, ey)) {
				ctrl.addWaypointAtPlayer();
				return true;
			}

			// Waypoint delete buttons
			// layout: base + titleH + 26 (button+gap) + 8 (gap incl. separator) = base + titleH + 34
			int ry = base + titleH + 26 + 8;
			List<ForkClientController.Waypoint> waypoints = ctrl.getWaypoints();
			int delSz = 16;
			int rowH  = 40;
			for (int i = 0; i < waypoints.size(); i++) {
				int delX = x + w - delSz - 10;
				int delY = ry + (rowH - delSz) / 2;
				if (contains(delX, delY, delSz, delSz, ex, ey)) {
					ctrl.removeWaypoint(i);
					return true;
				}
				ry += rowH + 4;
			}
		}

		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
		if (vAmount == 0) return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
		if (mouseX >= this.width - RIGHT_W && mouseY >= HEADER_H) {
			if (this.rightMaxScroll > 0) {
				this.rightScrollOffset = clamp(this.rightScrollOffset - (int) Math.round(vAmount * 20), 0, this.rightMaxScroll);
			}
			return true;
		}
		if (this.maxScroll <= 0) return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
		this.scrollOffset = clamp(this.scrollOffset - (int) Math.round(vAmount * 20), 0, this.maxScroll);
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!this.searchFocused) return false;
		int c = event.codepoint();
		if (c >= 32 && c < 127) {
			ForkClientController.INSTANCE.setSearchQuery(
				ForkClientController.INSTANCE.getSearchQuery() + (char) c);
			this.scrollOffset = 0;
			this.smoothScrollOffset = 0;
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			if (this.searchFocused) {
				this.searchFocused = false;
				return true;
			}
			this.minecraft.gui.setScreen(null);
			return true;
		}
		if (this.searchFocused && event.key() == GLFW.GLFW_KEY_BACKSPACE) {
			String q = ForkClientController.INSTANCE.getSearchQuery();
			if (!q.isEmpty()) {
				ForkClientController.INSTANCE.setSearchQuery(q.substring(0, q.length() - 1));
				this.scrollOffset = 0;
				this.smoothScrollOffset = 0;
			}
			return true;
		}
		return this.searchFocused;
	}

	@Override
	public void removed() {
		ForkClientController.INSTANCE.saveConfig();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public java.util.List<? extends GuiEventListener> children() {
		return java.util.List.of();
	}

	// ── Helpers ───────────────────────────────────────────────────────────────
	/** Opens the per-module config screen for a module, when one is registered. */
	private void openModuleConfig(String moduleId) {
		if (this.minecraft == null) {
			return;
		}
		if (ArmorDurabilityHudComponent.MODULE_ID.equals(moduleId)) {
			this.minecraft.gui.setScreen(new ArmorDurabilityConfigScreen(this));
		}
	}

	private String trimToWidth(String text, int maxWidth) {
		if (text == null || text.isEmpty() || maxWidth <= 0) return "";
		if (this.font.width(text) <= maxWidth) return text;
		String ellipsis = "...";
		String trimmed  = text;
		while (trimmed.length() > 1 && this.font.width(trimmed + ellipsis) > maxWidth) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed.length() < text.length() ? trimmed + ellipsis : trimmed;
	}

	/** Hit-test: returns true when (tx, ty) is within the rectangle [x, x+w) × [y, y+h). */
	private boolean contains(int x, int y, int w, int h, double tx, double ty) {
		return tx >= x && tx < x + w && ty >= y && ty < y + h;
	}

	private int clamp(int v, int min, int max) {
		return Math.clamp(v, min, max);
	}

	private static int withAlpha(int color, int alpha) {
		return (color & 0x00FFFFFF) | (alpha << 24);
	}

	/** Maps a category to its human-readable column header label. */
	private static String catDisplayLabel(ForkClientController.ModuleCategory cat) {
		return switch (cat) {
			case HUD          -> "HUD WIDGETS";
			case VISUAL       -> "VISUAL";
			case MOVEMENT     -> "MOVEMENT";
			case UTILITY      -> "UTILITY";
			case COMBAT       -> "COMBAT";
			case CONTENT      -> "CONTENT CREATION";
			case BUILDING     -> "BUILDING HELPERS";
			case MULTIPLAYER  -> "MULTIPLAYER UTILITIES";
		};
	}

	/** Returns the accent colour for a category. */
	private static int catColor(ForkClientController.ModuleCategory cat) {
		return switch (cat) {
			case HUD          -> 0xFF55D0FF;
			case VISUAL       -> 0xFFFF74D4;
			case MOVEMENT     -> 0xFF7CFF74;
			case UTILITY      -> 0xFFFFD45E;
			case COMBAT       -> 0xFFFF6B6B;
			case CONTENT      -> 0xFFCF6BEF;
			case BUILDING     -> 0xFFEFC86B;
			case MULTIPLAYER  -> 0xFF6BD1EF;
		};
	}
}
