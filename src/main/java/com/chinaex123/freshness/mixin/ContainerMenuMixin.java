package com.chinaex123.freshness.mixin;

import com.chinaex123.freshness.util.FreshnessUtil;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuMixin {
    @Unique
    private int[] freshness$oldCounts;

    @Unique
    private ItemStack freshness$sourceSnapshot;

    @Inject(method = "moveItemStackTo", at = @At("HEAD"))
    private void freshness$captureSlots(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        int[] counts = new int[menu.slots.size()];
        for (int i = 0; i < menu.slots.size(); i++) {
            counts[i] = menu.slots.get(i).getItem().getCount();
        }
        this.freshness$oldCounts = counts;
        this.freshness$sourceSnapshot = stack.copy();
    }

    @Inject(method = "moveItemStackTo", at = @At("RETURN"))
    private void freshness$averageMergedSlots(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection, CallbackInfoReturnable<Boolean> cir) {
        int[] oldCounts = this.freshness$oldCounts;
        ItemStack source = this.freshness$sourceSnapshot;
        this.freshness$oldCounts = null;
        this.freshness$sourceSnapshot = null;

        if (oldCounts == null || source == null || source.isEmpty()) {
            return;
        }

        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        int slotCount = Math.min(oldCounts.length, menu.slots.size());
        for (int i = 0; i < slotCount; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack destination = slot.getItem();
            int moved = destination.getCount() - oldCounts[i];
            if (moved > 0 && oldCounts[i] > 0) {
                FreshnessUtil.averageMergedTime(destination, oldCounts[i], source, moved);
            }
        }
    }
}
