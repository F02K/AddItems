package dev.fosk.additems.item;

import java.util.Locale;
import java.util.function.Predicate;

/** Helpers for ids of recipes, drop rules and other generated entries ([a-z0-9_], at most 48 characters). */
public final class Ids {

    private Ids() {}

    /** Free text turned into a valid id part: lowercase, other characters become single underscores, never empty. */
    public static String slug(String text) {
        String s = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "entry" : s;
    }

    /**
     * An id derived from {@code base} that no other entry uses: the base itself when free, else base_2, base_3, ...
     * The check and the caller's store should happen under one lock, the dashboard calls this inside synchronized code.
     */
    public static String unique(String base, int maxLength, Predicate<String> taken) {
        String clean = slug(base);
        String stem = clean.length() > maxLength - 4 ? clean.substring(0, maxLength - 4) : clean;
        String candidate = clean.length() <= maxLength ? clean : stem;
        for (int n = 2; taken.test(candidate); n++) candidate = stem + "_" + n;
        return candidate;
    }
}
