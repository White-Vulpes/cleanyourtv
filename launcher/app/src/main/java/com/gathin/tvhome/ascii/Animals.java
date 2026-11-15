package com.gathin.tvhome.ascii;

import java.util.Locale;

/** Port of src/animals/index.ts: every animal by its key. */
public final class Animals {
    private Animals() {}

    /** A fresh animal for its key (butterflies, koi, jellyfish, dragonflies, frogs, cranes, crabs), or null. */
    public static Animal<?> create(String key) {
        switch (key.trim().toLowerCase(Locale.ROOT)) {
            case "butterflies": case "butterfly": return new Butterflies();
            case "koi": return new Koi();
            case "jellyfish": return new Jellyfish();
            case "dragonflies": case "dragonfly": return new Dragonflies();
            case "frogs": case "frog": return new Frogs();
            case "cranes": case "crane": return new Cranes();
            case "crabs": case "crab": return new Crabs();
            default: return null;
        }
    }
}
