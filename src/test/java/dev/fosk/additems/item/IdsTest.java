package dev.fosk.additems.item;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdsTest {

    private static final Pattern VALID = Pattern.compile("[a-z0-9_]{1,48}");

    @Test
    void slugKeepsOnlyValidCharacters() {
        assertEquals("ruby_pickaxe", Ids.slug("Ruby Pickaxe"));
        assertEquals("a_b", Ids.slug("__a--b__"));
        assertEquals("entry", Ids.slug("!!!"));
        assertEquals("entry", Ids.slug(null));
    }

    @Test
    void uniqueReturnsTheBaseWhenFreeAndNumbersOtherwise() {
        assertEquals("ruby", Ids.unique("ruby", 48, taken -> false));
        Set<String> used = Set.of("ruby", "ruby_2", "ruby_3");
        assertEquals("ruby_4", Ids.unique("ruby", 48, used::contains));
    }

    @Test
    void longBasesStayWithinTheLimitEvenWithASuffix() {
        String base = "x".repeat(80);
        Set<String> used = new java.util.HashSet<>();
        for (int i = 0; i < 12; i++) {
            String id = Ids.unique(base, 48, used::contains);
            assertTrue(VALID.matcher(id).matches(), id);
            assertTrue(used.add(id), "never hands out the same id twice: " + id);
        }
    }
}
