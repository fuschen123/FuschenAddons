/* Adapted from Feesh 1.14.0 (MoonTheSadFisher and contributors), Apache-2.0.
 * See THIRD_PARTY_NOTICES.md and licenses/Feesh-Apache-2.0.txt. */
package com.FishHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** Feesh's level/name/heart convention, number parser and entity offsets. */
public record SeaCreatureNametag(String name, Double currentHp, Double maxHp) {
    private static final Pattern HP = Pattern.compile("([0-9.,]+[kKmMbB]?)\\/([0-9.,]+[kKmMbB]?)\\s*❤");
    private static final Pattern CURRENT_HP = Pattern.compile("([0-9.,]+[kKmMbB]?)\\s*❤");
    private static final Pattern PARTIAL_HP = Pattern.compile("([0-9.,]+[kKmMbB]?|\\?)\\/([0-9.,]+[kKmMbB]?|\\?)\\s*❤");
    private static final List<String> NAMES;
    static {
        try (var stream = SeaCreatureNametag.class.getResourceAsStream("/feesh-sea-creatures.txt")) {
            if (stream == null) throw new IllegalStateException("Missing sea creature catalog");
            NAMES = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .sorted(Comparator.comparingInt(String::length).reversed()).toList();
        } catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
    }

    public static SeaCreatureNametag parse(String formatted) {
        if (formatted == null) return null;
        String plain = formatted.replace("§e﴾ ", "").replace(" §e﴿", "")
                .replace("§5§ka", "").replaceAll("§k(?:§.)*a", "").replaceAll("§.", "").trim();
        int levelEnd = plain.indexOf("] ");
        if (!plain.contains("[Lv") || levelEnd < 0) return null;
        if (!plain.contains("❤") && !plain.contains("Puddle Jumper")) return null;
        String body = plain.substring(levelEnd + 2).replace("Corrupted ", "").trim();
        var full = HP.matcher(body);
        var current = CURRENT_HP.matcher(body);
        var partial = PARTIAL_HP.matcher(body);
        Double hp = null, max = null;
        int nameEnd = body.contains("❤") ? body.indexOf('❤') : body.length();
        if (full.find()) {
            nameEnd = full.start(); hp = number(full.group(1)); max = number(full.group(2));
        } else if (partial.find()) {
            nameEnd = partial.start(); hp = number(partial.group(1)); max = number(partial.group(2));
        } else if (current.find() && !body.substring(0, current.start()).contains("/")) {
            // Extension: retain current-only HP. No vanilla max-HP fallback.
            nameEnd = current.start(); hp = number(current.group(1));
        }
        // Feesh removes attribute icons from the base name and exact-matches its catalog.
        String name = body.substring(0, nameEnd).replaceAll("[^a-zA-Z\\s'-]", "").trim().replaceAll("\\s+", " ");
        return NAMES.contains(name) ? new SeaCreatureNametag(name, hp, max) : null;
    }

    private static Double number(String value) {
        try {
            char suffix = Character.toLowerCase(value.charAt(value.length() - 1));
            double scale = switch (suffix) { case 'k' -> 1e3; case 'm' -> 1e6; case 'b' -> 1e9; default -> 1; };
            String digits = scale == 1 ? value : value.substring(0, value.length() - 1);
            double number = Double.parseDouble(digits.replace(",", "")) * scale;
            return Double.isFinite(number) && number >= 0 ? number : null;
        } catch (NumberFormatException e) { return null; }
    }

    /** RareMobHighlight's actual mob association, including composite creature heads. */
    public static int entityOffset(String name) {
        return switch (name) {
            case "Werewolf", "Puddle Jumper" -> 2;
            case "Fire Eel" -> 11;
            case "Drowned Captain" -> 6;
            case "Reindrake" -> 8;
            case "Titanoboa" -> 43;
            default -> 1;
        };
    }

    public boolean dead() { return currentHp != null && currentHp == 0; }
    public SeaCreatureNametag unknownHealth() { return new SeaCreatureNametag(name, null, null); }
}
