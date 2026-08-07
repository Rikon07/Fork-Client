package com.mahidx7.forkclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MotionBlurOptionsScreen extends Screen {
    private final Screen parent;
    private StrengthSliderWidget sliderWidget;
    private Button toggleButton;
    private boolean enabled;

    public MotionBlurOptionsScreen(Screen parent) {
        super(Component.translatable("screen.fork-client.motion_blur_plus.title"));
        this.parent = parent;
        this.enabled = MotionBlurPlusModule.isEnabled();
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 100;
        int centerY = this.height / 2;
        this.sliderWidget = this.addRenderableWidget(
                new StrengthSliderWidget(left, centerY - 30, 200, 20, MotionBlurPlusModule.getStrength()));
        this.toggleButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.enabled = !this.enabled;
            this.refreshToggleLabel();
        }).bounds(left, centerY, 200, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.fork-client.motion_blur_plus.apply"),
                button -> this.applyAndClose()).bounds(left, centerY + 28, 200, 20).build());
        this.refreshToggleLabel();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int titleY = this.height / 2 - 72;
        context.centeredText(this.font, this.title, this.width / 2, titleY, 0xFFFFFF);
        context.centeredText(this.font,
                Component.translatable("screen.fork-client.motion_blur_plus.subtitle"),
                this.width / 2, titleY + 14, 0xA0A0A0);
        context.centeredText(this.font,
                Component.translatable("screen.fork-client.motion_blur_plus.current",
                        this.sliderWidget != null ? this.sliderWidget.getStrengthValue() : MotionBlurPlusModule.getStrength(),
                        this.enabledStateText()),
                this.width / 2, titleY + 30, 0xD0D0D0);
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

    private void applyAndClose() {
        int strength = this.sliderWidget != null ? this.sliderWidget.getStrengthValue() : MotionBlurPlusModule.getStrength();
        MotionBlurPlusModule.applySettings(this.enabled, strength);
        this.onClose();
    }

    private void refreshToggleLabel() {
        if (this.toggleButton != null) {
            this.toggleButton.setMessage(Component.translatable(
                    "screen.fork-client.motion_blur_plus.toggle", this.enabledStateText()));
        }
    }

    private Component enabledStateText() {
        return Component.translatable(this.enabled
                ? "screen.fork-client.motion_blur_plus.enabled"
                : "screen.fork-client.motion_blur_plus.disabled");
    }

    private static final class StrengthSliderWidget extends AbstractSliderButton {
        private int strengthValue;

        private StrengthSliderWidget(int x, int y, int width, int height, int initialValue) {
            super(x, y, width, height, Component.empty(), (double) initialValue / 100.0);
            this.strengthValue = initialValue;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable("screen.fork-client.motion_blur_plus.slider", this.strengthValue));
        }

        @Override
        protected void applyValue() {
            this.strengthValue = (int) Math.round(this.value * 100.0);
            this.updateMessage();
        }

        private int getStrengthValue() {
            return this.strengthValue;
        }
    }
}
