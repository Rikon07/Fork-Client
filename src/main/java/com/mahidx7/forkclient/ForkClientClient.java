package com.mahidx7.forkclient;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.modules.MotionBlurPlusModule;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import com.mahidx7.forkclient.network.ForkClientHandshakePayload;
import com.mahidx7.forkclient.network.MovementPermissionsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ForkClientClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MovementPermissionsPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> FeaturePermissions.applyPermissions(payload));
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(ForkClientHandshakePayload.TYPE)) {
                ForkClientHandshakePayload handshake = new ForkClientHandshakePayload(ForkClient.MOD_VERSION);
                ClientPlayNetworking.send(handshake);
                ForkClient.LOGGER.info("Fork Client handshake sent to server.");
            } else {
                ForkClient.LOGGER.info("Server does not support Fork Client handshake; features disabled.");
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            client.execute(FeaturePermissions::reset);
        });

        ForkClientController.INSTANCE.initialize();
        MotionBlurPlusModule.initialize();
        ForkClient.LOGGER.info("Fork Client initialized.");
    }
}
