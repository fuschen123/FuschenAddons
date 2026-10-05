package com.FishHelper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class HyperionPolicyTest {
    @Test void threeMillionIsInclusiveAndMissingHealthUsesChimera(){
        assertEquals(HyperionPolicy.Kind.ULTIMATE_WISE,HyperionPolicy.thunder(List.of(3_000_001.0)));
        assertEquals(HyperionPolicy.Kind.CHIMERA,HyperionPolicy.thunder(List.of(3_000_000.0)));
        assertEquals(HyperionPolicy.Kind.CHIMERA,HyperionPolicy.thunder(Arrays.asList((Double)null)));
        assertEquals(HyperionPolicy.Kind.CHIMERA,HyperionPolicy.thunder(List.of(Double.NaN)));
    }
    @Test void multipleThundersUseChimeraIfAnyCouldBeKilled(){
        assertEquals(HyperionPolicy.Kind.CHIMERA,HyperionPolicy.thunder(List.of(30_000_000.0,2_000_000.0)));
    }
    @Test void slotsMustBeDifferentAndInsideHotbar(){
        for(int a=-1;a<=10;a++)for(int b=-1;b<=10;b++)
            assertEquals(a>=1&&a<=9&&b>=1&&b<=9&&a!=b,HyperionPolicy.validSlots(a,b));
    }
}
