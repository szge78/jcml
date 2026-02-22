package sk.concentra.jcml.pipeline.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octomix.josson.Josson;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.pipeline.StreamAction;

import java.util.*;
import java.util.TreeMap;

/**
 * Barrier action: receives all items, filters by condition, extracts CML header fields
 * into sessionContext, then passes ALL items through unchanged.
 *
 * <p>Run-once guard ensures extraction happens exactly once per session.</p>
 */
@Singleton
public class CmlHeaderExtractorAction implements StreamAction {

    private static final Logger log = LoggerFactory.getLogger(CmlHeaderExtractorAction.class);
    private static final String SESSION_KEY_KEY    = "sessionKey";
    private static final String EXECUTION_FLAG     = "__CML_HEADER_EXTRACTOR_EXECUTED";
    private static final String DEFAULT_KEY_PREFIX = "cml_";
    private static final int    DEFAULT_CML_ID_INDEX = 1;
    private static final String DEFAULT_HEADER_KEY   = "_header";

    @Override
    public List<ObjectNode> process(
            List<ObjectNode> input,
            Map<String, Object> globalContext,
            Map<String, Object> sessionContext,
            JsonNode params
    ) {
        final String sessionKey = (String) sessionContext.getOrDefault(SESSION_KEY_KEY, "???");

        // Run-once guard
        if (sessionContext.containsKey(EXECUTION_FLAG)) {
            log.debug("[{}] CmlHeaderExtractorAction already executed for this session — skipping.", sessionKey);
            return input;
        }
        sessionContext.put(EXECUTION_FLAG, true);

        final String headerKey     = params.path("headerKey").asText(DEFAULT_HEADER_KEY);
        final JsonNode cmlIdConfig = params.path("cmlId");
        final int cmlIdIndex       = cmlIdConfig.path("lookupIndex").asInt(DEFAULT_CML_ID_INDEX);
        final String keyPrefix     = params.path("keyPrefix").asText(DEFAULT_KEY_PREFIX);
        final List<HeaderField> fields = parseFields(sessionKey, params.path("fields"));

        final String filterCondition = params.path("filterCondition").asText(null);
        if (filterCondition == null || filterCondition.isBlank()) {
            log.warn("[{}] No 'filterCondition' specified in params — extracting from ALL items", sessionKey);
        }

        log.debug("[{}] headerKey={}, cmlIdIndex={}, keyPrefix={}, filterCondition={}",
                sessionKey, headerKey, cmlIdIndex, keyPrefix, filterCondition);

        int matched = 0, extracted = 0;
        for (final ObjectNode node : input) {
            if (filterCondition != null && !filterCondition.isBlank() && !evaluateCondition(filterCondition, node)) {
                continue;
            }
            matched++;
            if (extractAndStore(sessionKey, node, headerKey, cmlIdIndex, keyPrefix, fields, sessionContext)) {
                extracted++;
            }
        }

        log.info("[{}] CmlHeaderExtractorAction: {} total items, {} matched filter, {} session entries stored.",
                sessionKey, input.size(), matched, extracted);

        // ── Recycled CML ID report ────────────────────────────────────────────
        // Collect all CML IDs that have 2+ occurrences and report them.
        List<String> recycledKeys = sessionContext.entrySet().stream()
                .filter(e -> e.getKey().startsWith(keyPrefix))
                .filter(e -> e.getValue() instanceof TreeMap<?, ?> m && m.size() >= 2)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        if (recycledKeys.isEmpty()) {
            log.info("[{}] CML ID recycling report: no recycled IDs detected in this session.", sessionKey);
        } else {
            StringBuilder report = new StringBuilder();
            report.append("\n╔══════════════════════════════════════════════════════════════════════════════");
            report.append("\n║ CML ID RECYCLING REPORT  [").append(sessionKey).append("]");
            report.append("\n║ ").append(recycledKeys.size()).append(" CML ID(s) recycled in this date range");
            report.append("\n╠══╤════════════╤══════════════════════════════════════════════════════════");
            report.append("\n║  │ CML Key    │ Occurrences (recoveryKey → userName @ machineName)");
            report.append("\n╠══╪════════════╪══════════════════════════════════════════════════════════");
            int i = 1;
            for (String key : recycledKeys) {
                @SuppressWarnings("unchecked")
                TreeMap<Double, Map<String, Object>> occurrences =
                        (TreeMap<Double, Map<String, Object>>) sessionContext.get(key);
                report.append(String.format("\n║%2d│ %-10s │ %d occurrences:", i++, key, occurrences.size()));
                occurrences.forEach((rk, info) ->
                        report.append(String.format("\n║  │            │   rk=%-20s  %s @ %s",
                                rk,
                                info.getOrDefault("userName", "?"),
                                info.getOrDefault("machineName", "?"))));
            }
            report.append("\n╚══════════════════════════════════════════════════════════════════════════════");
            log.warn(report.toString());
        }

        return input; // all items pass through unchanged
    }

