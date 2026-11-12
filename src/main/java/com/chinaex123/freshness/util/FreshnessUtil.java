package com.chinaex123.freshness.util;

import com.chinaex123.freshness.Freshness;
import com.chinaex123.freshness.data.FreshnessData;
import com.chinaex123.freshness.data.FreshnessStage;
import com.chinaex123.freshness.init.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class FreshnessUtil {
    public static final long STAGE_DURATION_TICKS = FreshnessData.STAGE_DURATION_TICKS;
    public static final long DAY_TICKS = 24000L;

    /** 肉类：总保质期 7 天。 */
    public static final TagKey<Item> MEAT_FOOD = tag("meat_food");
    /** 农作物/果蔬：总保质期 15 天。 */
    public static final TagKey<Item> CROP_FOOD = tag("crop_food");
    /** 其他易腐物品（含部分非食物原料）：总保质期 20 天。 */
    public static final TagKey<Item> PERISHABLE = tag("perishable");

    private static final long MEAT_SHELF_LIFE_TICKS = 7L * DAY_TICKS;
    private static final long CROP_SHELF_LIFE_TICKS = 15L * DAY_TICKS;
    private static final long DEFAULT_SHELF_LIFE_TICKS = 20L * DAY_TICKS;

    private static final float FRESH_FACTOR = 1.25F;
    private static final float DECENT_FACTOR = 1.0F;
    private static final float NEAR_EXPIRY_FACTOR = 0.75F;
    private static final float ROTTEN_FACTOR = 0.5F;
    private static final float ROTTEN_EFFECT_CHANCE = 0.4F;
    private static final FoodProperties.PossibleEffect ROTTEN_EFFECT =
            new FoodProperties.PossibleEffect(FreshnessUtil::randomRottenEffect, ROTTEN_EFFECT_CHANCE);

    private FreshnessUtil() {
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(Freshness.MODID, path));
    }

    public static boolean isFood(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.has(DataComponents.FOOD)) {
            return true;
        }
        try {
            return stack.getFoodProperties(null) != null;
        } catch (RuntimeException ignored) {
            // 某些模组实现假设实体一定非空；这种情况下不要因为兼容探测而崩溃。
            return false;
        }
    }

    public static boolean isPerishable(ItemStack stack) {
        return isFood(stack) || (!stack.isEmpty() && stack.is(PERISHABLE));
    }

    /**
     * 物品的“总保质期”：从新鲜到腐烂的总时长，不是单个阶段的时长。
     */
    public static long shelfLifeTicks(ItemStack stack) {
        if (stack.is(MEAT_FOOD)) {
            return MEAT_SHELF_LIFE_TICKS;
        }
        if (stack.is(CROP_FOOD)) {
            return CROP_SHELF_LIFE_TICKS;
        }
        return DEFAULT_SHELF_LIFE_TICKS;
    }

    /**
     * 单个阶段（新鲜/尚可/临期）的时长。
     * <p>
     * 腐烂是终点，不占用一个会流逝的阶段，所以总保质期平均分成三份。
     */
    public static long stageDurationTicks(ItemStack stack) {
        return Math.max(1L, shelfLifeTicks(stack) / 3L);
    }

    public static boolean isRotten(ItemStack stack, long gameTime) {
        if (!isPerishable(stack)) {
            return false;
        }
        FreshnessData data = FreshnessData.of(stack).advance(gameTime, stageDurationTicks(stack));
        return data.isRotten();
    }

    public static boolean hasRottenIngredient(Container container, long gameTime) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (isRotten(container.getItem(i), gameTime)) {
                return true;
            }
        }
        return false;
    }

    public static void notifyRottenCraft(Player player) {
        player.displayClientMessage(Component.translatable("message.freshness.cannot_craft_rotten")
                .withStyle(ChatFormatting.RED), true);
    }

    public static FoodProperties modify(FoodProperties original, FreshnessData data) {
        FreshnessStage stage = data.stage();
        if (stage == FreshnessStage.DECENT) {
            return original;
        }

        float factor = switch (stage) {
            case FRESH -> FRESH_FACTOR;
            case DECENT -> DECENT_FACTOR;
            case NEAR_EXPIRY -> NEAR_EXPIRY_FACTOR;
            case ROTTEN -> ROTTEN_FACTOR;
        };

        int nutrition = Math.max(0, Math.round(original.nutrition() * factor));
        float saturation = Math.max(0.0F, original.saturation() * factor);

        List<FoodProperties.PossibleEffect> effects = original.effects();
        if (stage == FreshnessStage.ROTTEN) {
            List<FoodProperties.PossibleEffect> withRotten = new ArrayList<>(effects);
            withRotten.add(ROTTEN_EFFECT);
            effects = withRotten;
        }

        return new FoodProperties(
                nutrition,
                saturation,
                original.canAlwaysEat(),
                original.eatSeconds(),
                original.usingConvertsTo(),
                effects
        );
    }

    public static void setFreshness(ItemStack stack, FreshnessData data) {
        if (stack.isEmpty() || data == null) {
            return;
        }
        stack.set(ModDataComponents.FRESHNESS.get(), data);
        syncFood(stack, data);
    }

    public static void syncFood(ItemStack stack, FreshnessData data) {
        if (!isFood(stack)) {
            return;
        }
        FoodProperties base = stack.get(ModDataComponents.BASE_FOOD.get());
        if (base == null) {
            // 优先保留当前堆叠上的 FOOD，避免覆盖其他模组按堆叠自定义的食物属性；
            FoodProperties current = stack.get(DataComponents.FOOD);
            FoodProperties defaultFood = stack.getItem().getDefaultInstance().get(DataComponents.FOOD);
            base = current != null ? current : defaultFood;
            if (base == null) {
                return;
            }
            stack.set(ModDataComponents.BASE_FOOD.get(), base);
        }

        FoodProperties modified = modify(base, data);
        if (!modified.equals(stack.get(DataComponents.FOOD))) {
            stack.set(DataComponents.FOOD, modified);
        }
    }

    private static MobEffectInstance randomRottenEffect() {
        return switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> new MobEffectInstance(MobEffects.HUNGER, 600, 0);
            case 1 -> new MobEffectInstance(MobEffects.CONFUSION, 300, 0);
            case 2 -> new MobEffectInstance(MobEffects.POISON, 200, 0);
            default -> new MobEffectInstance(MobEffects.WEAKNESS, 600, 0);
        };
    }

    public static void markCrafted(ItemStack stack, long gameTime) {
        markFresh(stack, gameTime);
    }

    public static void markFresh(ItemStack stack, long gameTime) {
        if (isPerishable(stack)) {
            setFreshness(stack, FreshnessData.fresh(gameTime, shelfLifeTicks(stack)));
        }
    }

    /**
     * 让合成产物继承原料的“年龄比例”。
     * <p>
     * 不同原料的总保质期可能不同，直接平均绝对时间没有意义；这里先算出每种原料
     * 已经流逝的比例，再把这个比例应用到产物的总保质期上。这样新鲜原料做出的
     * 产物是新鲜的，放久了的原料做出的产物也会相应变旧。
     */
    public static void markCraftedFromIngredients(ItemStack output, Container ingredients, long gameTime) {
        if (!isPerishable(output)) {
            return;
        }

        double fractionSum = 0.0D;
        int ingredientCount = 0;
        for (int i = 0; i < ingredients.getContainerSize(); i++) {
            ItemStack ingredient = ingredients.getItem(i);
            if (!isPerishable(ingredient)) {
                continue;
            }

            long total = shelfLifeTicks(ingredient);
            long stageDuration = stageDurationTicks(ingredient);
            FreshnessData data = FreshnessData.of(ingredient).advance(gameTime, stageDuration);
            if (data.isFixed() && !data.isRotten()) {
                continue;
            }

            double fraction;
            if (data.isRotten()) {
                fraction = 1.0D;
            } else {
                long created = data.expiresAt() - (data.stage().ordinal() + 1L) * stageDuration;
                long elapsed = Math.clamp(total, 0L, gameTime - created);
                fraction = (double) elapsed / (double) total;
            }
            fractionSum += fraction;
            ingredientCount++;
        }

        if (ingredientCount == 0) {
            markCrafted(output, gameTime);
            return;
        }

        long outputTotal = shelfLifeTicks(output);
        long outputStage = stageDurationTicks(output);
        long elapsed = Math.round(fractionSum / ingredientCount * outputTotal);
        if (elapsed >= outputTotal) {
            setFreshness(output, FreshnessData.fixed(FreshnessStage.ROTTEN));
            return;
        }

        int ordinal = Math.min(FreshnessStage.ROTTEN.ordinal() - 1, (int) (elapsed / outputStage));
        long intoStage = elapsed - (long) ordinal * outputStage;
        long expiresAt = Math.max(gameTime + 1L, gameTime + outputStage - intoStage);
        setFreshness(output, new FreshnessData(FreshnessStage.values()[ordinal], expiresAt));
    }

    public static void markLoot(ItemStack stack, RandomSource random, long gameTime) {
        if (!isPerishable(stack) || stack.has(ModDataComponents.FRESHNESS.get())) {
            return;
        }

        FreshnessStage[] stages = FreshnessStage.values();
        FreshnessStage stage = stages[random.nextInt(stages.length)];
        FreshnessData data;
        if (stage == FreshnessStage.ROTTEN) {
            data = FreshnessData.fixed(stage);
        } else {
            // 在当前阶段内随机取一个时间点，阶段越靠后整体剩余时间越短。
            long stageDuration = stageDurationTicks(stack);
            data = new FreshnessData(stage,
                    gameTime + random.nextInt((int) Math.min(Integer.MAX_VALUE, stageDuration) + 1));
        }
        setFreshness(stack, data);
    }

    public static void normalize(ItemStack stack, long gameTime) {
        if (!isPerishable(stack)) {
            return;
        }
        long stageDuration = stageDurationTicks(stack);
        FreshnessData data = stack.get(ModDataComponents.FRESHNESS.get());
        if (data == null) {
            data = new FreshnessData(FreshnessStage.DECENT, gameTime + stageDuration);
        } else {
            data = data.advance(gameTime, stageDuration);
        }
        setFreshness(stack, data);
    }

    public static void averageMergedTime(ItemStack destination, int destinationOld,
                                         ItemStack source, int moved) {
        FreshnessData.averageInto(destination, destination, destinationOld, source, moved);
        syncFood(destination, FreshnessData.of(destination));
    }

    public static ItemStack fixedDecentCopy(ItemStack original) {
        ItemStack stack = original.copy();
        stack.setCount(1);
        setFreshness(stack, FreshnessData.fixed(FreshnessStage.DECENT));
        return stack;
    }

    /**
     * 工具提示里的保质期：至少一天时向上取整显示天数，不足一天仍显示小时/分钟/秒。
     */
    public static Component formatShelfLife(long remainingTicks) {
        long ticks = Math.max(0L, remainingTicks);
        if (ticks >= DAY_TICKS) {
            long days = (ticks + DAY_TICKS - 1L) / DAY_TICKS;
            return Component.translatable("tooltip.freshness.duration.days", days);
        }
        return formatDuration(ticks);
    }

    public static Component formatDuration(long remainingTicks) {
        long ticks = Math.max(0L, remainingTicks);
        long days = ticks / 24000L;
        long hours = (ticks % 24000L) / 1000L;
        long minutes = ((ticks % 1000L) * 60L) / 1000L;

        // 只显示有意义的单位，避免出现“0天0小时”这种冗长文本。
        if (days > 0L) {
            return hours > 0L
                    ? Component.translatable("tooltip.freshness.duration.days_hours", days, hours)
                    : Component.translatable("tooltip.freshness.duration.days", days);
        }
        if (hours > 0L) {
            return minutes > 0L
                    ? Component.translatable("tooltip.freshness.duration.hours_minutes", hours, minutes)
                    : Component.translatable("tooltip.freshness.duration.hours", hours);
        }
        if (minutes > 0L) {
            return Component.translatable("tooltip.freshness.duration.minutes", minutes);
        }

        // 不足一分钟时改用秒，避免最后阶段一直显示“0分钟”。
        long seconds = Math.clamp(Math.round(ticks * 3600.0D / 1000.0D), 1L, 59L);
        return Component.translatable("tooltip.freshness.duration.seconds", seconds);
    }
}
