package sk.concentra.jcml.schema;

import io.micronaut.context.annotation.ConfigurationProperties;

import io.micronaut.scheduling.annotation.Scheduled;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Registry that loads and manages message schemas from JSON files.
 * Can be refreshed without application restart.
 */
@Singleton
public class SchemaRegistry {
    private static final Logger log = LoggerFactory.getLogger(SchemaRegistry.class);

    private final Map<String, MessageSchema> schemas = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final SchemaConfig schemaConfig;
    private volatile long lastLoadTime = 0;

    private final boolean isClasspathResource;
    private final String schemaLocation; // normalized: without "classpath:" prefix if classpath-based

    public SchemaRegistry(ObjectMapper objectMapper, SchemaConfig schemaConfig) {
        this.objectMapper = objectMapper;
        this.schemaConfig = schemaConfig;

        String configured = schemaConfig.path();
        this.isClasspathResource = configured != null && configured.startsWith("classpath:");
        this.schemaLocation = isClasspathResource ? configured.substring("classpath:".length()) : configured;

        loadSchemas();
    }

    /**
     * Get schema by message type.
     */
    public MessageSchema getSchema(String messageType) {
        MessageSchema schema = schemas.get(messageType);
        if (schema == null) {
            throw new SchemaNotFoundException("No schema found for message type: " + messageType);
        }
        return schema;
    }

    /**
     * Manually trigger schema reload.
     */
    public synchronized void refresh() {
        log.info("Manually refreshing schemas from: {}", schemaConfig.path());
        loadSchemas();
    }

    /**
     * Automatically refresh schemas periodically if enabled.
     */
    @Scheduled(fixedDelay = "${schema.auto-refresh-interval:3600s}",
            initialDelay = "${schema.auto-refresh-initial-delay:3600s}")
    public void autoRefresh() {
        if (!schemaConfig.autoRefresh()) {
            return;
        }

        if (isClasspathResource) {
            log.debug("Skipping auto-refresh for classpath schemas: classpath:{}", schemaLocation);
            return; // can't reliably watch resources inside JARs
        }

        try {
            Path schemaDir = Paths.get(schemaLocation);
            if (!Files.exists(schemaDir)) {
                log.warn("Schema directory does not exist: {}", schemaDir);
                return;
            }

            // Check if any schema file has been modified since last load
            long latestModification = findLatestModificationTime(schemaDir);

            if (latestModification > lastLoadTime) {
                log.info("Schema files have been modified, reloading...");
                refresh();
            }
        } catch (IOException e) {
            log.error("Error checking schema file modifications", e);
        }
    }

