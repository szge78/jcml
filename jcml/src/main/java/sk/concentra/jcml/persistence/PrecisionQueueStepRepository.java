package sk.concentra.jcml.persistence;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.SQL_SERVER)
public interface PrecisionQueueStepRepository extends CrudRepository<PrecisionQueueStep, Integer> {
    PrecisionQueueStep getByPrecisionQueueStepId(Integer id);
} // class
