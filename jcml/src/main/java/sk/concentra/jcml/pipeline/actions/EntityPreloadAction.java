package sk.concentra.jcml.pipeline.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.pipeline.PipelineAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class EntityPreloadAction implements PipelineAction {

    private static final Logger log = LoggerFactory.getLogger(EntityPreloadAction.class);
    private static final String SESSION_KEY_KEY = "sessionKey";

    @Inject
    private ApplicationContext applicationContext;

    @Override
    public List<ObjectNode> process(
            List<ObjectNode> input,
            Map<String, Object> globalContext,
            Map<String, Object> sessionContext,
            JsonNode params
    ) {
        String sessionKey = (String) sessionContext.get(SESSION_KEY_KEY);
        try {
            preloadAllEntitiesIfNeeded(sessionContext, params);
        } catch (Exception e) {
            log.error("[{}] Error during entity preload: {}", sessionKey, e.getMessage(), e);
            // continue — items pass through
        }
        return input;
    }

    private void preloadAllEntitiesIfNeeded(Map<String, Object> sessionContext, JsonNode params) {
        String sessionKey         = (String) sessionContext.get(SESSION_KEY_KEY);
        String entityClassName    = params.path("entityClassName").asText(null);
        String repositoryClassName = params.path("repositoryClassName").asText(null);
        String contextKey         = params.path("sessionContextKey").asText(null);

        if (entityClassName == null || entityClassName.isBlank()) {
            throw new IllegalArgumentException("[" + sessionKey + "] Missing or empty 'entityClassName' in params");
        }
        if (repositoryClassName == null || repositoryClassName.isBlank()) {
            throw new IllegalArgumentException("[" + sessionKey + "] Missing or empty 'repositoryClassName' in params");
        }
        if (contextKey == null || contextKey.isBlank()) {
            String simpleName = entityClassName.substring(entityClassName.lastIndexOf('.') + 1);
            contextKey = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
        }

        String mapKey  = contextKey;
        String listKey = contextKey + "List";

        if (sessionContext.containsKey(mapKey) || sessionContext.containsKey(listKey)) {
            log.debug("Skipping preload of {} — already present in session context (keys: {}, {})",
                    entityClassName, mapKey, listKey);
            return;
        }
        log.info("[{}] Preloading {} into session context (keys: {}, {})", sessionKey, entityClassName, mapKey, listKey);

        try {
            Class<?> entityClass = Class.forName(entityClassName);
            Class<?> repoClass   = Class.forName(repositoryClassName);
            Object repo          = applicationContext.getBean(repoClass);

            // Use reflection — repository may extend any Micronaut Data interface
            // (CrudRepository, GenericRepository, JpaRepository, etc.)
            String finderMethod = params.path("finderMethod").asText("findAll");
            Iterable<?> allEntities = (Iterable<?>) repoClass.getMethod(finderMethod).invoke(repo);

            ConcurrentHashMap<Object, Object> entityMap = new ConcurrentHashMap<>();
            List<Object> entityList = new ArrayList<>();

            for (Object entity : allEntities) {
                entityList.add(entity);
                try {
                    java.lang.reflect.Field idField = findIdField(entityClass);
                    if (idField != null) {
                        idField.setAccessible(true);
                        Object id = idField.get(entity);
                        if (id != null) {
                            entityMap.put(id, entity);
                        }
                    }
                } catch (Exception ignored) {}
            }

            sessionContext.put(listKey, entityList);
            if (!entityMap.isEmpty()) {
                sessionContext.put(mapKey, entityMap);
                log.info("[{}] Preloaded {} entities ({} with IDs) into session context under keys '{}/{}List'",
                        sessionKey, entityList.size(), entityMap.size(), mapKey, contextKey);
            } else {
                log.info("[{}] Preloaded {} entities (list only) into session context under key '{}List'",
                        sessionKey, entityList.size(), contextKey);
            }

        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("[" + sessionKey + "] Class not found: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("[" + sessionKey + "] Failed to preload entities from " + repositoryClassName, e);
        }
    }

    private java.lang.reflect.Field findIdField(Class<?> entityClass) {
        for (java.lang.reflect.Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(io.micronaut.data.annotation.Id.class)) {
                return field;
            }
        }
        List<String> commonIdNames = List.of("id", "skillTargetId", "agentTeamId", "precisionQueueId", "agentTeamID", "precisionQueueID");
        for (String name : commonIdNames) {
            try { return entityClass.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }
} // class