package dev.fosk.additems.recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Persisted description of one recipe (one JSON file). Plain data holder (Gson). */
public final class RecipeDefinition {

    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]{1,48}");

    public String id;
    public RecipeType type = RecipeType.SHAPED;

    /** Result item reference (custom or vanilla, never a tag). */
    public String result;
    public int amount = 1;

    /** SHAPED / SHAPELESS: 9 cells (row-major 3x3), empty string or null = empty cell. */
    public List<String> grid = emptyGrid();

    /** Cooking and stonecutting input. */
    public String input;
    public float experience = 0.35f;
    public int cookTime = 200;

    /** SMITHING. */
    public String template;
    public String base;
    public String addition;

    /** ANVIL: left item, right item (with amount consumed) and the level cost. */
    public String left;
    public String right;
    public int rightAmount = 1;
    public int levelCost = 1;

    public static List<String> emptyGrid() {
        List<String> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) grid.add("");
        return grid;
    }

    /** Result of laying out the grid as a minimal shaped pattern. */
    public record Shape(String[] pattern, Map<Character, String> ingredients) {}

    /** Trims empty rows/columns and assigns a letter per distinct reference. Null if the grid is empty. */
    public Shape shape() {
        int minR = 3, maxR = -1, minC = 3, maxC = -1;
        for (int i = 0; i < 9; i++) {
            if (!blank(cell(i))) {
                minR = Math.min(minR, i / 3);
                maxR = Math.max(maxR, i / 3);
                minC = Math.min(minC, i % 3);
                maxC = Math.max(maxC, i % 3);
            }
        }
        if (maxR < 0) return null;
        Map<String, Character> letters = new LinkedHashMap<>();
        Map<Character, String> ingredients = new LinkedHashMap<>();
        String[] pattern = new String[maxR - minR + 1];
        for (int r = minR; r <= maxR; r++) {
            StringBuilder row = new StringBuilder();
            for (int c = minC; c <= maxC; c++) {
                String ref = cell(r * 3 + c);
                if (blank(ref)) {
                    row.append(' ');
                } else {
                    char letter = letters.computeIfAbsent(ref, k -> (char) ('A' + letters.size()));
                    ingredients.put(letter, ref);
                    row.append(letter);
                }
            }
            pattern[r - minR] = row.toString();
        }
        return new Shape(pattern, ingredients);
    }

    /** Non-empty grid cells in order (shapeless). */
    public List<String> gridRefs() {
        List<String> refs = new ArrayList<>();
        for (int i = 0; i < 9; i++) if (!blank(cell(i))) refs.add(cell(i));
        return refs;
    }

    private String cell(int i) {
        return grid != null && i < grid.size() ? grid.get(i) : null;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** All references this recipe uses as ingredients (not the result). */
    public List<String> ingredientRefs() {
        List<String> refs = new ArrayList<>();
        if (type == null) return refs;
        switch (type) {
            case SHAPED, SHAPELESS -> refs.addAll(gridRefs());
            case FURNACE, BLASTING, SMOKING, CAMPFIRE, STONECUTTING -> refs.add(input);
            case SMITHING -> { refs.add(template); refs.add(base); refs.add(addition); }
            case ANVIL -> { refs.add(left); refs.add(right); }
        }
        return refs;
    }

    /** Structural validation (syntax only; whether items exist is checked when registering). */
    public String validate(String customNamespace) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) return "id must match [a-z0-9_]{1,48}";
        if (type == null) return "type is required";
        ItemRef resultRef = ItemRef.parse(result, customNamespace);
        if (resultRef == null || resultRef.kind() == ItemRef.Kind.TAG) return "a valid result item is required";
        if (amount < 1 || amount > 64) return "amount must be 1-64";
        if (grid != null && grid.size() > 9) return "grid has too many cells";

        switch (type) {
            case SHAPED, SHAPELESS -> {
                if (gridRefs().isEmpty()) return "put at least one ingredient in the grid";
                if (type == RecipeType.SHAPELESS && gridRefs().size() < 1) return "ingredients required";
            }
            case SMITHING -> {
                if (blank(template) || blank(base) || blank(addition)) return "template, base and addition are required";
            }
            case ANVIL -> {
                if (blank(left) || blank(right)) return "left and right item are required";
                if (rightAmount < 1 || rightAmount > 64) return "rightAmount must be 1-64";
                if (levelCost < 0 || levelCost > 39) return "levelCost must be 0-39";
            }
            default -> {
                if (blank(input)) return "input is required";
                if (type.isCooking()) {
                    if (experience < 0 || experience > 100) return "experience must be 0-100";
                    if (cookTime < 1 || cookTime > 100_000) return "cookTime must be 1-100000 ticks";
                }
            }
        }
        if (type != RecipeType.SHAPED && type != RecipeType.SHAPELESS && type != RecipeType.STONECUTTING && amount != 1) {
            return "only crafting table and stonecutter recipes can have a result amount above 1";
        }
        for (String ref : ingredientRefs()) {
            if (ItemRef.parse(ref, customNamespace) == null) return "invalid item reference: " + ref;
        }
        return null;
    }
}
