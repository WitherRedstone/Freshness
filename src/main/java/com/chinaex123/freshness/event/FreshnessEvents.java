package com.chinaex123.freshness.event;

import com.chinaex123.freshness.data.FreshnessData;
import com.chinaex123.freshness.init.ModDataComponents;
import com.chinaex123.freshness.util.FreshnessUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

public class FreshnessEvents {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(EventPriority.LOWEST, FreshnessEvents::onCreativeTab);

        NeoForge.EVENT_BUS.addListener(FreshnessEvents::onTooltip);
        NeoForge.EVENT_BUS.addListener(FreshnessEvents::onCrafted);
        NeoForge.EVENT_BUS.addListener(FreshnessEvents::onSmelted);
        NeoForge.EVENT_BUS.addListener(FreshnessEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(FreshnessEvents::onContainerOpen);
    }

    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!FreshnessUtil.isPerishable(stack)) {
            return;
        }

        Player player = event.getEntity();
        long now = player != null ? player.level().getGameTime() : 0L;
        long stageDuration = FreshnessUtil.stageDurationTicks(stack);
        FreshnessData data = FreshnessData.of(stack).advance(now, stageDuration);

        event.getToolTip().add(Component.translatable("tooltip.freshness.stage",
                data.stage().getDisplayName()).withStyle(data.stage().getColor()));

        net.minecraft.network.chat.MutableComponent expiry;
        if (data.isRotten()) {
            expiry = Component.translatable("tooltip.freshness.expiry.rotten");
        } else if (data.isFixed()) {
            expiry = Component.translatable("tooltip.freshness.expiry.fixed");
        } else {
            expiry = Component.translatable("tooltip.freshness.expiry",
                    FreshnessUtil.formatShelfLife(data.remainingTotal(now, stageDuration)));
        }
        event.getToolTip().add(expiry.withStyle(ChatFormatting.DARK_GRAY));
    }

    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        FreshnessUtil.markCraftedFromIngredients(event.getCrafting(), event.getInventory(),
                event.getEntity().level().getGameTime());
    }

    public static void onSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        FreshnessUtil.markCrafted(event.getSmelting(), event.getEntity().level().getGameTime());
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || player.tickCount % 20 != 0) {
            return;
        }

        long now = player.level().getGameTime();
        normalize(player.getInventory().items, now);
        normalize(player.getInventory().armor, now);
        normalize(player.getInventory().offhand, now);
    }

    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        long now = event.getEntity().level().getGameTime();
        for (Slot slot : event.getContainer().slots) {
            FreshnessUtil.normalize(slot.getItem(), now);
        }
    }

    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        List<ItemStack> entries = List.copyOf(event.getParentEntries());
        for (ItemStack original : entries) {
            if (!FreshnessUtil.isFood(original) || original.has(ModDataComponents.FRESHNESS.get())) {
                continue;
            }

            ItemStack replacement = FreshnessUtil.fixedDecentCopy(original);
            CreativeModeTab.TabVisibility visibility = event.getSearchEntries().contains(original)
                    ? CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
                    : CreativeModeTab.TabVisibility.PARENT_TAB_ONLY;

            // 组件比较只检查阶段，因此要先确认物品栏里没有同阶段的替代品，避免重复。
            if (event.getParentEntries().contains(replacement)) {
                event.remove(original, visibility);
                continue;
            }

            event.insertAfter(original, replacement, visibility);
            event.remove(original, visibility);
        }
    }

    private static void normalize(List<ItemStack> stacks, long now) {
        for (ItemStack stack : stacks) {
            FreshnessUtil.normalize(stack, now);
        }
    }
}
