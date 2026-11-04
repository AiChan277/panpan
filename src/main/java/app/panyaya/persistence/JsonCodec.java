package app.panyaya.persistence;

import app.panyaya.exception.DataCorruptedException;
import app.panyaya.model.BakeryData;
import app.panyaya.model.inventory.InventoryMovement;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

/**
 * Converts {@link BakeryData} to JSON bytes and back. This is the <b>only</b> class that knows the
 * JSON library (Jackson); replacing Jackson would only change this file and the mix-in.
 *
 * <p>Rules of the file format:</p>
 * <ul>
 *   <li>private fields are written directly (no getters/setters needed, so the model can stay
 *       encapsulated);</li>
 *   <li>money and quantities are written as decimal strings ("18000.0000"), so no tool can read
 *       them as inexact floating-point numbers;</li>
 *   <li>dates and times are ISO-8601 text ("2026-11-04", "2026-11-04T01:15:00Z");</li>
 *   <li>unknown properties and trailing garbage make the file invalid (strict reading).</li>
 * </ul>
 */
public class JsonCodec {

    private final ObjectMapper mapper;

    public JsonCodec() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        mapper.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // use fields only: getters such as getRemainingValue() are calculations, not data
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        // BigDecimal as "12500.0000" (string, plain notation)
        mapper.configOverride(BigDecimal.class).setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING));
        mapper.configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);

        mapper.addMixIn(InventoryMovement.class, InventoryMovementMixin.class);
    }

    public byte[] encode(BakeryData data) throws DataCorruptedException {
        try {
            return mapper.writeValueAsBytes(data);
        } catch (IOException e) {
            throw new DataCorruptedException("Could not convert the data to JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Reads JSON bytes. The envelope (format and schema version) is checked first, so a file from a
     * newer Pan-ya Ya version is refused with a clear message instead of a confusing parse error.
     */
    public BakeryData decode(byte[] content) throws DataCorruptedException {
        if (content == null || content.length == 0) {
            throw new DataCorruptedException("The data file is empty.");
        }
        JsonNode root;
        try {
            root = mapper.readTree(content);
        } catch (IOException e) {
            throw new DataCorruptedException("The data file is not valid JSON: " + shortMessage(e), e);
        }
        if (root == null || !root.isObject()) {
            throw new DataCorruptedException("The data file does not contain a JSON object.");
        }
        String format = root.path("format").asText("");
        if (!BakeryData.FORMAT.equals(format)) {
            throw new DataCorruptedException("This is not a Pan-ya Ya data file (format \"" + format + "\").");
        }
        int version = root.path("schemaVersion").asInt(-1);
        if (version > BakeryData.CURRENT_SCHEMA_VERSION) {
            throw new DataCorruptedException("The data was created by a newer Pan-ya Ya version (data format "
                    + version + "). Please install the newer version.");
        }
        if (version < 1) {
            throw new DataCorruptedException("The data file has an invalid schemaVersion.");
        }
        try {
            return mapper.treeToValue(root, BakeryData.class);
        } catch (IOException e) {
            throw new DataCorruptedException("The data file has an invalid structure: " + shortMessage(e), e);
        }
    }

    /** Writes any object (e.g. a backup manifest) as JSON. */
    public byte[] encodeObject(Object value) throws DataCorruptedException {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (IOException e) {
            throw new DataCorruptedException("Could not convert to JSON: " + e.getMessage(), e);
        }
    }

    /** Reads any JSON into the given class (e.g. starter recipes, backup manifest). */
    public <T> T decodeObject(InputStream input, Class<T> type) throws DataCorruptedException {
        try {
            return mapper.readValue(input, type);
        } catch (IOException e) {
            throw new DataCorruptedException("Invalid JSON for " + type.getSimpleName() + ": " + shortMessage(e), e);
        }
    }

    public <T> T decodeObject(byte[] input, Class<T> type) throws DataCorruptedException {
        try {
            return mapper.readValue(input, type);
        } catch (IOException e) {
            throw new DataCorruptedException("Invalid JSON for " + type.getSimpleName() + ": " + shortMessage(e), e);
        }
    }

    /** Version of the JSON library, shown on the About card. */
    public static String libraryVersion() {
        return "Jackson " + com.fasterxml.jackson.databind.cfg.PackageVersion.VERSION;
    }

    private static String shortMessage(IOException e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        int newline = message.indexOf('\n');
        return newline > 0 ? message.substring(0, newline) : message;
    }
}
