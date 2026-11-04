package app.panyaya.persistence;

import app.panyaya.model.inventory.ProductionConsumption;
import app.panyaya.model.inventory.ProductionOutput;
import app.panyaya.model.inventory.SaleIssue;
import app.panyaya.model.inventory.SaleReversal;
import app.panyaya.model.inventory.StockAdjustment;
import app.panyaya.model.inventory.StockReceipt;
import app.panyaya.model.inventory.WasteWriteOff;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Tells Jackson how to save and read the polymorphic {@code List<InventoryMovement>}: each movement
 * gets a {@code "type"} property such as {@code "SALE_ISSUE"}, and that name decides which subclass
 * is created when the file is read.
 *
 * <p>This is a Jackson "mix-in": the annotations are attached here instead of on the model class,
 * so the model package does not depend on the JSON library. Only fixed logical names are accepted
 * (never Java class names from the file), which is the safe way to use polymorphic JSON.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StockReceipt.class, name = "STOCK_RECEIPT"),
        @JsonSubTypes.Type(value = ProductionConsumption.class, name = "PRODUCTION_CONSUMPTION"),
        @JsonSubTypes.Type(value = ProductionOutput.class, name = "PRODUCTION_OUTPUT"),
        @JsonSubTypes.Type(value = SaleIssue.class, name = "SALE_ISSUE"),
        @JsonSubTypes.Type(value = SaleReversal.class, name = "SALE_REVERSAL"),
        @JsonSubTypes.Type(value = WasteWriteOff.class, name = "WASTE_WRITE_OFF"),
        @JsonSubTypes.Type(value = StockAdjustment.class, name = "STOCK_ADJUSTMENT")
})
abstract class InventoryMovementMixin {
}
