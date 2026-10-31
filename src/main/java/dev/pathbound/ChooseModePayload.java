package dev.pathbound;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the host picked a difficulty. */
public record ChooseModePayload(String mode) implements CustomPacketPayload {
    public static final Type<ChooseModePayload> TYPE = new Type<>(Pathbound.id("choose_mode"));
    public static final StreamCodec<ByteBuf, ChooseModePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), ChooseModePayload::mode,
            ChooseModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
