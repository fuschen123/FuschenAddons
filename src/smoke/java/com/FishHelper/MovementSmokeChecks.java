package com.FishHelper;

import com.FishHelper.features.RandomMovementFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

final class MovementSmokeChecks {
    static void run(SmokeClient t,Minecraft c) throws Exception {
        var tick=RandomMovementFeature.class.getDeclaredMethod("tick",Minecraft.class);tick.setAccessible(true);
        var active=RandomMovementFeature.class.getDeclaredField("active");active.setAccessible(true);
        var owned=RandomMovementFeature.class.getDeclaredField("ownsMovementKeys");owned.setAccessible(true);
        var center=RandomMovementFeature.class.getDeclaredField("center");center.setAccessible(true);
        var oldPosition=c.player.position();float oldYaw=c.player.getYRot();
        Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();
        int y=(int)Math.floor(oldPosition.y),x=(int)Math.floor(oldPosition.x),z=(int)Math.floor(oldPosition.z);
        try {
            // A known local fixture: five blocks of solid support, clear headroom.
            for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)for(int dy=-1;dy<=2;dy++) {
                var pos=new BlockPos(x+dx,y+dy,z+dz);blocks.put(pos,c.level.getBlockState(pos));
                c.level.setBlock(pos,(dy==-1?Blocks.STONE:Blocks.AIR).defaultBlockState(),3);
            }
            c.player.setPos(x+.5,y,z+.5);c.player.setOnGround(true);c.setScreen(null);
            Config.INSTANCE.randomMovementEnabled=true;RandomMovementFeature.reset(c);active.set(null,true);
            tick.invoke(null,c);t.check(!(boolean)owned.get(null),"no center holds no movement keys");
            t.commands.execute("fa movement center",null);t.check(center.get(null).equals(c.player.position()),"fa movement command sets center");
            t.commands.execute("fuschen movement center",null);
            for(int yaw:new int[]{0,90,180,270}) {
                c.player.setPos(x+2.35,y,z+.5);c.player.setYRot(yaw);tick.invoke(null,c);
                int forward=(c.options.keyUp.isDown()?1:0)-(c.options.keyDown.isDown()?1:0);
                int left=(c.options.keyLeft.isDown()?1:0)-(c.options.keyRight.isDown()?1:0);
                double dx=-Math.sin(Math.toRadians(yaw))*forward+Math.cos(Math.toRadians(yaw))*left;
                t.check(dx<0 && c.player.getYRot()==yaw,"movement near edge heads inward at yaw "+yaw+" without rotation");
            }
            c.player.setPos(x+.5,y,z+.5);c.player.setYRot(0);
            for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)c.level.setBlock(new BlockPos(x+dx,y-1,z+dz),Blocks.AIR.defaultBlockState(),3);
            tick.invoke(null,c);t.check(!(boolean)owned.get(null),"unsupported floor stops movement rather than crossing edge");
            Config.INSTANCE.randomMovementEnabled=false;tick.invoke(null,c);t.check(!(boolean)active.get(null) && !(boolean)owned.get(null),"disabled movement releases its inputs");
            Config.INSTANCE.randomMovementEnabled=true;active.set(null,true);
            var connection=RandomMovementFeature.class.getDeclaredField("centerConnection");connection.setAccessible(true);connection.set(null,new Object());
            tick.invoke(null,c);t.check(center.get(null)==null && !(boolean)active.get(null),"connection change discards center and active state");
            System.out.println("SMOKE_MOVEMENT_OK: both aliases, missing center, 4 yaw directions, unsafe floor, disable and connection reset");
        } finally {
            blocks.forEach((pos,state)->c.level.setBlock(pos,state,3));c.player.setPos(oldPosition);c.player.setYRot(oldYaw);
            RandomMovementFeature.reset(c);Config.INSTANCE.randomMovementEnabled=false;
        }
    }
}
