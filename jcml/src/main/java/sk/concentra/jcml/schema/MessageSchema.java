package sk.concentra.jcml.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents the schema for a message type.
 * This is loaded from JSON and defines how to deserialize binary data.
 */
public record MessageSchema(
        @JsonProperty("messageType") String messageType,
        @JsonProperty("version") int version,
        @JsonProperty("description") String description,
        @JsonProperty("fields") List<FieldDefinition> fields
) {

    public record FieldDefinition(
            @JsonProperty(value = "name", required = true) String name,
            @JsonProperty(value = "type", required = true) FieldType type,
            @JsonProperty(value = "isArray") boolean isArray,
            @JsonProperty("nestedSchema") String nestedSchema, // Reference to another schema for OBJECT types
            @JsonProperty("stringPadding") PaddingType stringPadding, // Padding strategy for strings
            @JsonProperty("description") String description
    ) {
        public FieldDefinition {
            // Defaults
            if (stringPadding == null) {
                stringPadding = PaddingType.ALIGN_4; // default
            }
        } // public FieldDefinition {
    } // public record FieldDefinition(

    public enum FieldType {
        BYTE,      // 1 byte
        FLOAT,
        DOUBLE,
        SHORT,     // 2 bytes
        INTEGER,   // 4 bytes
        LONG,      // 8 bytes
        CHAR,      // 1 byte
        STRING,    // 2-byte length prefix + variable length + optional padding
        OBJECT     // Nested object (no header, uses referenced schema)
    }

    public enum PaddingType {
        NONE,           // No padding
        ALIGN_2,        // Align to 2-byte boundary
        ALIGN_4,        // Align to 4-byte boundary
        FIXED_1,        // Always 1 byte padding
        FIXED_2,        // Always 2 bytes padding
        FIXED_3         // Always 3 bytes padding
    }
} // record
