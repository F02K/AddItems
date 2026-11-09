package dev.fosk.additems.pack;

import java.util.List;

/** Registry of supported pack formats, keyed by Minecraft version. */
public final class PackFormats {

    private static final List<PackFormat> SUPPORTED = List.of(new PackFormat26_3());

    private PackFormats() {}

    /** Picks the implementation for the given Minecraft version, or the newest one if unknown. */
    public static PackFormat forVersion(String minecraftVersion) {
        for (PackFormat format : SUPPORTED) {
            if (format.minecraftVersion().equals(minecraftVersion)) return format;
        }
        return SUPPORTED.getLast();
    }

    public static boolean isSupported(String minecraftVersion) {
        return SUPPORTED.stream().anyMatch(f -> f.minecraftVersion().equals(minecraftVersion));
    }
}
