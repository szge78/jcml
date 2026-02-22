package sk.concentra.jcml.deserializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.schema.MessageSchema;
import sk.concentra.jcml.schema.MessageSchema.FieldDefinition;
import sk.concentra.jcml.schema.MessageSchema.PaddingType;
import sk.concentra.jcml.schema.SchemaRegistry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Singleton
public class BinaryMessageDeserializer {
    private static final Logger log = LoggerFactory.getLogger(BinaryMessageDeserializer.class);
    private static final int HEADER_SIZE = 6; // 6 integers = 24 bytes

    private final SchemaRegistry schemaRegistry;
    private final ObjectMapper objectMapper;

    public BinaryMessageDeserializer(SchemaRegistry schemaRegistry, ObjectMapper objectMapper) {
        this.schemaRegistry = schemaRegistry;
        this.objectMapper = objectMapper;
//        this.objectMapper.registerModule(new JavaTimeModule());
//        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Deserialize a binary message given its type.
     *
     * <p>If no schema is found for {@code messageType}, a partial {@link ObjectNode}
     * is returned containing {@code _messageType}, {@code _dataLength},
     * {@code _dbMetadata}, {@code _db_localDateTime}, and {@code _header},
     * but no deserialized fields. This allows the pipeline to continue processing
     * other messages without failing on unknown types.</p>
     *
     * @param messageType The type identifier for the message
     * @param data        The binary data (including 6-integer header)
     * @param dbMetadata  Metadata from the DB row
     * @return A partially or fully deserialized {@link ObjectNode}
     */
//    public Map<String, Object> deserialize(String messageType, byte[] data) {
    public ObjectNode deserialize(String messageType, byte[] data, Map<String, Object> dbMetadata) {
//        MessageSchema schema = schemaRegistry.getSchema(messageType);
//        validateSchema(schema);

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

//        Map<String, Object> result = new LinkedHashMap<>();
        ObjectNode result = objectMapper.createObjectNode();
        result.put("_messageType", messageType);
        result.put("_dataLength", data.length);
//        result.put("_schemaVersion", schema.version());
//        result.put("_schemaDescription", schema.description());
//        result.put("_schemaFields", schema.fields().size());
//        result.put("_schemaName", schema.messageType());
//        result.putPOJO("_dbMetadata", dbMetadata); // this is just lazily storin the Java object.
        result.set("_dbMetadata", objectMapper.valueToTree(dbMetadata));
        result.put("_db_localDateTime", dbMetadata.get("localDateTime") != null
                ? dbMetadata.get("localDateTime").toString() : "");

        // Read 6-integer header
        // always present regardless of schema
        int[] header = new int[HEADER_SIZE];
        for (int i = 0; i < HEADER_SIZE; i++) {
            header[i] = buffer.getInt();
        }
//        result.putPOJO("_header", header); // will not preserve array
        result.set("_header", objectMapper.valueToTree(header)); // this _will_ preserve array

        log.debug("_header: {}", Arrays.toString(header));
        final MessageSchema schema;
        try {
            schema = schemaRegistry.getSchema(messageType);
        } catch (SchemaRegistry.SchemaNotFoundException e) {
            log.warn("No schema found for messageType '{}' — returning partial ObjectNode (header + metadata only)",
                    messageType);
            return result; // partial result — no fields deserialized
        } // try to look up schema

        validateSchema(schema);
        result.put("_schemaVersion", schema.version());
        result.put("_schemaDescription", schema.description());
        result.put("_schemaFields", schema.fields().size());
        result.put("_schemaName", schema.messageType());


        header = null;
        // Deserialize fields according to schema
        deserializeFields(buffer, schema.fields(), result, schema);

        return result;
    }

    /**
     * Validate that array fields are only at the end of the field list.
     */
    private void validateSchema(MessageSchema schema) {
        List<FieldDefinition> fields = schema.fields();
        boolean foundArray = false;

        for (int i = 0; i < fields.size(); i++) {
            FieldDefinition field = fields.get(i);

            if (foundArray && !field.isArray()) {
                throw new IllegalArgumentException(
                        "Schema validation error: Array field must be the last field. " +
                                "Found non-array field '" + field.name() + "' after an array field in schema: " +
                                schema.messageType()
                );
            }

            if (field.isArray()) {
                foundArray = true;
                // Ensure it's the last field
                if (i < fields.size() - 1) {
                    throw new IllegalArgumentException(
                            "Schema validation error: Array field '" + field.name() +
                                    "' must be the last field in schema: " + schema.messageType()
                    );
                }
            }
        }
    }

    /**
     * Deserialize fields from a buffer (used for both top-level and nested objects).
     */
//    private void deserializeFields(ByteBuffer buffer, List<FieldDefinition> fields,
//                                   Map<String, Object> result, MessageSchema rootSchema) {
    private void deserializeFields(ByteBuffer buffer, List<FieldDefinition> fields,
                                   ObjectNode result, MessageSchema rootSchema) {
        var bufferPosition = buffer.position();
        var remainder = bufferPosition % 8;
        log.debug("bufferPosition: {}; %8 remainder: {}; buffer.remaining: {}", bufferPosition, remainder, buffer.remaining());
        var fieldsSize = fields.size();
        var i = 0;

        for (FieldDefinition field : fields) {
            Object value;

            log.debug("[{}/{}]>>> field: {}; field.type: {}; buffer.position: {}; buffer.remaining: {}", i, fieldsSize, field.name(),  field.type(), buffer.position(), buffer.remaining());
            if (field.isArray()) {
                value = deserializeArray(buffer, field, result, rootSchema);
            } else {
                value = deserializeField(buffer, field, rootSchema);
            }
            log.debug("[{}/{}]<<< field: {}; field.type: {}; buffer.position: {}; buffer.remaining: {}; value: {}", i, fieldsSize, field.name(),  field.type(), buffer.position(), buffer.remaining(), value);
//            result.putPOJO(field.name(), value); // broken, just stores Java object lazily
            result.set(field.name(), objectMapper.valueToTree(value));
            i++;
        } // for (FieldDefinition field : fields) {
    }

    private Object deserializeField(ByteBuffer buffer, FieldDefinition field, MessageSchema rootSchema) {
        return switch (field.type()) {
            case BYTE -> buffer.get() & 0xFF;
            case FLOAT -> buffer.getFloat();
            case DOUBLE -> buffer.getDouble();
            case SHORT -> buffer.getShort() & 0xFFFF;
            case INTEGER -> buffer.getInt() & 0xFFFFFFFFL;
            case LONG -> buffer.getLong();
            case CHAR -> (char) (buffer.get() & 0xFF);
            case STRING -> deserializeString(buffer, field.stringPadding());
            case OBJECT -> deserializeNestedObject(buffer, field.nestedSchema(), rootSchema);
        };
    }

//    private List<Object> deserializeArray(ByteBuffer buffer, FieldDefinition field,
//                                          Map<String, Object> context, MessageSchema rootSchema) {
    private List<Object> deserializeArray(ByteBuffer buffer, FieldDefinition field,
                                          ObjectNode context, MessageSchema rootSchema) {
        // Arrays are always at the end of the message
        // Read elements until buffer has no more data
        log.debug("[DesA] rootSchema.messageType: {}", rootSchema.messageType());
        final var initialBufferPosition = buffer.position();
        log.debug("[DesA] initialBufferPosition: {}", initialBufferPosition);
        List<Object> array = new ArrayList<>();
        int deltaSum = 0;
        int i = 1;
        while (buffer.hasRemaining()) {
            var preReadBufferPosition = buffer.position();
            log.debug("[DesA] i: {} before buffer position: {}", i, buffer.position());
            // this is not necessary, strings are \0 terminated
//            if (extraPaddingIntBeforeNthElement > 0 && (i % extraPaddingIntBeforeNthElement) == 0) {
//                int alignedPos = (buffer.position() + 7) & ~7; // Align to 8-byte boundary, bitwise trick!
//                int alignedPos = (buffer.position() + 3) & ~3; // Align to 4 bytes
//                buffer.position(alignedPos);
//                log.info("i: {} alignedPos: {}", i, alignedPos);
//            }
            Object element = deserializeField(buffer, field, rootSchema);
            array.add(element);
            var postReadBufferPosition = buffer.position();
            log.debug("[DesA] preReadBufferPosition: {}, postReadBufferPosition: {}, delta: {}", preReadBufferPosition, postReadBufferPosition, postReadBufferPosition - preReadBufferPosition);
            log.debug("[DesA] i: {} after buffer position: {}", i, buffer.position());
            i++;
            deltaSum += postReadBufferPosition - preReadBufferPosition;
            log.debug("[DesA] deltaSum: {}, %4: {}, %8: {}", deltaSum, deltaSum % 4, deltaSum % 8);
        }
        log.debug("[DesA] array size: {}", array.size());
        return array;
    }

    private String deserializeString(ByteBuffer buffer, PaddingType paddingType) {
        // Read 2-byte length prefix
        final var posBefore = buffer.position();
        // Read string length (2 bytes)
        final int originalLength = buffer.getShort() & 0xFFFF;
        // add one to the length to account for the \0 terminator
        final int length = originalLength + 1;

        // Read string bytes
        final byte[] stringBytes = new byte[length];
        buffer.get(stringBytes);
        final String value = new String(stringBytes, 0, originalLength, StandardCharsets.UTF_8);

        // Handle padding
        final int paddingBytes = calculatePadding(length, paddingType);
        if (paddingBytes > 0) {
            final var newPosition = buffer.position() + paddingBytes;
            buffer.position(newPosition);
            log.debug("\tPadding needed for string with length: {} (incl. NULL), paddingBytes: {}", length, paddingBytes);
        } else {
            log.debug("\tNo padding needed for string with length: {} (incl. NULL)", length);
        }
        log.debug("\tvalue: {}; originalLength: {}; length: {} (incl. NULL); paddingBytes: {} ", value, originalLength, length, paddingBytes);

        return value;
    }

    private int calculatePadding(int stringLength, PaddingType paddingType) {
        return switch (paddingType) {
            case NONE -> 0;
            case FIXED_1 -> 1;
            case FIXED_2 -> 2;
            case FIXED_3 -> 3;
            case ALIGN_2 -> {
                // 2-byte length prefix + string length
                int totalUsed = 2 + stringLength;
                int remainder = totalUsed % 2;
                yield remainder == 0 ? 0 : 2 - remainder;
            }
            case ALIGN_4 -> {
                int totalUsed = 2 + stringLength;
                int remainder = totalUsed % 4;
                yield remainder == 0 ? 0 : 4 - remainder;
            }
        };
    }

//    private Map<String, Object> deserializeNestedObject(ByteBuffer buffer, String nestedSchemaName,
//                                                        MessageSchema rootSchema) {
        private ObjectNode deserializeNestedObject(ByteBuffer buffer, String nestedSchemaName,
                MessageSchema rootSchema) {
        // For nested objects, we need to look up the schema
        // This could be either a separate schema file or embedded in the root schema
        MessageSchema nestedSchema = schemaRegistry.getSchema(nestedSchemaName);

//        Map<String, Object> nestedObject = new LinkedHashMap<>();
        var nestedObject = objectMapper.createObjectNode();
        deserializeFields(buffer, nestedSchema.fields(), nestedObject, nestedSchema);

        return nestedObject;
    }

} // class
