package dev.fosk.additems.recipe;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reference to an item used in a recipe, stored as a string:
 * <ul>
 *   <li>{@code additems:ruby}: a custom item (namespace = plugin namespace)</li>
 *   <li>{@code minecraft:iron_ingot}: a vanilla item</li>
 *   <li>{@code #minecraft:planks}: a vanilla item tag (ingredients only)</li>
 * </ul>
 */
public record ItemRef(Kind kind, String namespace, String name) {

    public enum Kind { CUSTOM, VANILLA, TAG }

    private static final Pattern SYNTAX = Pattern.compile("^(#?)([a-z0-9_.-]{1,32}):([a-z0-9_./-]{1,64})$");

    /** Parses a reference, or returns null if the syntax is invalid. */
    public static ItemRef parse(String raw, String customNamespace) {
        if (raw == null) return null;
        var m = SYNTAX.matcher(raw.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) return null;
        boolean tag = !m.group(1).isEmpty();
        String ns = m.group(2);
        String name = m.group(3);
        if (tag) return new ItemRef(Kind.TAG, ns, name);
        return ns.equals(customNamespace) ? new ItemRef(Kind.CUSTOM, ns, name) : new ItemRef(Kind.VANILLA, ns, name);
    }

    public String asString() {
        return (kind == Kind.TAG ? "#" : "") + namespace + ":" + name;
    }
}
