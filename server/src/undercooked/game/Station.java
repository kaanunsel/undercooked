package undercooked.game;

import undercooked.concurrency.BakeryLock;
import undercooked.concurrency.Lock;
import undercooked.concurrency.ResourceLocker;
import undercooked.net.MiniJson;

/**
 * A shared kitchen station tile guarded by its own Lamport's BakeryLock
 * (CMPE 436 Scenario C2 & C6).
 */
public class Station implements ResourceLocker.LockableResource {

    public static final double CHOP_TIME_MS = 2000.0;
    public static final double COOK_TIME_MS = 4000.0;
    public static final double BURN_TIME_MS = 9000.0;

    public enum Type {
        WALL,
        COUNTER,
        CRATE_BUN,
        CRATE_MEAT,
        CRATE_LETTUCE,
        CRATE_TOMATO,
        CRATE_CHEESE,
        CHOPPING_BOARD,
        STOVE,
        PLATE_DISPENSER,
        SERVING_WINDOW,
        TRASH_BIN
    }

    private final int resourceId;
    private final int tileX;
    private final int tileY;
    private final Type type;
    private final BakeryLock lock;

    private Item item;
    private double chopProgressMs;
    private double cookProgressMs;

    public Station(int resourceId, int tileX, int tileY, Type type, int maxThreadSlots) {
        this.resourceId = resourceId;
        this.tileX = tileX;
        this.tileY = tileY;
        this.type = type;
        this.lock = new BakeryLock(maxThreadSlots);
    }

    @Override
    public int getResourceId() {
        return resourceId;
    }

    @Override
    public Lock getLock() {
        return lock;
    }

    public int getTileX() {
        return tileX;
    }

    public int getTileY() {
        return tileY;
    }

    public Type getType() {
        return type;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
        this.chopProgressMs = 0.0;
        this.cookProgressMs = 0.0;
    }

    public double getChopProgressMs() {
        return chopProgressMs;
    }

    public void setChopProgressMs(double chopProgressMs) {
        this.chopProgressMs = chopProgressMs;
    }

    public double getCookProgressMs() {
        return cookProgressMs;
    }

    public void setCookProgressMs(double cookProgressMs) {
        this.cookProgressMs = cookProgressMs;
    }

    public boolean isCrate() {
        return type == Type.CRATE_BUN
                || type == Type.CRATE_MEAT
                || type == Type.CRATE_LETTUCE
                || type == Type.CRATE_TOMATO
                || type == Type.CRATE_CHEESE;
    }

    public Item spawnCrateItem() {
        return switch (type) {
            case CRATE_BUN -> Item.of(Item.Type.BUN);
            case CRATE_MEAT -> Item.of(Item.Type.RAW_PATTY);
            case CRATE_LETTUCE -> Item.of(Item.Type.RAW_LETTUCE);
            case CRATE_TOMATO -> Item.of(Item.Type.RAW_TOMATO);
            case CRATE_CHEESE -> Item.of(Item.Type.RAW_CHEESE);
            default -> null;
        };
    }

    public boolean canHoldItems() {
        return type == Type.COUNTER || type == Type.CHOPPING_BOARD || type == Type.STOVE;
    }

    public void reset() {
        this.item = null;
        this.chopProgressMs = 0.0;
        this.cookProgressMs = 0.0;
    }

    public String toStaticJson() {
        return new MiniJson.Obj()
                .put("id", resourceId)
                .put("x", tileX)
                .put("y", tileY)
                .put("type", type.name())
                .toString();
    }

    public String toDynamicJson() {
        if (item == null) {
            return null;
        }
        MiniJson.Obj obj = new MiniJson.Obj()
                .put("id", resourceId)
                .put("x", tileX)
                .put("y", tileY)
                .putRaw("item", item.toJson());
        if (type == Type.CHOPPING_BOARD && chopProgressMs > 0) {
            obj.put("chopRatio", Math.min(1.0, chopProgressMs / CHOP_TIME_MS));
        }
        if (type == Type.STOVE && (item.getType() == Item.Type.RAW_PATTY
                || item.getType() == Item.Type.COOKED_PATTY
                || item.getType() == Item.Type.BURNT_PATTY)) {
            obj.put("cookRatio", Math.min(1.0, cookProgressMs / COOK_TIME_MS));
            if (cookProgressMs > COOK_TIME_MS) {
                double burnSpan = BURN_TIME_MS - COOK_TIME_MS;
                obj.put("burnRatio", Math.min(1.0, (cookProgressMs - COOK_TIME_MS) / burnSpan));
            }
        }
        return obj.toString();
    }
}
