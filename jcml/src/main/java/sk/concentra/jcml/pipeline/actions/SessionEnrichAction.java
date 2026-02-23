package sk.concentra.jcml.pipeline.actions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octomix.josson.Josson;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.pipeline.PipelineAction;

import java.util.*;

@Singleton
public class SessionEnrichAction implements PipelineAction {

    private static final Logger log = LoggerFactory.getLogger(SessionEnrichAction.class);
    private static final String SESSION_KEY_KEY = "sessionKey";

    @Inject
    private ObjectMapper objectMapper;

    @Override
    public List<ObjectNode> process(
            List<ObjectNode> input,
            Map<String, Object> globalContext,
            Map<String, Object> sessionContext,
            JsonNode params
    ) {
        final String sessionKey = (String) sessionContext.get(SESSION_KEY_KEY);

        final String prefix = params.path("prefix").asText(null);
        if (prefix == null || prefix.isBlank()) {
            log.warn("[{}] Missing or empty 'prefix' — skipping enrichment", sessionKey);
            return input;
        }

        final String idExpression = params.path("idExpression").asText(null);
        if (idExpression == null || idExpression.isBlank()) {
            log.warn("[{}] Missing or empty 'idExpression' — skipping enrichment", sessionKey);
            return input;
        }

        final String idType = params.path("idType").asText("integer");

        final JsonNode includeNode = params.path("includeFields");
        final Set<String> includeFields = (!includeNode.isMissingNode() && includeNode.isArray())
                ? Set.copyOf(List.of(objectMapper.convertValue(includeNode, String[].class)))
                : Collections.emptySet();

        final JsonNode mappingsNode = params.path("fieldMappings");
        final Map<String, String> fieldMappings = (!mappingsNode.isMissingNode() && mappingsNode.isObject())
                ? objectMapper.convertValue(mappingsNode, new TypeReference<Map<String, String>>() {})
                : null;

        List<ObjectNode> result = new ArrayList<>(input.size());
        for (ObjectNode item : input) {
            try {
                final Josson josson = Josson.create(item);
                final JsonNode idNode = josson.getNode(idExpression);
                log.info("[{}] idExpression '{}' resolved to: {}", sessionKey, idExpression, idNode);

                if (idNode == null || idNode.isMissingNode() || idNode.isNull()) {
                    log.warn("[{}] idExpression '{}' resolved to missing or null — skipping item", sessionKey, idExpression);
                    result.add(item);
                    continue;
                }

                final Object id = switch (idType.toLowerCase()) {
                    case "integer" -> idNode.asInt();
                    case "string"  -> idNode.asText();
                    default -> throw new IllegalArgumentException("[" + sessionKey + "] Unsupported idType: " + idType);
                };

                final String fullKey = prefix + id;

                // CML IDs recycle — stored as TreeMap<recoveryKey, info>.
                // Find the floor entry: the latest ADD whose recoveryKey ≤ this item's recoveryKey.
                @SuppressWarnings("unchecked")
                final TreeMap<Double, Map<String, Object>> occurrences =
                        (TreeMap<Double, Map<String, Object>>) sessionContext.get(fullKey);
                if (occurrences == null || occurrences.isEmpty()) {
                    log.debug("[{}] No session entry for key '{}' — skipping enrichment", sessionKey, fullKey);
                    result.add(item);
                    continue;
                }

                final double itemRecoveryKey = item.path("_dbMetadata").path("recoveryKey").asDouble(0d);
                Map.Entry<Double, Map<String, Object>> floorEntry = occurrences.floorEntry(itemRecoveryKey);
                if (floorEntry == null) {
                    // Item precedes all known occurrences — fall back to first
                    floorEntry = occurrences.firstEntry();
                }
                final Object value = floorEntry.getValue();
                // Stamp a stable transaction identity: cmlId + the ADD recoveryKey that owns this item.
                // Downstream actions can group/filter by this field to isolate a single CML session.
                item.put("_cmlTransactionId", id + "_" + floorEntry.getKey());
                log.debug("[{}] Looking up '{}' for itemRK={} → matched occurrence at RK={}: {}",
                        sessionKey, fullKey, itemRecoveryKey, floorEntry.getKey(), value);

                final JsonNode valueJson = objectMapper.valueToTree(value);
                if (!valueJson.isObject()) {
                    log.debug("[{}] Value for key '{}' is not an object — skipping", sessionKey, fullKey);
                    result.add(item);
                    continue;
                }

                ((ObjectNode) valueJson).properties().forEach(entry -> {
                    final String originalKey = entry.getKey();
                    if (!includeFields.isEmpty() && !includeFields.contains(originalKey)) return;
                    final String targetKey = (fieldMappings != null && fieldMappings.containsKey(originalKey))
                            ? fieldMappings.get(originalKey)
                            : originalKey;
                    item.set(targetKey, entry.getValue());
                });
                result.add(item);

            } catch (Exception err) {
                log.warn("[{}] Continued after error: {}", sessionKey, err.getMessage());
                result.add(item); // keep the item — mirrors onErrorContinue behaviour
            }
        }
        return result;
    }
} // class