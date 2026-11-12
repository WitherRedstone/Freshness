package com.chinaex123.freshness.init;

import com.chinaex123.freshness.Freshness;
import com.chinaex123.freshness.data.FreshnessData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public interface ModDataComponents {
    DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Freshness.MODID);

    /** 食物保质期组件 */
    DeferredHolder<DataComponentType<?>, DataComponentType<FreshnessData>> FRESHNESS =
            DATA_COMPONENTS.registerComponentType("freshness", builder -> builder
                    .persistent(FreshnessData.CODEC)
                    .networkSynchronized(FreshnessData.STREAM_CODEC));

    /** 食物原始的饥饿值/饱和度等属性。*/
    DeferredHolder<DataComponentType<?>, DataComponentType<FoodProperties>> BASE_FOOD =
            DATA_COMPONENTS.registerComponentType("base_food", builder -> builder
                    .persistent(FoodProperties.DIRECT_CODEC)
                    .networkSynchronized(FoodProperties.DIRECT_STREAM_CODEC));

    static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
