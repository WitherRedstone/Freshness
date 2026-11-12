package com.chinaex123.freshness.mixin;

import com.chinaex123.freshness.util.FreshnessUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LootTable.class)
public abstract class LootTableMixin {
    @Inject(
            method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At("RETURN")
    )
    private void freshness$stampLoot(LootContext context,
                                     CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir) {
        ObjectArrayList<ItemStack> stacks = cir.getReturnValue();
        if (stacks == null || stacks.isEmpty()) {
            return;
        }

        ServerLevel level = context.getLevel();
        long gameTime = level.getGameTime();

        Entity source = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        boolean mobDrop = source instanceof LivingEntity && !(source instanceof Player);

        for (ItemStack stack : stacks) {
            if (mobDrop) {
                FreshnessUtil.markFresh(stack, gameTime);
            } else {
                FreshnessUtil.markLoot(stack, context.getRandom(), gameTime);
            }
        }
    }
}
