package com.chinaex123.freshness.util;

import com.chinaex123.freshness.data.FreshnessData;
import com.chinaex123.freshness.data.FreshnessStage;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class FreshnessEffects {
    private static final int AMBIENT_INTERVAL = 10;

    private FreshnessEffects() {
    }

    public static void tickItem(ItemEntity entity) {
        Level level = entity.level();
        if (!level.isClientSide() || entity.tickCount % AMBIENT_INTERVAL != 0) {
            return;
        }

        ItemStack stack = entity.getItem();
        if (!FreshnessUtil.isFood(stack)) {
            return;
        }

        FreshnessStage stage = FreshnessData.of(stack)
                .advance(level.getGameTime(), FreshnessUtil.stageDurationTicks(stack))
                .stage();
        ParticleOptions particle = ambientParticle(stage);
        if (particle == null) {
            return;
        }

        RandomSource random = level.random;
        double x = entity.getX() + (random.nextDouble() - 0.5D) * 0.5D;
        double y = entity.getY() + 0.35D + random.nextDouble() * 0.35D;
        double z = entity.getZ() + (random.nextDouble() - 0.5D) * 0.5D;
        level.addParticle(particle, x, y, z, 0.0D, 0.015D, 0.0D);
    }

    public static void onEat(Level level, LivingEntity entity, ItemStack stack, FreshnessStage stage) {
        if (level.isClientSide()) {
            spawnEatParticlesLocal(level, entity, stage);
        } else if (level instanceof ServerLevel serverLevel) {
            playEatSound(serverLevel, entity, stage);
            spawnEatParticlesServer(serverLevel, entity, stage);
        }
    }

    private static ParticleOptions ambientParticle(FreshnessStage stage) {
        return switch (stage) {
            case FRESH -> ParticleTypes.HAPPY_VILLAGER;
            case DECENT -> null;
            case NEAR_EXPIRY -> ParticleTypes.SMOKE;
            case ROTTEN -> ParticleTypes.ASH;
        };
    }

    private static ParticleOptions eatParticle(FreshnessStage stage) {
        return switch (stage) {
            case FRESH -> ParticleTypes.HAPPY_VILLAGER;
            case DECENT -> ParticleTypes.POOF;
            case NEAR_EXPIRY -> ParticleTypes.SMOKE;
            case ROTTEN -> ParticleTypes.ASH;
        };
    }

    private static int eatParticleCount(FreshnessStage stage) {
        return switch (stage) {
            case FRESH -> 5;
            case DECENT -> 3;
            case NEAR_EXPIRY -> 4;
            case ROTTEN -> 7;
        };
    }

    private static void spawnEatParticlesLocal(Level level, LivingEntity entity, FreshnessStage stage) {
        ParticleOptions particle = eatParticle(stage);
        int count = eatParticleCount(stage);
        RandomSource random = level.random;
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.75D;
        double z = entity.getZ();

        for (int i = 0; i < count; i++) {
            level.addParticle(
                    particle,
                    x + (random.nextDouble() - 0.5D) * 0.45D,
                    y + (random.nextDouble() - 0.5D) * 0.25D,
                    z + (random.nextDouble() - 0.5D) * 0.45D,
                    (random.nextDouble() - 0.5D) * 0.08D,
                    0.02D,
                    (random.nextDouble() - 0.5D) * 0.08D
            );
        }
    }

    private static void spawnEatParticlesServer(ServerLevel level, LivingEntity entity, FreshnessStage stage) {
        ParticleOptions particle = eatParticle(stage);
        int count = eatParticleCount(stage);
        level.sendParticles(
                particle,
                entity.getX(),
                entity.getY() + entity.getBbHeight() * 0.75D,
                entity.getZ(),
                count,
                0.25D,
                0.2D,
                0.25D,
                0.02D
        );
    }

    private static void playEatSound(Level level, LivingEntity entity, FreshnessStage stage) {
        SoundEvent sound = switch (stage) {
            case FRESH -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case DECENT -> null;
            case NEAR_EXPIRY -> SoundEvents.GENERIC_DRINK;
            case ROTTEN -> SoundEvents.PLAYER_HURT;
        };
        if (sound == null) {
            return;
        }

        float volume = switch (stage) {
            case FRESH -> 0.25F;
            case NEAR_EXPIRY -> 0.20F;
            case ROTTEN -> 0.25F;
            case DECENT -> 0.0F;
        };
        float pitch = switch (stage) {
            case FRESH -> 1.45F;
            case NEAR_EXPIRY -> 0.65F;
            case ROTTEN -> 0.55F;
            case DECENT -> 1.0F;
        };

        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                sound, SoundSource.PLAYERS, volume, pitch);
    }
}
