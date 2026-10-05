package com.FishHelper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PetSwapSequenceTest {
    static class Controls implements PetSwapSequence.Controls {
        PetSwapSequence.Menu menu = PetSwapSequence.Menu.NONE;
        PetSwapSequence.Selection selected = PetSwapSequence.Selection.SUMMON;
        boolean hook = true;
        int opens, clicks, closes;
        public PetSwapSequence.Menu menu() { return menu; }
        public boolean hookReady() { return hook; }
        public PetSwapSequence.Selection selection() { return selected; }
        public void open() { opens++; }
        public void click() { assertEquals(PetSwapSequence.Selection.SUMMON, selected); clicks++; }
        public void closeOwnedMenu() { if (menu == PetSwapSequence.Menu.PETS) { closes++; menu = PetSwapSequence.Menu.NONE; } }
    }
    void ticks(PetSwapSequence s, Controls c, int count) { for (int i=0;i<count;i++) s.tick(c); }
    @Test void commandWaitHonorsConfigurationAndIsSentOnlyOnce() {
        var c=new Controls(); var s=new PetSwapSequence(80);
        ticks(s,c,80); assertEquals(0,c.opens); s.tick(c); assertEquals(1,c.opens);
        c.menu=PetSwapSequence.Menu.PETS; ticks(s,c,120); assertEquals(1,c.opens); assertEquals(6,c.clicks);
    }
    @Test void retriesAreOneSecondApartAndActivePetIsNeverClickedAgain() {
        var c=new Controls(); c.menu=PetSwapSequence.Menu.PETS; var s=new PetSwapSequence(10);
        s.tick(c); assertEquals(1,c.clicks); ticks(s,c,19); assertEquals(1,c.clicks);
        s.tick(c); assertEquals(2,c.clicks); c.selected=PetSwapSequence.Selection.ACTIVE;
        ticks(s,c,300); assertEquals(2,c.clicks); assertTrue(s.confirmed()); assertFalse(s.active()); assertEquals(1,c.closes);
    }
    @Test void alreadyActivePetAndExistingMenuNeverSendPets() {
        var c=new Controls(); c.menu=PetSwapSequence.Menu.PETS; c.selected=PetSwapSequence.Selection.ACTIVE;
        var s=new PetSwapSequence(0); ticks(s,c,100);
        assertEquals(0,c.clicks); assertEquals(0,c.opens); assertTrue(s.confirmed());
    }
    @Test void menuChangesAndAmbiguousEntriesNeverGetClicked() {
        var c=new Controls(); c.menu=PetSwapSequence.Menu.PETS; c.selected=PetSwapSequence.Selection.AMBIGUOUS;
        var s=new PetSwapSequence(0); ticks(s,c,100); assertEquals(0,c.clicks);
        c.menu=PetSwapSequence.Menu.OTHER; ticks(s,c,100); assertEquals(0,c.clicks); assertEquals(0,c.closes); assertFalse(s.active());
    }
    @Test void unconfirmedServerCannotBlockForever() {
        var c=new Controls(); c.menu=PetSwapSequence.Menu.PETS; var s=new PetSwapSequence(0);
        ticks(s,c,602); assertFalse(s.active()); assertFalse(s.confirmed()); assertEquals(30,c.clicks);
    }
    @Test void noMenuResponseAndNoHookAreBounded() {
        var c=new Controls(); var s=new PetSwapSequence(0); ticks(s,c,102); assertFalse(s.active()); assertEquals(1,c.opens);
        c=new Controls(); c.hook=false; s=new PetSwapSequence(20); ticks(s,c,61); assertFalse(s.active()); assertEquals(0,c.opens);
    }
    @Test void cancellingForJawbusOrManualOffCannotIssueFutureInputs() {
        var c=new Controls(); var s=new PetSwapSequence(5); s.cancel(); ticks(s,c,500);
        assertEquals(0,c.opens); assertEquals(0,c.clicks);
    }
    @Test void closedMenuDoesNotReopenAndIsNotTreatedAsConfirmation() {
        var c=new Controls(); c.menu=PetSwapSequence.Menu.PETS; var s=new PetSwapSequence(0); s.tick(c);
        c.menu=PetSwapSequence.Menu.NONE; ticks(s,c,100);
        assertFalse(s.active()); assertFalse(s.confirmed()); assertEquals(0,c.opens);
    }
    @Test void identitySurvivesReorderingLevelChangesAndDuplicateNames() {
        var wanted=new PetIdentity("uuid:a","Flying Fish","LEGENDARY",90,"None");
        var other=new PetIdentity("uuid:b","Flying Fish","LEGENDARY",90,"None");
        var leveled=new PetIdentity("uuid:a","Flying Fish","LEGENDARY",91,"Washed-up Souvenir");
        assertEquals(1,PetIdentity.uniqueIndex(wanted,List.of(other,leveled)));
        assertEquals(0,PetIdentity.uniqueIndex(wanted,List.of(leveled,other)));
        assertEquals(-2,PetIdentity.uniqueIndex(wanted,List.of(wanted,leveled)));
    }
    @Test void fallbackRequiresUniqueCurrentDescription() {
        var pet=new PetIdentity("","Dolphin","EPIC",100,"None");
        assertEquals(-2,PetIdentity.uniqueIndex(pet,List.of(pet,pet)));
        assertEquals(-1,PetIdentity.uniqueIndex(pet,List.of(new PetIdentity("","Dolphin","RARE",100,"None"))));
    }
}
