package undercooked.game;

import undercooked.net.MiniJson;

/**
 * Timed customer cooking order managed by OrderBoard.
 */
public class Order {

    private final int orderId;
    private final Recipe recipe;
    private final long createdAtMs;
    private final long durationMs;
    private final long deadlineMs;
    private boolean completed = false;
    private boolean expired = false;

    public Order(int orderId, Recipe recipe, long createdAtMs) {
        this.orderId = orderId;
        this.recipe = recipe;
        this.createdAtMs = createdAtMs;
        this.durationMs = recipe.getDurationMs();
        this.deadlineMs = createdAtMs + this.durationMs;
    }

    public int getOrderId() {
        return orderId;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getDeadlineMs() {
        return deadlineMs;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void markCompleted() {
        this.completed = true;
    }

    public boolean isExpired() {
        return expired;
    }

    public void markExpired() {
        this.expired = true;
    }

    public long remainingMs(long nowMs) {
        return Math.max(0L, deadlineMs - nowMs);
    }

    public String toJson(long nowMs) {
        MiniJson.Arr req = new MiniJson.Arr();
        for (Item.Type ing : recipe.getRequiredIngredients()) {
            req.add(ing.name());
        }
        return new MiniJson.Obj()
                .put("id", orderId)
                .put("recipe", recipe.name())
                .put("name", recipe.getDisplayName())
                .put("basePoints", recipe.getBasePoints())
                .put("remainingMs", remainingMs(nowMs))
                .put("durationMs", durationMs)
                .putRaw("ingredients", req.toString())
                .toString();
    }
}
