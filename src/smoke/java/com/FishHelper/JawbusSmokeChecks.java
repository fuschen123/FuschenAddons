package com.FishHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.MagmaCube;
import java.util.UUID;

/** Synthetic local-world fixtures exercise the real input gate, not a parallel test scheduler. */
final class JawbusSmokeChecks {
    private record Mob(IronGolem entity, ArmorStand label) { }
    private static Mob create(Minecraft client, int id, UUID uuid, double distance) {
        IronGolem mob = new IronGolem(EntityType.IRON_GOLEM, client.level);
        mob.setId(id);
        if (uuid != null) mob.setUUID(uuid);
        mob.setPos(client.player.position().add(distance, 0, 0));
        client.level.addEntity(mob);
        ArmorStand label = new ArmorStand(EntityType.ARMOR_STAND, client.level);
        label.setId(id + 1); label.setPos(mob.position());
        label.setCustomName(Component.literal("[Lv600] Lord Jawbus 100M/100M❤"));
        client.level.addEntity(label);
        return new Mob(mob, label);
    }
    private static void remove(Minecraft client, Entity entity) {
        client.level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
    }
    private static boolean paused(SmokeClient test) throws Exception {
        return ((JawbusPauseFeature) test.mod("JAWBUS_PAUSE")).active();
    }
    private static void spawn(Minecraft client) {
        FishHelperClient.onGameMessage(client, Component.literal(JawbusPauseFeature.SPAWN_MESSAGE), false);
    }

