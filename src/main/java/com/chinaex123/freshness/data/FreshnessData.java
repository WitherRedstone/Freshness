package com.chinaex123.freshness.data;

import com.chinaex123.freshness.init.ModDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record FreshnessData(FreshnessStage stage, long expiresAt) {
    public static final long STAGE_DURATION_TICKS = 7L * 24000L;

    public static final Codec<FreshnessData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FreshnessStage.CODEC.fieldOf("stage").forGetter(FreshnessData::stage),
            Codec.LONG.fieldOf("expires_at").forGetter(FreshnessData::expiresAt)
    ).apply(instance, FreshnessData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FreshnessData> STREAM_CODEC = StreamCodec.composite(
            FreshnessStage.STREAM_CODEC, FreshnessData::stage,
            ByteBufCodecs.VAR_LONG, FreshnessData::expiresAt,
            FreshnessData::new
    );

    public static FreshnessData fixed(FreshnessStage stage) {
        return new FreshnessData(stage, -1L);
    }

    public static FreshnessData fresh(long now) {
        return new FreshnessData(FreshnessStage.FRESH, now + STAGE_DURATION_TICKS);
    }

    public static FreshnessData fresh(long now, long totalShelfLifeTicks) {
        long stageDuration = Math.max(1L, totalShelfLifeTicks / 3L);
        return new FreshnessData(FreshnessStage.FRESH, now + stageDuration);
    }

    public static FreshnessData of(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.FRESHNESS.get(), fixed(FreshnessStage.DECENT));
    }

    public boolean isFixed() {
        return this.expiresAt < 0L;
    }

    public boolean isRotten() {
        return this.stage == FreshnessStage.ROTTEN;
    }

    public FreshnessData advance(long now) {
        return advance(now, STAGE_DURATION_TICKS);
    }

    public FreshnessData advance(long now, long stageDurationTicks) {
        long stageDuration = Math.max(1L, stageDurationTicks);
        if (this.isRotten() || this.isFixed() || now < this.expiresAt) {
            return this;
        }

        long elapsed = now - this.expiresAt;
        int steps = 1 + (int) (elapsed / stageDuration);
        int next = Math.min(FreshnessStage.ROTTEN.ordinal(), this.stage.ordinal() + steps);

        if (next >= FreshnessStage.ROTTEN.ordinal()) {
            return fixed(FreshnessStage.ROTTEN);
        }

        return new FreshnessData(FreshnessStage.values()[next],
                this.expiresAt + (long) steps * stageDuration);
    }

    public long remaining(long now) {
        if (this.isFixed()) {
            return 0L;
        }
        return Math.max(0L, this.expiresAt - now);
    }

    public long remainingTotal(long now, long stageDurationTicks) {
        if (this.isFixed() || this.isRotten()) {
            return 0L;
        }
        long stageDuration = Math.max(1L, stageDurationTicks);
        return this.remaining(now) + (long) (2 - this.stage.ordinal()) * stageDuration;
    }

    public static void averageInto(ItemStack target, ItemStack first, int firstCount, ItemStack second, int secondCount) {
        FreshnessData a = of(first);
        FreshnessData b = of(second);
        if (a.stage() != b.stage()) {
            return;
        }

        FreshnessData merged;
        if (a.isFixed() && b.isFixed()) {
            merged = a;
        } else if (a.isFixed()) {
            merged = b;
        } else if (b.isFixed()) {
            merged = a;
        } else {
            long total = (long) firstCount + secondCount;
            if (total <= 0L) {
                return;
            }
            long weighted = Math.round((a.expiresAt() * (double) firstCount + b.expiresAt() * (double) secondCount) / (double) total);
            merged = new FreshnessData(a.stage(), weighted);
        }

        target.set(ModDataComponents.FRESHNESS.get(), merged);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof FreshnessData data && this.stage == data.stage;
    }

    @Override
    public int hashCode() {
        return this.stage.hashCode();
    }
}
