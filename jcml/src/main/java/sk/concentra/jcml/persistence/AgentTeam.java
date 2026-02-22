package sk.concentra.jcml.persistence;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.StringJoiner;

@MappedEntity(value = "Agent_Team")
@Serdeable
public class AgentTeam {
    @Id
    @MappedProperty(value = "AgentTeamID")
    private Integer agentTeamId;

    @MappedProperty(value = "EnterpriseName")
    private String enterpriseName;

    @Nullable
    @MappedProperty(value = "Description")
    private String description;

    @MappedProperty(value = "ChangeStamp")
    private Integer changeStamp;

    public AgentTeam() {
    }

    public AgentTeam(Integer agentTeamId, String enterpriseName, @Nullable String description, Integer changeStamp) {
        this.agentTeamId = agentTeamId;
        this.enterpriseName = enterpriseName;
        this.description = description;
        this.changeStamp = changeStamp;
    }

    public Integer getAgentTeamId() {
        return agentTeamId;
    }

    public void setAgentTeamId(Integer agentTeamId) {
        this.agentTeamId = agentTeamId;
    }

    public String getEnterpriseName() {
        return enterpriseName;
    }

    public void setEnterpriseName(String enterpriseName) {
        this.enterpriseName = enterpriseName;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    public Integer getChangeStamp() {
        return changeStamp;
    }

    public void setChangeStamp(Integer changeStamp) {
        this.changeStamp = changeStamp;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", AgentTeam.class.getSimpleName() + "[", "]")
                .add("agentTeamId=" + agentTeamId)
                .add("enterpriseName='" + enterpriseName + "'")
                .add("description='" + description + "'")
                .add("changeStamp=" + changeStamp)
                .toString();
    }
}
