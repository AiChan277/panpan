package app.panyaya.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Shape of {@code starter-recipes.json}. The starter recipes are <b>data in a resource file</b>, not
 * Java code: the owner can edit, duplicate or archive them like any other recipe.
 */
public class StarterData {

    static class StarterIngredient {
        String code;
        String name;
        String baseUnit;
        String minimumStock;
        boolean tracksExpiry;
        boolean stockTracked;
    }

    static class StarterProduct {
        String sku;
        String name;
        String category;
        String price;
        int shelfLifeHours;
        String description;
    }

    static class StarterItem {
        String ingredientCode;
        String quantity;
        String unit;
        String note;
    }

    static class StarterRecipe {
        String name;
        String productSku;
        String yield;
        int prepMinutes;
        int bakeMinutes;
        String instructions;
        List<StarterItem> items = new ArrayList<>();
    }

    String note;
    List<StarterIngredient> ingredients = new ArrayList<>();
    List<StarterProduct> products = new ArrayList<>();
    List<StarterRecipe> recipes = new ArrayList<>();
}