    static void run(SmokeClient test, Minecraft client) throws Exception {
        test.check(!(boolean) test.mod("enabled"), "Jawbus fixtures start with manual OFF");
        client.setScreen(null);
        test.toggle(client); test.tick(client, 100);
        HookRecoveryFeature cancelled = (HookRecoveryFeature) test.mod("hookRecovery");
        test.check(cancelled != null && !cancelled.castIssued(), "watchdog recast is pending");
        test.mod("hoppityAwaitingYes", true); test.mod("hoppityPauseTicks", 40);
        test.mod("sosFlarePending", true); test.mod("fishingAction", 3); test.mod("actionTimer", 0);
        spawn(client);
        test.check(paused(test) && (boolean) test.mod("enabled"), "own Jawbus message immediately pauses without disabling");
        test.check(test.mod("hookRecovery") == null && cancelled.problem() == RecastSequence.Problem.CANCELLED
                && !(boolean) test.mod("sosFlarePending") && (int) test.mod("fishingAction") == -1,
                "pending recast and catch/flare actions synchronously cancelled");
        test.tick(client, 80);
        test.check(paused(test) && test.mod("hookRecovery") == null, "spawn packet delay bridged");
        Mob first = create(client, 2_100_000, null, 8);
        Mob second = create(client, 2_100_010, null, 12);
        test.tick(client, 6);
        test.check(SeaCreatureTracker.INSTANCE.get(first.entity().getUUID()) != null, "central recognition maps Jawbus to real mob");
        SmokeClient.TestHook stuck = new SmokeClient.TestHook(client, 2_100_020);
        MagmaCube hooked = new MagmaCube(EntityType.MAGMA_CUBE, client.level);
        hooked.setId(2_100_030); hooked.setPos(client.player.position()); client.level.addEntity(hooked);
        stuck.attached = hooked;
        test.check(HookRecoveryFeature.isBlockingMob(stuck, client.player), "fixture would trigger normal mob recovery");
        boolean thunderOption = Config.INSTANCE.thunderResponseEnabled;
        Config.INSTANCE.thunderResponseEnabled = true;
        FishHelperClient.onGameMessage(client, Component.literal(ThunderResponseFeature.SPAWN_MESSAGE), false);
        FishHelperClient.onGameMessage(client, Component.literal("The sky darkens and the air thickens. The end times are upon us: Ragnarok is here."), false);
        Config.INSTANCE.thunderResponseEnabled = thunderOption;
        client.player.getInventory().setSelectedSlot(7); // The player retains item control during the fight.
        test.tick(client, 300);
        test.check(paused(test) && test.mod("hookRecovery") == null && test.mod("thunderResponse") == null
                && (int) test.mod("fishingAction") == -1 && (int) test.mod("hotspotRadarStage") == 0
                && !cancelled.castIssued() && client.player.getInventory().getSelectedSlot() == 7,
                "watchdog, attached-mob recovery, chat actions and queued recast cannot run during Jawbus");
        test.check((int) test.mod("hoppityPauseTicks") == 40, "independent Hoppity wait preserved");
        remove(client, stuck); remove(client, hooked);
        first.entity().setPos(client.player.position().add(80, 0, 0));
        remove(client, first.label()); test.tick(client, 250);
        test.check(paused(test), "nametag loss and leaving attack/acquisition radius do not release living Jawbus");
        first.entity().setHealth(0); test.tick(client, 1);
        test.check(paused(test), "second Jawbus keeps pause after first death");
        UUID survivor = second.entity().getUUID();
        remove(client, second.entity()); remove(client, second.label());
        test.tick(client, JawbusPause.ABSENCE_TICKS - 1);
        test.check(paused(test), "short entity unload does not release pause");
        second = create(client, 2_100_050, survivor, 60); // Same UUID, different runtime ID, already outside acquisition range.
        test.tick(client, 6);
        test.check(paused(test), "returning UUID outside range stays relevant after entity ID changes");
        client.setScreen(new HudEditorScreen(null));
        second.label().setCustomName(Component.literal("[Lv600] Lord Jawbus 0/100M❤"));
        for (int i = 0; i < 6 && paused(test); i++) test.tick(client, 1);
        test.check(!paused(test) && second.entity().isAlive(), "zero-HP nametag confirms death before vanilla entity removal");
        test.check((boolean) test.mod("enabled") && (int) test.mod("hoppityPauseTicks") == 40,
                "Jawbus end leaves helper enabled and preserves other pause reasons");
        test.tick(client, 39);
        test.check(test.mod("hookRecovery") == null && (int) test.mod("hoppityPauseTicks") == 1, "Hoppity still blocks continuation");
        test.tick(client, 151);
        test.check(test.mod("hookRecovery") == null && (int) test.mod("fishingAction") == -1, "open menu still blocks continuation");
        client.setScreen(null); test.tick(client, 99);
        test.check(test.mod("hookRecovery") == null, "post-Jawbus failsafe gets a fresh full five-second window");
        test.tick(client, 1);
        test.check(test.mod("hookRecovery") != null, "fishing resumes automatically after all pause reasons end");
        test.toggle(client);
        remove(client, first.entity()); remove(client, second.entity()); remove(client, second.label());

        // Recognition alone must preempt the catch sequence before the next rod-use tick.
        Mob nearby = create(client, 2_100_100, null, 20);
        SeaCreatureTracker.INSTANCE.reset();
        test.toggle(client);
        test.check(paused(test), "nearby Jawbus pauses even without own spawn message");
        test.toggle(client);
        nearby.entity().setHealth(0); spawn(client); test.tick(client, 400);
        test.check(!(boolean) test.mod("enabled") && !paused(test) && test.mod("hookRecovery") == null,
                "manual OFF has priority and a later spawn/end never enables fishing");
        remove(client, nearby.entity()); remove(client, nearby.label());

        test.toggle(client); test.mod("fishingAction", 0); test.mod("actionTimer", 0);
        spawn(client);
        test.check(paused(test) && (int) test.mod("fishingAction") == -1, "spawn during normal catch cancels scheduled reel immediately");
        test.mod("observedWorldLevel", null); test.tick(client, 400);
        test.check(!(boolean) test.mod("enabled") && !paused(test) && test.mod("hookRecovery") == null,
                "world change discards pending Jawbus spawn and cannot auto-enable");
        System.out.println("SMOKE_JAWBUS_OK: immediate spawn/catch/recast cancellation, recovery exclusion, multi-mob/range/gap/UUID tracking, HP-zero death, Hoppity/menu continuation, fresh watchdog, manual/world OFF");
    }
}
