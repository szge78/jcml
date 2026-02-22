package sk.concentra.jcml.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.soap.dto.GetReportResponse;
import sk.concentra.jcml.soap.dto.ReportRow;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts the pipeline-processed {@link ObjectNode} list into a {@link GetReportResponse}.
 * Shared by both the REST controller and the SOAP endpoint.
 *
 * <h3>Timezone handling</h3>
 * <p>The database {@code DATETIME} column stores local time (configured via
 * {@code report.timezone}, default {@code Europe/Prague}). Queries use local
 * wall-clock time directly — no UTC conversion for the DB query.</p>
 *
 * <p>The offset from the caller's {@code dateFrom} string is used to display
 * {@code dateTime} values in the response. If no offset is supplied, the
 * configured default timezone is used as fallback with correct DST handling.</p>
 *
 * <h3>Chunked deserialization</h3>
 * <p>For large date ranges, messages are fetched and deserialized in pages of
 * {@code report.deserialize-chunk-size} rows (default 5000). Binary blobs are
 * released after each chunk before the next is fetched, keeping peak heap usage
 * bounded. All deserialized {@link ObjectNode}s are merged into a single list
 * before the pipeline runs — ensuring correct global sorting and entity enrichment.</p>
 *
 * <p>Configuration in {@code application.yml}:</p>
 * <pre>
 * report:
 *   timezone: Europe/Prague
 *   deserialize-chunk-size: 5000
 * </pre>
 *
 * <p>Accepted datetime string formats:</p>
 * <ul>
 *   <li>{@code 2026-02-19T00:00:00+01:00} — with timezone offset (recommended)</li>
 *   <li>{@code 2026-02-19T00:00:00Z} — UTC</li>
 *   <li>{@code 2026-02-19T00:00:00} — no offset, default timezone applied</li>
 * </ul>
 */
