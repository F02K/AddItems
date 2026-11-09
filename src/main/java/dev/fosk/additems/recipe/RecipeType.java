package dev.fosk.additems.recipe;

/** Crafting stations a recipe can belong to. */
public enum RecipeType {
    SHAPED("Crafting table (shaped)", "Ingredients in a fixed pattern"),
    SHAPELESS("Crafting table (shapeless)", "Ingredients in any arrangement"),
    FURNACE("Furnace", "Smelting"),
    BLASTING("Blast furnace", "Smelting ores and metal gear, twice as fast"),
    SMOKING("Smoker", "Cooking food, twice as fast"),
    CAMPFIRE("Campfire", "Slow cooking without fuel"),
    STONECUTTING("Stonecutter", "One input, one output"),
    SMITHING("Smithing table", "Template + base + addition"),
    ANVIL("Anvil", "Combine two items for a level cost");

    private final String label;
    private final String hint;

    RecipeType(String label, String hint) {
        this.label = label;
        this.hint = hint;
    }

    public String label() {
        return label;
    }

    public String hint() {
        return hint;
    }

    public boolean isCooking() {
        return this == FURNACE || this == BLASTING || this == SMOKING || this == CAMPFIRE;
    }

    public boolean isCrafting() {
        return this == SHAPED || this == SHAPELESS;
    }

    /** Vanilla cook time in ticks, used as dashboard default. */
    public int defaultCookTime() {
        return switch (this) {
            case FURNACE -> 200;
            case BLASTING, SMOKING -> 100;
            case CAMPFIRE -> 600;
            default -> 0;
        };
    }
}
