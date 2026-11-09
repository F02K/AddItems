package dev.fosk.additems.block;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The sound events this server knows (read from Paper's sound registry), used for autocompletion in the dashboard
 * and to reject sound ids that do not exist. Vanilla sounds are listed without the "minecraft:" prefix
 * ("block.stone.place"); sounds from other namespaces keep theirs.
 */
public final class SoundCatalog {

    private final List<String> names;
    private final Set<String> lookup;

    public SoundCatalog() {
        this.names = Registry.SOUNDS.stream()
                .map(Registry.SOUNDS::getKey)
                .filter(Objects::nonNull)
                .map(SoundCatalog::display)
                .distinct()
                .sorted()
                .toList();
        this.lookup = new HashSet<>(names);
    }

    private static String display(NamespacedKey key) {
        return key.getNamespace().equals(NamespacedKey.MINECRAFT) ? key.getKey() : key.asString();
    }

    public List<String> names() {
        return names;
    }

    /** True if the sound exists; "minecraft:block.stone.place" and "block.stone.place" are the same sound. */
    public boolean isValid(String sound) {
        return sound != null && lookup.contains(strip(sound));
    }

    /** The form that is stored: trimmed and without the default namespace. */
    public static String strip(String sound) {
        String s = sound.trim();
        return s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s;
    }
}
