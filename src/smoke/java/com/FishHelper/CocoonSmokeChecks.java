package com.FishHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/** Synthetic packet-order/release fixtures; production input is captured, never sent to Hypixel. */
final class CocoonSmokeChecks {
    record Mob(LivingEntity mob, ArmorStand tag) { }
    private static Mob create(Minecraft c, int id, String name, String hp, double distance, int tagOffset) {
        LivingEntity mob = name.equals("Thunder") ? new ElderGuardian(EntityType.ELDER_GUARDIAN,c.level)
                : new IronGolem(EntityType.IRON_GOLEM,c.level);
        mob.setId(id); mob.setPos(c.player.position().add(distance,0,0)); c.level.addEntity(mob);
        var tag = new ArmorStand(EntityType.ARMOR_STAND,c.level); tag.setId(id+tagOffset); tag.setPos(mob.position().add(0,2,0));
        tag.setCustomName(Component.literal("[Lv600] " + name + " " + hp + "/100M❤")); c.level.addEntity(tag);
        return new Mob(mob,tag);
    }
    private static void remove(Minecraft c, Mob mob) {
        SequenceSmokeChecks.remove(c,mob.mob()); SequenceSmokeChecks.remove(c,mob.tag());
    }
    private static void hp(Mob mob, String hp) { mob.tag().setCustomName(Component.literal("[Lv400] Thunder " + hp + "/35M❤")); }
    private static boolean paused(SmokeClient t) throws Exception { return ((SeaCreatureEncounter)t.mod("THUNDER_PAUSE")).active(); }
    private static void chat(Minecraft c, String text) { FishHelperClient.onGameMessage(c,Component.literal(text),false); }
    private static long uses(SequenceSmokeChecks.Inputs in, int from, String item) {
        return in.uses.subList(from,in.uses.size()).stream().filter(s->s.endsWith(":"+item)).count();
    }
    static void run(SmokeClient t, Minecraft c) throws Exception {
        var oldConfig=Config.INSTANCE; var oldMode=c.gameMode;
        var input=new SequenceSmokeChecks.Inputs(c); c.gameMode=input; Config.INSTANCE=new Config();
        Config.INSTANCE.petSwapEnabled=false; Config.INSTANCE.flareTier=Config.FlareTier.NONE;
        Config.INSTANCE.autoRodSwap=false; Config.INSTANCE.twoHyperions=true; Config.INSTANCE.autoDetectHyperions=false;
        Config.INSTANCE.ultimateWiseSlot=1; Config.INSTANCE.chimeraSlot=2;
        var inv=c.player.getInventory(); List<Mob> fixtures=new ArrayList<>();
        try {
            c.setScreen(null); for(int i=0;i<9;i++) inv.setItem(i,ItemStack.EMPTY);
            inv.setItem(0,SequenceSmokeChecks.hyperion("ultimate_wise")); inv.setItem(1,SequenceSmokeChecks.hyperion("ultimate_chimera"));
            inv.setItem(3,new ItemStack(Items.FISHING_ROD)); inv.setItem(6,SequenceSmokeChecks.named("Ice Spray Wand"));
            inv.setItem(7,SequenceSmokeChecks.named("Ink Wand")); inv.setItem(8,SequenceSmokeChecks.named("Thunder Cocoon"));
            inv.setSelectedSlot(3); SeaCreatureTracker.INSTANCE.reset();
            t.toggle(c); chat(c,"CAUGHT! You cocooned a Thunder!"); chat(c,"CAUGHT! You cocooned a Lord Jawbus!");
            t.tick(c,20); t.check(!paused(t) && !((JawbusPauseFeature)t.mod("JAWBUS_PAUSE")).active(),"capture messages/items never imply a live release");
            t.tick(c,80); var pending=(HookRecoveryFeature)t.mod("hookRecovery");
            t.check(pending!=null,"recast pending before release");
            var first=create(c,2_400_000,"Thunder","35M",3,13); fixtures.add(first); // Non-adjacent new release pair, no chat.
            input.uses.clear(); t.tick(c,1);
            t.check(paused(t) && t.mod("hookRecovery")==null && t.mod("thunderResponse")==null,"response OFF still pauses and cancels recast in first recognized tick");
            t.tick(c,300); t.check(input.uses.isEmpty() && !pending.castIssued(),"five-second watchdog and queued recast cannot bypass live Thunder");
            t.check(SeaCreatureTracker.INSTANCE.get(first.mob().getUUID())!=null,"released Thunder associated through unique model below nametag");
            Config.INSTANCE.thunderResponseEnabled=true; t.tick(c,35);
            t.check(input.uses.size()>2 && input.uses.get(0).endsWith(":Ice Spray Wand") && input.uses.get(1).endsWith(":Ink Wand"),"wands run in order before any Hyperion");
            t.check(input.uses.stream().anyMatch(s->s.contains(":0:Hyperion")),"above six million uses configured Ultimate Wise");
            Object response=t.mod("thunderResponse");
            chat(c,ThunderResponseFeature.SPAWN_MESSAGE); chat(c,ThunderResponseFeature.SPAWN_MESSAGE);
            t.tick(c,10); t.check(t.mod("thunderResponse")==response && uses(input,0,"Ice Spray Wand")==1,"late/duplicate normal spawn chat does not restart release sequence");
            var second=create(c,2_400_040,"Thunder","2M",5,1); fixtures.add(second);
            int before=input.uses.size(); t.tick(c,16);
            t.check(input.uses.subList(before,input.uses.size()).stream().allMatch(s->s.contains(":0:Hyperion")),"another low-HP Thunder never supplies selected target HP");
            hp(first,"?"); before=input.uses.size(); t.tick(c,16);
            t.check(input.uses.subList(before,input.uses.size()).stream().allMatch(s->s.contains(":0:Hyperion")),"unknown update keeps Ultimate Wise");
            hp(first,"6M"); before=input.uses.size(); t.tick(c,16);
            t.check(input.uses.subList(before,input.uses.size()).stream().anyMatch(s->s.contains(":1:Hyperion")),"exactly six million selects configured Chimera");
            hp(first,"35M"); before=input.uses.size(); t.tick(c,16);
            t.check(input.uses.subList(before,input.uses.size()).stream().allMatch(s->s.contains(":1:Hyperion")),"stale high HP cannot switch latched target back");
            var interruption=create(c,2_400_060,"Lord Jawbus","100M",8,1); fixtures.add(interruption);
            before=input.uses.size(); t.tick(c,160);
            t.check(input.uses.size()==before && t.mod("thunderResponse")==response,"Jawbus suspends Thunder input while retaining its weapon latch");
            interruption.mob().setHealth(0); t.tick(c,20);
            t.check(t.mod("thunderResponse")==response && inv.getSelectedSlot()==1 && uses(input,0,"Ice Spray Wand")==1,"Thunder resumes after Jawbus without repeating wands or reverting to Ultimate Wise");
            remove(c,interruption); fixtures.remove(interruption);
            Config.INSTANCE.thunderResponseEnabled=false; before=input.uses.size(); t.tick(c,150);
            t.check(paused(t) && input.uses.size()==before && t.mod("thunderResponse")==response,"disabling response suspends input without releasing fishing or identity");
            Config.INSTANCE.thunderResponseEnabled=true; t.tick(c,12);
            t.check(input.uses.size()>before && uses(input,0,"Ice Spray Wand")==1,"response toggle resumes existing sequence");
            first.tag().setCustomName(null); first.mob().setPos(c.player.position().add(60,0,0));
            before=input.uses.size(); float yaw=c.player.getYRot(),pitch=c.player.getXRot(); t.tick(c,200);
            t.check(paused(t) && t.mod("thunderResponse")==response && input.uses.size()==before,"nametag gap and leaving range keep sequence without attacking the other Thunder");
            t.check(c.player.getYRot()==yaw && c.player.getXRot()==pitch,"no automatic rotation");
            first.mob().setPos(c.player.position().add(3,0,0)); hp(first,"?"); t.tick(c,12);
            t.check(input.uses.size()>before && inv.getSelectedSlot()==1,"return keeps latched Chimera despite unknown health");
            var chimera=inv.getItem(1); inv.setItem(1,SequenceSmokeChecks.named("Unrelated sword"));
            before=input.uses.size(); t.tick(c,40);
            t.check(uses(input,before,"Unrelated sword")==0 && input.uses.subList(before,input.uses.size()).stream().anyMatch(s->s.contains(":0:Hyperion")),"missing Chimera uses only validated Ultimate Wise");
            t.check((boolean)SmokeClient.field(response,"missingItemReported"),"fallback notice recorded once per encounter");
            inv.setItem(0,ItemStack.EMPTY); before=input.uses.size(); t.tick(c,140);
            t.check(input.uses.size()==before && paused(t) && t.mod("hookRecovery")==null,"missing both items waits without slot loop or fishing");
            inv.setItem(1,chimera); inv.setItem(0,SequenceSmokeChecks.hyperion("ultimate_wise")); t.tick(c,16);
            t.check(inv.getSelectedSlot()==1 && input.uses.size()>before,"returned Chimera resumes same sequence");
            first.mob().setHealth(0); t.tick(c,35);
            t.check(paused(t) && inv.getSelectedSlot()==1 && uses(input,0,"Ice Spray Wand")==2,"second low target gets its own wand sequence and directly uses Chimera");
            c.setScreen(new HudEditorScreen(null)); second.mob().setHealth(0); t.tick(c,1);
            t.check(!paused(t) && (boolean)t.mod("enabled"),"last death ends pause without disabling helper");
            before=input.uses.size(); t.tick(c,150); t.check(input.uses.size()==before && t.mod("hookRecovery")==null,"open menu still blocks post-fight fishing");
            c.setScreen(null); t.tick(c,99); t.check(t.mod("hookRecovery")==null,"failsafe timer starts fresh after menu closes");
            t.tick(c,1); t.check(t.mod("hookRecovery")!=null,"automatic fishing resumes after all encounters and pauses"); t.toggle(c);
            remove(c,first); remove(c,second); fixtures.clear();

            var delayed=create(c,2_400_080,"Thunder","?",3,1); fixtures.add(delayed);
            input.uses.clear(); t.toggle(c); t.tick(c,40);
            t.check(uses(input,0,"Hyperion")==0 && uses(input,0,"Ice Spray Wand")==1,"initial unknown HP waits after wands");
            hp(delayed,"5.9M"); t.tick(c,16); t.check(inv.getSelectedSlot()==1,"delayed first HP below threshold uses Chimera directly");
            t.toggle(c); Config.INSTANCE.twoHyperions=false; inv.setSelectedSlot(3); input.uses.clear(); t.toggle(c); t.tick(c,25);
            int single=inv.getSelectedSlot(); hp(delayed,"1M"); t.tick(c,16);
            t.check(single==0 && inv.getSelectedSlot()==single,"single-Hyperion mode never switches at the threshold");
            UUID uuid=delayed.mob().getUUID(); remove(c,delayed); fixtures.clear(); before=input.uses.size(); t.tick(c,80);
            t.check(paused(t) && input.uses.size()==before,"temporary entity unload retains pause with no input");
            delayed=create(c,2_400_120,"Thunder","1M",60,1); delayed.mob().setUUID(uuid); fixtures.add(delayed);
            t.tick(c,10); t.check(paused(t),"returning UUID at new ID outside range remains part of encounter");
            t.toggle(c); delayed.mob().setHealth(0); t.tick(c,160);
            t.check(!(boolean)t.mod("enabled") && !paused(t),"manual OFF cannot be undone by encounter end");
            remove(c,delayed); fixtures.clear();

            var jawbus=create(c,2_400_160,"Lord Jawbus","100M",4,13); fixtures.add(jawbus);
            t.toggle(c); input.uses.clear(); t.tick(c,1);
            t.check(((JawbusPauseFeature)t.mod("JAWBUS_PAUSE")).active() && input.uses.isEmpty(),"released Jawbus with non-adjacent nametag blocks catch sequence without chat");
            jawbus.mob().setHealth(0); t.tick(c,1); t.toggle(c); remove(c,jawbus); fixtures.clear();
            t.toggle(c); chat(c,ThunderResponseFeature.SPAWN_MESSAGE); t.tick(c,80);
            t.check(paused(t) && t.mod("hookRecovery")==null,"normal spawn message bridges delayed entity");
            t.mod("observedWorldLevel",null); t.tick(c,250);
            t.check(!(boolean)t.mod("enabled") && !paused(t) && t.mod("thunderResponse")==null,"world change discards pending spawn and cannot re-enable");
            System.out.println("SMOKE_COCOON_OK: no capture/item false positives, release without chat, recast/failsafe exclusion, stable target HP/6M/latch, wands, range/gaps/multiple mobs, item fallback, menus/resume/manual/world");
        } finally {
            if ((boolean)t.mod("enabled")) t.toggle(c);
            for (Mob mob:fixtures) remove(c,mob);
            c.setScreen(null); c.gameMode=oldMode; Config.INSTANCE=oldConfig; Config.save(); SeaCreatureTracker.INSTANCE.reset();
        }
    }
}
