package app.panyaya.model.supplier;

import app.panyaya.model.BaseEntity;
import app.panyaya.model.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A delivery from a supplier: one document with several lines. */
public class GoodsReceipt extends BaseEntity {

    private String receiptNo;
    private long supplierId;
    private String supplierName;
    private LocalDate receivedDate;
    private Instant recordedAt;
    private String invoiceNo;
    private List<GoodsReceiptLine> lines = new ArrayList<>();
    private BigDecimal totalCost = Money.ZERO;
    private long receivedBy;

    private GoodsReceipt() {
    }

    public GoodsReceipt(long id, String receiptNo, long supplierId, String supplierName, LocalDate receivedDate,
                        Instant recordedAt, String invoiceNo, long receivedBy) {
        super(id);
        this.receiptNo = receiptNo;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.receivedDate = receivedDate;
        this.recordedAt = recordedAt;
        this.invoiceNo = invoiceNo;
        this.receivedBy = receivedBy;
    }

    public void addLine(GoodsReceiptLine line) {
        lines.add(line);
        totalCost = Money.add(totalCost, line.getTotalCost());
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public long getSupplierId() {
        return supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public LocalDate getReceivedDate() {
        return receivedDate;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public List<GoodsReceiptLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public long getReceivedBy() {
        return receivedBy;
    }
}
