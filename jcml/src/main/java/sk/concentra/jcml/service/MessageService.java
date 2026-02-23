package sk.concentra.jcml.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.deserializer.BinaryMessageDeserializer;
import sk.concentra.jcml.persistence.ConfigMessageLog;
import sk.concentra.jcml.persistence.ConfigMessageLogRepository;
import sk.concentra.jcml.pipeline.ConfigurablePipeline;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for retrieving and deserializing messages.
 */
@Singleton
@ExecuteOn(TaskExecutors.VIRTUAL)
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final ConfigMessageLogRepository configMessageLogRepository;
    private final BinaryMessageDeserializer binaryMessageDeserializer;
    private final ConfigurablePipeline pipeline;
    private final String zoneId;

    public MessageService(ConfigMessageLogRepository configMessageLogRepository,
                          BinaryMessageDeserializer binaryMessageDeserializer,
                          ConfigurablePipeline pipeline,
                          @Value("${cml.db.zoneId:Europe/Prague}") String zoneId) {
        this.configMessageLogRepository = configMessageLogRepository;
        this.binaryMessageDeserializer = binaryMessageDeserializer;
        this.pipeline = pipeline;
        this.zoneId = zoneId;
    }

    /**
     * Get a message by ID and deserialize it.
     */
    public Optional<ObjectNode> getDeserializedMessageById(Double id) {
        var configMessageLog = configMessageLogRepository.findById(id);
        log.info("Retrieved configMessageLog with id {}: {}", id, configMessageLog);
        return configMessageLog.map(this::deserializeConfigMessageLog);
    }

    /**
     * Get messages by recovery key range and deserialize them.
     */
    public List<ObjectNode> getDeserializedMessagesByRecoveryKeyRange(Double startRecoveryKey, Double endRecoveryKey) {
        log.info("Retrieving configMessageLogs between {} and {}", startRecoveryKey, endRecoveryKey);
        List<ConfigMessageLog> messages = configMessageLogRepository
                .findAllByRecoveryKeyBetweenOrderByRecoveryKeyAsc(startRecoveryKey, endRecoveryKey);
        log.info("Retrieved {} configMessageLogs", messages.size());

        int batchSize = 50_000;
        List<List<ObjectNode>> resultBatches = new ArrayList<>();

        for (int start = 0; start < messages.size(); start += batchSize) {
            int end = Math.min(start + batchSize, messages.size());
            List<ObjectNode> batch = messages.subList(start, end)
                    .stream()
                    .map(this::deserializeConfigMessageLog)
                    .collect(Collectors.toList());
            resultBatches.add(batch);
        }

        return resultBatches.parallelStream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * Get messages by date/time range, deserialize them in chunks, and return a single page.
     *
     * <p>The full result set is split into pages of {@code deserializeChunkSize} records each.
     * {@code pageNumber} is 1-based: page 1 returns the first chunk, page 2 the second, etc.
     * If {@code pageNumber} exceeds the number of available pages the last page is returned.</p>
     *
     * @param dateFrom            range start (inclusive), ISO-8601 string parsed to LocalDateTime
     * @param dateTo              range end   (inclusive), ISO-8601 string parsed to LocalDateTime
     * @param pageNumber          1-based page index
     * @param deserializeChunkSize number of deserialized records per page
     */
    public List<ObjectNode> getDeserializedMessagesByDateTimeRange(
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            int pageNumber,
            int deserializeChunkSize) {

        log.info("Retrieving configMessageLogs between {} and {} (page {}, chunkSize {})",
                dateFrom, dateTo, pageNumber, deserializeChunkSize);

        List<ConfigMessageLog> messages = configMessageLogRepository
                .findAllByDateTimeBetweenOrderByRecoveryKeyAsc(dateFrom, dateTo);
        log.info("Retrieved {} configMessageLogs for date range", messages.size());

        if (messages.isEmpty()) {
            return Collections.emptyList();
        }

        // Clamp pageNumber to [1, maxPage]
        int effectiveChunkSize = Math.max(1, deserializeChunkSize);
        int totalPages = (int) Math.ceil((double) messages.size() / effectiveChunkSize);
        int effectivePage = Math.max(1, Math.min(pageNumber, totalPages));

        int fromIndex = (effectivePage - 1) * effectiveChunkSize;
        int toIndex   = Math.min(fromIndex + effectiveChunkSize, messages.size());

        log.info("Returning page {}/{} (records {} – {} of {})",
                effectivePage, totalPages, fromIndex + 1, toIndex, messages.size());

        return messages.subList(fromIndex, toIndex)
                .parallelStream()
                .map(this::deserializeConfigMessageLog)
                .collect(Collectors.toList());
    }

    /**
     * Get messages by recovery key range and deserialize them — parallel stream variant.
     */
    public List<ObjectNode> getDeserializedMessagesByRecoveryKeyRangeParallel(Double startRecoveryKey, Double endRecoveryKey) {
        log.info("Retrieving configMessageLogs between {} and {}", startRecoveryKey, endRecoveryKey);
        List<ConfigMessageLog> messages = configMessageLogRepository
                .findAllByRecoveryKeyBetweenOrderByRecoveryKeyAsc(startRecoveryKey, endRecoveryKey);
        log.info("Retrieved {} configMessageLogs", messages.size());
        return messages.parallelStream()
                .map(this::deserializeConfigMessageLog)
                .collect(Collectors.toList());
    }

    /**
     * Process messages through the pipeline and return results.
     * Runs on the calling thread (virtual thread / executor thread from the web layer).
     */
    public List<ObjectNode> processMessages(List<ObjectNode> input) {
        return pipeline.process(input);
    }

    /**
     * Like {@link #processMessages(List)}, but skips the named steps for this invocation.
     */
    public List<ObjectNode> processMessages(List<ObjectNode> input, Collection<String> ignoredStepNames) {
        return pipeline.process(input, ignoredStepNames);
    }

    private ObjectNode deserializeConfigMessageLog(ConfigMessageLog configMessageLog) {
        var dbRecoveryKey = configMessageLog.getRecoveryKey();
        var dbDateTime = configMessageLog.getDateTime();
        var dbLogOperation = configMessageLog.getLogOperation();
        var dbTableName = configMessageLog.getTableName();
        var dbMetadata = new HashMap<String, Object>();
        dbMetadata.put("recoveryKey", dbRecoveryKey);
        dbMetadata.put("logOperation", dbLogOperation);
        dbMetadata.put("tableName", dbTableName);
        dbMetadata.put("dateTime", dbDateTime.atZone(ZoneId.of(zoneId)).toInstant().toEpochMilli());

        log.info("Deserializing message with recoveryKey {} and dateTime {}", dbRecoveryKey, dbDateTime);
        final String logOperation = configMessageLog.getLogOperation() != null
                ? configMessageLog.getLogOperation().toUpperCase() : "";
        final String tableName = configMessageLog.getTableName();
        final String messageType = (tableName == null || tableName.isEmpty())
                ? logOperation
                : logOperation + "__" + tableName.toUpperCase();
        final var configMessage = configMessageLog.getConfigMessage();
        log.info("Deserializing message of type {} with id {}", messageType, configMessageLog.getRecoveryKey());
        return binaryMessageDeserializer.deserialize(messageType, configMessage, dbMetadata);
    }

} // class