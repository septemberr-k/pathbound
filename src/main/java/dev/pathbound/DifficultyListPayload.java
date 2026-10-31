package dev.pathbound;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: the difficulties for the chooser, as JSON (see Difficulty#toClientJson). */
public record DifficultyListPayload(String json) implements CustomPacketPayload {
    public static final Type<DifficultyListPayload> TYPE = new Type<>(Pathbound.id("difficulties"));
    public static final StreamCodec<ByteBuf, DifficultyListPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(1 << 20), DifficultyListPayload::json,
            DifficultyListPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
