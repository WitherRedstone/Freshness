package com.chinaex123.freshness.mixin;

import com.chinaex123.freshness.util.FreshnessUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
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

    @Inject(
            method = "slotChangedCraftingGrid",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/ResultContainer;setItem(ILnet/minecraft/world/item/ItemStack;)V",
                    shift = At.Shift.AFTER
            )
    )
    private static void freshness$markCraftedPreview(AbstractContainerMenu menu, Level level, Player player,
                                                     CraftingContainer craftSlots, ResultContainer resultSlots,
                                                     RecipeHolder<CraftingRecipe> recipe, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }

        ItemStack result = resultSlots.getItem(0);
        if (FreshnessUtil.isPerishable(result)) {
            FreshnessUtil.markCraftedFromIngredients(result, craftSlots, level.getGameTime());
        }
    }
}
