package app.panyaya.persistence;

/** Keys of the id sequences in the data file. Constants avoid typos such as "prodcut". */
public final class EntityTypes {

    public static final String USER = "user";
    public static final String CATEGORY = "category";
    public static final String PRODUCT = "product";
    public static final String INGREDIENT = "ingredient";
    public static final String SUPPLIER = "supplier";
    public static final String RECIPE = "recipe";
    public static final String INGREDIENT_LOT = "ingredientLot";
    public static final String FG_BATCH = "fgBatch";
    public static final String RECEIPT = "receipt";
    public static final String PRODUCTION = "production";
    public static final String SALE = "sale";
    public static final String WASTE = "waste";
    public static final String MOVEMENT = "movement";
    public static final String AUDIT = "audit";

    private EntityTypes() {
    }
}
