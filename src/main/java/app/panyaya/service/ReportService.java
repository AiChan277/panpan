package app.panyaya.service;

import app.panyaya.exception.AuthorizationException;
import app.panyaya.exception.DataCorruptedException;
import app.panyaya.model.BakeryData;
import app.panyaya.model.user.Permission;
import app.panyaya.persistence.DataStore;
import app.panyaya.report.DailySalesReport;
import app.panyaya.report.DateRange;
import app.panyaya.report.FinishedGoodsReport;
import app.panyaya.report.IngredientInventoryReport;
import app.panyaya.report.IngredientUsageReport;
import app.panyaya.report.MonthlySalesReport;
import app.panyaya.report.PaymentMethodReport;
import app.panyaya.report.ProductPerformanceReport;
import app.panyaya.report.ProductionReport;
import app.panyaya.report.Report;
import app.panyaya.report.ReportGenerator;
import app.panyaya.report.WasteReport;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs reports. Reports are generated on a <b>copy</b> of the last saved data, so they can safely
 * run on a background thread while the cashier keeps selling.
 */
public class ReportService extends BaseService {

    private final List<ReportGenerator> generators = new ArrayList<>();

    public ReportService(DataStore store, Session session, Clock clock) {
        super(store, session, clock);
        generators.add(new DailySalesReport());
        generators.add(new MonthlySalesReport());
        generators.add(new ProductPerformanceReport());
        generators.add(new PaymentMethodReport());
        generators.add(new IngredientInventoryReport());
        generators.add(new FinishedGoodsReport());
        generators.add(new ProductionReport());
        generators.add(new WasteReport());
        generators.add(new IngredientUsageReport());
    }

    public List<ReportGenerator> getGenerators() {
        return Collections.unmodifiableList(generators);
    }

    /** Safe to call from a background thread. */
    public Report generate(ReportGenerator generator, DateRange range)
            throws AuthorizationException, DataCorruptedException {
        session.require(Permission.VIEW_REPORTS);
        BakeryData copy = store.readCommittedCopy();
        return generator.generate(copy, range, now(), copy.getSettings().zone());
    }
}
