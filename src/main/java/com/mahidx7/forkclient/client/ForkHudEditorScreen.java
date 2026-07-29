package com.mahidx7.forkclient.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import com.mahidx7.forkclient.ForkClient;
import org.lwjgl.glfw.GLFW;

public class ForkHudEditorScreen extends Screen {
	private static final int SIDEBAR_MARGIN = 20;
	private static final int SIDEBAR_WIDTH = 178;
	private static final int SIDEBAR_HEIGHT = 252;
	private static final int BUTTON_HEIGHT = 22;
	private static final int BUTTON_GAP = 8;
	private static final int COLOR_PANEL = 0xE01C222B;
	private static final int COLOR_PANEL_BORDER = 0xFF878D85;
	private static final int COLOR_PANEL_INNER = 0xFF474D48;
	private static final int COLOR_TITLE = 0xDD323741;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_MUTED = 0xFFE1E6DE;
	private static final int COLOR_WARN = 0xFFFFD45E;

	private double mouseX;
	private double mouseY;
	private ForkClientController.WidgetState draggingWidget;
	private long screenOpenTime;

	public ForkHudEditorScreen() {
		super(Component.literal("Fork HUD Editor"));
		this.screenOpenTime = System.currentTimeMillis();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
		Layout layout = layout();
		this.mouseX = mouseX;
		this.mouseY = mouseY;

		extractTransparentBackground(extractor);
		ForkClientController.INSTANCE.renderEditorPreview(extractor, mouseX, mouseY, this.draggingWidget);

		drawPanel(extractor, layout.sidebarX, layout.sidebarY, layout.sidebarWidth, layout.sidebarHeight);
		extractor.fill(layout.sidebarX + 3, layout.sidebarY + 3, layout.sidebarX + layout.sidebarWidth - 3, layout.sidebarY + 28, COLOR_TITLE);
		extractor.outline(layout.sidebarX + 3, layout.sidebarY + 3, layout.sidebarWidth - 6, 25, 0xFF8EE7FF);
		extractor.fill(layout.sidebarX + 8, layout.sidebarY + 32, layout.sidebarX + layout.sidebarWidth - 8, layout.sidebarY + 35, 0xCCFF74D4);

		extractor.text(this.font, Component.literal("HUD Editor"), layout.sidebarX + 12, layout.sidebarY + 12, COLOR_TEXT, true);
		extractor.text(this.font, Component.literal("Left Click"), layout.sidebarX + 12, layout.sidebarY + 42, COLOR_WARN, false);
		extractor.text(this.font, Component.literal("Drag a widget to move it."), layout.sidebarX + 12, layout.sidebarY + 54, COLOR_MUTED, false);
		extractor.text(this.font, Component.literal("Right Click"), layout.sidebarX + 12, layout.sidebarY + 82, COLOR_WARN, false);
		extractor.text(this.font, Component.literal("Toggle a widget on or off."), layout.sidebarX + 12, layout.sidebarY + 94, COLOR_MUTED, false);
		extractor.text(this.font, Component.literal("Hotkeys"), layout.sidebarX + 12, layout.sidebarY + 122, COLOR_WARN, false);
		extractor.text(this.font, Component.literal("H or Esc closes this editor."), layout.sidebarX + 12, layout.sidebarY + 134, COLOR_MUTED, false);
		extractor.text(this.font, Component.literal("Right Shift opens the click GUI."), layout.sidebarX + 12, layout.sidebarY + 146, COLOR_MUTED, false);

		renderButtons(extractor, layout);
		renderTooltips(extractor, layout, mouseX, mouseY);
		renderGridGuides(extractor);
		renderFadeOverlay(extractor);
	}

