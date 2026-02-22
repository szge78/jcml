package sk.concentra.jcml.persistence;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.SQL_SERVER)
public interface CampaignRepository extends CrudRepository<Campaign, Integer> {
    Campaign getByCampaignId(Integer campaignId);
    Campaign getByCampaignName(String campaignName);
}
