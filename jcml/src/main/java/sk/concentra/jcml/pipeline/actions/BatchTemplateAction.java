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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * StreamAction that applies Josson template rules to an entire item batch in a
 * single {@code process()} invocation.
 *
 * <p>Replaces multiple {@link JossonTemplateAction} pipeline steps with a single
 * step containing a list of conditional rules. Each rule specifies a Josson condition
 * and a map of {@code targetField -> templateString} pairs. For each item, the first
 * matching rule's templates are applied.</p>
 *
 * <p>All pre-processing (template parsing, fast-path classification, entity node
 * cache construction, condition cache) happens <em>once per {@code process()} call</em>,
 * not per item. Per-item cost is O(1) map lookups and direct field accesses.</p>
 *
 * <h3>pipeline.json structure</h3>
 * <pre>{@code
 * {
 *   "name": "ApplyTemplates",
 *   "className": "sk.concentra.jcml.pipeline.actions.BatchTemplateAction",
 *   "enabled": true,
 *   "condition": "true",
 *   "params": {
 *     "rules": [
 *       {
 *         "condition": "_messageType.in('ADD__SKILL_GROUP_MEMBER', 'DELETE__SKILL_GROUP_MEMBER')",
 *         "templates": {
 *           "_full_description": "Agent '{{eval(concat('session.agents.', item.agentSkillTargetID)).enterpriseName}}' -> SG: '{{eval(concat('session.skillGroups.', item.skillGroupSkillTargetID)).enterpriseName}}'"
 *         }
 *       }
 *     ]
 *   }
 * }
 * }</pre>
 *
 * <h3>Fast-path expression patterns (no Josson per item)</h3>
 * <ul>
 *   <li>{@code eval(concat('session.&lt;map&gt;.', item.&lt;field&gt;)).&lt;property&gt;}
 *       — resolved via pre-built entity node cache</li>
 *   <li>{@code item.&lt;field&gt;} — resolved via direct {@code ObjectNode.path()} call</li>
 * </ul>
 * <p>Expressions not matching either pattern fall back to Josson against a lightweight
 * envelope (entity maps excluded).</p>
 */
@Singleton
public class BatchTemplateAction implements PipelineAction {

    private static final Logger log = LoggerFactory.getLogger(BatchTemplateAction.class);
    private static final String SESSION_KEY_KEY = "sessionKey";