    private boolean evaluateCondition(final String expr, final ObjectNode item) {
        try {
            final JsonNode result = Josson.create(item).getNode(expr);
            return result != null && result.asBoolean();
        } catch (Exception e) {
            log.warn("Condition evaluation failed for expr '{}': {}", expr, e.getMessage());
            return false;
        }
    }

    private boolean extractAndStore(
            final String sessionKey, final ObjectNode node, final String headerKey,
            final int cmlIdIndex, final String keyPrefix, final List<HeaderField> fields,
            final Map<String, Object> sessionContext
    ) {
        final JsonNode headerNode = node.path(headerKey);
        log.debug("[{}] headerNode: {}", sessionKey, headerNode);

        if (!headerNode.isArray() || headerNode.size() < 2) {
            log.warn("[{}] Invalid or too-short header '{}' in message — skipping: {}", sessionKey, headerKey, node);
            return false;
        }

        final JsonNode cmlIdNode = headerNode.get(cmlIdIndex);
        if (cmlIdNode == null || !cmlIdNode.isIntegralNumber()) {
            log.warn("[{}] Invalid CML ID at index {} in header — skipping: {}", sessionKey, cmlIdIndex, cmlIdNode);
            return false;
        }

        final int cmlId = cmlIdNode.asInt();
        final Map<String, Object> info = new HashMap<>();
        info.put("cmlId", cmlId);
        fields.forEach(headerField -> {
            final Object value = convertValue(sessionKey, node.path(headerField.lookupKey), headerField.type);
            if (value != null) info.put(headerField.targetKey, value);
        });

        // Include the recoveryKey of this ADD message so SessionEnrichAction can do
        // a "closest preceding" lookup when the CML ID has been recycled.
        final JsonNode rkNode = node.path("_dbMetadata").path("recoveryKey");
        final double recoveryKey = rkNode.isMissingNode() ? 0d : rkNode.asDouble();
        info.put("_recoveryKey", recoveryKey);

        // Store as TreeMap<Double, Map> keyed by recoveryKey — sorted ascending,
        // so SessionEnrichAction can find the floor entry for any item recoveryKey.
        final String transactionKey = keyPrefix + cmlId;
        @SuppressWarnings("unchecked")
        TreeMap<Double, Map<String, Object>> occurrences =
                (TreeMap<Double, Map<String, Object>>) sessionContext.get(transactionKey);
        if (occurrences == null) {
            occurrences = new TreeMap<>();
            sessionContext.put(transactionKey, occurrences);
        }
        final int occurrenceNumber = occurrences.size() + 1;
        occurrences.put(recoveryKey, info);
        log.info("[{}] Stored session entry '{}' occurrence #{} at recoveryKey={}: {}",
                sessionKey, transactionKey, occurrenceNumber, recoveryKey, info);
        return true;
    }

    private List<HeaderField> parseFields(final String sessionKey, final JsonNode fieldsNode) {
        if (!fieldsNode.isArray()) return Collections.emptyList();
        final List<HeaderField> result = new ArrayList<>();
        for (final JsonNode fieldNode : fieldsNode) {
            final String lookupKey = fieldNode.path("lookupKey").asText(null);
            final String targetKey = fieldNode.path("targetKey").asText(null);
            final String typeStr   = fieldNode.path("type").asText("String");
            if (lookupKey == null || lookupKey.isBlank() || targetKey == null || targetKey.isBlank()) {
                log.warn("[{}] Invalid field config — skipping: {}", sessionKey, fieldNode);
                continue;
            }
            final Class<?> type = switch (typeStr.toLowerCase()) {
                case "string"         -> String.class;
                case "integer", "int" -> Integer.class;
                case "long"           -> Long.class;
                case "boolean"        -> Boolean.class;
                default -> { log.warn("[{}] Unsupported field type '{}' — falling back to String", sessionKey, typeStr); yield String.class; }
            };
            result.add(new HeaderField(lookupKey, targetKey, type));
        }
        return result;
    }

    private Object convertValue(final String sessionKey, final JsonNode node, final Class<?> targetType) {
        try {
            if (targetType == String.class)  return node.asText(null);
            if (targetType == Integer.class) return node.isInt()                  ? node.asInt()  : null;
            if (targetType == Long.class)    return node.isLong() || node.isInt() ? node.asLong() : null;
            if (targetType == Boolean.class) return node.asBoolean();
        } catch (Exception e) {
            log.debug("[{}] Conversion failed for node {} to {}: {}", sessionKey, node, targetType.getSimpleName(), e.getMessage());
        }
        return null;
    }

    private record HeaderField(String lookupKey, String targetKey, Class<?> type) {}
} // class