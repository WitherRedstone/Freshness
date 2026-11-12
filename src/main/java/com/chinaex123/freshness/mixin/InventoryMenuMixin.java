package com.chinaex123.freshness.mixin;

import com.chinaex123.freshness.util.FreshnessUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {
    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void freshness$blockRottenQuickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index == 0 && !player.level().isClientSide()
                && FreshnessUtil.hasRottenIngredient(craftSlots, player.level().getGameTime())) {
            FreshnessUtil.notifyRottenCraft(player);
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
