package com.FishHelper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FlareSequenceTest {
    static class Controls implements FlareSequence.Controls {
        int tick; boolean nearby, present=true, held=true;
        List<String> actions=new ArrayList<>();
        public boolean satisfied(){return nearby;}
        public boolean select(){if(present)actions.add("select:"+tick);return present;}
        public boolean stillSelected(){return held;}
        public void use(){actions.add("use:"+tick);}
        public void restore(){actions.add("restore:"+tick);}
    }
    void tick(FlareSequence s,Controls c,int n){for(int i=0;i<n;i++){c.tick++;s.tick(c);}}
    @Test void tiersAreOrderedAndPlasmafluxIsNotAFlare(){
        for(int required=1;required<=3;required++) for(int actual=0;actual<=3;actual++)
            assertEquals(actual>=required,FlareRules.sufficient(required,actual));
        assertFalse(FlareRules.sufficient(0,3)); assertEquals(0,FlareRules.tier("Plasmaflux Power Orb 60s"));
        assertEquals(3,FlareRules.tier("§5SOS Flare")); assertEquals(2,FlareRules.tier("Alert Flare")); assertEquals(1,FlareRules.tier("Warning Flare"));
    }
    @Test void selectUseAndRestoreHaveConfiguredSpacing(){
        for(int delay:new int[]{1,3,20}) {
            var c=new Controls();var s=new FlareSequence(delay);tick(s,c,100);
            assertEquals(List.of("select:1","use:"+(1+delay),"restore:"+(1+2*delay)),c.actions);
            assertFalse(s.active());
        }
    }
    @Test void newlyAppearedFlareOrChangedSlotSkipsUse(){
        for(boolean nearby:new boolean[]{true,false}){
            var c=new Controls();var s=new FlareSequence(3);tick(s,c,1);c.nearby=nearby;c.held=nearby;
            tick(s,c,20);assertEquals(List.of("select:1","restore:7"),c.actions);
        }
    }
    @Test void missingItemDoesNothingAndCancellationRestoresOnlyOnce(){
        var c=new Controls();c.present=false;var s=new FlareSequence(3);tick(s,c,100);assertTrue(c.actions.isEmpty());
        c=new Controls();s=new FlareSequence(3);tick(s,c,1);s.cancel(c);s.cancel(c);tick(s,c,100);
        assertEquals(List.of("select:1","restore:1"),c.actions);
    }
}
