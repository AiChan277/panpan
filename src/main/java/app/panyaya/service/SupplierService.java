package app.panyaya.service;

import app.panyaya.exception.BusinessException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.exception.ValidationException;
import app.panyaya.model.BakeryData;
import app.panyaya.model.supplier.GoodsReceipt;
import app.panyaya.model.supplier.Supplier;
import app.panyaya.model.user.Permission;
import app.panyaya.persistence.DataStore;
import app.panyaya.persistence.EntityTypes;
import app.panyaya.util.TextUtil;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Supplier records and their delivery history. */
public class SupplierService extends BaseService {

    public SupplierService(DataStore store, Session session, Clock clock) {
        super(store, session, clock);
    }

    public List<Supplier> search(String text, boolean includeInactive) {
        List<Supplier> result = new ArrayList<>();
        for (Supplier supplier : data().getSuppliers()) {
            boolean matches = TextUtil.containsIgnoreCase(supplier.getName(), text)
                    || TextUtil.containsIgnoreCase(supplier.getContactPerson(), text)
                    || TextUtil.containsIgnoreCase(supplier.getPhone(), text);
            if (matches && (includeInactive || supplier.isActive())) {
                result.add(supplier);
            }
        }
        result.sort(Comparator.comparing(Supplier::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public List<Supplier> getActiveSuppliers() {
        return search("", false);
    }

    public Supplier getSupplier(long supplierId) {
        return BakeryData.findById(data().getSuppliers(), supplierId);
    }

    public Supplier create(String name, String contact, String phone, String email, String address, String notes)
            throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_SUPPLIERS);
        return store.execute("CREATE_SUPPLIER", data -> {
            ensureNameFree(data, name, 0);
            Supplier supplier = new Supplier(data.nextId(EntityTypes.SUPPLIER), name, contact, phone, email, address, notes);
            data.getSuppliers().add(supplier);
            audit(data, "CREATE_SUPPLIER", supplier.getName());
            return supplier;
        });
    }

    public Supplier update(long supplierId, String name, String contact, String phone, String email, String address,
                           String notes) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_SUPPLIERS);
        return store.execute("UPDATE_SUPPLIER", data -> {
            ensureNameFree(data, name, supplierId);
            Supplier supplier = BakeryData.require(data.getSuppliers(), supplierId, "Supplier");
            supplier.update(name, contact, phone, email, address, notes);
            audit(data, "UPDATE_SUPPLIER", supplier.getName());
            return supplier;
        });
    }

    public void setActive(long supplierId, boolean active) throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_SUPPLIERS);
        store.execute("SUPPLIER_ACTIVE", data -> {
            BakeryData.require(data.getSuppliers(), supplierId, "Supplier").setActive(active);
            return null;
        });
    }

    /** Deliveries of one supplier, newest first. */
    public List<GoodsReceipt> receiptsOf(long supplierId) {
        List<GoodsReceipt> result = new ArrayList<>();
        for (GoodsReceipt receipt : data().getGoodsReceipts()) {
            if (receipt.getSupplierId() == supplierId) {
                result.add(receipt);
            }
        }
        result.sort(Comparator.comparing(GoodsReceipt::getRecordedAt).reversed());
        return result;
    }

    private static void ensureNameFree(BakeryData data, String name, long exceptId) throws ValidationException {
        for (Supplier supplier : data.getSuppliers()) {
            if (supplier.getId() != exceptId && TextUtil.sameKey(supplier.getName(), name)) {
                throw new ValidationException("Supplier \"" + TextUtil.normalize(name) + "\" already exists.");
            }
        }
    }
}