	private void renderButtons(GuiGraphicsExtractor extractor, Layout layout) {
		boolean returnHovered = contains(layout.buttonX, layout.returnButtonY, layout.buttonWidth, BUTTON_HEIGHT, this.mouseX, this.mouseY);
		boolean closeHovered = contains(layout.buttonX, layout.closeButtonY, layout.buttonWidth, BUTTON_HEIGHT, this.mouseX, this.mouseY);

		drawButton(
			extractor,
			layout.buttonX,
			layout.returnButtonY,
			layout.buttonWidth,
			BUTTON_HEIGHT,
			"Open Click GUI",
			returnHovered ? 0xD53C4765 : 0xB829313E,
			returnHovered ? 0xFF8EE7FF : 0xFF55D0FF
		);
		drawButton(
			extractor,
			layout.buttonX,
			layout.closeButtonY,
			layout.buttonWidth,
			BUTTON_HEIGHT,
			"Close",
			closeHovered ? 0xD5503455 : 0xB829313E,
			closeHovered ? 0xFFFF74D4 : 0xFF8A9088
		);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Layout layout = layout();
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT && event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			return false;
		}

		if (contains(layout.buttonX, layout.returnButtonY, layout.buttonWidth, BUTTON_HEIGHT, event.x(), event.y())) {
			if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				return false;
			}
			this.minecraft.gui.setScreen(new ForkClickGuiScreen());
			return true;
		}

		if (contains(layout.buttonX, layout.closeButtonY, layout.buttonWidth, BUTTON_HEIGHT, event.x(), event.y())) {
			if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				return false;
			}
			this.minecraft.gui.setScreen(null);
			return true;
		}

		ForkClientController.WidgetState target = ForkClientController.INSTANCE.findWidgetAt(event.x(), event.y());
		if (target == null) {
			return false;
		}

		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			ForkClientController.INSTANCE.toggleWidget(target);
			return true;
		}

		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			this.draggingWidget = target;
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.draggingWidget == null || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}

		ForkClientController.INSTANCE.moveWidget(
			this.draggingWidget,
			this.draggingWidget.x() + (int) Math.round(dragX),
			this.draggingWidget.y() + (int) Math.round(dragY)
		);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingWidget != null) {
			this.draggingWidget = null;
			ForkClientController.INSTANCE.saveConfig();
			return true;
		}

		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_H) {
			this.minecraft.gui.setScreen(null);
			return true;
		}

		return false;
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
	public List<? extends GuiEventListener> children() {
		return List.of();
	}

	private boolean contains(int x, int y, int width, int height, double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private void drawButton(GuiGraphicsExtractor extractor, int x, int y, int width, int height, String label, int fill, int outline) {
		extractor.fill(x, y, x + width, y + height, fill);
		extractor.outline(x, y, width, height, outline);
		extractor.outline(x + 2, y + 2, width - 4, height - 4, 0xFF4C5450);
		extractor.fill(x + 2, y + 2, x + width - 2, y + 5, 0x77D5DCD4);
		int textX = x + Math.max(6, (width - this.font.width(label)) / 2);
		int textY = y + Math.max(6, (height - this.font.lineHeight) / 2);
		extractor.text(this.font, Component.literal(label), textX, textY, COLOR_TEXT, false);
	}

	private void drawPanel(GuiGraphicsExtractor extractor, int x, int y, int width, int height) {
		extractor.fill(x, y, x + width, y + height, COLOR_PANEL);
		extractor.outline(x, y, width, height, COLOR_PANEL_BORDER);
		extractor.outline(x + 2, y + 2, width - 4, height - 4, COLOR_PANEL_INNER);
		extractor.outline(x + 4, y + 4, width - 8, height - 8, 0x444F5751);
		extractor.fill(x + 3, y + 3, x + width - 3, y + 8, 0x66A6ACA4);
	}

	private void renderFadeOverlay(GuiGraphicsExtractor extractor) {
		float t = fadeProgress();
		if (t >= 1.0f) return;
		int overlayAlpha = Math.round((1.0f - t) * 0xC0);
		extractor.fill(0, 0, this.width, this.height, overlayAlpha << 24);
	}

	private float fadeProgress() {
		return Math.min(1.0f, (System.currentTimeMillis() - this.screenOpenTime) / 200.0f);
	}

	private void renderTooltips(GuiGraphicsExtractor extractor, Layout layout, int mouseX, int mouseY) {
		if (contains(layout.buttonX, layout.returnButtonY, layout.buttonWidth, BUTTON_HEIGHT, mouseX, mouseY)) {
			renderCustomTooltip(extractor, mouseX, mouseY, Component.literal("Open the main Click GUI"));
		} else if (contains(layout.buttonX, layout.closeButtonY, layout.buttonWidth, BUTTON_HEIGHT, mouseX, mouseY)) {
			renderCustomTooltip(extractor, mouseX, mouseY, Component.literal("Close the HUD Editor"));
		} else {
			ForkClientController.WidgetState hovered = ForkClientController.INSTANCE.findWidgetAt(mouseX, mouseY);
			if (hovered != null && this.draggingWidget == null) {
				String status = hovered.enabled() ? "Visible" : "Hidden";
				renderCustomTooltip(extractor, mouseX, mouseY, Component.literal(hovered.title() + " (" + status + ")"));
			}
		}
	}

	private void renderCustomTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY, Component text) {
		String raw = text.getString();
		int textWidth = this.font.width(raw);
		int lineHeight = this.font.lineHeight;
		int pad = 6;
		int accentW = 3;

		int bx = mouseX + 10;
		int by = mouseY - lineHeight / 2 - pad;
		int bw = textWidth + pad * 2 + accentW;
		int bh = lineHeight + pad * 2;

		if (bx + bw > this.width) bx = mouseX - bw - 10;
		if (by < 2) by = 2;
		if (by + bh > this.height - 2) by = this.height - bh - 2;

		extractor.fill(bx + 2, by + 2, bx + bw + 2, by + bh + 2, 0x80000000);
		extractor.fill(bx, by, bx + bw, by + bh, 0xE01C222B);
		extractor.fill(bx, by, bx + accentW, by + bh, 0xFF55D0FF);
		extractor.outline(bx, by, bw, bh, 0xFF878D85);
		extractor.outline(bx + 1, by + 1, bw - 2, bh - 2, 0xFF474D48);
		extractor.fill(bx + accentW, by + bh - 1, bx + bw, by + bh, 0x88474D48);

		Component styled = Component.literal(raw).withStyle(s -> s.withFont(new FontDescription.Resource(ForkClient.id("text"))));
		extractor.text(this.font, styled, bx + accentW + pad, by + pad, 0xFFFFFFFF, true);
	}

	private void renderGridGuides(GuiGraphicsExtractor extractor) {
		if (this.draggingWidget == null) return;
		int cx = this.width / 2;
		int cy = this.height / 2;
		extractor.horizontalLine(0, this.width, cy, 0x88FFFFFF);
		extractor.verticalLine(cx, 0, this.height, 0x88FFFFFF);
	}

	private Layout layout() {
		int sidebarX = Math.max(SIDEBAR_MARGIN, this.width - SIDEBAR_WIDTH - SIDEBAR_MARGIN);
		int sidebarY = Math.max(SIDEBAR_MARGIN, (this.height - SIDEBAR_HEIGHT) / 2);
		int buttonX = sidebarX + 12;
		int buttonWidth = SIDEBAR_WIDTH - 24;
		int closeButtonY = sidebarY + SIDEBAR_HEIGHT - 36;
		int returnButtonY = closeButtonY - BUTTON_HEIGHT - BUTTON_GAP;
		return new Layout(sidebarX, sidebarY, SIDEBAR_WIDTH, SIDEBAR_HEIGHT, buttonX, buttonWidth, returnButtonY, closeButtonY);
	}

	private record Layout(int sidebarX, int sidebarY, int sidebarWidth, int sidebarHeight, int buttonX, int buttonWidth, int returnButtonY, int closeButtonY) {
	}
}
