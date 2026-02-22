package sk.concentra.jcml.persistence;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of {@link FindAllRepository} for {@link DialingMode}.
 *
 * <p>No database access — values are hardcoded. Implements {@link FindAllRepository}
 * so that {@code EntityPreloadAction} can load it via the standard
 * {@code repo.findAll()} mechanism, keyed by {@code dialingMode} integer ID.</p>
 *
 * <p>Intentionally does NOT implement {@code CrudRepository} to avoid triggering
 * Micronaut Data's annotation processor, which would try to generate SQL for a
 * non-existent table.</p>
 */
@Singleton
public class DialingModeRepository implements FindAllRepository<DialingMode> {

    private static final Map<Integer, DialingMode> DIALING_MODES;

    static {
        DIALING_MODES = new LinkedHashMap<>();
        register(0, "INBOUND");
        register(1, "PREDICTIVE_ONLY");
        register(2, "PREDICTIVE_BLENDED");
        register(3, "PREVIEW_ONLY");
        register(4, "PREVIEW_BLENDED");
        register(5, "PROGRESSIVE_ONLY");
        register(6, "PROGRESSIVE_BLENDED");
        register(7, "DIRECT_PREVIEW_ONLY");
        register(8, "DIRECT_PREVIEW_BLENDED");
    }

    private static void register(int id, String text) {
        DIALING_MODES.put(id, new DialingMode(id, text));
    }

    // ── FindAllRepository ─────────────────────────────────────────────────────

    @Override
    public List<DialingMode> findAll() {
        return List.copyOf(DIALING_MODES.values());
    }

    // ── Convenience lookups ───────────────────────────────────────────────────

    public Optional<DialingMode> findById(@NonNull Integer dialingMode) {
        return Optional.ofNullable(DIALING_MODES.get(dialingMode));
    }

    public boolean existsById(@NonNull Integer dialingMode) {
        return DIALING_MODES.containsKey(dialingMode);
    }

    public long count() {
        return DIALING_MODES.size();
    }

    /**
     * Returns the {@code dialingModeText} for the given ID,
     * or {@code null} if the ID is not recognised.
     */
    public String getDialingModeText(@NonNull Integer dialingMode) {
        final DialingMode dm = DIALING_MODES.get(dialingMode);
        return dm != null ? dm.getDialingModeText() : null;
    }
}