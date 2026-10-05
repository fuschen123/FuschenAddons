package com.FishHelper;

import com.mojang.blaze3d.platform.InputConstants;
import com.FishHelper.features.RandomMovementFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import java.util.*;

/** Local-world integration. Captures production use/click calls without sending them to a server. */
final class SequenceSmokeChecks {
    static class Inputs extends MultiPlayerGameMode {
        int now;
        List<String> uses = new ArrayList<>();
        List<Integer> clicks = new ArrayList<>();
        Inputs(Minecraft c) { super(c, c.getConnection()); }
        @Override public InteractionResult useItem(Player p, InteractionHand hand) {
            uses.add(now + ":" + p.getInventory().getSelectedSlot() + ":" + p.getItemInHand(hand).getHoverName().getString());
            return InteractionResult.SUCCESS;
        }
        @Override public void handleContainerInput(int menu, int slot, int button, ContainerInput type, Player player) { clicks.add(slot); }
    }
    static ItemStack named(String name) { var s=new ItemStack(Items.DIAMOND_SWORD);s.set(DataComponents.CUSTOM_NAME,Component.literal(name));return s; }
    static ItemStack hyperion(String enchantment) {
        var s=named("Hyperion");var attributes=new CompoundTag();attributes.putString("id","HYPERION");
        var ench=new CompoundTag();ench.putInt(enchantment,5);attributes.put("enchantments",ench);
        s.set(DataComponents.CUSTOM_DATA,CustomData.of(attributes));return s;
    }
    static ItemStack pet(String uuid, boolean active) {
        var s=new ItemStack(Items.PLAYER_HEAD);s.set(DataComponents.CUSTOM_NAME,Component.literal("[Lvl 100] Flying Fish"));
        s.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("Held Item: Washed-up Souvenir"),Component.literal(active?"Click to despawn!":"Left-click to summon!"))));
        var tag=new CompoundTag();tag.putString("petInfo","{\"uuid\":\""+uuid+"\",\"tier\":\"LEGENDARY\",\"heldItem\":\"WASHED_UP_SOUVENIR\"}");
        s.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));return s;
    }
    static ChestMenu pets(Minecraft c,String title) {
        var menu=ChestMenu.sixRows(170,c.player.getInventory(),new SimpleContainer(54));
        c.player.containerMenu=menu;c.setScreen(new ContainerScreen(menu,c.player.getInventory(),Component.literal(title)));return menu;
    }
    static void ticks(SmokeClient t,Minecraft c,Inputs input,int count){for(int i=0;i<count;i++){input.now++;t.tick(c,1);}}
    static void remove(Minecraft c,Entity e){c.level.removeEntity(e.getId(),Entity.RemovalReason.DISCARDED);}
    static void clickExact(ConfigScreen screen,String label) {
        var button=screen.children().stream().filter(e->e instanceof Button b && b.getMessage().getString().equals(label))
                .map(e->(Button)e).findFirst().orElseThrow();
        var event=SmokeClient.mouse(button.getX()+8,button.getY()+8);screen.mouseClicked(event,false);screen.mouseReleased(event);
    }
    static void run(SmokeClient t,Minecraft c) throws Exception {
        var originalGameMode=c.gameMode;var originalConfig=Config.INSTANCE;
        var input=new Inputs(c);c.gameMode=input;Config.INSTANCE=new Config();
        Config.INSTANCE.petSwapEnabled=false;Config.INSTANCE.flareTier=Config.FlareTier.NONE;Config.INSTANCE.autoRodSwap=false;
        var inv=c.player.getInventory();
        try {
            for(int i=0;i<9;i++)inv.setItem(i,ItemStack.EMPTY);
            inv.setItem(3,new ItemStack(Items.FISHING_ROD));inv.setSelectedSlot(8);c.setScreen(null);
            var hook=new SmokeClient.TestHook(c,2_200_000);hook.water=true;
            t.toggle(c);ticks(t,c,input,250);
            t.check(inv.getSelectedSlot()==8 && input.uses.isEmpty(),"auto-rod OFF respects manual slot during idle fishing");
            Config.INSTANCE.autoRodSwap=true;ticks(t,c,input,1);t.check(inv.getSelectedSlot()==3,"auto-rod ON finds rod");
            Config.INSTANCE.autoRodSwap=false;inv.setSelectedSlot(8);t.toggle(c);
            t.check(inv.getSelectedSlot()==8,"manual OFF does not override idle slot");

            inv.setItem(0,hyperion("ultimate_wise"));inv.setItem(1,hyperion("ultimate_chimera"));
            Config.INSTANCE.twoHyperions=true;Config.INSTANCE.ultimateWiseSlot=1;Config.INSTANCE.chimeraSlot=2;
            Config.INSTANCE.autoDetectHyperions=false;
            t.check(HyperionAccess.validPair(c.player),"actual custom-data enchants validate both Hyperions");
            Config.INSTANCE.chimeraSlot=1;t.check(!HyperionAccess.validPair(c.player),"same slot refused");Config.INSTANCE.chimeraSlot=2;
            var chimera=inv.getItem(1);inv.setItem(1,named("Unrelated sword"));
            t.check(HyperionAccess.find(c.player,HyperionPolicy.Kind.ULTIMATE_WISE)<0,"changed hotbar invalidates the pair");inv.setItem(1,chimera);
            HyperionDetectionSmokeChecks.run(t,c);

            var mob=new MagmaCube(EntityType.MAGMA_CUBE,c.level);mob.setId(2_200_010);mob.setPos(c.player.position());c.level.addEntity(mob);hook.attached=mob;
            input.uses.clear();input.now=0;t.toggle(c); // recognition creates the sequence; no use on this tick
            ticks(t,c,input,4);t.check(input.uses.isEmpty(),"hook Hyperion has a four-tick delay");ticks(t,c,input,1);
            t.check(input.uses.size()==1 && input.uses.getFirst().equals("5:1:Hyperion"),"fifth tick uses Chimera exactly once");
            ticks(t,c,input,20);t.check(input.uses.stream().filter(s->s.endsWith(":Hyperion")).count()==1,"no duplicate recovery Hyperion");t.toggle(c);
            var guard=new HookEncounterGuard();var recovery=new HookRecoveryFeature(c,hook.getUUID(),mob.getUUID(),3,InteractionHand.MAIN_HAND,true,guard);
            input.uses.clear();recovery.tick();hook.attached=null;for(int i=0;i<12;i++)recovery.tick();
            t.check(input.uses.stream().noneMatch(s->s.endsWith(":Hyperion")),"detached mob cancels delayed Hyperion");recovery.finish();remove(c,mob);

            Config.INSTANCE.petSwapEnabled=true;Config.INSTANCE.petPages.clear();
            String a="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",b="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
            Config.INSTANCE.selectedPet=PetMenus.read(pet(a,false));
            t.toggle(c);t.mod("fishingAction",6);t.mod("actionTimer",0);
            var menu=pets(c,"Pets (1/2)");menu.slots.get(10).set(PetGridSmokeChecks.favorite(pet(a,false),"⭐"));menu.slots.get(11).set(pet(b,false));
            input.clicks.clear();ticks(t,c,input,1);t.check(input.clicks.equals(List.of(10)),"first pet click resolves a favorited pet by UUID");
            menu.slots.get(10).set(ItemStack.EMPTY);menu.slots.get(20).set(pet(a,false));
            ticks(t,c,input,19);t.check(input.clicks.size()==1,"no retry before twenty ticks");ticks(t,c,input,1);
            t.check(input.clicks.equals(List.of(10,20)),"retry resolves moved pet rather than stale slot");
            menu.slots.get(20).set(PetGridSmokeChecks.favorite(pet(a,true),"★"));ticks(t,c,input,1);
            t.check(input.clicks.size()==2 && c.screen==null && (int)t.mod("fishingAction")==-1,"active favorite confirms swap without despawn");
            t.check(Config.INSTANCE.petPages.size()==1 && Config.INSTANCE.petPageCount==2,"only observed pages cached");
            t.mod("fishingAction",6);menu=pets(c,"Bank");menu.slots.get(10).set(pet(a,false));ticks(t,c,input,50);
            t.check(input.clicks.size()==2 && c.screen!=null,"foreign menu never clicked or closed");c.player.closeContainer();
            Config.INSTANCE.petSwapEnabled=false;t.mod("fishingAction",5);ticks(t,c,input,50);
            t.check((int)t.mod("fishingAction")==-1 && input.clicks.size()==2,"disabled pet swap does not click");t.toggle(c);

            remove(c,hook);hook=new SmokeClient.TestHook(c,2_200_011);hook.water=true;
            Config.INSTANCE.flareTier=Config.FlareTier.ALERT;Config.INSTANCE.flareSwapDelayTicks=3;
            inv.setItem(5,named("SOS Flare"));inv.setSelectedSlot(8);input.uses.clear();
            t.toggle(c);ticks(t,c,input,198);t.check(t.mod("flare")==null,"periodic flare waits ten seconds");ticks(t,c,input,1);
            t.check(t.mod("flare")!=null,"periodic flare runs at 200 enabled ticks");ticks(t,c,input,1);
            t.check(inv.getSelectedSlot()==5 && input.uses.isEmpty(),"flare selects before use");ticks(t,c,input,2);t.check(input.uses.isEmpty(),"flare respects swap delay");
            ticks(t,c,input,1);t.check(input.uses.size()==1,"flare use after delay");ticks(t,c,input,3);
            t.check(inv.getSelectedSlot()==8 && t.mod("flare")==null,"flare restores original manual slot without recast");
            var stand=new ArmorStand(EntityType.ARMOR_STAND,c.level);stand.setId(2_200_020);stand.setPos(c.player.position());c.level.addEntity(stand);
            stand.setCustomName(Component.literal("Warning Flare 100s"));t.check(!FlareFeature.nearby(c,2),"lower flare does not satisfy Alert");
            stand.setCustomName(Component.literal("Plasmaflux Power Orb 60s"));t.check(!FlareFeature.nearby(c,1),"Plasmaflux never counts");
            stand.setCustomName(Component.literal("SOS Flare 100s"));t.check(FlareFeature.nearby(c,2),"higher flare satisfies Alert");
            var skull=new ItemStack(Items.PLAYER_HEAD);
            var properties=com.google.common.collect.ArrayListMultimap.<String,com.mojang.authlib.properties.Property>create();
            String texture=Base64.getEncoder().encodeToString("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/c0062cc98ebda72a6a4b89783adcef2815b483a01d73ea87b3df76072a89d13b\"}}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            properties.put("textures",new com.mojang.authlib.properties.Property("textures",texture));
            skull.set(DataComponents.PROFILE,net.minecraft.world.item.component.ResolvableProfile.createResolved(
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(),"flare",new com.mojang.authlib.properties.PropertyMap(properties))));
            stand.setCustomName(null);stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,skull);
            t.check(FlareFeature.nearby(c,3),"real SOS skull texture works without nametag");
            int before=input.uses.size();ticks(t,c,input,200);t.check(input.uses.size()==before,"existing flare prevents placement");
            t.toggle(c);remove(c,stand);Config.INSTANCE.flareTier=Config.FlareTier.NONE;

            var thunder=new ElderGuardian(EntityType.ELDER_GUARDIAN,c.level);thunder.setId(2_200_030);thunder.setPos(c.player.position().add(3,0,0));c.level.addEntity(thunder);
            var label=new ArmorStand(EntityType.ARMOR_STAND,c.level);label.setId(2_200_031);label.setPos(thunder.position());
            label.setCustomName(Component.literal("[Lv400] Thunder 35M/35M❤"));c.level.addEntity(label);
            SeaCreatureTracker.INSTANCE.reset();SeaCreatureTracker.INSTANCE.tick(c);Config.INSTANCE.thunderResponseEnabled=true;
            inv.setItem(6,named("Ice Spray Wand"));inv.setItem(7,named("Ink Wand"));inv.setSelectedSlot(8);
            var response=new ThunderResponseFeature(c,8,c.player.position());input.uses.clear();
            for(int i=0;i<30;i++){c.player.setYRot(37+i);c.player.setXRot(-15+i);float yaw=c.player.getYRot(),pitch=c.player.getXRot();response.tick();
                t.check(c.player.getYRot()==yaw && c.player.getXRot()==pitch,"Thunder never rotates camera during wand/Hyperion use");}
            t.check(input.uses.stream().anyMatch(s->s.contains(":0:Hyperion")),"high HP Thunder uses Ultimate Wise");
            label.setCustomName(Component.literal("[Lv400] Thunder 3M/35M❤"));for(int i=0;i<6;i++)SeaCreatureTracker.INSTANCE.tick(c);
            int count=input.uses.size();for(int i=0;i<12;i++)response.tick();
            t.check(input.uses.subList(count,input.uses.size()).stream().anyMatch(s->s.contains(":1:Hyperion")),"3M Thunder switches to Chimera");
            thunder.setPos(c.player.position().add(20,0,0));count=input.uses.size();for(int i=0;i<30;i++)response.tick();
            t.check(response.hasLivingThunder() && input.uses.size()==count,"out of range is alive and no attack");
            float yaw=c.player.getYRot(),pitch=c.player.getXRot();response.finish();
            t.check(c.player.getYRot()==yaw && c.player.getXRot()==pitch && inv.getSelectedSlot()==8,"cancel restores slot but never view");
            remove(c,thunder);remove(c,label);remove(c,hook);

            c.setScreen(new ConfigScreen(null,ModBindings.get("toggle")));var screen=(ConfigScreen)c.screen;t.screen=screen;t.click("General");
            String old=ModBindings.get("toggle").saveString();clickExact(screen,ModBindings.get("toggle").getTranslatedKeyMessage().getString());
            t.check(ModBindings.get("toggle").saveString().equals(old),"starting capture click is not assigned");
            var mouse=new MouseButtonEvent(20,20,new MouseButtonInfo(4,0));screen.mouseClicked(mouse,false);screen.mouseReleased(mouse);
            t.check(ModBindings.get("toggle").saveString().equals("key.mouse.5"),"side mouse button assigned using vanilla key type: " + ModBindings.get("toggle").saveString() + ", listener=" + SmokeClient.field(screen,"listeningForKey") + ", target=" + SmokeClient.field(screen,"bindingId"));
            Config.load();t.check(Config.INSTANCE.toggleBinding.equals("key.mouse.5"),"mouse binding persisted");
            clickExact(screen,ModBindings.get("toggle").getTranslatedKeyMessage().getString());screen.keyPressed(SmokeClient.key(256));
            t.check(ModBindings.get("toggle").saveString().equals("key.mouse.5"),"Escape cancels key capture");
            clickExact(screen,ModBindings.get("toggle").getTranslatedKeyMessage().getString());screen.keyPressed(SmokeClient.key(82));
            Config.load();t.check(Config.INSTANCE.toggleBinding.equals("key.keyboard.r"),"keyboard binding persisted after mouse replacement");
            String configKey=ModBindings.get("config").saveString(),movementKey=ModBindings.get("movement").saveString();
            clickExact(screen,ModBindings.get("config").getTranslatedKeyMessage().getString());
            mouse=new MouseButtonEvent(20,20,new MouseButtonInfo(3,0));screen.mouseClicked(mouse,false);screen.mouseReleased(mouse);
            clickExact(screen,ModBindings.get("movement").getTranslatedKeyMessage().getString());
            mouse=new MouseButtonEvent(20,20,new MouseButtonInfo(5,0));screen.mouseClicked(mouse,false);screen.mouseReleased(mouse);
            Config.load();t.check(Config.INSTANCE.configBinding.equals("key.mouse.4") && Config.INSTANCE.movementBinding.equals("key.mouse.6"),"all mod bindings support mouse and persistence");
            ModBindings.bind("config",InputConstants.getKey(configKey));ModBindings.bind("movement",InputConstants.getKey(movementKey));
            t.check(!(boolean)t.mod("enabled"),"binding does not toggle fishing");
            c.setScreen(null);RandomMovementFeature.setCenter(c);
            var type=RandomMovementFeature.class;var center=type.getDeclaredField("center");center.setAccessible(true);
            t.check(center.get(null).equals(c.player.position()),"movement center uses actual current position");
            RandomMovementFeature.reset(c);t.check(center.get(null)==null,"world reset discards movement center");
            MovementSmokeChecks.run(t,c);
            System.out.println("SMOKE_SEQUENCES_OK: pet UUID/retry/active/foreign-menu, auto-rod, hook fifth tick, Hyperion validation/3M/no rotation, periodic delayed flares, mouse/keyboard persistence, movement center");
        } finally {
            if((boolean)t.mod("enabled"))t.mod("enabled",false);
            c.setScreen(null);c.player.containerMenu=c.player.inventoryMenu;c.gameMode=originalGameMode;Config.INSTANCE=originalConfig;
            ModBindings.bind("toggle",InputConstants.Type.KEYSYM.getOrCreate(originalConfig.toggleKeyCode));Config.save();
        }
    }
}
