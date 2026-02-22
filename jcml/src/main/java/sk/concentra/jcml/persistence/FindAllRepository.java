package sk.concentra.jcml.persistence;

/**
 * Minimal repository interface exposing only {@code findAll()}.
 *
 * <p>Used by {@link sk.concentra.jcml.pipeline.actions.EntityPreloadAction} instead
 * of {@code CrudRepository} so that in-memory repositories (e.g.
 * {@link DialingModeRepository}) can be implemented without triggering Micronaut
 * Data's annotation processor, which expects a real database-backed entity.</p>
 *
 * <p>All Micronaut Data repositories already satisfy this interface implicitly
 * since they extend {@code CrudRepository} which also declares {@code findAll()}.
 * No changes are needed to existing repositories.</p>
 *
 * @param <T> the entity type
 */
public interface FindAllRepository<T> {
    Iterable<T> findAll();
}