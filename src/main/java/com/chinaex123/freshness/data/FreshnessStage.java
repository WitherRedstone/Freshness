package com.chinaex123.freshness.data;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum FreshnessStage implements StringRepresentable {
    /** 新鲜 */
    FRESH("fresh"),
    /** 尚可 */
    DECENT("decent"),
    /** 临期 */
    NEAR_EXPIRY("near_expiry"),
    /** 腐烂 */
    ROTTEN("rotten");

    public static final Codec<FreshnessStage> CODEC = StringRepresentable.fromEnum(FreshnessStage::values);
    public static final StreamCodec<ByteBuf, FreshnessStage> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            id -> values()[Math.floorMod(id, values().length)],
            FreshnessStage::ordinal
    );

    private final String id;

    FreshnessStage(String id) {
        this.id = id;
    }

    @Override
    public @NotNull String getSerializedName() {
        return this.id;
    }

    public String getId() {
        return this.id;
    }

    public Component getDisplayName() {
        return Component.translatable("freshness.stage." + this.id);
    }

    public ChatFormatting getColor() {
        return switch (this) {
            case FRESH -> ChatFormatting.GREEN;
            case DECENT -> ChatFormatting.WHITE;
            case NEAR_EXPIRY -> ChatFormatting.DARK_AQUA;
            case ROTTEN -> ChatFormatting.DARK_GRAY;
        };
    }

    public String getTranslationKey() {
        return "freshness.stage." + this.id;
    }
}
