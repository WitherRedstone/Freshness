package com.chinaex123.freshness;

import com.chinaex123.freshness.event.FreshnessEvents;
import com.chinaex123.freshness.init.ModDataComponents;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Freshness.MODID)
public class Freshness {
    public static final String MODID = "freshness";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Freshness(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.register(modEventBus);
        FreshnessEvents.register(modEventBus);
    }
}
