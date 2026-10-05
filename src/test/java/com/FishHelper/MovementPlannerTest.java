package com.FishHelper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MovementPlannerTest {
    @Test void currentYawTranslatesTheSameWorldDirectionIntoDifferentKeys(){
        var a=MovementPlanner.choose(0,0,0,0,1,(x,z)->true);
        var b=MovementPlanner.choose(0,0,90,0,1,(x,z)->true);
        assertEquals(1,a.forward());assertEquals(0,a.left());assertEquals(0,b.forward());assertEquals(1,b.left());
    }
    @Test void nearBoundaryAlwaysSteersInwardAtAnyYaw(){
        for(int yaw=0;yaw<360;yaw+=7)for(int angle=0;angle<360;angle+=15){
            double x=1.85*Math.cos(Math.toRadians(angle)),z=1.85*Math.sin(Math.toRadians(angle));
            var in=MovementPlanner.choose(x,z,yaw,4,4,(a,b)->true);
            assertTrue(Math.hypot(x+in.dx()*.35,z+in.dz()*.35)<Math.hypot(x,z));
        }
    }
    @Test void blockedOrUnsafeDirectionsStopRatherThanCrossAnEdge(){
        var in=MovementPlanner.choose(0,0,0,1,0,(x,z)->false);
        assertEquals(0,in.forward());assertEquals(0,in.left());
        in=MovementPlanner.choose(0,0,0,1,0,(x,z)->x<.5);
        assertEquals(0,in.forward());assertEquals(0,in.left());
    }
    @Test void prolongedMovementRemainsWithinTwoBlocks(){
        double x=0,z=0;
        for(int t=0;t<20000;t++) {
            var in=MovementPlanner.choose(x,z,t%360,1.2*Math.sin(t/30.0),1.2*Math.cos(t/30.0),(a,b)->true);
            x+=in.dx()*.07;z+=in.dz()*.07;assertTrue(Math.hypot(x,z)<2);
        }
    }
}