    private long findLatestModificationTime(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .max(Long::compare)
                    .orElse(0L);
        }
    }

    private synchronized void loadSchemas() {
        try {
            Map<String, MessageSchema> newSchemas = new ConcurrentHashMap<>();

            if (isClasspathResource) {
                loadSchemasFromClasspath(schemaLocation, newSchemas);
                // Atomic replacement of schemas
                schemas.clear();
                schemas.putAll(newSchemas);
                lastLoadTime = 0; // no meaningful lastModified for classpath resources

                log.info("Loaded {} schemas from classpath:{}", schemas.size(), schemaLocation);
                schemas.keySet().forEach(type -> log.debug("  - {}", type));
                return;
            }

            Path schemaDir = Paths.get(schemaLocation);

            if (!Files.exists(schemaDir)) {
                log.warn("Schema directory does not exist, creating: {}", schemaDir);
                Files.createDirectories(schemaDir);
                return;
            }

            try (Stream<Path> paths = Files.walk(schemaDir)) {
                paths.filter(p -> p.toString().endsWith(".json"))
                        .forEach(path -> loadSchema(path, newSchemas));
            }

            // Atomic replacement of schemas
            schemas.clear();
            schemas.putAll(newSchemas);
            lastLoadTime = System.currentTimeMillis();

            log.info("Loaded {} schemas from {}", schemas.size(), schemaDir);
            schemas.keySet().forEach(type -> log.debug("  - {}", type));

        } catch (Exception e) {
            log.error("Error loading schemas", e);
            throw new RuntimeException("Failed to load schemas", e);
        }
    }

    private void loadSchemasFromClasspath(String schemaDirOnClasspath, Map<String, MessageSchema> targetMap) throws IOException {
        String dir = normalizeClasspathDir(schemaDirOnClasspath);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        Enumeration<URL> resources = cl.getResources(dir);
        if (!resources.hasMoreElements()) {
            throw new IOException("Classpath schema directory not found: " + dir);
        }

        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            String protocol = url.getProtocol();

            if ("file".equals(protocol)) {
                loadSchemasFromClasspathFileUrl(url, targetMap);
            } else if ("jar".equals(protocol)) {
                loadSchemasFromClasspathJarUrl(url, dir, targetMap);
            } else {
                log.warn("Unsupported classpath URL protocol '{}' for schemas at: {}", protocol, url);
            }
        }
    }

    private void loadSchemasFromClasspathFileUrl(URL url, Map<String, MessageSchema> targetMap) throws IOException {
        try {
            URI uri = url.toURI();
            Path dirPath = Paths.get(uri);
            try (Stream<Path> paths = Files.walk(dirPath)) {
                paths.filter(p -> p.toString().endsWith(".json"))
                        .forEach(path -> loadSchema(path, targetMap));
            }
        } catch (Exception e) {
            if (e instanceof IOException io) throw io;
            throw new IOException("Failed to load schemas from classpath file URL: " + url, e);
        }
    }

    private void loadSchemasFromClasspathJarUrl(URL url, String dir, Map<String, MessageSchema> targetMap) throws IOException {
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        try (JarFile jar = connection.getJarFile()) {
            String prefix = dir.endsWith("/") ? dir : (dir + "/");

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.startsWith(prefix) && name.endsWith(".json")) {
                    try (InputStream in = jar.getInputStream(entry)) {
                        loadSchema(in, "jar:" + jar.getName() + "!/" + name, targetMap);
                    }
                }
            }
        }
    }

    private String normalizeClasspathDir(String raw) {
        if (raw == null) {
            return "";
        }
        String dir = raw.trim();
        while (dir.startsWith("/")) {
            dir = dir.substring(1);
        }
        if (dir.endsWith("/")) {
            dir = dir.substring(0, dir.length() - 1);
        }
        return dir;
    }

    private void loadSchema(Path path, Map<String, MessageSchema> targetMap) {
        try {
            MessageSchema schema = objectMapper.readValue(path.toFile(), MessageSchema.class);
            targetMap.put(schema.messageType(), schema);
            log.debug("Loaded schema: {} (version {}) from {}",
                    schema.messageType(), schema.version(), path.getFileName());
        } catch (IOException e) {
            log.error("Error loading schema from {}", path, e);
        }
    }

    private void loadSchema(InputStream inputStream, String source, Map<String, MessageSchema> targetMap) {
        try {
            MessageSchema schema = objectMapper.readValue(inputStream, MessageSchema.class);
            targetMap.put(schema.messageType(), schema);
            log.debug("Loaded schema: {} (version {}) from {}",
                    schema.messageType(), schema.version(), source);
        } catch (IOException e) {
            log.error("Error loading schema from {}", source, e);
        }
    }

    /**
     * Configuration for schema registry.
     */
    @ConfigurationProperties("schema")
    public record SchemaConfig(
            String path,
            boolean autoRefresh
    ) {
        public SchemaConfig {
            if (path == null || path.isBlank()) {
                path = "./schemas";
            }
        }
    }

    public static class SchemaNotFoundException extends RuntimeException {
        public SchemaNotFoundException(String message) {
            super(message);
        }
    }

} // class
