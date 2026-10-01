/* Adapted 2026-10-01 from FishyAddons SkyblockCleaner, valkeea and contributors.
 * GPL-3.0-only. See THIRD_PARTY_NOTICES.md and LICENSE. */
package com.FishHelper;

/** FishyAddons' sound matching and 65-second tail; the clock and mob query are injected. */
public final class ThunderSoundFilter {
    private long thunderCalled;
    private boolean heardThunder;

    public boolean shouldMute(boolean enabled, boolean skyblock, String path, boolean thunderAlive, long now) {
        if (!enabled || !skyblock || path == null || !thunderSound(path)) return false;
        if (thunderAlive) {
            thunderCalled = now;
            heardThunder = true;
            return true;
        }
        return heardThunder && now >= thunderCalled && now - thunderCalled < 65_000;
    }

    private boolean thunderSound(String path) {
        return path.contains("lightning_bolt") || path.contains("guardian");
    }
    public void reset() { heardThunder = false; thunderCalled = 0; }
}
