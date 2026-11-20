package undercooked.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import undercooked.net.MiniJson;

/**
 * Represents a kitchen item: raw/prepared ingredient or a Plate holding prepared ingredients.
 */
public class Item {

    public enum Type {
        BUN,
        RAW_LETTUCE,
        CHOPPED_LETTUCE,
        RAW_TOMATO,
        CHOPPED_TOMATO,
        RAW_CHEESE,
        SLICED_CHEESE,
        RAW_PATTY,
        COOKED_PATTY,
        BURNT_PATTY,
        PLATE
    }

    private Type type;
    private final List<Type> plateContents = new ArrayList<>();

    public Item(Type type) {
        this.type = type;
    }

    public static Item plate() {
        return new Item(Type.PLATE);
    }

    public static Item of(Type type) {
        return new Item(type);
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public boolean isPlate() {
        return type == Type.PLATE;
    }

    public boolean isEmptyPlate() {
        return isPlate() && plateContents.isEmpty();
    }

    public List<Type> getPlateContents() {
        return Collections.unmodifiableList(plateContents);
    }

    public boolean isChoppable() {
        return type == Type.RAW_LETTUCE || type == Type.RAW_TOMATO || type == Type.RAW_CHEESE;
    }

    public Type choppedResult() {
        return switch (type) {
            case RAW_LETTUCE -> Type.CHOPPED_LETTUCE;
            case RAW_TOMATO -> Type.CHOPPED_TOMATO;
            case RAW_CHEESE -> Type.SLICED_CHEESE;
            default -> null;
        };
    }

    public boolean isCookable() {
        return type == Type.RAW_PATTY;
    }

    public static boolean isPlateableIngredient(Type t) {
        return t == Type.BUN
                || t == Type.CHOPPED_LETTUCE
                || t == Type.CHOPPED_TOMATO
                || t == Type.SLICED_CHEESE
                || t == Type.COOKED_PATTY;
    }

    public boolean canAddToPlate(Type ingredient) {
        if (!isPlate() || !isPlateableIngredient(ingredient)) {
            return false;
        }
        if (plateContents.size() >= 4 || plateContents.contains(ingredient)) {
            return false;
        }
        return true;
    }

    public boolean addToPlate(Type ingredient) {
        if (!canAddToPlate(ingredient)) {
            return false;
        }
        plateContents.add(ingredient);
        plateContents.sort(Enum::compareTo);
        return true;
    }

    public void clearPlate() {
        if (isPlate()) {
            plateContents.clear();
        }
    }

    public Item copy() {
        Item clone = new Item(this.type);
        clone.plateContents.addAll(this.plateContents);
        return clone;
    }

    public String toJson() {
        MiniJson.Obj obj = new MiniJson.Obj().put("type", type.name());
        if (isPlate()) {
            MiniJson.Arr arr = new MiniJson.Arr();
            for (Type t : plateContents) {
                arr.add(t.name());
            }
            obj.putRaw("contents", arr.toString());
            Recipe matched = Recipe.matchPlate(this);
            if (matched != null) {
                obj.put("recipe", matched.name());
            }
        }
        return obj.toString();
    }
}
