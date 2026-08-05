package com.mahidx7.forkclient;

import com.mahidx7.forkclient.network.ForkClientHandshakePayload;
import com.mahidx7.forkclient.network.MovementPermissionsPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForkClient implements ModInitializer {
    public static final String MOD_ID = "fork-client";
    public static final String MOD_VERSION = "1.1.0+26.2";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(
                MovementPermissionsPayload.TYPE, MovementPermissionsPayload.STREAM_CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
                ForkClientHandshakePayload.TYPE, ForkClientHandshakePayload.STREAM_CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(ForkClientHandshakePayload.TYPE, (payload, context) -> {
            MinecraftServer server = context.server();
            server.execute(() -> {
                ServerPlayer player = context.player();
                if (player == null) {
                    LOGGER.warn("Received handshake from null player");
                    return;
                }
                LOGGER.info("Fork Client handshake from {} (version {})",
                        player.getGameProfile().name(), payload.modVersion());

                MovementPermissionsPayload response = new MovementPermissionsPayload(
                        true,
                        true,
                        true
                );
                ServerPlayNetworking.send(player, response);
            });
        });

        LOGGER.info("Fork Client common bootstrap ready.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
