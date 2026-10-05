package com.FishHelper;

import java.util.List;

/** Server pet UUID when available; descriptive fallback never uses a remembered slot. */
public record PetIdentity(String id, String name, String rarity, int level, String heldItem) {
    public boolean stable() { return id != null && id.startsWith("uuid:"); }
    public String fallback() { return name + "|" + rarity + "|" + level + "|" + heldItem; }
    public boolean matches(PetIdentity other) {
        return stable() ? other.stable() && id.equals(other.id) : fallback().equals(other.fallback());
    }
    public static int uniqueIndex(PetIdentity wanted, List<PetIdentity> current) {
        int found = -1;
        for (int i = 0; i < current.size(); i++) if (wanted.matches(current.get(i))) {
            if (found >= 0) return -2;
            found = i;
        }
        return found;
    }
}
