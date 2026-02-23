package sk.concentra.jcml.pipeline.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.pipeline.PipelineAction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Removes entire CML transaction groups from the pipeline output.
 *
 * <p>A "transaction" is identified by the {@code _cmlTransactionId} field stamped by
 * {@link SessionEnrichAction}.  If <em>any</em> item in a transaction group matches
 * one of the configured conditions, <em>all</em> items that share the same
 * {@code _cmlTransactionId} are dropped.  Items without a {@code _cmlTransactionId}
 * (i.e. those skipped by SessionEnrichAction) are passed through unchanged.
 *
 * <p>Condition syntax: {@code "fieldName==value"} — compared case-insensitively.
 * Configure via {@code "condition"} (single string) or {@code "conditions"} (array).
 *
 * <p>Must run after {@link SessionEnrichAction} in the pipeline.
 */
@Singleton
@ExecuteOn(TaskExecutors.VIRTUAL)
public class CmlTransactionFilterAction implements PipelineAction {

    private static final Logger log = LoggerFactory.getLogger(CmlTransactionFilterAction.class);
    private static final String SESSION_KEY_KEY = "sessionKey";

    @Override
    public List<ObjectNode> process(
            List<ObjectNode> input,
            Map<String, Object> globalContext,
            Map<String, Object> sessionContext,
            JsonNode params
    ) {
        final String sessionKey = (String) sessionContext.getOrDefault(SESSION_KEY_KEY, "???");

        final List<Condition> conditions = parseConditions(params, sessionKey);
        if (conditions.isEmpty()) {
            log.warn("[{}] No valid conditions configured — passing all items through", sessionKey);
            return input;
        }

        // Pass 1: collect _cmlTransactionId values where any item matches a condition.
        final Set<String> excluded = ConcurrentHashMap.newKeySet();
        input.parallelStream().forEach(item -> {
            final JsonNode txNode = item.path("_cmlTransactionId");
            if (txNode.isMissingNode() || txNode.isNull()) return;
            final String txId = txNode.asText();
            if (excluded.contains(txId)) return;
            for (Condition cond : conditions) {
                if (cond.matches(item)) {
                    excluded.add(txId);
                    log.debug("[{}] Transaction '{}' flagged for removal: '{}' == '{}'",
                            sessionKey, txId, cond.field, cond.value);
                    break;
                }
            }
        });

        if (excluded.isEmpty()) {
            log.debug("[{}] No transactions matched conditions — nothing removed", sessionKey);
            return input;
        }

        // Pass 2: drop items whose transaction was flagged.
        final List<ObjectNode> result = input.parallelStream()
                .filter(item -> {
                    final JsonNode txNode = item.path("_cmlTransactionId");
                    return txNode.isMissingNode() || txNode.isNull()
                            || !excluded.contains(txNode.asText());
                })
                .collect(Collectors.toList());

        log.info("[{}] Filtered {} transaction(s) {} — removed {}/{} items",
                sessionKey, excluded.size(), excluded, input.size() - result.size(), input.size());
        return result;
    }

    // ── Condition parsing ──────────────────────────────────────────────────────

    private List<Condition> parseConditions(JsonNode params, String sessionKey) {
        final List<Condition> result = new ArrayList<>();

        // "condition": single expression string
        final JsonNode single = params.path("condition");
        if (single.isTextual() && !single.asText().isBlank()) {
            parseOne(single.asText(), sessionKey).ifPresent(result::add);
        }

        // "conditions": array of expression strings
        final JsonNode array = params.path("conditions");
        if (array.isArray()) {
            array.forEach(n -> {
                if (n.isTextual()) parseOne(n.asText(), sessionKey).ifPresent(result::add);
            });
        }

        return result;
    }

    private Optional<Condition> parseOne(String expr, String sessionKey) {
        final int sep = expr.indexOf("==");
        if (sep <= 0 || sep >= expr.length() - 2) {
            log.warn("[{}] Skipping malformed condition (expected 'field==value'): '{}'", sessionKey, expr);
            return Optional.empty();
        }
        return Optional.of(new Condition(expr.substring(0, sep).trim(), expr.substring(sep + 2).trim()));
    }

    private record Condition(String field, String value) {
        boolean matches(ObjectNode item) {
            return item.path(field).asText("").equalsIgnoreCase(value);
        }
    }
}
