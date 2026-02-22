//package sk.concentra.jcml.pipeline;
//
//import com.octomix.josson.Josson;
//import io.micronaut.context.ApplicationContext;
//import io.micronaut.context.annotation.Value;
//import io.micronaut.core.annotation.Nullable;
//import io.micronaut.runtime.context.scope.Refreshable;
//import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
//import io.micronaut.context.event.ApplicationEventPublisher;
//import io.micronaut.scheduling.annotation.Scheduled;
//import jakarta.inject.Singleton;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import reactor.core.publisher.Flux;
//import sk.concentra.jcml.util.StringUtils;
//
//import java.io.File;
//import java.io.IOException;
//import java.io.InputStream;
//import java.nio.file.Files;
//import java.nio.file.attribute.BasicFileAttributes;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//
///**
// * Eager pipeline processor — executes each pipeline step to completion before
// * starting the next one.
// *
// * <p>Unlike {@link ConfigurablePipeline} which builds a lazy Reactor chain,
// * this class materializes the result of each step into a {@code List<ObjectNode>}
// * before proceeding to the next step. This gives:</p>
// * <ul>
// *   <li>Accurate per-step wall-clock timing</li>
// *   <li>Accurate per-step item counts (visible after each step completes)</li>
// *   <li>Simpler mental model — no lazy evaluation, no operator ordering surprises</li>
// *   <li>Barrier steps (StreamAction) work naturally — no special handling needed</li>
// * </ul>
// *
// * <p>Configuration in {@code application.yml}:</p>
// * <pre>
// * pipeline:
// *   config:
// *     path: classpath:pipeline.json
// *   reload:
// *     fixed-delay: 120s
// *     initial-delay: 120s
// * </pre>
// */
//@Singleton
//@Refreshable
//public class EagerPipeline {
//
//    private static final Logger log = LoggerFactory.getLogger(EagerPipeline.class);
//
//    private static final String SESSION_KEY_KEY = "sessionKey";
//    private static final String START_NANOS_KEY = "startNanos";
//
//    private final ObjectMapper objectMapper;
//    private final ApplicationEventPublisher<RefreshEvent> refreshPublisher;
//    private final ApplicationContext applicationContext;
//
//    private final String configPath;
//    private final boolean isClasspathResource;
//
//    private volatile List<PipelineStep> steps = Collections.emptyList();
//    private volatile long lastModified = 0;
//    private final ConcurrentHashMap<String, Object> globalContext = new ConcurrentHashMap<>();
//
//    // ── Constructor ───────────────────────────────────────────────────────────
//
//    public EagerPipeline(
//            ObjectMapper objectMapper,
//            ApplicationEventPublisher<RefreshEvent> refreshPublisher,
//            ApplicationContext applicationContext,
//            @Value("${pipeline.config.path:classpath:pipeline.json}") String configPath
//    ) throws Exception {
//        this.objectMapper        = objectMapper;
//        this.refreshPublisher    = refreshPublisher;
//        this.applicationContext  = applicationContext;
//        this.isClasspathResource = configPath.startsWith("classpath:");
//        this.configPath          = isClasspathResource
//                ? configPath.substring("classpath:".length())
//                : configPath;
//        reloadConfig();
//    }
//
//    // ── Hot-reload ────────────────────────────────────────────────────────────
//
//    /**
//     * Periodically checks whether the pipeline config file has been modified on disk
//     * and reloads it if so. Skipped for classpath resources.
//     *
//     * <p>Delays are configurable via:</p>
//     * <pre>
//     * pipeline:
//     *   reload:
//     *     fixed-delay: 120s
//     *     initial-delay: 120s
//     * </pre>
//     */
//    @Scheduled(
//            fixedDelay  = "${pipeline.reload.fixed-delay:120s}",
//            initialDelay = "${pipeline.reload.initial-delay:120s}"
//    )
//    public void checkForConfigChanges() throws Exception {
//        if (isClasspathResource) {
//            log.debug("Skipping classpath resource check: {}", configPath);
//            return;
//        }
//
//        final File configFile = new File(configPath);
//        if (configFile.exists()) {
//            final BasicFileAttributes attrs = Files.readAttributes(
//                    configFile.toPath(), BasicFileAttributes.class);
//            log.debug("Checking config file: {} last modified: {}",
//                    configFile.getAbsolutePath(), attrs.lastModifiedTime().toMillis());
//            if (attrs.lastModifiedTime().toMillis() > lastModified) {
//                reloadConfig();
//                refreshPublisher.publishEvent(new RefreshEvent());
//                log.info("Reloaded pipeline configuration from {}", configFile.getAbsolutePath());
//            }
//        }
//    }
//
//    // ── Config loading ────────────────────────────────────────────────────────
//
//    private synchronized void reloadConfig() throws Exception {
//        final JsonNode config;
//        if (isClasspathResource) {
//            final InputStream inputStream = Thread.currentThread()
//                    .getContextClassLoader().getResourceAsStream(configPath);
//            if (inputStream == null) {
//                throw new IOException("Classpath resource not found: " + configPath);
//            }
//            config = objectMapper.readTree(inputStream);
//            lastModified = 0;
//        } else {
//            final File file = new File(configPath);
//            config = objectMapper.readTree(file);
//            lastModified = file.lastModified();
//        }
//
//        final JsonNode stepsArray = config.path("steps");
//        if (!stepsArray.isArray()) {
//            throw new IllegalStateException("pipeline.json: 'steps' must be an array");
//        }
//
//        final List<PipelineStep> loaded = new ArrayList<>();
//        for (final JsonNode stepNode : stepsArray) {
//            final String name = stepNode.path("name").asText(null);
//            if (name == null || name.isBlank()) continue;
//
//            final String description  = stepNode.path("description").asText("");
//            final boolean enabled     = stepNode.path("enabled").asBoolean(true);
//            final String className    = stepNode.path("className").asText();
//            final Class<?> actionClass = Class.forName(className);
//            final PipelineAction action = (PipelineAction) applicationContext.getBean(actionClass);
//            final String condition    = stepNode.path("condition").asText("true");
//            final JsonNode params     = stepNode.path("params").isObject()
//                    ? stepNode.path("params")
//                    : objectMapper.createObjectNode();
//
//            loaded.add(new PipelineStep(name, description, className, action, enabled, condition, params));
//            log.info("Loaded pipeline step class: {}; params: {}", className, params);
//        }
//
//        steps = loaded;
//        log.info("Loaded {} pipeline steps", steps.size());
//    }
//
//    // ── Main entry point ──────────────────────────────────────────────────────
//
//    /**
//     * Processes the input list through each enabled pipeline step eagerly.
//     * Each step runs to full completion before the next step begins.
//     *
//     * <p>Returns {@code Flux<ObjectNode>} for API compatibility with
//     * {@link ConfigurablePipeline}. The actual processing is synchronous and
//     * fully complete before the Flux is assembled.</p>
//     */
//    public Flux<ObjectNode> process(final List<ObjectNode> input) {
//        final long pipelineStart = System.nanoTime();
//        final Map<String, Object> sessionContext = new HashMap<>();
//        final String sessionKey = StringUtils.generateRandomString();
//        sessionContext.put(SESSION_KEY_KEY, sessionKey);
//        sessionContext.put(START_NANOS_KEY, pipelineStart);
//
//        final List<PipelineStep> enabledSteps = steps.stream()
//                .filter(PipelineStep::enabled)
//                .toList();
//
//        log.info("[{}] EagerPipeline: {} items, {} enabled steps",
//                sessionKey, input.size(), enabledSteps.size());
//
//        List<ObjectNode> current = new ArrayList<>(input);
//
//        for (final PipelineStep step : enabledSteps) {
//            final String stepName  = step.name();
//            final int itemsIn      = current.size();
//            final long stepStart   = System.nanoTime();
//
//            if (step.action() instanceof StreamAction) {
//                // ── StreamAction: pass the full stream unchanged ──────────
//                // The action handles its own item-level filtering internally
//                // (e.g. CmlHeaderExtractorAction, SortAction).
//                final List<ObjectNode> result = step.action()
//                        .process(Flux.fromIterable(current), globalContext, sessionContext, step.params())
//                        .collectList()
//                        .block();
//                current = result != null ? result : Collections.emptyList();
//
//            } else {
//                // ── PipelineAction: process per item ──────────────────────
//                // Items that don't match the step condition pass through unchanged.
//                // Items that match are processed by the action (which may expand
//                // them, e.g. ArrayUnwrapAction producing multiple items per input).
//                final List<ObjectNode> nextBatch = new ArrayList<>(current.size());
//                for (final ObjectNode item : current) {
//                    if (!evaluateCondition(step.condition(), item)) {
//                        nextBatch.add(item); // condition not met — pass through unchanged
//                    } else {
//                        final List<ObjectNode> result = step.action()
//                                .process(Flux.just(item), globalContext, sessionContext, step.params())
//                                .collectList()
//                                .block();
//                        if (result != null) nextBatch.addAll(result);
//                    }
//                }
//                current = nextBatch;
//            }
//
//            final long stepMs = (System.nanoTime() - stepStart) / 1_000_000;
//            log.info("[{}] Step '{}' — {} items in -> {} items out — {}ms",
//                    sessionKey, stepName, itemsIn, current.size(), stepMs);
//        }
//
//        final long totalMs = (System.nanoTime() - pipelineStart) / 1_000_000;
//        log.info("[{}] EagerPipeline complete — {} items, {}ms total",
//                sessionKey, current.size(), totalMs);
//
//        return Flux.fromIterable(current);
//    }
//
//    // ── Condition evaluation ──────────────────────────────────────────────────
//
//    private boolean evaluateCondition(@Nullable String expr, final ObjectNode item) {
//        if (expr == null) return true;
//        log.debug("e: {} item: {}", expr, item);
//        final JsonNode jsonNode = Josson.create(item).getNode(expr);
//        log.debug("e: {} result node: {}", expr, jsonNode);
//        return jsonNode != null && jsonNode.asBoolean();
//    }
//
//} // class