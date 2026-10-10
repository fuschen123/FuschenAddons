package com.FishHelper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HyperionPolicyTest {
    @Test void sixMillionIsInclusive() {
        assertEquals(HyperionPolicy.Kind.ULTIMATE_WISE, new HyperionPolicy().thunder(6_000_001d));
        assertEquals(HyperionPolicy.Kind.CHIMERA, new HyperionPolicy().thunder(6_000_000d));
        assertEquals(HyperionPolicy.Kind.CHIMERA, new HyperionPolicy().thunder(5_999_999d));
    }
    @Test void unknownInitialHealthWaitsAndMissingUpdatesKeepTheChoice() {
        var policy = new HyperionPolicy();
        for (Double hp : new Double[]{null, Double.NaN, Double.POSITIVE_INFINITY, -1d, 0d}) assertNull(policy.thunder(hp));
        assertEquals(HyperionPolicy.Kind.ULTIMATE_WISE, policy.thunder(35_000_000d));
        assertEquals(HyperionPolicy.Kind.ULTIMATE_WISE, policy.thunder(null));
        assertEquals(HyperionPolicy.Kind.CHIMERA, policy.thunder(6_000_000d));
        assertEquals(HyperionPolicy.Kind.CHIMERA, policy.thunder(null));
    }
    @Test void lowHealthLatchesForOnlyThatCreature() {
        var first = new HyperionPolicy(); var second = new HyperionPolicy();
        first.thunder(6_000_000d);
        assertEquals(HyperionPolicy.Kind.CHIMERA, first.thunder(35_000_000d));
        assertEquals(HyperionPolicy.Kind.ULTIMATE_WISE, second.thunder(35_000_000d));
    }
    @Test void nametagUsesCurrentRatherThanMaximumOrGuessedHp() {
        for (String hp : new String[]{"6M", "6000k", "6,000,000", "5.99M"}) {
            var parsed = SeaCreatureNametag.parse("[Lv400] Thunder " + hp + "/35M❤");
            assertEquals(HyperionPolicy.Kind.CHIMERA, new HyperionPolicy().thunder(parsed.currentHp()));
        }
        assertNull(new HyperionPolicy().thunder(SeaCreatureNametag.parse("[Lv400] Thunder ?/35M❤").currentHp()));
        assertEquals(HyperionPolicy.Kind.ULTIMATE_WISE,
                new HyperionPolicy().thunder(SeaCreatureNametag.parse("[Lv400] Thunder 6.001M/35M❤").currentHp()));
    }
    @Test void slotsMustBeDifferentAndInsideHotbar() {
        for(int a=-1;a<=10;a++)for(int b=-1;b<=10;b++)
            assertEquals(a>=1&&a<=9&&b>=1&&b<=9&&a!=b,HyperionPolicy.validSlots(a,b));
    }
}
