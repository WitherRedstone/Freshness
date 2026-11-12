package com.chinaex123.freshness.mixin;

import com.chinaex123.freshness.util.FreshnessEffects;
import com.chinaex123.freshness.util.FreshnessUtil;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Unique
    private static final ThreadLocal<Integer> freshness$destinationCount = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<ItemStack> freshness$sourceSnapshot = new ThreadLocal<>();

    @Inject(method = "setItem", at = @At("HEAD"))
    private void freshness$initializeShelfLife(ItemStack stack, CallbackInfo ci) {
        ItemEntity entity = (ItemEntity) (Object) this;
        if (!entity.level().isClientSide()) {
            FreshnessUtil.normalize(stack, entity.level().getGameTime());
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void freshness$ambientParticles(CallbackInfo ci) {
        ItemEntity entity = (ItemEntity) (Object) this;
        FreshnessEffects.tickItem(entity);
        if (!entity.level().isClientSide() && entity.tickCount % 20 == 0) {
            FreshnessUtil.normalize(entity.getItem(), entity.level().getGameTime());
        }
    }

    @Inject(
            method = "merge(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD")
    )
    private static void freshness$captureDestination(ItemStack destination, ItemStack origin, int amount,
                                                     CallbackInfoReturnable<ItemStack> cir) {
        freshness$destinationCount.set(destination.getCount());
        freshness$sourceSnapshot.set(origin.copy());
    }

    @Inject(
            method = "merge(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN")
    )
    private static void freshness$averageShelfLife(ItemStack destination, ItemStack origin, int amount,
                                                   CallbackInfoReturnable<ItemStack> cir) {
        Integer oldCount = freshness$destinationCount.get();
        freshness$destinationCount.remove();
        ItemStack source = freshness$sourceSnapshot.get();
        freshness$sourceSnapshot.remove();
        if (oldCount == null || source == null) {
            return;
        }

        ItemStack result = cir.getReturnValue();
        int moved = result.getCount() - oldCount;
        if (moved > 0) {
            FreshnessUtil.averageMergedTime(result, oldCount, source, moved);
        }
    }
}
