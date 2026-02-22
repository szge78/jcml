package sk.concentra.jcml.pipeline;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import jakarta.inject.Singleton;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Refreshable
public class ConfigurablePipeline {

    private static final Logger log = LoggerFactory.getLogger(ConfigurablePipeline.class);
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher<RefreshEvent> refreshPublisher;
    private final ApplicationContext applicationContext;

    private final String configPath;
    private final boolean isClasspathResource;

    private volatile List<PipelineStep> steps = Collections.emptyList();
    private volatile long lastModified = 0;
    private final ConcurrentHashMap<String, Object> globalContext = new ConcurrentHashMap<>();

    private static final String SESSION_KEY_KEY = "sessionKey";
    private static final String START_NANOS_KEY = "startNanos";

    public ConfigurablePipeline(ObjectMapper objectMapper,
                                ApplicationEventPublisher<RefreshEvent> refreshPublisher,
                                ApplicationContext applicationContext,
                                @Value("${pipeline.config.path:classpath:pipeline.json}") String configPath) throws Exception {
        this.objectMapper = objectMapper;
        this.refreshPublisher = refreshPublisher;
        this.applicationContext = applicationContext;
        this.isClasspathResource = configPath.startsWith("classpath:");
        this.configPath = isClasspathResource ? configPath.substring("classpath:".length()) : configPath;
        reloadConfig();  // Initial load
    }

    @Scheduled(fixedDelay = "120s", initialDelay = "120s")
    public void checkForConfigChanges() throws Exception {
        if (isClasspathResource) {
            log.debug("Skipping classpath resource check: {}", configPath);
            return;
        }

        File configFile = new File(configPath);
        if (configFile.exists()) {
            BasicFileAttributes attrs = Files.readAttributes(configFile.toPath(), BasicFileAttributes.class);
            log.debug("Checking config file: {} last modified: {}", configFile.getAbsolutePath(), attrs.lastModifiedTime().toMillis());
            if (attrs.lastModifiedTime().toMillis() > lastModified) {
                reloadConfig();
                refreshPublisher.publishEvent(new RefreshEvent());
                log.info("Reloaded pipeline configuration from {}", configFile.getAbsolutePath());
            }
        }
    }

    private synchronized void reloadConfig() throws Exception {
        JsonNode config;
        if (isClasspathResource) {
            java.io.InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(configPath);
            if (inputStream == null) {
                throw new java.io.IOException("Classpath resource not found: " + configPath);
            }
            config = objectMapper.readTree(inputStream);
            lastModified = 0;
        } else {
            File file = new File(configPath);
            config = objectMapper.readTree(file);
            lastModified = file.lastModified();
        }

        JsonNode stepsNodeArray = config.path("steps");
        if (!stepsNodeArray.isArray()) {
            throw new IllegalStateException("pipeline.json: 'steps' must be an array");
        }

        steps = new ArrayList<>();
        for (JsonNode stepNode : stepsNodeArray) {
            String name = stepNode.path("name").asText(null);
            if (name == null || name.isBlank()) { continue; }
            String description = stepNode.path("description").asText("");
            boolean enabled = stepNode.path("enabled").asBoolean(true);
            String className = stepNode.path("className").asText();
            Class<?> actionClass = Class.forName(className);
            PipelineAction action = (PipelineAction) applicationContext.getBean(actionClass);
            JsonNode params = stepNode.path("params").isObject() ? stepNode.path("params") : objectMapper.createObjectNode();
            steps.add(new PipelineStep(name, description, className, action, enabled, params));
            log.info("Loaded pipeline step class: {}; params: {}", className, params);
        }
        log.info("Loaded {} pipeline steps", steps.size());
    }

    /**
     * Process a list of input items through all enabled pipeline steps sequentially.
     * Each step receives the full output list of the previous step.
     * A per-step and total summary report is logged at INFO level at the end of each run.
     */
    public List<ObjectNode> process(List<ObjectNode> input) {
        long startNanos = System.nanoTime();
        Map<String, Object> sessionContext = new HashMap<>();
        String sessionKey = StringUtils.generateRandomString();
        sessionContext.put(SESSION_KEY_KEY, sessionKey);
        sessionContext.put(START_NANOS_KEY, startNanos);

        List<PipelineStep> enabledSteps = steps.stream()
                .filter(PipelineStep::enabled)
                .toList();

        log.info("[{}] Processing {} items with {} enabled pipeline steps", sessionKey, input.size(), enabledSteps.size());

        // Per-step stats collected for the final report
        record StepStat(String name, int itemsIn, int itemsOut, long elapsedMs) {}
        List<StepStat> stats = new ArrayList<>(enabledSteps.size());

        List<ObjectNode> current = input;
        for (PipelineStep step : enabledSteps) {
            long stepStart = System.nanoTime();
            int itemsIn = current.size();
            current = applyStep(current, step, globalContext, sessionContext);
            long stepMs = (System.nanoTime() - stepStart) / 1_000_000;
            stats.add(new StepStat(step.name(), itemsIn, current.size(), stepMs));
        }

        long totalMs = (System.nanoTime() - startNanos) / 1_000_000;

        // ── Pipeline run report ───────────────────────────────────────────────
        StringBuilder report = new StringBuilder();
        report.append("\n╔══════════════════════════════════════════════════════════════════════════════");
        report.append("\n║ PIPELINE RUN REPORT  [").append(sessionKey).append("]");
        report.append("\n╠══╤═══════════════════════════════════════════╤════════╤════════╤═══════════");
        report.append("\n║  │ Step                                      │  In    │  Out   │  Time     ");
        report.append("\n╠══╪═══════════════════════════════════════════╪════════╪════════╪═══════════");
        for (int i = 0; i < stats.size(); i++) {
            StepStat s = stats.get(i);
            int delta = s.itemsOut() - s.itemsIn();
            String deltaStr = delta == 0 ? "=" : (delta > 0 ? "+" + delta : String.valueOf(delta));
            report.append(String.format("\n║%2d│ %-41s │%7d │%7d │%7dms  %s",
                    i + 1, truncate(s.name(), 41), s.itemsIn(), s.itemsOut(), s.elapsedMs(), deltaStr));
        }
        report.append("\n╠══╧═══════════════════════════════════════════╧════════╧════════╧═══════════");
        report.append(String.format("\n║  TOTAL: %d → %d items  in %dms", input.size(), current.size(), totalMs));
        report.append("\n╚══════════════════════════════════════════════════════════════════════════════");
        log.info(report.toString());

        return current;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private List<ObjectNode> applyStep(List<ObjectNode> input, PipelineStep step,
                                       Map<String, Object> global, Map<String, Object> session) {
        long startNanos = System.nanoTime();
        var sessionKey = (String) session.get(SESSION_KEY_KEY);
        log.info("[{}] Applying step '{}' with {} items, session key '{}'", sessionKey, step.name(), input.size(), sessionKey );
        var result = step.action().process(input, global, session, step.params());
        log.info("[{}] Step '{}' completed in {}ms", sessionKey, step.name(), (System.nanoTime() - startNanos) / 1_000_000);
        return result;
    } // applyStep

}

record PipelineStep(
        String name,
        String description,
        String className,
        PipelineAction action,
        boolean enabled,
        JsonNode params
) {}