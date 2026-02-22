package sk.concentra.jcml.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * Contract for a pipeline processing step.
 * Receives a list of items and returns a (potentially transformed) list of items.
 * Implementations may add, remove, or modify items.
 */
public interface PipelineAction {
    List<ObjectNode> process(List<ObjectNode> input,
                             Map<String, Object> globalContext,
                             Map<String, Object> sessionContext,
                             JsonNode params);
}