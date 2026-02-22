package sk.concentra.jcml.pipeline.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octomix.josson.Josson;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.pipeline.PipelineAction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class JossonTemplateAction implements PipelineAction {

    private static final Logger log = LoggerFactory.getLogger(JossonTemplateAction.class);
    private static final String SESSION_KEY_KEY = "sessionKey";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(.+?)}}");

    private record ParsedTemplate(String targetField, List<String> segments, int expressionCount) {}

    @Inject
    private ObjectMapper objectMapper;

    @Override
    public List<ObjectNode> process(
            List<ObjectNode> input,
            Map<String, Object> globalContext,
            Map<String, Object> sessionContext,
            JsonNode params
    ) {
        final String sessionKey = (String) sessionContext.getOrDefault(SESSION_KEY_KEY, "???");
        final JsonNode templates = params.path("templates");

        if (templates.isMissingNode() || !templates.isObject()) {
            return input;
        }

        if (log.isDebugEnabled()) {
            log.debug("[{}] Applying Josson templates for fields: {}", sessionKey, templates.fieldNames().toString());
        }

        // Pre-parse all template strings once per process() call
        final List<ParsedTemplate> parsedTemplates = new ArrayList<>();
        final Iterator<String> fieldNames = templates.fieldNames();
        while (fieldNames.hasNext()) {
            final String targetField = fieldNames.next();
            final String templateStr = templates.get(targetField).asText();
            parsedTemplates.add(parseTemplate(targetField, templateStr));
        }

        // Build static envelope parts once; only "item" changes per record
        final JsonNode globalNode  = objectMapper.valueToTree(globalContext);
        final JsonNode sessionNode = objectMapper.valueToTree(sessionContext);
        final ObjectNode envelope  = objectMapper.createObjectNode();
        envelope.set("session", sessionNode);
        envelope.set("global",  globalNode);

        List<ObjectNode> result = new ArrayList<>(input.size());
        for (ObjectNode node : input) {
            envelope.set("item", node);
            final Josson josson  = Josson.create(envelope);
            final ObjectNode out = node.deepCopy();
            for (final ParsedTemplate pt : parsedTemplates) {
                out.put(pt.targetField(), resolveSegments(pt, josson, sessionKey));
            }
            result.add(out);
        }
        return result;
    }

    private static ParsedTemplate parseTemplate(String targetField, String template) {
        final List<String> segments = new ArrayList<>();
        final Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        int lastEnd = 0;
        int expressionCount = 0;
        while (matcher.find()) {
            segments.add(template.substring(lastEnd, matcher.start()));
            segments.add(matcher.group(1));
            lastEnd = matcher.end();
            expressionCount++;
        }
        segments.add(template.substring(lastEnd));
        return new ParsedTemplate(targetField, segments, expressionCount);
    }

    private String resolveSegments(ParsedTemplate pt, Josson josson, String sessionKey) {
        if (pt.expressionCount() == 0) {
            return pt.segments().getFirst();
        }
        final List<String> segs = pt.segments();
        int capacity = 0;
        for (final String s : segs) capacity += s.length();
        capacity += pt.expressionCount() * 16;

        final StringBuilder sb = new StringBuilder(capacity);
        for (int i = 0; i < segs.size(); i++) {
            if ((i & 1) == 0) {
                sb.append(segs.get(i));
            } else {
                final String expression = segs.get(i);
                try {
                    final JsonNode result = josson.getNode(expression);
                    if (result != null && !result.isMissingNode()) {
                        if (result.isContainerNode()) {
                            sb.append(result);
                        } else {
                            sb.append(result.asText());
                        }
                    }
                } catch (Exception e) {
                    log.warn("[{}] Josson template error in expression [{}]: {}", sessionKey, expression, e.getMessage());
                }
            }
        }
        return sb.toString();
    }
} // class