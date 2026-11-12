package com.chinaex123.freshness.mixin;

import com.chinaex123.freshness.data.FreshnessData;
import com.chinaex123.freshness.util.FreshnessEffects;
import com.chinaex123.freshness.util.FreshnessUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @ModifyVariable(
            method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"),
            argsOnly = true,
            index = 3
    )
    private FoodProperties freshness$modifyFood(FoodProperties food, Level level, ItemStack stack) {
        if (!FreshnessUtil.isFood(stack)) {
            return food;
        }

        FreshnessData data = FreshnessData.of(stack);
        FreshnessData advanced = data.advance(level.getGameTime(), FreshnessUtil.stageDurationTicks(stack));
        FreshnessUtil.setFreshness(stack, advanced);

        FreshnessEffects.onEat(level, (Player) (Object) this, stack, advanced.stage());
        FoodProperties synced = stack.get(DataComponents.FOOD);
        return synced != null ? synced : FreshnessUtil.modify(food, advanced);
    }
}
