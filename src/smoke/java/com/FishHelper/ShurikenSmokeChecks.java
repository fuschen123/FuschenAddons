package com.FishHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;

/** Local entities only. The test input recorder never sends item-use packets. */
final class ShurikenSmokeChecks {
    private static final String BASE = "§e﴾ §8[§7Lv600§8] §c♆§7⚙§d♣ §c§lLord Jawbus§r §a100M§f/§a100M§c❤ §e﴿";
    private record Mob(IronGolem entity, ArmorStand tag) { }
    private static Mob preview;
    private static Mob create(Minecraft c, int id, double distance, String name) {
        var mob = new IronGolem(EntityType.IRON_GOLEM, c.level); mob.setId(id);
        mob.setPos(c.player.position().add(distance, 0, 0)); c.level.addEntity(mob);
        var tag = new ArmorStand(EntityType.ARMOR_STAND, c.level); tag.setId(id + 1); tag.setPos(mob.position());
        tag.setCustomName(Component.literal(name)); c.level.addEntity(tag);
        return new Mob(mob, tag);
    }
    private static void remove(Minecraft c, Entity entity) { c.level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED); }
    private static boolean visible(Minecraft c) { return JawbusShurikenWarningFeature.INSTANCE.visible(c); }
    private static boolean paused(SmokeClient t) throws Exception { return ((JawbusPauseFeature)t.mod("JAWBUS_PAUSE")).active(); }
    static void run(SmokeClient t, Minecraft c) throws Exception {
        var originalGameMode = c.gameMode; var input = new SequenceSmokeChecks.Inputs(c); c.gameMode = input;
        SeaCreatureTracker.INSTANCE.reset(); JawbusShurikenWarningFeature.INSTANCE.reset();
        var a = create(c, 2_400_000, 8, "[Lv600] Lord Jawbus ?/100M❤");
        var b = create(c, 2_400_010, 12, BASE + " §b✯");
        var far = create(c, 2_400_020, 96, BASE);
        try {
            t.toggle(c); t.tick(c, 12);
            t.check(paused(t) && !visible(c), "unknown nearby and marked Jawbus do not warn; distant unmarked Jawbus excluded");
            a.tag.setCustomName(Component.literal(BASE)); t.tick(c, 12);
            t.check(visible(c), "confirmed unmarked Jawbus warns even beside marked Jawbus");
            a.tag.setCustomName(null); t.tick(c, 20);
            t.check(visible(c) && paused(t), "short missing nametag keeps warning and pause");
            t.tick(c, 25);
            t.check(!visible(c) && paused(t), "stale status becomes unknown without resuming fishing");
            a.tag.setCustomName(Component.literal(BASE)); t.tick(c, 12);
            t.check(visible(c), "fresh full missing reads restore warning");
            a.tag.setCustomName(Component.literal("[Lv600] Lord Jawbus ?/100M❤ §b✯")); t.tick(c, 6);
            t.check(!visible(c) && paused(t), "explicit Shuriken marker removes warning even with loading HP, without ending pause");
            a.tag.setCustomName(Component.literal(BASE)); b.tag.setCustomName(Component.literal(BASE)); t.tick(c, 12);
            a.tag.setCustomName(Component.literal(BASE + " §b✯")); t.tick(c, 6);
            t.check(visible(c), "second unmarked Jawbus keeps warning");
            b.tag.setCustomName(Component.literal(BASE.replace("100M§f/", "0§f/"))); t.tick(c, 6);
            t.check(!visible(c) && b.entity.isAlive(), "zero HP removes warning before vanilla mob removal");
            a.tag.setCustomName(Component.literal(BASE)); t.tick(c, 12);
            t.check(visible(c), "unmarking is detected");
            a.entity.setHealth(0); t.tick(c, 1);
            t.check(!visible(c), "actual entity death removes warning immediately");
            t.check(input.uses.isEmpty() && t.mod("hookRecovery") == null, "warning/status changes never bypass Jawbus input gate or failsafe");
            t.toggle(c); remove(c, a.entity); remove(c, a.tag); remove(c, b.entity); remove(c, b.tag);
            a = create(c, 2_400_030, 8, BASE); t.tick(c, 12);
            t.check(visible(c) && !(boolean)t.mod("enabled"), "read-only warning also works with FishHelper disabled");
            remove(c, a.entity); remove(c, a.tag); t.tick(c, 1);
            t.check(!visible(c), "despawn removes warning");
            a = create(c, 2_400_040, 8, BASE); t.tick(c, 12);
            t.check(visible(c), "warning active before connection reset");
            t.mod("observedConnection", null); t.tick(c, 1);
            t.check(!visible(c) && !(boolean)t.mod("enabled"), "server connection change resets warning without enabling fishing");
            t.tick(c, 12); t.check(visible(c), "new live observations may reacquire the local fixture");
            t.mod("observedWorldLevel", null); t.tick(c, 1);
            t.check(!visible(c), "world change clears warning");
            System.out.println("SMOKE_SHURIKEN_OK: unknown/missing/marked, exact entity association, multi-Jawbus, tag gaps, death/despawn, world/server reset, no input bypass");
        } finally {
            remove(c, a.entity); remove(c, a.tag); remove(c, b.entity); remove(c, b.tag); remove(c, far.entity); remove(c, far.tag);
            JawbusShurikenWarningFeature.INSTANCE.reset(); SeaCreatureTracker.INSTANCE.reset(); c.gameMode = originalGameMode;
        }
    }
    static void preparePreview(SmokeClient t, Minecraft c) {
        c.setScreen(null); preview = create(c, 2_400_050, 8, BASE); t.tick(c, 12);
        t.check(visible(c), "warning visible for real HUD screenshot");
    }
    static void finishPreview(Minecraft c) {
        remove(c, preview.entity); remove(c, preview.tag); preview = null;
        JawbusShurikenWarningFeature.INSTANCE.reset(); SeaCreatureTracker.INSTANCE.reset();
    }
}
