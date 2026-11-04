package app.panyaya.service;

import app.panyaya.exception.BusinessException;
import app.panyaya.model.AuditEntry;
import app.panyaya.model.BakeryData;
import app.panyaya.model.inventory.InventoryMovement;
import app.panyaya.model.inventory.StockLot;
import app.panyaya.persistence.DataStore;
import app.panyaya.persistence.EntityTypes;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Shared helpers for all services: current time, current user, audit log and the single method
 * that posts a stock movement. Services inherit these instead of copying them.
 */
public abstract class BaseService {

    protected final DataStore store;
    protected final Session session;
    protected final Clock clock;

    protected BaseService(DataStore store, Session session, Clock clock) {
        this.store = store;
        this.session = session;
        this.clock = clock;
    }

    protected BakeryData data() {
        return store.getData();
    }

    protected Instant now() {
        return clock.instant();
    }

    protected ZoneId zone() {
        return store.getData().getSettings().zone();
    }

    /** Business date = calendar date in the bakery's time zone (default Asia/Jakarta). */
    protected LocalDate today() {
        return LocalDate.ofInstant(now(), zone());
    }

    protected long userId() {
        return session.getUserId();
    }

    protected void audit(BakeryData data, String action, String details) {
        data.getAuditLog().add(new AuditEntry(data.nextId(EntityTypes.AUDIT), now(), session.getUserId(),
                session.getUsername(), action, details));
    }

    /**
     * Applies a movement to its lot and appends it to the ledger. Every stock change in the whole
     * application goes through this one method.
     */
    protected void post(BakeryData data, InventoryMovement movement, StockLot lot) throws BusinessException {
        movement.applyTo(lot, now(), data.getSettings().zone());
        data.getMovements().add(movement);
    }
}
