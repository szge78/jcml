package sk.concentra.jcml.persistence;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@JdbcRepository(dialect = Dialect.SQL_SERVER)
public interface ConfigMessageLogRepository extends CrudRepository<ConfigMessageLog, Double> {
    ConfigMessageLog getByRecoveryKey(Double recoveryKey);

    List<ConfigMessageLog> findAllByDateTimeBetweenOrderByRecoveryKeyAsc(LocalDateTime startDateTime, LocalDateTime endDateTime);

    Page<ConfigMessageLog> findAllByDateTimeBetweenOrderByRecoveryKeyAsc(
            LocalDateTime dateFrom, LocalDateTime dateTo, Pageable pageable);

    List<ConfigMessageLog> findAllByRecoveryKeyBetweenOrderByRecoveryKeyAsc(Double startRecoveryKey, Double endRecoveryKey);
} // class
