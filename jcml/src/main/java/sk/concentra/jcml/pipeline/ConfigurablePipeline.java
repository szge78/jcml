package sk.concentra.jcml.pipeline;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import io.micronaut.runtime.event.annotation.EventListener;
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
public class ConfigurablePipeline {

    private static final Logger log = LoggerFactory.getLogger(ConfigurablePipeline.class);
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher<RefreshEvent> refreshPublisher;
    private final ApplicationContext applicationContext;

    private final PipelineConfig pipelineConfig;
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
                                PipelineConfig pipelineConfig) throws Exception {
        this.objectMapper = objectMapper;
        this.refreshPublisher = refreshPublisher;
        this.applicationContext = applicationContext;
        this.pipelineConfig = pipelineConfig;
        String configured = pipelineConfig.path();
        this.isClasspathResource = configured != null && configured.startsWith("classpath:");
        this.configPath = isClasspathResource ? configured.substring("classpath:".length()) : configured;
        log.warn("Look, ma, I'm a constructor now!");
        reloadConfig();  // Initial load
    }

    @Scheduled(fixedDelay = "${pipeline.auto-refresh-interval:3600s}", initialDelay = "${pipeline.auto-refresh-initial-delay:3600s}")
    public void checkForConfigChanges() throws Exception {
        log.info("pipelineConfig: {}", pipelineConfig);
        if (!pipelineConfig.autoRefresh()) {
            log.warn("Auto-refresh is disabled");
            return;
        } else {
            log.warn("Auto-refresh is enabled");
        }

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

    @EventListener
    public void onRefreshEvent(RefreshEvent event) throws Exception {
        if (isClasspathResource) return;
        File configFile = new File(configPath);
        if (configFile.exists()) {
            BasicFileAttributes attrs = Files.readAttributes(configFile.toPath(), BasicFileAttributes.class);
            if (attrs.lastModifiedTime().toMillis() > lastModified) {
                reloadConfig();
                log.info("Reloaded pipeline configuration on RefreshEvent from {}", configFile.getAbsolutePath());
            }
        }
    }

    private synchronized void reloadConfig() throws Exception {
        log.warn("Reloading pipeline configuration from {}", configPath);
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
        Set<String> seenNames = new HashSet<>();
        for (JsonNode stepNode : stepsNodeArray) {
            String name = stepNode.path("name").asText(null);
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("pipeline.json: every step must have a non-blank 'name'");
            }
            if (!seenNames.add(name)) {
                throw new IllegalStateException("pipeline.json: duplicate step name: '" + name + "'");
            }
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
        return process(input, Collections.emptyList());
    }

    /**
     * Like {@link #process(List)}, but skips any steps whose name appears in
     * {@code ignoredStepNames} for this invocation only (does not modify config).
     */
    public List<ObjectNode> process(List<ObjectNode> input, Collection<String> ignoredStepNames) {
        long startNanos = System.nanoTime();
        Map<String, Object> sessionContext = new HashMap<>();
        String sessionKey = StringUtils.generateRandomString();
        sessionContext.put(SESSION_KEY_KEY, sessionKey);
        sessionContext.put(START_NANOS_KEY, startNanos);

        Set<String> ignored = ignoredStepNames.isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(ignoredStepNames);

        List<PipelineStep> enabledSteps = steps.stream()
                .filter(PipelineStep::enabled)
                .filter(s -> !ignored.contains(s.name()))
                .toList();

        log.info("[{}] Processing {} items with {} enabled pipeline steps ({} explicitly ignored)",
                sessionKey, input.size(), enabledSteps.size(), ignored.size());

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

    @ConfigurationProperties("pipeline")
    public record PipelineConfig(
            String path,
            boolean autoRefresh
    ) {
        public PipelineConfig {
            if (path == null || path.isBlank()) {
                path = "./pipeline.json";
            } // if
        } // constructor
    } // class PipelineConfig

} // class ConfigurablePipeline

record PipelineStep(
        String name,
        String description,
        String className,
        PipelineAction action,
        boolean enabled,
        JsonNode params
) {}