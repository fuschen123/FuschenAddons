package com.FishHelper;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static com.FishHelper.JawbusShurikenWarning.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class JawbusShurikenWarningTest {
    private final JawbusShurikenWarning warning = new JawbusShurikenWarning();
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    private void gap(Set<UUID> alive, int ticks) { for (int i = 0; i < ticks; i++) warning.tick(alive, Map.of()); }
    private void missing(Set<UUID> alive, UUID id) {
        warning.tick(alive, Map.of(id, MISSING)); gap(alive, 4); warning.tick(alive, Map.of(id, MISSING));
    }

    @Test void usesTheDocumentedTrailingShurikenOnActualFeeshJawbusFixture() {
        String marked = "§e﴾ §8[§7Lv600§8] §c♆§7⚙§d♣ §c§lLord Jawbus§r§r §e6.3M§f/§a100M§c❤ §e﴿ §b✯";
        assertEquals(PRESENT, JawbusShurikenWarning.parse(marked));
        assertEquals(MISSING, JawbusShurikenWarning.parse(marked.replace(" §b✯", "")));
        assertEquals(PRESENT, JawbusShurikenWarning.parse("[Lv600] Lord Jawbus 100M/100M❤ ✯"));
        assertEquals(PRESENT, JawbusShurikenWarning.parse("[Lv600] Lord Jawbus ?/100M❤ ✯"));
    }
    @Test void partialUnknownDeadOtherCreatureAndUnexpectedSuffixAreNotMissing() {
        assertEquals(UNKNOWN, JawbusShurikenWarning.parse(null));
        for (String tag : new String[]{"", "Lord Jawbus", "[Lv600] Lord Jawbus ?/100M❤",
                "[Lv600] Lord Jawbus 3M❤", "[Lv600] Lord Jawbus 0/100M❤",
                "[Lv400] Thunder 35M/35M❤", "[Lv600] Lord Jawbus 3M/100M❤ ?",
                "[Lv600] Lord Jawbus 3M/100M❤ ★", "[Lv600] ✯ Lord Jawbus 3M/100M❤"})
            assertEquals(UNKNOWN, JawbusShurikenWarning.parse(tag), tag);
    }
    @Test void noWarningForUnknownAndOneReadCannotConfirmMissing() {
        warning.tick(Set.of(a), Map.of(a, UNKNOWN));
        assertFalse(warning.visible());
        warning.tick(Set.of(a), Map.of(a, MISSING));
        assertFalse(warning.visible());
        warning.tick(Set.of(a), Map.of(a, UNKNOWN));
        warning.tick(Set.of(a), Map.of(a, MISSING));
        assertFalse(warning.visible());
        warning.tick(Set.of(a), Map.of(a, MISSING));
        assertTrue(warning.visible());
    }
    @Test void confirmedMissingWarnsAndPresentClearsImmediately() {
        missing(Set.of(a), a); assertTrue(warning.visible());
        warning.tick(Set.of(a), Map.of(a, PRESENT)); assertFalse(warning.visible());
        warning.tick(Set.of(a), Map.of(a, MISSING)); assertFalse(warning.visible());
        gap(Set.of(a), 4);
        warning.tick(Set.of(a), Map.of(a, MISSING)); assertTrue(warning.visible());
    }
    @Test void shortTagGapsAndUnknownSamplesHoldButDoNotRefreshOldEvidence() {
        missing(Set.of(a), a);
        for (int i = 0; i < JawbusShurikenWarning.GAP_TICKS; i++) {
            warning.tick(Set.of(a), Map.of(a, UNKNOWN)); assertTrue(warning.visible());
        }
        warning.tick(Set.of(a), Map.of()); assertFalse(warning.visible());
    }
    @Test void farApartIncompleteObservationsCannotConfirmMissing() {
        warning.tick(Set.of(a), Map.of(a, MISSING)); gap(Set.of(a), 20);
        warning.tick(Set.of(a), Map.of(a, MISSING)); assertFalse(warning.visible());
    }
    @Test void multipleTargetsKeepWarningUntilEachIsMarkedOrGone() {
        missing(Set.of(a, b), a); missing(Set.of(a, b), b);
        warning.tick(Set.of(a, b), Map.of(a, PRESENT)); assertTrue(warning.visible());
        warning.tick(Set.of(a), Map.of()); assertFalse(warning.visible());
        warning.tick(Set.of(a), Map.of(b, MISSING)); assertFalse(warning.visible());
    }
    @Test void deathDespawnAndWorldResetRemoveWarning() {
        missing(Set.of(a), a); warning.tick(Set.of(), Map.of()); assertFalse(warning.visible());
        missing(Set.of(a), a); warning.reset(); assertFalse(warning.visible());
        warning.tick(Set.of(a), Map.of(a, UNKNOWN)); assertFalse(warning.visible());
    }
}
