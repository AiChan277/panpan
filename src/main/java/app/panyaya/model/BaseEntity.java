package app.panyaya.model;

/**
 * Common superclass of every persisted business object.
 *
 * <p>Course topic <b>Inheritance</b>: every entity (Product, Ingredient, Sale, ...) "is a"
 * BaseEntity and inherits the stable identifier and the equality rules below.
 * The id is assigned once by {@link BakeryData#nextId(String)} and never changes
 * (<b>Encapsulation</b>: there is no setter).</p>
 */
public abstract class BaseEntity {

    private long id;

    /** Used only by the JSON reader (Jackson) when rebuilding objects from the data file. */
    protected BaseEntity() {
    }

    protected BaseEntity(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Entity id must be positive, got " + id);
        }
        this.id = id;
    }

    public long getId() {
        return id;
    }

    /** Two entities are equal when they are the same class and have the same id. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        return id == ((BaseEntity) other).id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
