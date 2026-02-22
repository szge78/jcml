package sk.concentra.jcml.pipeline.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.concentra.jcml.pipeline.PipelineAction;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class NativeSqlPreloadAction implements PipelineAction {

    private static final Logger log = LoggerFactory.getLogger(NativeSqlPreloadAction.class);
    private static final String SESSION_KEY_KEY = "sessionKey";

    @Inject
    private DataSource dataSource;

    @Override
    public List<ObjectNode> process(
            List<ObjectNode> input,
            Map<String, Object> globalContext,
            Map<String, Object> sessionContext,
            JsonNode params
    ) {
        String sessionKey = (String) sessionContext.get(SESSION_KEY_KEY);
        try {
            preloadIfNeeded(sessionContext, params);
        } catch (Exception e) {
            log.error("[{}] Error during native SQL preload: {}", sessionKey, e.getMessage(), e);
            // continue — items pass through
        }
        return input;
    }

    private void preloadIfNeeded(Map<String, Object> sessionContext, JsonNode params) throws Exception {
        String sessionKey       = (String) sessionContext.get(SESSION_KEY_KEY);
        String nativeSql        = params.path("nativeSql").asText(null);
        String idColumn         = params.path("idColumn").asText(null);
        String sessionContextKey = params.path("sessionContextKey").asText(null);

        if (nativeSql == null || nativeSql.isBlank()) {
            throw new IllegalArgumentException("[" + sessionKey + "] Missing or empty 'nativeSql' in params");
        }
        if (idColumn == null || idColumn.isBlank()) {
            throw new IllegalArgumentException("[" + sessionKey + "] Missing or empty 'idColumn' in params");
        }
        if (sessionContextKey == null || sessionContextKey.isBlank()) {
            throw new IllegalArgumentException("[" + sessionKey + "] Missing or empty 'sessionContextKey' in params");
        }

        String mapKey  = sessionContextKey;
        String listKey = sessionContextKey + "List";

        if (sessionContext.containsKey(mapKey) || sessionContext.containsKey(listKey)) {
            log.debug("Skipping native SQL preload for '{}' — already present in session context (keys: {}, {})",
                    sessionContextKey, mapKey, listKey);
            return;
        }
        log.info("[{}] Preloading via native SQL into session context (keys: {}, {})", sessionKey, mapKey, listKey);

        List<Object> rowList = new ArrayList<>();
        ConcurrentHashMap<Object, Object> rowMap = new ConcurrentHashMap<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(nativeSql);
             ResultSet rs = ps.executeQuery()) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            while (rs.next()) {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rowList.add(row);
                Object id = row.get(idColumn);
                if (id != null) {
                    rowMap.put(id, row);
                }
            }
        }

        sessionContext.put(listKey, rowList);
        if (!rowMap.isEmpty()) {
            sessionContext.put(mapKey, rowMap);
            log.info("[{}] Preloaded {} rows ({} with IDs) into session context under keys '{}/{}List'",
                    sessionKey, rowList.size(), rowMap.size(), mapKey, sessionContextKey);
        } else {
            log.info("[{}] Preloaded {} rows (list only) into session context under key '{}List'",
                    sessionKey, rowList.size(), sessionContextKey);
        }
    }
} // class
