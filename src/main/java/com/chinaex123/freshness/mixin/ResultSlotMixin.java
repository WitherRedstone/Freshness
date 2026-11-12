package com.chinaex123.freshness.mixin;

import com.chinaex123.freshness.util.FreshnessUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
    @Shadow
    @Final
    private CraftingContainer craftSlots;

    public boolean mayPickup(Player player) {
        if (!player.level().isClientSide()
                && FreshnessUtil.hasRottenIngredient(craftSlots, player.level().getGameTime())) {
            FreshnessUtil.notifyRottenCraft(player);
            return false;
        }
        return true;
    }
}
