package sk.concentra.jcml.pipeline.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.pipeline.PipelineAction;

import java.util.List;
import java.util.Map;

@Singleton
public class SessionContextDumperAction implements PipelineAction {

    private static final Logger log = LoggerFactory.getLogger(SessionContextDumperAction.class);

    @Override
    public List<ObjectNode> process(List<ObjectNode> input,
                                    Map<String, Object> globalContext,
                                    Map<String, Object> sessionContext,
                                    JsonNode params) {

        log.info("=== Session Context Dump (size: {}) ===", sessionContext.size());
        if (sessionContext.isEmpty()) {
            log.info("  (empty)");
        } else {
            sessionContext.forEach((key, value) -> log.info("  {} = {}", key, value));
        }
        log.info("=====================================");

        return input; // pass-through
    }
}