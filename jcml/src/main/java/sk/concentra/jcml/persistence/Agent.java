package sk.concentra.jcml.persistence;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.StringJoiner;

@MappedEntity(value = "Agent")
@Serdeable
public class Agent {
    @Id
    @MappedProperty(value = "SkillTargetID")
    private Integer skillTargetId;

    @MappedProperty(value = "PersonID")
    private Integer personId;

    @Nullable
    @MappedProperty(value = "EnterpriseName")
    private String enterpriseName;

    @MappedProperty(value = "PeripheralNumber")
    private String peripheralNumber;

    @Nullable
    @MappedProperty(value = "Description")
    private String description;

    @MappedProperty(value = "Deleted")
    private String deleted;

    @MappedProperty(value = "SupervisorAgent")
    private String supervisorAgent;

    @Nullable
    @MappedProperty(value = "ChangeStamp")
    private Integer changeStamp;

    public Agent() {
    }

    public Agent(Integer skillTargetId, Integer personId, @Nullable String enterpriseName, String peripheralNumber, @Nullable String description, String deleted, String supervisorAgent, Integer changeStamp) {
        this.skillTargetId = skillTargetId;
        this.personId = personId;
        this.enterpriseName = enterpriseName;
        this.peripheralNumber = peripheralNumber;
        this.description = description;
        this.deleted = deleted;
        this.supervisorAgent = supervisorAgent;
        this.changeStamp = changeStamp;
    }

    public Integer getSkillTargetId() {
        return skillTargetId;
    }

    public void setSkillTargetId(Integer skillTargetId) {
        this.skillTargetId = skillTargetId;
    }

    public Integer getPersonId() {
        return personId;
    }

    public void setPersonId(Integer personId) {
        this.personId = personId;
    }

    public @Nullable String getEnterpriseName() {
        return enterpriseName;
    }

    public void setEnterpriseName(@Nullable String enterpriseName) {
        this.enterpriseName = enterpriseName;
    }

    public String getPeripheralNumber() {
        return peripheralNumber;
    }

    public void setPeripheralNumber(String peripheralNumber) {
        this.peripheralNumber = peripheralNumber;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    public String getDeleted() {
        return deleted;
    }

    public void setDeleted(String deleted) {
        this.deleted = deleted;
    }

    public String getSupervisorAgent() {
        return supervisorAgent;
    }

    public void setSupervisorAgent(String supervisorAgent) {
        this.supervisorAgent = supervisorAgent;
    }

    public @Nullable Integer getChangeStamp() {
        return changeStamp;
    }

    public void setChangeStamp(@Nullable Integer changeStamp) {
        this.changeStamp = changeStamp;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Agent.class.getSimpleName() + "[", "]")
                .add("skillTargetId=" + skillTargetId)
                .add("personId=" + personId)
                .add("enterpriseName='" + enterpriseName + "'")
                .add("peripheralNumber='" + peripheralNumber + "'")
                .add("description='" + description + "'")
                .add("deleted='" + deleted + "'")
                .add("supervisorAgent='" + supervisorAgent + "'")
                .add("changeStamp=" + changeStamp)
                .toString();
    }
}
