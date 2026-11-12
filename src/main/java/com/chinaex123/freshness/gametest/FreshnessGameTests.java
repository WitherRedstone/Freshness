package com.chinaex123.freshness.gametest;

import com.chinaex123.freshness.Freshness;
import com.chinaex123.freshness.data.FreshnessData;
import com.chinaex123.freshness.data.FreshnessStage;
import com.chinaex123.freshness.event.FreshnessEvents;
import com.chinaex123.freshness.init.ModDataComponents;
import com.chinaex123.freshness.util.FreshnessUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.InsertableLinkedOpenCustomHashSet;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Freshness.MODID)
@PrefixGameTestTemplate(false)
public final class FreshnessGameTests {
    private static final long WEEK = 7L * 24000L;

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void stagesAdvanceEverySevenDays(GameTestHelper helper) {
        FreshnessData data = FreshnessData.fresh(0L, 3L * WEEK);
        helper.assertTrue(data.stage() == FreshnessStage.FRESH, "crafted food starts fresh");
        helper.assertTrue(data.expiresAt() == WEEK, "fresh lasts exactly one third of the total shelf life");

        data = data.advance(WEEK, WEEK);
        helper.assertTrue(data.stage() == FreshnessStage.DECENT, "fresh -> decent after one stage");
        helper.assertTrue(data.expiresAt() == 2L * WEEK, "decent expires one stage later");

        data = data.advance(2L * WEEK, WEEK);
        helper.assertTrue(data.stage() == FreshnessStage.NEAR_EXPIRY, "decent -> near_expiry");
        helper.assertTrue(data.expiresAt() == 3L * WEEK, "near_expiry expires one stage later");

        data = data.advance(3L * WEEK, WEEK);
        helper.assertTrue(data.stage() == FreshnessStage.ROTTEN, "near_expiry -> rotten");
        helper.assertTrue(data.isFixed(), "rotten does not decay further");

        data = data.advance(100L * WEEK, WEEK);
        helper.assertTrue(data.stage() == FreshnessStage.ROTTEN && data.isFixed(), "rotten stays rotten forever");

        FreshnessData skipped = FreshnessData.fresh(0L, 3L * WEEK).advance(3L * WEEK, WEEK);
        helper.assertTrue(skipped.stage() == FreshnessStage.ROTTEN, "a long elapsed time can skip stages");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void freshFoodGivesTwentyFivePercentBonus(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack apple = new ItemStack(Items.APPLE);
        FoodProperties base = apple.getFoodProperties(player);
        helper.assertTrue(base != null, "apple must have food properties");

        apple.set(ModDataComponents.FRESHNESS.get(), FreshnessData.fresh(helper.getLevel().getGameTime()));
        player.getFoodData().setFoodLevel(0);
        player.getFoodData().setSaturation(0.0F);
        apple.finishUsingItem(helper.getLevel(), player);

        int expectedFood = 0;
        if (base != null) {
            expectedFood = Math.max(0, Math.round(base.nutrition() * 1.25F));
        }
        float expectedSaturation = 0;
        if (base != null) {
            expectedSaturation = base.saturation() * 1.25F;
        }
        helper.assertTrue(player.getFoodData().getFoodLevel() == expectedFood, "fresh food nutrition should be 125% of base");
        helper.assertTrue(Math.abs(player.getFoodData().getSaturationLevel() - expectedSaturation) < 1.0E-4F,
                "fresh food saturation should be 125% of base");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void stageChangeUpdatesFoodComponentImmediately(GameTestHelper helper) {
        ItemStack apple = new ItemStack(Items.APPLE);
        FoodProperties base = apple.getFoodProperties(null);
        helper.assertTrue(base != null, "apple must have food properties");

        FreshnessUtil.setFreshness(apple, FreshnessData.fresh(0L));
        FoodProperties fresh = apple.get(DataComponents.FOOD);
        helper.assertTrue(fresh != null && apple.has(ModDataComponents.BASE_FOOD.get()),
                "setting freshness should snapshot the base food and rewrite FOOD");
        if (base != null && fresh != null) {
            helper.assertTrue(fresh.nutrition() == Math.max(0, Math.round(base.nutrition() * 1.25F)),
                    "fresh FOOD nutrition should update without eating");
            helper.assertTrue(Math.abs(fresh.saturation() - base.saturation() * 1.25F) < 1.0E-4F,
                    "fresh FOOD saturation should update without eating");
        }

        FreshnessUtil.setFreshness(apple, FreshnessData.fixed(FreshnessStage.ROTTEN));
        FoodProperties rotten = apple.get(DataComponents.FOOD);
        helper.assertTrue(rotten != null, "rotten FOOD must exist");
        if (base != null && rotten != null) {
            helper.assertTrue(rotten.nutrition() == Math.max(0, Math.round(base.nutrition() * 0.5F)),
                    "rotten FOOD nutrition should update immediately");
            helper.assertTrue(Math.abs(rotten.saturation() - base.saturation() * 0.5F) < 1.0E-4F,
                    "rotten FOOD saturation should update immediately");
            helper.assertTrue(rotten.effects().size() == base.effects().size() + 1,
                    "rotten FOOD should include the negative effect");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void normalizeAdvancesFoodComponentWithStage(GameTestHelper helper) {
        ItemStack apple = new ItemStack(Items.APPLE);
        FoodProperties base = apple.getFoodProperties(null);
        helper.assertTrue(base != null, "apple must have food properties");

        long stageDuration = FreshnessUtil.stageDurationTicks(apple);
        FreshnessUtil.setFreshness(apple, FreshnessData.fresh(0L, 3L * stageDuration));
        FreshnessUtil.normalize(apple, 2L * stageDuration);
        FreshnessData data = apple.get(ModDataComponents.FRESHNESS.get());
        FoodProperties near = apple.get(DataComponents.FOOD);
        helper.assertTrue(data != null && data.stage() == FreshnessStage.NEAR_EXPIRY,
                "normalize should advance fresh to near_expiry after two stage durations");
        if (base != null && near != null) {
            helper.assertTrue(near.nutrition() == Math.max(0, Math.round(base.nutrition() * 0.75F)),
                    "advancing the stage should update FOOD nutrition in the same operation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void sameStageStacksAndAveragesTime(GameTestHelper helper) {
        ItemStack first = new ItemStack(Items.APPLE);
        first.set(ModDataComponents.FRESHNESS.get(), new FreshnessData(FreshnessStage.FRESH, 1000L));
        ItemStack second = new ItemStack(Items.APPLE, 3);
        second.set(ModDataComponents.FRESHNESS.get(), new FreshnessData(FreshnessStage.FRESH, 2000L));

        helper.assertTrue(ItemStack.isSameItemSameComponents(first, second), "same-stage foods must be stack-compatible");

        FreshnessData.averageInto(first, first, 1, second, 3);
        FreshnessData merged = first.get(ModDataComponents.FRESHNESS.get());
        helper.assertTrue(merged != null && merged.expiresAt() == 1750L, "merged expiry should be the count-weighted average");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void creativeCopyIsFixedDecent(GameTestHelper helper) {
        ItemStack original = new ItemStack(Items.APPLE, 16);
        ItemStack copy = FreshnessUtil.fixedDecentCopy(original);
        FreshnessData data = copy.get(ModDataComponents.FRESHNESS.get());

        helper.assertTrue(copy.getCount() == 1, "creative display stack has count 1");
        helper.assertTrue(data != null && data.stage() == FreshnessStage.DECENT,
                "creative display stack is decent");
        helper.assertTrue(data != null && data.isFixed(), "creative display stack never decays");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void plainFoodGetsDefaultShelfLife(GameTestHelper helper) {
        ItemStack apple = new ItemStack(Items.APPLE);
        FreshnessUtil.normalize(apple, 1000L);
        FreshnessData data = apple.get(ModDataComponents.FRESHNESS.get());
        long stageDuration = FreshnessUtil.stageDurationTicks(apple);

        helper.assertTrue(data != null && data.stage() == FreshnessStage.DECENT, "food without a component defaults to decent");
        helper.assertTrue(data != null && data.expiresAt() == 1000L + stageDuration,
                "default decent food receives a full stage timer");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void dataCodecRoundTripsStageAndTime(GameTestHelper helper) {
        FreshnessData original = new FreshnessData(FreshnessStage.FRESH, 12345L);
        Tag encoded = FreshnessData.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        FreshnessData decoded = FreshnessData.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        helper.assertTrue(decoded.stage() == FreshnessStage.FRESH, "codec preserves the stage");
        helper.assertTrue(decoded.expiresAt() == 12345L, "codec preserves the exact expiry time");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void smartDurationFormatting(GameTestHelper helper) {
        helper.assertTrue(usesKey(FreshnessUtil.formatDuration(10L), "tooltip.freshness.duration.seconds"),
                "under one minute should use seconds");
        helper.assertTrue(usesKey(FreshnessUtil.formatDuration(100L), "tooltip.freshness.duration.minutes"),
                "minutes should be used once at least one minute remains");
        helper.assertTrue(usesKey(FreshnessUtil.formatDuration(1000L), "tooltip.freshness.duration.hours"),
                "zero minutes should be omitted");
        helper.assertTrue(usesKey(FreshnessUtil.formatDuration(24000L), "tooltip.freshness.duration.days"),
                "zero hours should be omitted");
        helper.assertTrue(usesKey(FreshnessUtil.formatDuration(25000L), "tooltip.freshness.duration.days_hours"),
                "days and hours should both show when present");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void shelfLifeTagsClassifyFood(GameTestHelper helper) {
        long appleStage = FreshnessUtil.stageDurationTicks(new ItemStack(Items.APPLE));
        long beefStage = FreshnessUtil.stageDurationTicks(new ItemStack(Items.BEEF));
        long defaultStage = FreshnessUtil.stageDurationTicks(new ItemStack(Items.GOLDEN_APPLE));

        helper.assertTrue(appleStage == 5L * 24000L, "crop food should use a 15-day total shelf life");
        helper.assertTrue(beefStage == 7L * 24000L / 3L, "meat should use a 7-day total shelf life");
        helper.assertTrue(defaultStage == 20L * 24000L / 3L, "untagged food should use a 20-day total shelf life");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void shelfLifeRoundsDaysUp(GameTestHelper helper) {
        helper.assertTrue(usesKey(FreshnessUtil.formatShelfLife(24000L + 1L), "tooltip.freshness.duration.days"),
                "at least one day should round up and use the days key");
        helper.assertTrue(usesKey(FreshnessUtil.formatShelfLife(12000L), "tooltip.freshness.duration.hours"),
                "under one day should stay in hours for urgency");
        helper.assertTrue(usesKey(FreshnessUtil.formatShelfLife(164000L), "tooltip.freshness.duration.days"),
                "a 6 day 20 hour remainder should round up to days, not show a fast-ticking hour line");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void mobDropsStartFresh(GameTestHelper helper) {
        ItemStack beef = new ItemStack(Items.BEEF);
        long now = 5000L;
        FreshnessUtil.markFresh(beef, now);

        FreshnessData data = FreshnessData.of(beef);
        long stage = FreshnessUtil.stageDurationTicks(beef);

        helper.assertTrue(data.stage() == FreshnessStage.FRESH, "mob drops should start fresh");
        helper.assertTrue(data.expiresAt() == now + stage, "fresh mob drop should get a full first stage");
        helper.assertTrue(data.remainingTotal(now, stage) == 3L * stage,
                "fresh mob drop should show the full shelf life");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void craftingInheritsIngredientAge(GameTestHelper helper) {
        ItemStack apple = new ItemStack(Items.APPLE);
        long stageDuration = FreshnessUtil.stageDurationTicks(apple);
        FreshnessUtil.setFreshness(apple, FreshnessData.fresh(0L, 3L * stageDuration));

        SimpleContainer grid = new SimpleContainer(1);
        grid.setItem(0, apple);

        ItemStack bread = new ItemStack(Items.BREAD);
        long now = 2L * stageDuration;
        FreshnessUtil.markCraftedFromIngredients(bread, grid, now);

        FreshnessData data = bread.get(ModDataComponents.FRESHNESS.get());
        helper.assertTrue(data != null && data.stage() == FreshnessStage.NEAR_EXPIRY,
                "bread should inherit the aged state of its ingredient");
        helper.assertTrue(data != null && data.expiresAt() == now + stageDuration,
                "inherited expiry should preserve the remaining fraction");
        helper.succeed();
    }

    private static boolean usesKey(Component component, String key) {
        return component.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(key);
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void itemEntityMergeAveragesTime(GameTestHelper helper) {
        ItemStack destination = new ItemStack(Items.APPLE, 1);
        destination.set(ModDataComponents.FRESHNESS.get(), new FreshnessData(FreshnessStage.FRESH, 1000L));
        ItemStack origin = new ItemStack(Items.APPLE, 3);
        origin.set(ModDataComponents.FRESHNESS.get(), new FreshnessData(FreshnessStage.FRESH, 2000L));

        ItemStack result = ItemEntity.merge(destination, origin, 64);
        helper.assertTrue(result.getCount() == 4, "item entity merge should combine both stacks");
        FreshnessData merged = result.get(ModDataComponents.FRESHNESS.get());
        helper.assertTrue(merged != null && merged.expiresAt() == 1750L,
                "item entity merge should use the count-weighted average expiry");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void containerShiftClickAveragesTime(GameTestHelper helper) {
        SimpleContainer container = new SimpleContainer(2);
        ItemStack source = new ItemStack(Items.APPLE, 10);
        source.set(ModDataComponents.FRESHNESS.get(), new FreshnessData(FreshnessStage.FRESH, 1000L));
        ItemStack destination = new ItemStack(Items.APPLE, 10);
        destination.set(ModDataComponents.FRESHNESS.get(), new FreshnessData(FreshnessStage.FRESH, 2000L));
        container.setItem(0, source);
        container.setItem(1, destination);

        TestMenu menu = new TestMenu(container);
        helper.assertTrue(menu.moveToDestination(), "shift-click should merge into the destination slot");
        helper.assertTrue(container.getItem(0).isEmpty(), "source slot should be empty after the merge");
        helper.assertTrue(container.getItem(1).getCount() == 20, "destination should contain both stacks");

        FreshnessData merged = container.getItem(1).get(ModDataComponents.FRESHNESS.get());
        helper.assertTrue(merged != null && merged.expiresAt() == 1500L,
                "container merge should use the count-weighted average expiry");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void rottenIngredientsBlockResultPickup(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SimpleContainer result = new SimpleContainer(1);
        AbstractContainerMenu menu = new TestMenu(new SimpleContainer(2));
        TransientCraftingContainer grid = new TransientCraftingContainer(menu, 3, 3);
        ResultSlot slot = new ResultSlot(player, grid, result, 0, 0, 0);

        ItemStack rotten = new ItemStack(Items.APPLE);
        FreshnessUtil.setFreshness(rotten, FreshnessData.fixed(FreshnessStage.ROTTEN));
        grid.setItem(0, rotten);
        helper.assertFalse(slot.mayPickup(player), "rotten ingredient should block result pickup");

        grid.setItem(0, new ItemStack(Items.APPLE));
        helper.assertTrue(slot.mayPickup(player), "fresh ingredient should allow result pickup");
        helper.succeed();
    }

    private static final class TestMenu extends AbstractContainerMenu {
        private TestMenu(SimpleContainer container) {
            super(MenuType.GENERIC_9x1, 0);
            this.addSlot(new Slot(container, 0, 8, 8));
            this.addSlot(new Slot(container, 1, 26, 8));
        }

        private boolean moveToDestination() {
            return this.moveItemStackTo(this.getSlot(0).getItem(), 1, 2, false);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void rottenFoodLosesHalfAndRollsAnEffect(GameTestHelper helper) {
        ItemStack apple = new ItemStack(Items.APPLE);
        FoodProperties base = apple.getFoodProperties(null);
        helper.assertTrue(base != null, "apple must have food properties");

        FoodProperties rotten = FreshnessUtil.modify(base, FreshnessData.fixed(FreshnessStage.ROTTEN));
        int expectedFood = 0;
        if (base != null) {
            expectedFood = Math.max(0, Math.round(base.nutrition() * 0.5F));
        }
        helper.assertTrue(rotten.nutrition() == expectedFood,
                "rotten food should have 50% nutrition");
        if (base != null) {
            helper.assertTrue(Math.abs(rotten.saturation() - base.saturation() * 0.5F) < 1.0E-4F,
                    "rotten food should have 50% saturation");
        }
        helper.assertTrue(rotten.effects().size() == base.effects().size() + 1,
                "rotten food should add one possible negative effect");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = Freshness.MODID)
    public void creativeTabReplacesFoodWithFixedDecent(GameTestHelper helper) {
        CreativeModeTab tab = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB
                .get(CreativeModeTabs.FOOD_AND_DRINKS);
        helper.assertTrue(tab != null, "food creative tab must exist");

        ItemStack plain = new ItemStack(Items.APPLE);
        InsertableLinkedOpenCustomHashSet<ItemStack> parent = new InsertableLinkedOpenCustomHashSet<>();
        InsertableLinkedOpenCustomHashSet<ItemStack> search = new InsertableLinkedOpenCustomHashSet<>();
        parent.add(plain.copy());
        search.add(plain.copy());

        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.VANILLA_SET, true, helper.getLevel().registryAccess());
        BuildCreativeModeTabContentsEvent event = null;
        if (tab != null) {
            event = new BuildCreativeModeTabContentsEvent(
                    tab, CreativeModeTabs.FOOD_AND_DRINKS, parameters, parent, search);
        }

        FreshnessEvents.onCreativeTab(event);

        helper.assertTrue(event.getParentEntries().size() == 1, "creative tab should still have exactly one apple entry");
        helper.assertTrue(event.getSearchEntries().size() == 1, "search tab should still have exactly one apple entry");

        ItemStack result = event.getParentEntries().getFirst();
        FreshnessData data = result.get(ModDataComponents.FRESHNESS.get());
        helper.assertTrue(data != null && data.stage() == FreshnessStage.DECENT, "creative apple should be fixed decent");
        helper.assertTrue(data != null && data.isFixed(), "creative apple should never decay");
        helper.succeed();
    }
}
