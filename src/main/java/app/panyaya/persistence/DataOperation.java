package app.panyaya.persistence;

import app.panyaya.exception.BusinessException;
import app.panyaya.model.BakeryData;

/**
 * A unit of work that changes the bakery data. Passed to {@link DataStore#execute}, usually as a
 * lambda: {@code store.execute("Create product", data -> { ... return product; })}.
 *
 * @param <T> what the operation returns (the created product, the completed sale, ...)
 */
@FunctionalInterface
public interface DataOperation<T> {

    T apply(BakeryData data) throws BusinessException;
}
