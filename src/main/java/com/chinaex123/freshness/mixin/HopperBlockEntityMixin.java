package com.chinaex123.freshness.mixin;

import com.chinaex123.freshness.util.FreshnessUtil;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Unique
    private static final ThreadLocal<ItemStack> freshness$sourceSnapshot = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<Integer> freshness$oldCount = new ThreadLocal<>();

    @Inject(method = "tryMoveInItem", at = @At("HEAD"))
    private static void freshness$captureMove(Container source, Container destination, ItemStack stack,
                                              int slot, Direction direction, CallbackInfoReturnable<ItemStack> cir) {
        freshness$sourceSnapshot.set(stack.copy());
        freshness$oldCount.set(destination.getItem(slot).getCount());
    }

    @Inject(method = "tryMoveInItem", at = @At("RETURN"))
    private static void freshness$averageMove(Container source, Container destination, ItemStack stack,
                                              int slot, Direction direction,
                                              CallbackInfoReturnable<ItemStack> cir) {
        ItemStack sourceSnapshot = freshness$sourceSnapshot.get();
        Integer oldCount = freshness$oldCount.get();
        freshness$sourceSnapshot.remove();
        freshness$oldCount.remove();
        if (sourceSnapshot == null || oldCount == null || sourceSnapshot.isEmpty()) {
            return;
        }

        ItemStack destinationStack = destination.getItem(slot);
        int moved = destinationStack.getCount() - oldCount;
        if (moved > 0 && oldCount > 0) {
            FreshnessUtil.averageMergedTime(destinationStack, oldCount, sourceSnapshot, moved);
        }
    }
}
