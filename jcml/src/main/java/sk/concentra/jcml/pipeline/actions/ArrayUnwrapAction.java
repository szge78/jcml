package sk.concentra.jcml.pipeline.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.pipeline.PipelineAction;

import java.util.*;
import java.util.stream.Collectors;

@Singleton
@ExecuteOn(TaskExecutors.VIRTUAL)
public class ArrayUnwrapAction implements PipelineAction {

    private static final Logger log = LoggerFactory.getLogger(ArrayUnwrapAction.class);
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
        String sessionKey = (String) sessionContext.getOrDefault(SESSION_KEY_KEY, "???");

        // Read configuration
        JsonNode arraysNode = params.path("arraysToUnwrap");
        if (!arraysNode.isObject() || arraysNode.isEmpty()) {
            log.warn("[{}] 'arraysToUnwrap' must be a non-empty object (map), passing through", sessionKey);
            return input;
        }

        Map<String, List<String>> arraysToUnwrapByType = new LinkedHashMap<>();
        arraysNode.fields().forEachRemaining(e -> {
            List<String> fields = new ArrayList<>();
            e.getValue().forEach(n -> { if (n.asText(null) != null) fields.add(n.asText()); });
            if (!fields.isEmpty()) arraysToUnwrapByType.put(e.getKey(), fields);
        });

        String messageTypeField = params.path("messageTypeField").asText("_messageType");

        JsonNode copyNode = params.path("fieldsToCopy");
        Set<String> fieldsToCopy = new HashSet<>();
        if (copyNode.isArray()) {
            copyNode.forEach(n -> {
                String f = n.asText(null);
                if (f != null && !f.isBlank()) fieldsToCopy.add(f);
            });
        }

        String indexFieldName = params.path("indexFieldName").asText("idx");
        if (indexFieldName.isBlank()) indexFieldName = "idx";

        String arrayKeyFieldName = params.path("arrayKeyFieldName").asText(null);
        boolean preserveArrayKey = arrayKeyFieldName != null && !arrayKeyFieldName.trim().isEmpty();

        log.info("[{}] Unwrapping arrays for types: {}, copying fields: {}, index key name: {}",
                sessionKey, arraysToUnwrapByType.keySet(), fieldsToCopy, indexFieldName);

        final String finalIndexFieldName    = indexFieldName;
        final String finalArrayKeyFieldName = arrayKeyFieldName;

        return input.parallelStream()
                .flatMap(original -> processItem(original, arraysToUnwrapByType, messageTypeField,
                        fieldsToCopy, finalIndexFieldName, finalArrayKeyFieldName,
                        preserveArrayKey, sessionKey).stream())
                .collect(Collectors.toList());
    }

    private List<ObjectNode> processItem(
            ObjectNode original,
            Map<String, List<String>> arraysToUnwrapByType,
            String messageTypeField,
            Set<String> fieldsToCopy,
            String indexFieldName,
            String arrayKeyFieldName,
            boolean preserveArrayKey,
            String sessionKey
    ) {
        try {
            String messageType = original.path(messageTypeField).asText(null);
            List<String> arraysToUnwrap = messageType != null
                    ? arraysToUnwrapByType.get(messageType) : null;
            if (arraysToUnwrap == null || arraysToUnwrap.isEmpty()) {
                return Collections.singletonList(original);  // fast path: ~95% of items
            }

            // Collect target arrays
            Map<String, JsonNode> targetArrays = new LinkedHashMap<>();
            for (String arrayKey : arraysToUnwrap) {
                log.info("[{}] Looking for array: {}", sessionKey, arrayKey);
                JsonNode arr = original.path(arrayKey);
                log.info("[{}] Found array: {}, type: {}, isEmpty: {}", sessionKey, arr, arr.getNodeType(), arr.isEmpty());

                // Unwrap POJO collections
                if (arr.isPojo()) {
                    Object pojoValue = ((POJONode) arr).getPojo();
                    if (pojoValue instanceof Collection<?> || pojoValue instanceof Object[]) {
                        arr = objectMapper.valueToTree(pojoValue);
                        log.info("[{}] Converted POJO collection - now type: {}, size: {}", sessionKey, arr.getNodeType(), arr.size());
                    }
                }

                if (arr.isArray() && !arr.isEmpty()) {
                    targetArrays.put(arrayKey, arr);
                }
            }

            if (targetArrays.isEmpty()) {
                log.info("[{}] No arrays to unwrap, passing through unchanged", sessionKey);
                return Collections.singletonList(original);
            }

            // Produce one output per element per array (concatenated, not cartesian)
            List<ObjectNode> itemResult = new ArrayList<>();
            for (Map.Entry<String, JsonNode> entry : targetArrays.entrySet()) {
                String arrayKey   = entry.getKey();
                JsonNode arrayNode = entry.getValue();
                log.info("[{}] Array key: {}, size: {}", sessionKey, arrayKey, arrayNode.size());

                long idx = 0;
                for (JsonNode element : arrayNode) {
                    if (!element.isObject()) {
                        log.info("[{}] Non-object element in array – skipping", sessionKey);
                        idx++;
                        continue;
                    }

                    ObjectNode out = original.deepCopy();

                    // 1. Remove all arrays being unwrapped
                    for (String key : arraysToUnwrap) out.remove(key);

                    // 2. Keep only requested scalar fields (optional)
                    if (!fieldsToCopy.isEmpty()) {
                        Iterator<String> fieldNames = out.fieldNames();
                        while (fieldNames.hasNext()) {
                            String name = fieldNames.next();
                            if (!fieldsToCopy.contains(name)) fieldNames.remove();
                        }
                    }

                    // 3. Add index (and optional array-key field)
                    out.put(indexFieldName, idx);
                    if (preserveArrayKey) out.put(arrayKeyFieldName, arrayKey);

                    // 4. Merge element fields
                    ((ObjectNode) element).properties().forEach(e -> out.set(e.getKey(), e.getValue()));

                    itemResult.add(out);
                    idx++;
                }
            }
            return itemResult;
        } catch (Exception err) {
            log.warn("[{}] Unwrap error on item – item dropped: {}", sessionKey, err.getMessage());
            return Collections.emptyList();
        }
    }
} // class