    /** Matches {@code {{...}}} placeholders in template strings. */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(.+?)}}");

    /**
     * Fast-path 1: entity map lookup.
     * {@code eval(concat('session.<mapKey>.', item.<idField>)).<property>}
     */
    private static final Pattern SESSION_LOOKUP_PATTERN = Pattern.compile(
            "eval\\(concat\\('session\\.([^.]+)\\.', item\\.([^)]+)\\)\\)\\.(.+)"
    );

    /** Fast-path 2: direct item field. {@code item.<field>} — simple identifiers only;
     *  bracket/dot sub-expressions (e.g. {@code item._header[1]}) fall through to Josson. */
    private static final Pattern ITEM_FIELD_PATTERN = Pattern.compile("item\\.(\\w+)");

    @Inject
    private ObjectMapper objectMapper;

    // ── Records / sealed types ────────────────────────────────────────────────

    private record ParsedTemplate(String targetField, List<String> segments, int expressionCount) {}

    private sealed interface FastPath permits EntityLookup, ItemFieldLookup {}
    private record EntityLookup(String mapKey, String idField, String property) implements FastPath {}
    private record ItemFieldLookup(String field) implements FastPath {}

    /**
     * A fully pre-processed rule: condition string, parsed templates, fast-path
     * index, and a flag indicating whether any expression needs Josson.
     */
    private record CompiledRule(
            String condition,
            List<ParsedTemplate> parsedTemplates,
            Map<Integer, Map<Integer, FastPath>> fastPathIndex,
            boolean needsJosson
    ) {}

    // ── Main entry point ──────────────────────────────────────────────────────

    @Override
    public List<ObjectNode> process(
            List<ObjectNode> input,
            Map<String, Object> globalContext,
            Map<String, Object> sessionContext,
            JsonNode params
    ) {
        final String sessionKey = (String) sessionContext.getOrDefault(SESSION_KEY_KEY, "???");

        final JsonNode rulesNode = params.path("rules");
        if (!rulesNode.isArray() || rulesNode.isEmpty()) {
            log.warn("[{}] BatchTemplateAction: no rules defined — passing through unchanged", sessionKey);
            return input;
        }

        // ── Compile rules once per process() call ─────────────────────────────
        final List<CompiledRule> compiledRules = compileRules(rulesNode, sessionKey);

        // ── Build entity node cache once — only maps referenced by any rule ───
        final Set<String> neededMapKeys = collectNeededMapKeys(compiledRules);
        final Map<String, Map<Object, JsonNode>> entityNodeCache =
                buildEntityNodeCache(sessionContext, neededMapKeys);
        log.debug("[{}] Entity cache built for {} maps", sessionKey, entityNodeCache.size());

        // ── Build lightweight Josson envelope if any rule needs Josson ─────────
        final boolean anyNeedsJosson = compiledRules.stream().anyMatch(CompiledRule::needsJosson);
        final ObjectNode envelope;
        if (anyNeedsJosson) {
            final ObjectNode sessionNode = buildLightweightSessionNode(sessionContext);
            final JsonNode globalNode    = objectMapper.valueToTree(globalContext);
            envelope = objectMapper.createObjectNode();
            envelope.set("session", sessionNode);
            envelope.set("global",  globalNode);
            log.debug("[{}] Josson envelope built (some expressions need Josson fallback)", sessionKey);
        } else {
            envelope = null;
            log.debug("[{}] All expressions on fast-path — Josson not needed", sessionKey);
        }

        // ── Condition cache: messageType -> matching rule index (or -1) ────────
        // Evaluated once per distinct _messageType, not per item.
        final Map<String, Integer> conditionCache = new HashMap<>();

        // ── Process items ─────────────────────────────────────────────────────
        final List<ObjectNode> output = new ArrayList<>(input.size());

        for (final ObjectNode item : input) {
            final String messageType = item.path("_messageType").asText("");

            // Find matching rule — cached by messageType
            final int ruleIndex = conditionCache.computeIfAbsent(messageType, mt -> {
                for (int i = 0; i < compiledRules.size(); i++) {
                    if (evaluateCondition(compiledRules.get(i).condition(), item)) {
                        return i;
                    }
                }
                return -1; // no rule matched
            });

            if (ruleIndex == -1) {
                output.add(item); // no matching rule — pass through unchanged
                continue;
            }

            final CompiledRule rule = compiledRules.get(ruleIndex);

            // Build Josson per item only if this rule needs it
            final Josson josson;
            if (rule.needsJosson() && envelope != null) {
                envelope.set("item", item);
                josson = Josson.create(envelope);
            } else {
                josson = null;
            }

            final ObjectNode result = item.deepCopy();
            for (int ti = 0; ti < rule.parsedTemplates().size(); ti++) {
                final ParsedTemplate pt = rule.parsedTemplates().get(ti);
                result.put(pt.targetField(),
                        resolveSegments(
                                pt,
                                rule.fastPathIndex().getOrDefault(ti, Collections.emptyMap()),
                                josson, item, entityNodeCache, sessionKey));
            }
            output.add(result);
        }

        log.info("[{}] BatchTemplateAction: processed {} items, {} distinct messageTypes matched",
                sessionKey, input.size(), conditionCache.values().stream().filter(i -> i >= 0).count());

        return output;
    }

    // ── Rule compilation ──────────────────────────────────────────────────────

    private List<CompiledRule> compileRules(final JsonNode rulesNode, final String sessionKey) {
        final List<CompiledRule> compiled = new ArrayList<>();
        for (final JsonNode ruleNode : rulesNode) {
            final String condition  = ruleNode.path("condition").asText("true");
            final JsonNode tmplNode = ruleNode.path("templates");
            if (!tmplNode.isObject()) {
                log.warn("[{}] Rule with condition '{}' has no valid 'templates' object — skipping",
                        sessionKey, condition);
                continue;
            }

            final List<ParsedTemplate> parsedTemplates = new ArrayList<>();
            final Iterator<String> fieldNames = tmplNode.fieldNames();
            while (fieldNames.hasNext()) {
                final String targetField = fieldNames.next();
                parsedTemplates.add(parseTemplate(targetField, tmplNode.get(targetField).asText()));
            }

            final Map<Integer, Map<Integer, FastPath>> fastPathIndex =
                    buildFastPathIndex(parsedTemplates, sessionKey, condition);

            final boolean needsJosson = parsedTemplates.stream().anyMatch(pt -> {
                final int ti = parsedTemplates.indexOf(pt);
                return fastPathIndex.getOrDefault(ti, Collections.emptyMap()).size()
                        < pt.expressionCount();
            });

            compiled.add(new CompiledRule(condition, parsedTemplates, fastPathIndex, needsJosson));
            log.info("[{}] Compiled rule: condition='{}', templates={}, needsJosson={}",
                    sessionKey, condition, parsedTemplates.stream()
                            .map(ParsedTemplate::targetField).toList(), needsJosson);
        }
        return compiled;
    }

    // ── Fast-path index ───────────────────────────────────────────────────────

    private Map<Integer, Map<Integer, FastPath>> buildFastPathIndex(
            final List<ParsedTemplate> parsedTemplates,
            final String sessionKey,
            final String ruleCondition
    ) {
        final Map<Integer, Map<Integer, FastPath>> index = new HashMap<>();
        for (int ti = 0; ti < parsedTemplates.size(); ti++) {
            final ParsedTemplate pt = parsedTemplates.get(ti);
            final List<String> segs = pt.segments();
            Map<Integer, FastPath> exprMap = null;
            int exprSlot = 0;
            for (int i = 1; i < segs.size(); i += 2) {
                final String expr = segs.get(i).trim();
                FastPath fastPath = null;

                final Matcher entityMatcher = SESSION_LOOKUP_PATTERN.matcher(expr);
                if (entityMatcher.matches()) {
                    fastPath = new EntityLookup(
                            entityMatcher.group(1), entityMatcher.group(2), entityMatcher.group(3));
                }
                if (fastPath == null) {
                    final Matcher itemMatcher = ITEM_FIELD_PATTERN.matcher(expr);
                    if (itemMatcher.matches()) {
                        fastPath = new ItemFieldLookup(itemMatcher.group(1));
                    }
                }

                if (fastPath != null) {
                    if (exprMap == null) exprMap = new HashMap<>();
                    exprMap.put(exprSlot, fastPath);
                    log.debug("  Fast-path [{}] slot={} -> {}", ruleCondition, exprSlot, fastPath);
                } else {
                    log.debug("  Josson fallback [{}] slot={} expr='{}'", ruleCondition, exprSlot, expr);
                }
                exprSlot++;
            }
            if (exprMap != null) index.put(ti, exprMap);
        }
        return index;
    }

    // ── Entity node cache ─────────────────────────────────────────────────────

    private Set<String> collectNeededMapKeys(final List<CompiledRule> compiledRules) {
        final Set<String> keys = new HashSet<>();
        for (final CompiledRule rule : compiledRules) {
            for (final Map<Integer, FastPath> exprMap : rule.fastPathIndex().values()) {
                for (final FastPath fp : exprMap.values()) {
                    if (fp instanceof EntityLookup lookup) keys.add(lookup.mapKey());
                }
            }
        }
        return keys;
    }

    private Map<String, Map<Object, JsonNode>> buildEntityNodeCache(
            final Map<String, Object> sessionContext,
            final Set<String> neededMapKeys
    ) {
        final Map<String, Map<Object, JsonNode>> cache = new HashMap<>();
        for (final String mapKey : neededMapKeys) {
            final Object value = sessionContext.get(mapKey);
            if (value instanceof ConcurrentHashMap<?, ?> entityMap) {
                final Map<Object, JsonNode> nodeMap = new HashMap<>(entityMap.size() * 2);
                entityMap.forEach((id, entity) -> {
                    final JsonNode node = objectMapper.valueToTree(entity);
                    nodeMap.put(id, node);
                    if (!(id instanceof String)) nodeMap.put(String.valueOf(id), node);
                });
                cache.put(mapKey, nodeMap);
                log.debug("Entity cache '{}': {} entities", mapKey, entityMap.size());
            }
        }
        return cache;
    }

    // ── Lightweight session node ──────────────────────────────────────────────

    private ObjectNode buildLightweightSessionNode(final Map<String, Object> sessionContext) {
        final ObjectNode node = objectMapper.createObjectNode();
        sessionContext.forEach((key, value) -> {
            if (value instanceof Map || value instanceof List) return;
            node.set(key, objectMapper.valueToTree(value));
        });
        return node;
    }

    // ── Template parsing ──────────────────────────────────────────────────────

    private static ParsedTemplate parseTemplate(final String targetField, final String template) {
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

    // ── Segment resolution ────────────────────────────────────────────────────

    private String resolveSegments(
            final ParsedTemplate pt,
            final Map<Integer, FastPath> fastPathExprs,
            final Josson josson,
            final ObjectNode item,
            final Map<String, Map<Object, JsonNode>> entityNodeCache,
            final String sessionKey
    ) {
        if (pt.expressionCount() == 0) return pt.segments().getFirst();

        final List<String> segs = pt.segments();
        int capacity = 0;
        for (final String s : segs) capacity += s.length();
        capacity += pt.expressionCount() * 24;

        final StringBuilder sb = new StringBuilder(capacity);
        int exprSlot = 0;

        for (int i = 0; i < segs.size(); i++) {
            if ((i & 1) == 0) {
                sb.append(segs.get(i)); // literal
            } else {
                final FastPath fastPath = fastPathExprs.get(exprSlot);

                if (fastPath instanceof EntityLookup lookup) {
                    final JsonNode idNode = item.path(lookup.idField());
                    if (!idNode.isMissingNode() && !idNode.isNull()) {
                        final Map<Object, JsonNode> entityMap = entityNodeCache.get(lookup.mapKey());
                        if (entityMap != null) {
                            JsonNode entityNode = entityMap.get(
                                    idNode.isInt() || idNode.isLong()
                                            ? (Object) idNode.asInt()
                                            : idNode.asText());
                            if (entityNode == null) entityNode = entityMap.get(idNode.asText());
                            if (entityNode != null) {
                                final JsonNode propNode = entityNode.path(lookup.property());
                                if (!propNode.isMissingNode() && !propNode.isNull()) {
                                    sb.append(propNode.asText());
                                } else {
                                    log.debug("[{}] Property '{}' not found on entity id={}",
                                            sessionKey, lookup.property(), idNode.asText());
                                }
                            } else {
                                log.debug("[{}] No entity in '{}' for id={}",
                                        sessionKey, lookup.mapKey(), idNode.asText());
                            }
                        }
                    }

                } else if (fastPath instanceof ItemFieldLookup lookup) {
                    final JsonNode val = item.path(lookup.field());
                    if (!val.isMissingNode() && !val.isNull()) {
                        sb.append(val.isContainerNode() ? val : val.asText());
                    }

                } else {
                    // Josson fallback
                    if (josson != null) {
                        try {
                            final JsonNode result = josson.getNode(segs.get(i));
                            if (result != null && !result.isMissingNode()) {
                                sb.append(result.isContainerNode() ? result : result.asText());
                            }
                        } catch (Exception e) {
                            log.warn("[{}] Josson error in expression [{}]: {}",
                                    sessionKey, segs.get(i), e.getMessage());
                        }
                    } else {
                        log.warn("[{}] Josson not available but expression [{}] needs it — skipped",
                                sessionKey, segs.get(i));
                    }
                }
                exprSlot++;
            }
        }
        return sb.toString();
    }

    // ── Condition evaluation ──────────────────────────────────────────────────

    private boolean evaluateCondition(final String expr, final ObjectNode item) {
        if (expr == null || expr.equals("true")) return true;
        try {
            final JsonNode result = Josson.create(item).getNode(expr);
            return result != null && result.asBoolean();
        } catch (Exception e) {
            log.warn("Condition evaluation failed for expr='{}': {}", expr, e.getMessage());
            return false;
        }
    }

} // class