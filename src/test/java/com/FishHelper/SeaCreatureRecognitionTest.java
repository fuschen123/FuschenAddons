package com.FishHelper;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SeaCreatureRecognitionTest {
    @Test void actualFeeshNametagExamplesParseIconsCorruptionAndTrailingStars() {
        // Fixtures from Feesh EntityUtils.kt, Apache-2.0 (see third-party notices).
        var squid = SeaCreatureNametag.parse("§r§8[§r§7Lv1§r§8] §r§9⚓§r§a☮ §r§cSquid§r §r§a100§r§f/§r§a100§r§c❤");
        assertEquals(new SeaCreatureNametag("Squid", 100d, 100d), squid);
        assertEquals(new SeaCreatureNametag("Squid", 300d, 300d), SeaCreatureNametag.parse("§r§8[§r§7Lv1§r§8] §r§9⚓§r§a☮ §r§k§5a§r§5Corrupted Squid§r§k§5a§r §r§a300§r§f/§r§a300§r§c❤"));
        assertEquals(new SeaCreatureNametag("Lord Jawbus", 6_300_000d, 100_000_000d), SeaCreatureNametag.parse("§e﴾ §8[§7Lv600§8] §c♆§7⚙§d♣ §c§lLord Jawbus§r§r §e6.3M§f/§a100M§c❤ §e﴿ §b✯"));
        var emperor = SeaCreatureNametag.parse("§r§8[§r§7Lv150§r§8] §r§9⚓§r§f🦴§r§5♃ §r§5§ka§r§5Corrupted The Loch Emperor§r§5§ka§r §r§e521.8k§r§f/§r§a2.4M§r§c❤ §r§b✯");
        assertEquals("The Loch Emperor", emperor.name()); assertEquals(521_800, emperor.currentHp(), .001);
        assertEquals(2_400_000, emperor.maxHp(), .001);
        assertEquals(new SeaCreatureNametag("Ent", 1d, 75_000d), SeaCreatureNametag.parse("§r§8[§r§7Lv14§r§8] §r§2⸙§r§9⚓ §r§5§ka§r§5Corrupted Ent§r§5§ka§r §r§e1§r§f/§r§a75,000§r§c❤"));
    }
    @Test void missingMaximumIsNeverFabricated() {
        assertEquals(new SeaCreatureNametag("Thunder", 500_000d, null), SeaCreatureNametag.parse("[Lv400] Thunder 500k❤"));
        assertEquals(new SeaCreatureNametag("Thunder", null, null), SeaCreatureNametag.parse("[Lv400] Thunder ?/?❤"));
        assertEquals(new SeaCreatureNametag("Thunder", null, 35_000_000d), SeaCreatureNametag.parse("[Lv400] Thunder ?/35M❤"));
        assertEquals(new SeaCreatureNametag("Puddle Jumper", null, null), SeaCreatureNametag.parse("[Lv100] Puddle Jumper"));
        assertTrue(SeaCreatureNametag.parse("[Lv400] Thunder 0/35M❤").dead());
    }
    @Test void rejectsOrdinaryLabelsAndUnknownMobs() {
        for (String input : List.of("Thunder", "[Lv400] Thunder", "[Lv400] Not Thunder 1/10❤", "[Lv10] Zombie 1/20❤", "!!!"))
            assertNull(SeaCreatureNametag.parse(input), input);
        assertNull(SeaCreatureNametag.parse(null));
    }
    @Test void usesFeeshCompositeEntityOffsets() {
        assertEquals(1, SeaCreatureNametag.entityOffset("Thunder"));
        assertEquals(11, SeaCreatureNametag.entityOffset("Fire Eel"));
        assertEquals(2, SeaCreatureNametag.entityOffset("Werewolf"));
        assertEquals(6, SeaCreatureNametag.entityOffset("Drowned Captain"));
        assertEquals(43, SeaCreatureNametag.entityOffset("Titanoboa"));
        assertEquals(8, SeaCreatureNametag.entityOffset("Reindrake"));
    }
    private SeaCreatureMemory.Observation observation(UUID id) {
        return new SeaCreatureMemory.Observation(id, 100, new SeaCreatureNametag("Thunder", 10d, 20d));
    }
    @Test void duplicateNametagsYieldOneMobAndRealDeathWins() {
        var memory = new SeaCreatureMemory(); var id = UUID.randomUUID();
        memory.update(0, List.of(observation(id), observation(id)), Map.of(id, SeaCreatureMemory.Presence.ALIVE));
        assertEquals(1, memory.entries().size());
        memory.update(1, List.of(), Map.of(id, SeaCreatureMemory.Presence.DEAD));
        assertTrue(memory.entries().isEmpty());
    }
    @Test void nametagGapRetainsLiveIdentityButExpiresHealth() {
        var memory = new SeaCreatureMemory(); var id = UUID.randomUUID();
        memory.update(0, List.of(observation(id)), Map.of());
        memory.update(200, List.of(), Map.of(id, SeaCreatureMemory.Presence.ALIVE));
        assertNotNull(memory.get(id)); assertNull(memory.get(id).nametag().currentHp());
    }
    @Test void temporaryEntityGapAndMultipleThunderDoNotCompleteEncounter() {
        var memory = new SeaCreatureMemory(); var a = UUID.randomUUID(); var b = UUID.randomUUID();
        memory.update(0, List.of(observation(a), observation(b)), Map.of());
        memory.update(20, List.of(), Map.of(a, SeaCreatureMemory.Presence.DEAD));
        assertNull(memory.get(a)); assertNotNull(memory.get(b));
        memory.update(35, List.of(), Map.of(b, SeaCreatureMemory.Presence.ALIVE));
        assertNotNull(memory.get(b));
        memory.update(75, List.of(), Map.of()); assertNotNull(memory.get(b));
        memory.update(76, List.of(), Map.of()); assertTrue(memory.entries().isEmpty());
    }
    @Test void reportedZeroHealthWinsOverDuplicateAliveNametag() {
        var memory = new SeaCreatureMemory(); var id = UUID.randomUUID();
        var dead = new SeaCreatureMemory.Observation(id, 100, new SeaCreatureNametag("Thunder", 0d, 20d));
        memory.update(0, List.of(dead, observation(id)), Map.of(id, SeaCreatureMemory.Presence.ALIVE));
        assertTrue(memory.entries().isEmpty());
    }
}
