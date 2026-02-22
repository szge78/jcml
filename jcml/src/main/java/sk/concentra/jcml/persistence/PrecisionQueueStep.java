package sk.concentra.jcml.persistence;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.StringJoiner;

@MappedEntity(value = "Precision_Queue_Step")
@Serdeable
public class PrecisionQueueStep {
    @Id
    @MappedProperty(value = "PrecisionQueueStepID")
    private Integer precisionQueueStepId;

    @Nullable
    @MappedProperty(value = "PrecisionQueueID")
    private Integer precisionQueueId;

    @MappedProperty(value = "StepOrder")
    private Integer stepOrder;

    @MappedProperty(value = "WaitTime")
    private Integer waitTime;

    @Nullable
    @MappedProperty(value = "ConsiderIf")
    private String considerIf;

    @MappedProperty(value = "NextStep")
    private String nextStep;

    @Nullable
    @MappedProperty(value = "Description")
    private String description;

    public PrecisionQueueStep() {
    }

    public PrecisionQueueStep(Integer precisionQueueStepId, @Nullable Integer precisionQueueId, Integer stepOrder, Integer waitTime, @Nullable String considerIf, String nextStep, @Nullable String description) {
        this.precisionQueueStepId = precisionQueueStepId;
        this.precisionQueueId = precisionQueueId;
        this.stepOrder = stepOrder;
        this.waitTime = waitTime;
        this.considerIf = considerIf;
        this.nextStep = nextStep;
        this.description = description;
    }

    public Integer getPrecisionQueueStepId() {
        return precisionQueueStepId;
    }

    public void setPrecisionQueueStepId(Integer precisionQueueStepId) {
        this.precisionQueueStepId = precisionQueueStepId;
    }

    public @Nullable Integer getPrecisionQueueId() {
        return precisionQueueId;
    }

    public void setPrecisionQueueId(@Nullable Integer precisionQueueId) {
        this.precisionQueueId = precisionQueueId;
    }

    public Integer getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(Integer stepOrder) {
        this.stepOrder = stepOrder;
    }

    public Integer getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(Integer waitTime) {
        this.waitTime = waitTime;
    }

    public @Nullable String getConsiderIf() {
        return considerIf;
    }

    public void setConsiderIf(@Nullable String considerIf) {
        this.considerIf = considerIf;
    }

    public String getNextStep() {
        return nextStep;
    }

    public void setNextStep(String nextStep) {
        this.nextStep = nextStep;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", PrecisionQueueStep.class.getSimpleName() + "[", "]")
                .add("precisionQueueStepId=" + precisionQueueStepId)
                .add("precisionQueueId=" + precisionQueueId)
                .add("stepOrder=" + stepOrder)
                .add("waitTime=" + waitTime)
                .add("considerIf='" + considerIf + "'")
                .add("nextStep='" + nextStep + "'")
                .add("description='" + description + "'")
                .toString();
    }
}
