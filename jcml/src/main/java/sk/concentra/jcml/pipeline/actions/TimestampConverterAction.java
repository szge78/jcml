package sk.concentra.jcml.pipeline.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.pipeline.PipelineAction;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
public class TimestampConverterAction implements PipelineAction {

    private static final Logger log = LoggerFactory.getLogger(TimestampConverterAction.class);
    private static final long FILETIME_TO_UNIX_EPOCH_MILLIS = 11644473600000L;
    private static final List<String> DEFAULT_INPUT_FIELD_NAMES = List.of("DateTimeStamp", "dateTimeStamp");
    private static final String DEFAULT_UNIX_TIMESTAMP_FIELD          = "unixTimestamp";
    private static final String DEFAULT_HUMAN_READABLE_TIMESTAMP_FIELD = "humanReadableTimestamp";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public List<ObjectNode> process(
            List<ObjectNode> input,
            Map<String, Object> globalContext,
            Map<String, Object> sessionContext,
            JsonNode params
    ) {
        final String sessionKey = (String) sessionContext.getOrDefault("sessionKey", "???");
        final ZoneId zoneId = ZoneId.of(params.path("zoneIdForHumanReadableTimestamp").asText("UTC"));
        final String unixTimestampField = params.path("unixTimestampFieldName").asText(DEFAULT_UNIX_TIMESTAMP_FIELD);
        final String humanReadableTimestampField = params.path("humanReadableTimestampFieldName").asText(DEFAULT_HUMAN_READABLE_TIMESTAMP_FIELD);

        final JsonNode fieldsNode = params.path("inputFieldNames");
        final List<String> inputFieldNames;
        if (fieldsNode.isArray() && !fieldsNode.isEmpty()) {
            List<String> fromParams = new ArrayList<>();
            fieldsNode.forEach(n -> { if (n.isTextual()) fromParams.add(n.asText()); });
            inputFieldNames = fromParams.isEmpty() ? DEFAULT_INPUT_FIELD_NAMES : fromParams;
        } else {
            inputFieldNames = DEFAULT_INPUT_FIELD_NAMES;
        }

        List<ObjectNode> result = new ArrayList<>(input.size());
        for (ObjectNode node : input) {
            final String fieldName = inputFieldNames.stream()
                    .filter(node::has)
                    .findFirst()
                    .orElse(null);

            if (fieldName == null) {
                log.debug("[{}] No timestamp field — skipping node of type '{}'",
                        sessionKey, node.path("_messageType").asText("?"));
                result.add(node);
                continue;
            }

            final long fileTime   = node.get(fieldName).asLong();
            final long unixMillis = (fileTime / 10_000L) - FILETIME_TO_UNIX_EPOCH_MILLIS;
            final Instant instant = Instant.ofEpochMilli(unixMillis);
            final ZonedDateTime zdt = instant.atZone(zoneId);

            node.put(unixTimestampField,          zdt.toInstant().toEpochMilli());
            node.put(humanReadableTimestampField, zdt.format(FORMATTER));
            result.add(node);
        }
        return result;
    }
}