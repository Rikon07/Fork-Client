package com.mahidx7.forkclient.client.modules;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

@SuppressWarnings("deprecation")
public final class BlurAssetsReloader implements IdentifiableResourceReloadListener, ResourceManagerReloadListener {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("fork-client", "motion_blur_plus_assets");

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        MotionBlurPlusRenderer.invalidate();
    }
}
