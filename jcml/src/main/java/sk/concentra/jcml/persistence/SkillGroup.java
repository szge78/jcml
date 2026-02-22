package sk.concentra.jcml.persistence;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.StringJoiner;

@MappedEntity(value = "Skill_Group")
@Serdeable
public class SkillGroup {
    @Id
    @MappedProperty(value = "SkillTargetID")
    private Integer skillTargetId;

    @Nullable
    @MappedProperty(value = "PrecisionQueueID")
    private Integer precisionQueueId;

    @Nullable
    @MappedProperty(value = "EnterpriseName")
    private String enterpriseName;

    @MappedProperty(value = "PeripheralNumber")
    private Integer peripheralNumber;

    @MappedProperty(value = "PeripheralName")
    private String peripheralName;

    @Nullable
    @MappedProperty(value = "BaseSkillTargetID")
    private Integer baseSkillTargetId;

    @Nullable
    @MappedProperty(value = "Description")
    private String description;

    @MappedProperty(value = "Deleted")
    private String deleted;

    @MappedProperty(value = "ChangeStamp")
    private Integer changeStamp;

    public SkillGroup() {
    }

    public SkillGroup(Integer skillTargetId, @Nullable Integer precisionQueueId, @Nullable String enterpriseName, Integer peripheralNumber, String peripheralName, @Nullable Integer baseSkillTargetId, @Nullable String description, String deleted, Integer changeStamp) {
        this.skillTargetId = skillTargetId;
        this.precisionQueueId = precisionQueueId;
        this.enterpriseName = enterpriseName;
        this.peripheralNumber = peripheralNumber;
        this.peripheralName = peripheralName;
        this.baseSkillTargetId = baseSkillTargetId;
        this.description = description;
        this.deleted = deleted;
        this.changeStamp = changeStamp;
    }

    public Integer getSkillTargetId() {
        return skillTargetId;
    }

    public void setSkillTargetId(Integer skillTargetId) {
        this.skillTargetId = skillTargetId;
    }

    public @Nullable Integer getPrecisionQueueId() {
        return precisionQueueId;
    }

    public void setPrecisionQueueId(@Nullable Integer precisionQueueId) {
        this.precisionQueueId = precisionQueueId;
    }

    public @Nullable String getEnterpriseName() {
        return enterpriseName;
    }

    public void setEnterpriseName(@Nullable String enterpriseName) {
        this.enterpriseName = enterpriseName;
    }

    public Integer getPeripheralNumber() {
        return peripheralNumber;
    }

    public void setPeripheralNumber(Integer peripheralNumber) {
        this.peripheralNumber = peripheralNumber;
    }

    public String getPeripheralName() {
        return peripheralName;
    }

    public void setPeripheralName(String peripheralName) {
        this.peripheralName = peripheralName;
    }

    public @Nullable Integer getBaseSkillTargetId() {
        return baseSkillTargetId;
    }

    public void setBaseSkillTargetId(@Nullable Integer baseSkillTargetId) {
        this.baseSkillTargetId = baseSkillTargetId;
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

    public Integer getChangeStamp() {
        return changeStamp;
    }

    public void setChangeStamp(Integer changeStamp) {
        this.changeStamp = changeStamp;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SkillGroup.class.getSimpleName() + "[", "]")
                .add("skillTargetId=" + skillTargetId)
                .add("precisionQueueId=" + precisionQueueId)
                .add("enterpriseName='" + enterpriseName + "'")
                .add("peripheralNumber=" + peripheralNumber)
                .add("peripheralName='" + peripheralName + "'")
                .add("baseSkillTargetId=" + baseSkillTargetId)
                .add("description='" + description + "'")
                .add("deleted='" + deleted + "'")
                .add("changeStamp=" + changeStamp)
                .toString();
    }
}
