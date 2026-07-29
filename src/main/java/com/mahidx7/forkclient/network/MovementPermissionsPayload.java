package com.mahidx7.forkclient.network;

import com.mahidx7.forkclient.ForkClient;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server-to-client permission payload.
 *
 * <p>Sent once in response to a {@link ForkClientHandshakePayload} to tell the
 * client which feature categories the server opts in to. All flags default to
 * {@code false} so that features are <em>disabled until the server explicitly
 * permits them</em>.
 *
 * @param movementAllowed  whether movement-affecting modules are allowed
 * @param renderingAllowed whether rendering-modification modules are allowed
 * @param utilityAllowed   whether utility/automation modules are allowed
 */
public record MovementPermissionsPayload(
        boolean movementAllowed,
        boolean renderingAllowed,
        boolean utilityAllowed
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MovementPermissionsPayload> TYPE =
            new CustomPacketPayload.Type<>(ForkClient.id("movement_permissions"));

    /**
     * Stream codec that writes/reads three boolean flags in a single byte,
     * keeping the wire format compact and forward-compatible.
     */
    public static final StreamCodec<ByteBuf, MovementPermissionsPayload> STREAM_CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                byte flags = 0;
                if (payload.movementAllowed)  flags |= 0x01;
                if (payload.renderingAllowed) flags |= 0x02;
                if (payload.utilityAllowed)   flags |= 0x04;
                buf.writeByte(flags);
            },
            buf -> {
                byte flags = buf.readByte();
                return new MovementPermissionsPayload(
                        (flags & 0x01) != 0,
                        (flags & 0x02) != 0,
                        (flags & 0x04) != 0
                );
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
