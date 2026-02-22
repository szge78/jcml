package sk.concentra.jcml.pipeline.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.pipeline.PipelineAction;

import java.util.List;
import java.util.Map;

/**
 * Diagnostic action to inspect Global and Session contexts.
 * Run-once guard: executes exactly once per session regardless of how many items pass through.
 */
@Singleton
public class ContextDumpAction implements PipelineAction {

    private static final Logger log = LoggerFactory.getLogger(ContextDumpAction.class);
    private static final String SESSION_KEY_KEY       = "sessionKey";
    private static final String EXECUTION_FLAG_PREFIX = "__CTX_DUMP_EXECUTED_";

    @Inject
    private ObjectMapper objectMapper;

    @Override
    public List<ObjectNode> process(
            List<ObjectNode> input,
            Map<String, Object> globalContext,
            Map<String, Object> sessionContext,
            JsonNode params
    ) {
        executeDumpIfFirstRun(globalContext, sessionContext, params);
        return input; // pass-through
    }

    private void executeDumpIfFirstRun(Map<String, Object> global, Map<String, Object> session, JsonNode params) {
        String sessionKey = (String) session.getOrDefault(SESSION_KEY_KEY, "???");
        String label = params.path("label").asText("CONTEXT DUMP");
        String executionKey = EXECUTION_FLAG_PREFIX + label.replaceAll("\\s+", "_").toUpperCase();

        if (session.containsKey(executionKey)) {
            log.debug("[{}] ContextDump '{}' already executed for this session. Skipping.", sessionKey, label);
            return;
        }
        session.put(executionKey, true);
        performDump(global, session, params, label, sessionKey);
    }

    private void performDump(Map<String, Object> global, Map<String, Object> session, JsonNode params, String label, String sessionKey) {
        boolean dumpGlobal  = params.path("dumpGlobal").asBoolean(true);
        boolean dumpSession = params.path("dumpSession").asBoolean(true);
        boolean keysOnly    = params.path("keysOnly").asBoolean(false);

        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════════════════════════════════════════");
        sb.append("\n║ ").append(label).append(" [").append(sessionKey).append("]");
        sb.append("\n╠══════════════════════════════════════════════════════════════════════════════");

        if (dumpGlobal) {
            sb.append("\n║ GLOBAL CONTEXT:");
            appendMapContent(sb, global, keysOnly);
        }

        if (dumpSession) {
            sb.append("\n╠══════════════════════════════════════════════════════════════════════════════");
            sb.append("\n║ SESSION CONTEXT:");
            appendMapContent(sb, session, keysOnly);
        }

        sb.append("\n╚══════════════════════════════════════════════════════════════════════════════");
        log.info(sb.toString());
    }

    private void appendMapContent(StringBuilder sb, Map<String, Object> map, boolean keysOnly) {
        if (map == null || map.isEmpty()) {
            sb.append("\n║   (empty)");
            return;
        }

        if (keysOnly) {
            map.forEach((k, v) -> {
                if (k.startsWith(EXECUTION_FLAG_PREFIX)) return;
                String meta = "null";
                if (v != null) {
                    if (v instanceof java.util.Collection<?> c) {
                        meta = c.getClass().getSimpleName() + " (size=" + c.size() + ")";
                    } else if (v instanceof Map<?,?> m) {
                        meta = m.getClass().getSimpleName() + " (size=" + m.size() + ")";
                    } else {
                        meta = v.getClass().getSimpleName();
                    }
                }
                sb.append("\n║   • ").append(k).append(" : ").append(meta);
            });
        } else {
            try {
                var view = map.entrySet().stream()
                        .filter(e -> !e.getKey().startsWith(EXECUTION_FLAG_PREFIX))
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                String json = objectMapper.writer()
                        .withDefaultPrettyPrinter()
                        .without(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                        .writeValueAsString(view);

                String indented = json.replaceAll("\n", "\n║     ");
                sb.append("\n║     ").append(indented);
            } catch (Exception e) {
                sb.append("\n║   [ERROR serialization failed: ").append(e.getMessage()).append("]");
                sb.append("\n║   Fallback keys: ").append(map.keySet());
            }
        }
    }
}