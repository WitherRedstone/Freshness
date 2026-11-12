package com.chinaex123.freshness.client;

import com.chinaex123.freshness.Freshness;
import com.chinaex123.freshness.data.FreshnessData;
import com.chinaex123.freshness.util.FreshnessUtil;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = Freshness.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FreshnessClientEvents {
    private static final int ROTTEN_TINT = 0xFF6E6E6E;
    private static final int NORMAL_TINT = 0xFFFFFFFF;

    private FreshnessClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        List<Item> perishables = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (FreshnessUtil.isPerishable(item.getDefaultInstance())) {
                perishables.add(item);
            }
        }
        if (perishables.isEmpty()) {
            return;
        }

        ItemColor color = (stack, tintIndex) ->
                FreshnessData.of(stack).isRotten() ? ROTTEN_TINT : NORMAL_TINT;
        event.register(color, perishables.toArray(Item[]::new));
    }
}
