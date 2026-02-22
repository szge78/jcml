package sk.concentra.jcml.pipeline.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.pipeline.PipelineAction;

import java.util.*;

/**
 * Terminal sort action — sorts items by one or more configurable keys (each with its own
 * direction and null-placement policy) and re-emits the sorted list.
 *
 * <p>Run-once guard prevents double-execution if the action is somehow invoked again
 * within the same session.</p>
 */
@Singleton
public class SortAction implements PipelineAction {

    private static final Logger log = LoggerFactory.getLogger(SortAction.class);
    private static final String SESSION_KEY_KEY = "sessionKey";
    private static final String EXECUTION_FLAG  = "__SORT_ACTION_EXECUTED";

    @Override
    public List<ObjectNode> process(
            List<ObjectNode> input,
            Map<String, Object> globalContext,
            Map<String, Object> sessionContext,
            JsonNode params
    ) {
        String sessionKey = (String) sessionContext.getOrDefault(SESSION_KEY_KEY, "???");

        // Run-once guard
        if (sessionContext.containsKey(EXECUTION_FLAG)) {
            log.debug("[{}] SortAction already executed for this session — skipping sort, re-emitting as-is.", sessionKey);
            return input;
        }
        sessionContext.put(EXECUTION_FLAG, true);

        List<SortKey> sortKeys = parseSortKeys(sessionKey, params);
        if (sortKeys.isEmpty()) {
            log.warn("[{}] SortAction: no valid sortKeys configured — items passed through unchanged.", sessionKey);
            return input;
        }

        log.info("[{}] SortAction: sorting {} items by keys: {}", sessionKey, input.size(), sortKeys);
        List<ObjectNode> sorted = new ArrayList<>(input);
        sorted.sort(buildComparator(sortKeys));
        log.info("[{}] SortAction: sort complete.", sessionKey);
        return sorted;
    }

    private Comparator<ObjectNode> buildComparator(List<SortKey> sortKeys) {
        Comparator<ObjectNode> comparator = null;
        for (SortKey sk : sortKeys) {
            Comparator<ObjectNode> keyComparator = (a, b) -> compareByKey(a, b, sk);
            comparator = (comparator == null) ? keyComparator : comparator.thenComparing(keyComparator);
        }
        return comparator;
    }

    private int compareByKey(ObjectNode a, ObjectNode b, SortKey sk) {
        JsonNode va = resolveNode(a, sk.field());
        JsonNode vb = resolveNode(b, sk.field());

        boolean aMissing = isMissingOrNull(va);
        boolean bMissing = isMissingOrNull(vb);

        if (aMissing && bMissing) return 0;
        if (aMissing) return sk.nullsFirst() ? -1 :  1;
        if (bMissing) return sk.nullsFirst() ?  1 : -1;

        int cmp = compareNodes(va, vb);
        return sk.ascending() ? cmp : -cmp;
    }

    private int compareNodes(JsonNode a, JsonNode b) {
        if (a.isNumber() && b.isNumber()) {
            if (a.isIntegralNumber() && b.isIntegralNumber()) {
                return Long.compare(a.asLong(), b.asLong());
            }
            return a.decimalValue().compareTo(b.decimalValue());
        }
        if (a.isBoolean() && b.isBoolean()) {
            return Boolean.compare(a.asBoolean(), b.asBoolean());
        }
        return a.asText("").compareTo(b.asText(""));
    }

    private JsonNode resolveNode(ObjectNode node, String field) {
        JsonNode current = node;
        for (final String segment : field.split("\\.")) {
            current = current.path(segment);
            if (current.isMissingNode()) return current;
        }
        return current;
    }

    private boolean isMissingOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull();
    }

    private List<SortKey> parseSortKeys(String sessionKey, JsonNode params) {
        JsonNode keysNode = params.path("sortKeys");
        if (!keysNode.isArray() || keysNode.isEmpty()) return Collections.emptyList();

        List<SortKey> result = new ArrayList<>();
        for (JsonNode keyNode : keysNode) {
            String field = keyNode.path("field").asText(null);
            if (field == null || field.isBlank()) {
                log.warn("[{}] SortAction: skipping sort key with missing 'field': {}", sessionKey, keyNode);
                continue;
            }
            String directionRaw = keyNode.path("direction").asText("asc").trim().toLowerCase();
            boolean ascending   = !directionRaw.equals("desc");
            boolean nullsFirst  = keyNode.path("nullsFirst").asBoolean(false);
            result.add(new SortKey(field, ascending, nullsFirst));
            log.debug("[{}] SortAction: parsed sort key → field='{}', direction='{}', nullsFirst={}",
                    sessionKey, field, ascending ? "asc" : "desc", nullsFirst);
        }
        return result;
    }

    private record SortKey(String field, boolean ascending, boolean nullsFirst) {
        @Override public String toString() {
            return "'" + field + "' " + (ascending ? "ASC" : "DESC") + " NULLS " + (nullsFirst ? "FIRST" : "LAST");
        }
    }
} // class