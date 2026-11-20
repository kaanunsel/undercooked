package undercooked.game;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The 4 cooperative kitchen recipes from GAME_DESIGN.md.
 */
public enum Recipe {
    SALAD(
            "Garden Salad",
            40,
            45_000L,
            Item.Type.CHOPPED_LETTUCE,
            Item.Type.CHOPPED_TOMATO
    ),
    BURGER(
            "Plain Burger",
            60,
            55_000L,
            Item.Type.BUN,
            Item.Type.COOKED_PATTY
    ),
    CHEESEBURGER(
            "Cheeseburger",
            80,
            65_000L,
            Item.Type.BUN,
            Item.Type.COOKED_PATTY,
            Item.Type.SLICED_CHEESE
    ),
    DELUXE_BURGER(
            "Deluxe Burger",
            110,
            75_000L,
            Item.Type.BUN,
            Item.Type.COOKED_PATTY,
            Item.Type.CHOPPED_LETTUCE,
            Item.Type.CHOPPED_TOMATO
    );

    private final String displayName;
    private final int basePoints;
    private final long durationMs;
    private final List<Item.Type> requiredIngredients;

    Recipe(String displayName, int basePoints, long durationMs, Item.Type... ingredients) {
        this.displayName = displayName;
        this.basePoints = basePoints;
        this.durationMs = durationMs;
        List<Item.Type> sorted = Arrays.asList(ingredients);
        sorted.sort(Enum::compareTo);
        this.requiredIngredients = Collections.unmodifiableList(sorted);
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getBasePoints() {
        return basePoints;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public List<Item.Type> getRequiredIngredients() {
        return requiredIngredients;
    }

    public static Recipe matchPlate(Item item) {
        if (item == null || !item.isPlate()) {
            return null;
        }
        List<Item.Type> contents = item.getPlateContents();
        for (Recipe r : values()) {
            if (r.requiredIngredients.equals(contents)) {
                return r;
            }
        }
        return null;
    }
}
