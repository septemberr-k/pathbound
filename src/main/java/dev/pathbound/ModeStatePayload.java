package dev.pathbound;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: the world's difficulty id ("none" while undecided) and whether this player may choose it. */
public record ModeStatePayload(String mode, boolean canChoose) implements CustomPacketPayload {
    public static final Type<ModeStatePayload> TYPE = new Type<>(Pathbound.id("mode_state"));
    public static final StreamCodec<ByteBuf, ModeStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), ModeStatePayload::mode,
            ByteBufCodecs.BOOL, ModeStatePayload::canChoose,
            ModeStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
