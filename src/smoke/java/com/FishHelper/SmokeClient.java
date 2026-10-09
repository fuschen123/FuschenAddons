package com.FishHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.KeyEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
/** Optional dev-client integration harness. Never included by the normal build. */
public class SmokeClient implements ClientModInitializer {
    Object mod(String name) throws Exception {Field f=FishHelperClient.class.getDeclaredField(name); f.setAccessible(true); return f.get(null);}
    void mod(String name,Object value) throws Exception {Field f=FishHelperClient.class.getDeclaredField(name); f.setAccessible(true); f.set(null,value);}
    void tick(Minecraft c,int count) {for(int i=0;i<count;i++) FishHelperClient.tick(c);}
    void toggle(Minecraft c) {
        net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(Config.INSTANCE.toggleKeyCode)); FishHelperClient.tick(c);
    }
    static class TestHook extends net.minecraft.world.entity.projectile.FishingHook {
        boolean water;
        net.minecraft.world.entity.Entity attached;
        TestHook(Minecraft c,int id) {super(net.minecraft.world.entity.EntityType.FISHING_BOBBER,c.level); setId(id); setOwner(c.player); setPos(c.player.position()); c.level.addEntity(this);}
        @Override public boolean isInWater() {return water;}
        @Override public net.minecraft.world.entity.Entity getHookedIn() {return attached;}
    }
    void runtimeChecks(Minecraft c) throws Exception {
        var p=c.player; var inv=p.getInventory();
        for(int i=0;i<9;i++) inv.setItem(i,net.minecraft.world.item.ItemStack.EMPTY);
        p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        inv.setSelectedSlot(1);
        var rod=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FISHING_ROD);
        rod.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("QA rod"));
        inv.setItem(1,rod);
        var access=new RodAccess(p,1,net.minecraft.world.InteractionHand.MAIN_HAND);
        inv.setItem(1,net.minecraft.world.item.ItemStack.EMPTY); inv.setItem(6,rod);
        check(access.select() && inv.getSelectedSlot()==6,"moved main-hand rod resolves");
        inv.setItem(6,net.minecraft.world.item.ItemStack.EMPTY); p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,rod);
        check(access.select() && access.hand()==net.minecraft.world.InteractionHand.OFF_HAND,"rod moved to offhand");
        p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        check(!access.available(),"genuinely missing rod"); inv.setItem(3,rod); check(access.select() && inv.getSelectedSlot()==3,"returned rod found");

        var old=new TestHook(c,2_000_000); c.level.removeEntity(old.getId(),net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        p.fishing=old; OwnedHookResolver.reset();
        for(int i=0;i<3;i++) OwnedHookResolver.tick(c);
        check(p.fishing==old,"stale pointer needs stable absence"); OwnedHookResolver.tick(c); check(p.fishing==null,"stale pointer cleared after absence");
        var live=new TestHook(c,2_000_010); p.fishing=old; OwnedHookResolver.tick(c);
        check(p.fishing==live && OwnedHookResolver.find(c,p)==live,"current world hook replaces stale pointer");
        c.level.removeEntity(live.getId(),net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        var foreign=new net.minecraft.world.entity.projectile.FishingHook(net.minecraft.world.entity.EntityType.FISHING_BOBBER,c.level);
        foreign.setId(2_000_020); c.level.addEntity(foreign); check(OwnedHookResolver.find(c,p)==null,"unowned hook ignored"); c.level.removeEntity(foreign.getId(),net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);

        inv.setItem(3,net.minecraft.world.item.ItemStack.EMPTY); c.setScreen(null);
        toggle(c); check((boolean)mod("enabled"),"manual enable"); tick(c,160);
        check((boolean)mod("enabled") && mod("hookRecovery")==null && mod("lastProblem").equals("rod"),"missing rod waits enabled without recovery spam");
        inv.setItem(3,rod); tick(c,99); check(mod("hookRecovery")==null,"watchdog does not fire before five seconds");
        tick(c,1); check(mod("hookRecovery")!=null,"watchdog starts a controlled recast at five seconds");
        toggle(c); tick(c,180); check(!(boolean)mod("enabled") && mod("hookRecovery")==null,"manual off never reactivates");

        var waiting=new TestHook(c,2_000_030); waiting.water=true;
        toggle(c); tick(c,500); check((boolean)mod("enabled") && mod("hookRecovery")==null,"ordinary bite wait exempt"); toggle(c);
        waiting.water=false;
        var countdown=new net.minecraft.world.entity.decoration.ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND,c.level);
        countdown.setId(2_000_031); countdown.setPos(waiting.position()); countdown.setCustomName(net.minecraft.network.chat.Component.literal("5s")); c.level.addEntity(countdown);
        Config.INSTANCE.reelInUsingPing=false; Config.INSTANCE.slugfishReelEnabled=false;
        toggle(c); tick(c,250); check(mod("hookRecovery")==null,"valid countdown exempt"); toggle(c);
        c.level.removeEntity(countdown.getId(),net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        Config.INSTANCE.slugfishReelEnabled=true; toggle(c); tick(c,180); check(mod("hookRecovery")==null,"Slugfish timing exempt"); toggle(c); Config.INSTANCE.slugfishReelEnabled=false;
        c.level.removeEntity(waiting.getId(),net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);

        toggle(c); mod("fishingAction",3); mod("actionTimer",0); mod("sosFlarePending",true); tick(c,1);
        check(mod("hookRecovery")!=null && (boolean)mod("sosFlarePending"),"normal catch preserves queued flare through shared recast"); toggle(c);
        toggle(c); mod("fishingAction",5); mod("actionTimer",10_000); tick(c,Config.INSTANCE.petCommandDelayTicks + 622);
        check((boolean)mod("enabled") && mod("hookRecovery")!=null,"stuck pet/action phase times out into recovery"); toggle(c);
        toggle(c); mod("hoppityAwaitingYes",true); mod("hoppityPauseTicks",200); tick(c,200);
        check((boolean)mod("enabled") && !(boolean)mod("hoppityAwaitingYes") && mod("hookRecovery")==null,"Hoppity wait bounded and resumes");
        c.setScreen(new HudEditorScreen(null)); tick(c,200); check(mod("hookRecovery")==null && (boolean)mod("enabled"),"menu freezes automation without disabling");
        c.setScreen(null); tick(c,100); check(mod("hookRecovery")!=null,"automatic continuation after menu"); toggle(c);
        toggle(c); mod("observedWorldLevel",null); tick(c,1); check(!(boolean)mod("enabled") && mod("hookRecovery")==null && mod("normalRod")==null,"world change disables and clears state");
        tick(c,180); check(!(boolean)mod("enabled"),"world-disabled helper cannot reactivate");

        var guardian=new net.minecraft.world.entity.monster.ElderGuardian(net.minecraft.world.entity.EntityType.ELDER_GUARDIAN,c.level);
        guardian.setId(2_000_040); guardian.setPos(p.position().add(4,0,0)); c.level.addEntity(guardian);
        var label=new net.minecraft.world.entity.decoration.ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND,c.level);
        label.setId(2_000_041); label.setPos(guardian.position()); label.setCustomName(net.minecraft.network.chat.Component.literal("[Lv400] Thunder 10M/35M❤")); c.level.addEntity(label);
        SeaCreatureTracker.INSTANCE.reset(); SeaCreatureTracker.INSTANCE.tick(c);
        check(SeaCreatureTracker.INSTANCE.get(guardian.getUUID())!=null && SeaCreatureTracker.INSTANCE.get(label.getUUID())==null,"Thunder maps to actual guardian, not stand");
        check(!ThunderMuter.shouldClean(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.GUARDIAN_AMBIENT,1f)),"muter rejects non-SkyBlock even with living Thunder");
        c.level.removeEntity(label.getId(),net.minecraft.world.entity.Entity.RemovalReason.DISCARDED); c.level.removeEntity(guardian.getId(),net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        check(net.minecraft.network.chat.Component.translatable("key.tsclient.toggle").getString().equals("Start/Stop FishHelper"),"renamed keybind");
        JawbusSmokeChecks.run(this, c);
        ShurikenSmokeChecks.run(this, c);
        SequenceSmokeChecks.run(this, c);
    }
    int ticks, step;
    ConfigScreen screen;
    HudEditorScreen editor;
    CommandDispatcher<FabricClientCommandSource> commands = new CommandDispatcher<>();
    static MouseButtonEvent mouse(double x, double y) { return new MouseButtonEvent(x,y,new MouseButtonInfo(0,0)); }
    static KeyEvent key(int code) { return new KeyEvent(code,0,0); }
    static Object field(Object obj,String name) throws Exception { Field f=obj.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(obj); }
    static void value(Object obj,String name,Object value) throws Exception { Field f=obj.getClass().getDeclaredField(name); f.setAccessible(true); f.set(obj,value); }
    void command(String input) throws Exception { commands.execute(input,null); Minecraft.getInstance().setScreen(null); Method drain=net.minecraft.util.thread.BlockableEventLoop.class.getDeclaredMethod("runAllTasks"); drain.setAccessible(true); drain.invoke(Minecraft.getInstance()); }
    void rebuild() throws Exception { Method m=ConfigScreen.class.getDeclaredMethod("rebuild"); m.setAccessible(true); m.invoke(screen); }
    void click(String prefix) {
        Button b=screen.children().stream().filter(e->e instanceof Button v && v.getMessage().getString().startsWith(prefix)).map(e->(Button)e).findFirst().orElseThrow(()->new AssertionError("Button missing: "+prefix));
        screen.mouseClicked(mouse(b.getX()+8,b.getY()+8),false); screen.mouseReleased(mouse(b.getX()+8,b.getY()+8));
    }
    void check(boolean passed,String message) { if(!passed) throw new AssertionError(message); }
    void screenshot(String name) { Minecraft c=Minecraft.getInstance(); Screenshot.grab(new File("../build/smoke-captures"),name,c.getMainRenderTarget(),1,m->System.out.println("SMOKE_SCREENSHOT: "+m.getString())); }
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client->{ try {
            client.options.pauseOnLostFocus = false;
            if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) client.setScreen(null);
            if(++ticks>5000) throw new AssertionError("Smoke timeout");
            if(client.getOverlay()!=null || ticks<100 || ticks%40!=0) return;
            switch(step++) {
                case 0 -> {
                    FishHelperClient.registerCommands(commands);
                    client.options.guiScale().set(1); client.resizeGui();
                    command("fa"); check(client.screen instanceof ConfigScreen,"/fa"); screen=(ConfigScreen)client.screen;
                    Config.INSTANCE.actionWeapon=Config.ActionWeapon.HYPERION; Config.INSTANCE.flareTier=Config.FlareTier.SOS; Config.INSTANCE.petNumber=1;
                    rebuild(); click("Hyperion"); check(field(screen,"dropdown")!=null,"open dropdown");
                }
                case 1 -> {
                    screenshot("dropdown-weapon.png");
                    Object menu=field(screen,"dropdown"); Button owner=(Button)field(menu,"owner");
                    var box=DropdownLayout.place(screen.width,screen.height,owner.getX(),owner.getY(),owner.getWidth(),owner.getHeight(),4);
                    screen.mouseClicked(mouse(box.x()+10,box.y()+DropdownLayout.PADDING+2*DropdownLayout.ROW_HEIGHT+5),false);
                    check(Config.INSTANCE.actionWeapon==Config.ActionWeapon.SOUL_WHIP,"mouse selection"); check(field(screen,"dropdown")==null,"selection closes");
                    click("SOS Flare"); screen.keyPressed(key(268)); screen.keyPressed(key(264)); screen.keyPressed(key(257));
                    check(Config.INSTANCE.flareTier==Config.FlareTier.WARNING,"keyboard selection");
                    click("Warning Flare"); screen.keyPressed(key(256)); check(field(screen,"dropdown")==null,"Escape closes popup");
                    click("Soul Whip"); screen.mouseClicked(mouse(1,1),false); check(field(screen,"dropdown")==null && client.screen==screen,"outside closes only popup");
                    client.options.guiScale().set(2); client.resizeGui(); value(screen,"scroll",4); rebuild(); click("Choose pet");
                }
                case 2 -> {
                    screenshot("dropdown-pet-compact.png");
                    check(client.screen instanceof PetSelectionScreen,"Cascade pet catalog opened");
                    Config.load(); check(Config.INSTANCE.actionWeapon==Config.ActionWeapon.SOUL_WHIP && Config.INSTANCE.flareTier==Config.FlareTier.WARNING,"dropdown persistence");
                    command("fuschen"); check(client.screen instanceof ConfigScreen,"/fuschen");
                    command("fa gui"); check(client.screen instanceof HudEditorScreen,"/fa gui"); editor=(HudEditorScreen)client.screen;
                    Config.INSTANCE.healthbarX=.5; Config.INSTANCE.healthbarY=.12;
                }
                case 3 -> {
                    screenshot("hud-preview-compact.png"); var p=new HudPosition(Config.INSTANCE.healthbarX,Config.INSTANCE.healthbarY);
                    int x=p.pixelX(editor.width,250),y=p.pixelY(editor.height,55);
                    check(editor.mouseClicked(mouse(x+10,y+10),false),"drag capture");
                    editor.mouseDragged(mouse(editor.width+100,editor.height+100),100,100); editor.mouseReleased(mouse(editor.width+100,editor.height+100));
                    check(Config.INSTANCE.healthbarX==1 && Config.INSTANCE.healthbarY==1,"edge clamp");
                    Config.load(); check(Config.INSTANCE.healthbarX==1 && Config.INSTANCE.healthbarY==1,"HUD persistence");
                }
                case 4 -> {
                    screenshot("hud-dragged-compact.png"); editor.onClose(); command("fuschen gui");
                    check(client.screen instanceof HudEditorScreen,"/fuschen gui"); client.options.guiScale().set(1); client.resizeGui();
                }
                case 5 -> { screenshot("hud-resized.png"); command("fa"); screen=(ConfigScreen)client.screen; click("General"); }
                case 6 -> {
                    screenshot("general-healthbar.png"); click("Edit HUD"); check(client.screen instanceof HudEditorScreen,"HUD config button");
                    System.out.println("SMOKE_UI_OK: dropdowns, commands, HUD and config persistence");
                    command("fa"); screen=(ConfigScreen)client.screen; Config.INSTANCE.thunderMuterEnabled=false; click("Thunder"); click("OFF");
                    check(Config.INSTANCE.thunderMuterEnabled,"Thunder Muter toggle"); Config.load(); check(Config.INSTANCE.thunderMuterEnabled,"Thunder Muter persistence");
                }
                case 7 -> {
                    screenshot("thunder-muter.png");
                    net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(client,()->client.stop());
                }
                case 8 -> {
                    if(!(client.screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen create)) {step--; return;}
                    var state=create.getUiState(); state.setName("Fuschen QA "+System.currentTimeMillis());
                    state.setGameMode(net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode.CREATIVE);
                    state.setAllowCommands(true); state.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL); state.setSeed("42");
                    for(var preset:state.getNormalPresetList()) if(preset.toString().contains("minecraft:flat")) {state.setWorldType(preset); break;}
                    Method onCreate=create.getClass().getDeclaredMethod("onCreate"); onCreate.setAccessible(true); onCreate.invoke(create);
                }
                case 9 -> {
                    if(client.level==null || client.player==null || client.screen!=null) {step--; return;}
                    runtimeChecks(client);
                    PetGridSmokeChecks.prepare(this, client);
                }
                case 10 -> {
                    screenshot("pet-catalog.png");
                    PetGridSmokeChecks.verify(this, client);
                    Config.INSTANCE.twoHyperions=true;Config.INSTANCE.autoDetectHyperions=true;
                    command("fa");screen=(ConfigScreen)client.screen;value(screen,"scroll",7);rebuild();
                }
                case 11 -> {
                    screenshot("hyperion-detection.png");
                    Config.INSTANCE.autoDetectHyperions=false;rebuild();
                }
                case 12 -> {
                    screenshot("hyperion-slots.png");
                    var slider=screen.children().stream().filter(e->e instanceof net.minecraft.client.gui.components.AbstractSliderButton).findFirst().orElseThrow();
                    int original=Config.INSTANCE.ultimateWiseSlot;
                    slider.keyPressed(key(262));check(Config.INSTANCE.ultimateWiseSlot==Math.min(9,original+1),"slot slider steps in integers");
                    Config.load();check(Config.INSTANCE.ultimateWiseSlot==Math.min(9,original+1),"slot slider persists");
                    Config.INSTANCE.petPages.clear();Config.INSTANCE.petMenuPages.clear();Config.INSTANCE.selectedPet=null;Config.INSTANCE.twoHyperions=false;Config.save();
                    ShurikenSmokeChecks.preparePreview(this, client);
                }
                case 13 -> {
                    screenshot("jawbus-shuriken-warning.png");
                    ShurikenSmokeChecks.finishPreview(client);
                    java.nio.file.Files.writeString(java.nio.file.Path.of("../build/smoke-result.txt"), "PASS");
                    System.out.println("SMOKE_RUNTIME_OK: real client rod slots/offhand, stale hook reconciliation, watchdog, legitimate waits, temporary actions, manual off and world disable");
                    client.stop();
                }
            }
        } catch(Throwable e) {e.printStackTrace(); System.out.println("SMOKE_FAILED: "+e); client.stop();} });
    }
}
