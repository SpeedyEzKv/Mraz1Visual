package dev.mraz1visual.module;

import java.util.ArrayList;

public final class LocalArtwork {
    public static final boolean AURA = present("/assets/auravisuals/textures/glow.png");
    private static final boolean PULSE = present("/assets/mraz1visual/textures/fx/pulse_jump.png");

    private static boolean present(String path) {
        return LocalArtwork.class.getResource(path) != null;
    }

    static String[] options(String... pairs) {
        var available = new ArrayList<String>();
        for (int i = 0; i < pairs.length; i += 2) {
            if ((!AURA && (pairs[i].equals("aura") || pairs[i].startsWith("aura_"))) ||
                    (!PULSE && pairs[i].equals("pulse"))) continue;
            available.add(pairs[i]);
            available.add(pairs[i + 1]);
        }
        return available.toArray(String[]::new);
    }

    private LocalArtwork() {}
}
