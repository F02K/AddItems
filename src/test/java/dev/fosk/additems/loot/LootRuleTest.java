package dev.fosk.additems.loot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootRuleTest {

    private static final String NS = "additems";

    private static LootRule.Drop drop(String item, int min, int max, double chance) {
        LootRule.Drop d = new LootRule.Drop();
        d.item = item;
        d.min = min;
        d.max = max;
        d.chance = chance;
        return d;
    }

    private static LootRule mobRule() {
        LootRule rule = new LootRule();
        rule.id = "zombie_rubies";
        rule.trigger = LootRule.Trigger.MOB;
        rule.targets = List.of("minecraft:zombie");
        rule.drops = List.of(drop("additems:ruby", 1, 3, 50));
        return rule;
    }

    @Test
    void validRuleIsAccepted() {
        assertNull(mobRule().validate(NS));
    }

    @Test
    void rejectsBadStructure() {
        LootRule noTargets = mobRule();
        noTargets.targets = List.of();
        assertNotNull(noTargets.validate(NS));

        LootRule noDrops = mobRule();
        noDrops.drops = List.of();
        assertNotNull(noDrops.validate(NS));

        LootRule badId = mobRule();
        badId.id = "../x";
        assertNotNull(badId.validate(NS));

        LootRule badTarget = mobRule();
        badTarget.targets = List.of("zombie");
        assertNotNull(badTarget.validate(NS));

        LootRule tagForMob = mobRule();
        tagForMob.targets = List.of("#minecraft:logs");
        assertNotNull(tagForMob.validate(NS));
    }

    @Test
    void rejectsBadDrops() {
        for (LootRule.Drop bad : new LootRule.Drop[]{
                drop("#minecraft:planks", 1, 1, 100), drop("nonsense", 1, 1, 100), drop("additems:ruby", 0, 1, 100),
                drop("additems:ruby", 3, 2, 100), drop("additems:ruby", 1, 65, 100), drop("additems:ruby", 1, 1, 0),
                drop("additems:ruby", 1, 1, 101)}) {
            LootRule rule = mobRule();
            rule.drops = List.of(bad);
            assertNotNull(rule.validate(NS), bad.item + " " + bad.min + "-" + bad.max + " @" + bad.chance);
        }
    }

    @Test
    void wildcardAndTagTargetsAreAllowedWhereSensible() {
        LootRule block = mobRule();
        block.trigger = LootRule.Trigger.BLOCK;
        block.targets = List.of("#minecraft:logs", "minecraft:stone");
        assertNull(block.validate(NS));

        LootRule chest = mobRule();
        chest.trigger = LootRule.Trigger.CHEST;
        chest.targets = List.of("*");
        assertNull(chest.validate(NS));
    }

    @Test
    void rollRespectsChanceAndAmountRange() {
        Random random = new Random(42);
        LootRule.Drop always = drop("additems:ruby", 2, 4, 100);
        for (int i = 0; i < 200; i++) {
            int amount = LootRule.roll(always, 0, random);
            assertTrue(amount >= 2 && amount <= 4, "amount " + amount);
        }
        LootRule.Drop never = drop("additems:ruby", 1, 1, 0.0001);
        int hits = 0;
        for (int i = 0; i < 1000; i++) if (LootRule.roll(never, 0, random) > 0) hits++;
        assertTrue(hits <= 2, "hits " + hits);
    }

    @Test
    void rollChanceIsRoughlyTheConfiguredPercentage() {
        Random random = new Random(7);
        LootRule.Drop half = drop("additems:ruby", 1, 1, 50);
        int hits = 0;
        for (int i = 0; i < 10_000; i++) if (LootRule.roll(half, 0, random) > 0) hits++;
        assertTrue(hits > 4700 && hits < 5300, "hits " + hits);
    }

    @Test
    void enchantmentLevelRaisesChanceAndAmount() {
        Random random = new Random(1);
        LootRule.Drop d = drop("additems:ruby", 1, 1, 10);
        d.chanceBonusPerLevel = 30; // level 3 -> 100 %
        d.amountBonusPerLevel = 2;
        boolean sawMore = false;
        for (int i = 0; i < 200; i++) {
            int amount = LootRule.roll(d, 3, random);
            assertTrue(amount >= 1 && amount <= 7, "amount " + amount);
            if (amount > 1) sawMore = true;
        }
        assertTrue(sawMore);
        assertEquals(0, LootRule.roll(drop("additems:ruby", 1, 1, 1), 0, new Random() {
            @Override
            public double nextDouble() {
                return 0.99; // above 1 %
            }
        }));
    }
}
