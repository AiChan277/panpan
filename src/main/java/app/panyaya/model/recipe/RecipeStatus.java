package app.panyaya.model.recipe;

/** Lifecycle of a recipe. Only ACTIVE recipes can be used for production. */
public enum RecipeStatus {
    DRAFT("Draft"),
    ACTIVE("Active"),
    ARCHIVED("Archived");

    private final String label;

    RecipeStatus(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
