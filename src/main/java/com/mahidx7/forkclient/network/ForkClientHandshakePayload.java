package com.mahidx7.forkclient.network;

import com.mahidx7.forkclient.ForkClient;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client-to-server handshake payload.
 *
 * <p>The client sends this immediately after joining a server to announce that
 * Fork Client is present and to request permission for its feature categories.
 * The server <em>must</em> respond with a {@link MovementPermissionsPayload}
 * before any client features activate.
 *
 * <p>The body carries the mod version so the server can make compatibility
 * decisions.  The wire format is a single length-prefixed (int) string.
 */
public record ForkClientHandshakePayload(String modVersion) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ForkClientHandshakePayload> TYPE =
            new CustomPacketPayload.Type<>(ForkClient.id("handshake"));

    public static final StreamCodec<ByteBuf, ForkClientHandshakePayload> STREAM_CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                byte[] bytes = payload.modVersion != null
                        ? payload.modVersion.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                        : new byte[0];
                buf.writeInt(bytes.length);
                buf.writeBytes(bytes);
            },
            buf -> {
                int len = buf.readInt();
                if (len < 0 || len > 256) {
                    throw new IllegalArgumentException("Invalid handshake payload length: " + len);
                }
                byte[] bytes = new byte[len];
                buf.readBytes(bytes);
                return new ForkClientHandshakePayload(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
