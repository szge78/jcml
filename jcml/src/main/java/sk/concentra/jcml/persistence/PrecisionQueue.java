package sk.concentra.jcml.persistence;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.StringJoiner;

@MappedEntity(value = "Precision_Queue")
@Serdeable
public class PrecisionQueue {
    @Id
    @MappedProperty(value = "PrecisionQueueID")
    private Integer precisionQueueId;

    @Nullable
    @MappedProperty(value = "EnterpriseName")
    private String enterpriseName;

    @Nullable
    @MappedProperty(value = "Description")
    private String description;

    @MappedProperty(value = "Deleted")
    private String deleted;

    @MappedProperty(value = "ChangeStamp")
    private Integer changeStamp;

    public PrecisionQueue() {
    }

    public PrecisionQueue(Integer precisionQueueId, @Nullable String enterpriseName, @Nullable String description, String deleted, Integer changeStamp) {
        this.precisionQueueId = precisionQueueId;
        this.enterpriseName = enterpriseName;
        this.description = description;
        this.deleted = deleted;
        this.changeStamp = changeStamp;
    }

    public Integer getPrecisionQueueId() {
        return precisionQueueId;
    }

    public void setPrecisionQueueId(Integer precisionQueueId) {
        this.precisionQueueId = precisionQueueId;
    }

    public @Nullable String getEnterpriseName() {
        return enterpriseName;
    }

    public void setEnterpriseName(@Nullable String enterpriseName) {
        this.enterpriseName = enterpriseName;
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
        return new StringJoiner(", ", PrecisionQueue.class.getSimpleName() + "[", "]")
                .add("precisionQueueId=" + precisionQueueId)
                .add("enterpriseName='" + enterpriseName + "'")
                .add("description='" + description + "'")
                .add("deleted='" + deleted + "'")
                .add("changeStamp=" + changeStamp)
                .toString();
    }
}
