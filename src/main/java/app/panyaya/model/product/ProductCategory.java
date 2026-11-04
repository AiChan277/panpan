package app.panyaya.model.product;

import app.panyaya.exception.ValidationException;
import app.panyaya.model.BaseEntity;
import app.panyaya.util.TextUtil;

/** A product category such as "Bread", "Pastry" or "Drinks". Configurable from the UI. */
public class ProductCategory extends BaseEntity {

    private String name;
    private boolean active = true;

    private ProductCategory() {
    }

    public ProductCategory(long id, String name) throws ValidationException {
        super(id);
        rename(name);
    }

    public void rename(String newName) throws ValidationException {
        String value = TextUtil.normalize(newName);
        if (value.isEmpty() || value.length() > 40) {
            throw new ValidationException("Category name must be 1-40 characters.");
        }
        this.name = value;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return name;
    }
}
