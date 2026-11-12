package com.chinaex123.freshness.mixin;

import com.chinaex123.freshness.util.FreshnessUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Inject(method = "addResource(ILnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"))
    private void freshness$averageShelfLife(int slot, ItemStack incoming,
                                            CallbackInfoReturnable<Integer> cir) {
        Inventory inventory = (Inventory) (Object) this;
        ItemStack destination = inventory.getItem(slot);
        if (destination.isEmpty() || !ItemStack.isSameItemSameComponents(destination, incoming)) {
            return;
        }

        int space = destination.getMaxStackSize() - destination.getCount();
        int moved = Math.min(incoming.getCount(), space);
        if (moved > 0) {
            FreshnessUtil.averageMergedTime(destination, destination.getCount(), incoming, moved);
        }
    }
}
