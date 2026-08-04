package com.mahidx7.forkclient.client.hud;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.ForkClientController.WidgetState;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ArrayListHudComponent {
	private static final int PANEL_BACKGROUND = 0xCC1B212A;
	private static final int PANEL_OUTLINE = 0xFF55D0FF;
	private static final int PANEL_OUTLINE_HOVER = 0xFF8EE7FF;
	private static final int PANEL_INNER = 0xFF474E4A;
	private static final int TEXT_MUTED = 0xFFE1E6DE;

	private static final int[] GRADIENT_PALETTE = {
		0xFFFF6B6B,
		0xFFFF9F43,
		0xFFFFD93D,
		0xFF6BCB77,
		0xFF4ECDC4,
		0xFF4D96FF,
		0xFF9B59B6,
		0xFFE056A0
	};

	private ArrayListHudComponent() {
	}

	public static void render(GuiGraphicsExtractor extractor, Minecraft client, int x, int y) {
		ForkClientController ctrl = ForkClientController.INSTANCE;
		List<String> enabledNames = ctrl.enabledModuleTitles();

		if (enabledNames.isEmpty()) {
			enabledNames = List.of("No active modules");
		}

		Font font = client.font;
		int width = calculateWidth(client, enabledNames);
		int height = calculateHeight(client, enabledNames);

		extractor.fill(x - 3, y - 3, x + width + 3, y + height + 3, PANEL_BACKGROUND);
		extractor.outline(x - 3, y - 3, width + 6, height + 6, PANEL_OUTLINE);
		extractor.outline(x - 1, y - 1, width + 2, height + 2, PANEL_INNER);
		extractor.fill(x, y, x + width, y + 4, 0x55566068);

		int lineHeight = font.lineHeight + 2;

		for (int i = 0; i < enabledNames.size(); i++) {
			String name = enabledNames.get(i);
			int textX = x + width - 6 - font.width(name);
			int textY = y + (i * lineHeight);
			int color = interpolateGradient(i, enabledNames.size());
			extractor.text(font, Component.literal(name), textX, textY, color, true);
		}
	}

	public static void renderEditorPreview(GuiGraphicsExtractor extractor, Minecraft client, WidgetState widget) {
		int x = widget.x();
		int y = widget.y();
		List<String> enabledNames = ForkClientController.INSTANCE.enabledModuleTitles();

		if (enabledNames.isEmpty()) {
			enabledNames = List.of("No active modules");
		}

		int width = calculateWidth(client, enabledNames);
		int height = calculateHeight(client, enabledNames);

		extractor.fill(x - 3, y - 3, x + width + 3, y + height + 3, PANEL_BACKGROUND);
		extractor.outline(x - 3, y - 3, width + 6, height + 6, PANEL_OUTLINE_HOVER);
		extractor.outline(x - 1, y - 1, width + 2, height + 2, PANEL_INNER);
		extractor.fill(x, y, x + width, y + 4, 0x889DEEFF);

		extractor.text(client.font, Component.literal("Array List"), x, y - client.font.lineHeight - 4, TEXT_MUTED, false);

		int lineHeight = client.font.lineHeight + 2;

		for (int i = 0; i < enabledNames.size(); i++) {
			String name = enabledNames.get(i);
			int textX = x + width - 6 - client.font.width(name);
			int textY = y + (i * lineHeight);
			int color = interpolateGradient(i, enabledNames.size());
			extractor.text(client.font, Component.literal(name), textX, textY, color, true);
		}
	}

	public static int calculateWidth(Minecraft client, List<String> names) {
		int width = 0;
		for (String name : names) {
			width = Math.max(width, client.font.width(name));
		}
		return Math.max(width, client.font.width("No active modules")) + 10;
	}

	public static int calculateHeight(Minecraft client, List<String> names) {
		int lineHeight = client.font.lineHeight + 2;
		return Math.max(client.font.lineHeight + 6, names.size() * lineHeight + 6);
	}

	private static int interpolateGradient(int index, int total) {
		if (total <= 1) {
			return GRADIENT_PALETTE[0];
		}

		float ratio = (float) index / (float) (total - 1);
		float scaledRatio = ratio * (GRADIENT_PALETTE.length - 1);
		int colorIndex = (int) scaledRatio;
		float fraction = scaledRatio - colorIndex;

		if (colorIndex >= GRADIENT_PALETTE.length - 1) {
			return GRADIENT_PALETTE[GRADIENT_PALETTE.length - 1];
		}

		int colorA = GRADIENT_PALETTE[colorIndex];
		int colorB = GRADIENT_PALETTE[colorIndex + 1];

		int rA = (colorA >> 16) & 0xFF;
		int gA = (colorA >> 8) & 0xFF;
		int bA = colorA & 0xFF;

		int rB = (colorB >> 16) & 0xFF;
		int gB = (colorB >> 8) & 0xFF;
		int bB = colorB & 0xFF;

		int r = (int) (rA + (rB - rA) * fraction);
		int g = (int) (gA + (gB - gA) * fraction);
		int b = (int) (bA + (bB - bA) * fraction);

		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
