package com.FishHelper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThunderSoundFilterTest {
    @Test void onlyMatchingSoundsInSkyblockWithToggleOnAreMuted() {
        var filter = new ThunderSoundFilter();
        assertFalse(filter.shouldMute(false,true,"entity.guardian.ambient",true,1000));
        assertFalse(filter.shouldMute(true,false,"entity.guardian.ambient",true,1000));
        assertFalse(filter.shouldMute(true,true,"entity.player.splash",true,1000));
        assertTrue(filter.shouldMute(true,true,"entity.lightning_bolt.thunder",true,1000));
        assertTrue(filter.shouldMute(true,true,"entity.elder_guardian.hurt",true,1000));
    }
    @Test void tailLastsExactly65SecondsFromLastMatchingSoundWithLiveThunder() {
        var f=new ThunderSoundFilter();
        assertTrue(f.shouldMute(true,true,"guardian",true,10_000));
        assertTrue(f.shouldMute(true,true,"guardian",true,20_000));
        assertTrue(f.shouldMute(true,true,"guardian",false,84_999));
        assertFalse(f.shouldMute(true,true,"guardian",false,85_000));
    }
    @Test void tailSoundsAndUnrelatedSoundsNeverExtendTheTimestamp() {
        var f=new ThunderSoundFilter(); f.shouldMute(true,true,"guardian",true,10_000);
        f.shouldMute(true,true,"unrelated",true,50_000); f.shouldMute(true,true,"guardian",false,74_000);
        assertFalse(f.shouldMute(true,true,"guardian",false,75_000));
    }
    @Test void worldResetClearsTheTailAndNoThunderMeansNoInitialTail() {
        var f=new ThunderSoundFilter(); assertFalse(f.shouldMute(true,true,"guardian",false,1));
        f.shouldMute(true,true,"guardian",true,1000); f.reset();
        assertFalse(f.shouldMute(true,true,"guardian",false,1001));
    }
    @Test void disablingOrLeavingSkyblockStopsFilteringImmediately() {
        var f=new ThunderSoundFilter(); f.shouldMute(true,true,"guardian",true,1000);
        assertFalse(f.shouldMute(false,true,"guardian",false,1001));
        assertFalse(f.shouldMute(true,false,"guardian",true,1002));
        assertFalse(f.shouldMute(true,true,null,true,1002));
    }
}
