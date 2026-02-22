package sk.concentra.jcml.persistence;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Transient;
import io.micronaut.serde.annotation.Serdeable;

import java.time.LocalDateTime;
import java.util.StringJoiner;

@MappedEntity(value = "Attribute")
@Serdeable
public class Attribute {
    @Id
    @MappedProperty(value = "AttributeID")
    private Integer attributeId;

    @MappedProperty(value = "EnterpriseName")
    private String enterpriseName;

    @MappedProperty(value = "AttributeDataType")
    private Integer attributeDataType;

    @Nullable
    @MappedProperty(value = "MinimumValue")
    private String minimumValue;

    @Nullable
    @MappedProperty(value = "MaximumValue")
    private String maximumValue;

    @Nullable
    @MappedProperty(value = "DefaultValue")
    private String defaultValue;

    @Nullable
    @MappedProperty(value = "Description")
    private String description;

    @MappedProperty(value = "ChangeStamp")
    private Integer changeStamp;

    @MappedProperty(value = "Deleted")
    private String deleted;

    @Nullable
    @MappedProperty(value = "DateTimeStamp")
    private LocalDateTime dateTimeStamp;

    public Attribute() {
    }

    public Attribute(Integer attributeId, String enterpriseName, Integer attributeDataType, @Nullable String minimumValue, @Nullable String maximumValue, @Nullable String defaultValue, @Nullable String description, Integer changeStamp, String deleted, @Nullable LocalDateTime dateTimeStamp) {
        this.attributeId = attributeId;
        this.enterpriseName = enterpriseName;
        this.attributeDataType = attributeDataType;
        this.minimumValue = minimumValue;
        this.maximumValue = maximumValue;
        this.defaultValue = defaultValue;
        this.description = description;
        this.changeStamp = changeStamp;
        this.deleted = deleted;
        this.dateTimeStamp = dateTimeStamp;
    }

    public Integer getAttributeId() {
        return attributeId;
    }

    public void setAttributeId(Integer attributeId) {
        this.attributeId = attributeId;
    }

    public String getEnterpriseName() {
        return enterpriseName;
    }

    public void setEnterpriseName(String enterpriseName) {
        this.enterpriseName = enterpriseName;
    }

    public Integer getAttributeDataType() {
        return attributeDataType;
    }

    public void setAttributeDataType(Integer attributeDataType) {
        this.attributeDataType = attributeDataType;
    }

    public @Nullable String getMinimumValue() {
        return minimumValue;
    }

    public void setMinimumValue(@Nullable String minimumValue) {
        this.minimumValue = minimumValue;
    }

    public @Nullable String getMaximumValue() {
        return maximumValue;
    }

    public void setMaximumValue(@Nullable String maximumValue) {
        this.maximumValue = maximumValue;
    }

    public @Nullable String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(@Nullable String defaultValue) {
        this.defaultValue = defaultValue;
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

    public String getDeleted() {
        return deleted;
    }

    public void setDeleted(String deleted) {
        this.deleted = deleted;
    }

    public @Nullable LocalDateTime getDateTimeStamp() {
        return dateTimeStamp;
    }

    public void setDateTimeStamp(@Nullable LocalDateTime dateTimeStamp) {
        this.dateTimeStamp = dateTimeStamp;
    }

    @Transient
    public AttributeDataType getAttributeDataTypeEnum() {
        return AttributeDataType.fromInt(attributeDataType);
    }

    public void setAttributeDataTypeEnum(AttributeDataType attributeDataTypeEnum) {
        this.attributeDataType = attributeDataTypeEnum != null ? attributeDataTypeEnum.getValue() : AttributeDataType.UNKNOWN.getValue();
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Attribute.class.getSimpleName() + "[", "]")
                .add("attributeId=" + attributeId)
                .add("enterpriseName='" + enterpriseName + "'")
                .add("attributeDataType=" + attributeDataType)
                .add("attributeDataTypeEnum=" + getAttributeDataTypeEnum())
                .add("minimumValue='" + minimumValue + "'")
                .add("maximumValue='" + maximumValue + "'")
                .add("defaultValue='" + defaultValue + "'")
                .add("description='" + description + "'")
                .add("changeStamp=" + changeStamp)
                .add("deleted='" + deleted + "'")
                .add("dateTimeStamp=" + dateTimeStamp)
                .toString();
    }
}