@Singleton
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final MessageService messageService;
    private final ZoneId defaultZone;
    private final int deserializeChunkSize;

    public ReportService(
            MessageService messageService,
            @Value("${cml.db.zoneId:Europe/Prague}") String defaultTimezone,
            @Value("${cml.db.deserialize-chunk-size:100000}") int deserializeChunkSize
    ) {
        this.messageService       = messageService;
        this.defaultZone          = ZoneId.of(defaultTimezone);
        this.deserializeChunkSize = deserializeChunkSize;
        log.info("ReportService: timezone={}, deserializeChunkSize={}",
                defaultTimezone, deserializeChunkSize);
    }

    /**
     * Accepts ISO-8601 datetime strings with optional timezone offset.
     * The wall-clock time is passed to the DB query; the offset determines display.
     */
    public GetReportResponse getReport(final String dateFrom, final String dateTo) {
        final ZoneOffset displayOffset = extractOffset(dateFrom, defaultZone);
        final LocalDateTime localFrom  = parseToLocal(dateFrom);
        final LocalDateTime localTo    = parseToLocal(dateTo);
        log.info("getReport: dateFrom='{}' → local {} (display offset: {})",
                dateFrom, localFrom, displayOffset);
        log.info("getReport: dateTo='{}'   → local {} (display offset: {})",
                dateTo, localTo, displayOffset);
        return getReport(localFrom, localTo, displayOffset);
    }

    /**
     * Fetches messages for the given local DateTime range, runs the pipeline,
     * and maps the result to a {@link GetReportResponse}.
     * Timestamps in the response are displayed in UTC.
     */
    public GetReportResponse getReport(final LocalDateTime dateFrom, final LocalDateTime dateTo) {
        return getReport(dateFrom, dateTo, ZoneOffset.UTC);
    }

    /**
     * Fetches messages for the given local DateTime range, runs the pipeline,
     * and maps the result to a {@link GetReportResponse}.
     * Timestamps in the response are displayed in the given {@code displayOffset}.
     *
     * <p>Messages are fetched and deserialized in chunks of
     * {@code report.deserialize-chunk-size} rows to limit peak heap usage from
     * binary blob deserialization. After all chunks are collected, the pipeline
     * runs once on the full merged dataset.</p>
     */
    public GetReportResponse getReport(final LocalDateTime dateFrom, final LocalDateTime dateTo,
                                       final ZoneOffset displayOffset) {
        log.info("getReport: dateFrom={}, dateTo={}, displayOffset={}", dateFrom, dateTo, displayOffset);

        // ── Chunked deserialization ───────────────────────────────────────────
        // Fetch and deserialize in pages — binary blobs are released after each
        // chunk before the next page is fetched. ObjectNodes are lightweight
        // and accumulate safely across all chunks.
        final List<ObjectNode> allMessages = new ArrayList<>();
        int pageNumber = 1;

        while (true) {
            final List<ObjectNode> chunk = messageService.getDeserializedMessagesByDateTimeRange(
                    dateFrom, dateTo, pageNumber, deserializeChunkSize);

            allMessages.addAll(chunk);
            log.info("getReport: chunk {} - {} items deserialized (total: {})",
                    pageNumber, chunk.size(), allMessages.size());

            if (chunk.size() < deserializeChunkSize) break; // last page
            pageNumber++;
        }

        log.info("getReport: deserialized {} messages total", allMessages.size());

        // ── Pipeline runs once on full merged dataset ─────────────────────────
        // EntityPreloadAction, SortAction, and BatchTemplateAction all require
        // the full dataset — pipeline must not be chunked.
        final List<ObjectNode> processed = messageService.processMessages(allMessages);

        log.info("getReport: pipeline produced {} items", processed.size());

        final List<ReportRow> rows = processed.stream()
                .map(node -> toReportRow(node, displayOffset))
                .toList();

        return new GetReportResponse(rows);
    }

    // ── DateTime parsing ──────────────────────────────────────────────────────

    /**
     * Parses an ISO-8601 datetime string and returns the wall-clock
     * {@link LocalDateTime} — the offset is stripped, not converted.
     * Used for DB queries since the DB stores local time.
     *
     * @throws IllegalArgumentException if the string cannot be parsed
     */
    static LocalDateTime parseToLocal(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DateTime value must not be null or blank");
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {}
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {}
        throw new IllegalArgumentException(
                "Cannot parse datetime value '" + value + "'. " +
                        "Expected ISO-8601 format, e.g. '2026-02-19T00:00:00+01:00', " +
                        "'2026-02-19T00:00:00Z', or '2026-02-19T00:00:00'");
    }

    /**
     * Extracts the {@link ZoneOffset} from an ISO-8601 datetime string.
     * If no offset is present, returns the current offset of {@code fallbackZone}
     * accounting for DST at the time of the call.
     */
    static ZoneOffset extractOffset(final String value, final ZoneId fallbackZone) {
        if (value == null || value.isBlank()) {
            return fallbackZone.getRules().getOffset(Instant.now());
        }
        try {
            return OffsetDateTime.parse(value).getOffset();
        } catch (DateTimeParseException e) {
            return fallbackZone.getRules().getOffset(Instant.now());
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private ReportRow toReportRow(final ObjectNode node, final ZoneOffset displayOffset) {
        final ReportRow row = new ReportRow();

        row.setMessageType(textOrNull(node, "_messageType"));
        row.setFullDescription(textOrNull(node, "_full_description"));
        row.setUserName(textOrNull(node, "_userName"));
        row.setHumanReadableTimestamp(textOrNull(node, "humanReadableTimestamp"));

        final JsonNode dbMeta = node.path("_dbMetadata");
        if (!dbMeta.isMissingNode() && !dbMeta.isNull()) {
            final JsonNode rkNode = dbMeta.path("recoveryKey");
            if (!rkNode.isMissingNode() && !rkNode.isNull()) {
                row.setRecoveryKey(rkNode.decimalValue());
            }
            row.setLogOperation(textOrNull(dbMeta, "logOperation"));
            row.setTableName(textOrNull(dbMeta, "tableName"));

            final JsonNode dtNode = dbMeta.path("dateTime");
            if (dtNode.isNumber()) {
                final LocalDateTime ldt = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(dtNode.asLong()), displayOffset);
                row.setDateTime(ldt.format(DATE_TIME_FORMATTER));
            }
        }

        return row;
    }

    private String textOrNull(final JsonNode node, final String field) {
        final JsonNode n = node.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        final String text = n.asText(null);
        return (text == null || text.isBlank()) ? null : text;
    }
}