package sk.concentra.jcml.persistence;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.StringJoiner;


@MappedEntity(value = "Config_Message_Log")
@Serdeable
public class ConfigMessageLog {
    @Id
    @MappedProperty(value = "RecoveryKey")
    private Double recoveryKey;

    @Nullable
    @MappedProperty(value = "LogOperation")
    private String logOperation;

    @Nullable
    @MappedProperty(value = "TableName")
    private String tableName;

    @MappedProperty(value = "DateTime")
    private LocalDateTime dateTime;

    @Nullable
    @MappedProperty(value = "ConfigMessage")
    private byte[] configMessage;

    public String toString() {
        return new StringJoiner(", ", ConfigMessageLog.class.getSimpleName() + "[", "]")
                .add("recoveryKey=" + recoveryKey)
                .add("logOperation='" + logOperation + "'")
                .add("tableName='" + tableName + "'")
                .add("dateTime=" + dateTime)
                .add("configMessage=" + Arrays.toString(configMessage))
                .toString();
    }

    public ConfigMessageLog(Double recoveryKey, @Nullable String logOperation, @Nullable String tableName, LocalDateTime dateTime, @Nullable byte[] configMessage) {
        this.recoveryKey = recoveryKey;
        this.logOperation = logOperation;
        this.tableName = tableName;
        this.dateTime = dateTime;
        this.configMessage = configMessage;
    }

    public ConfigMessageLog() {
    }

    public Double getRecoveryKey() {
        return recoveryKey;
    }

    public void setRecoveryKey(Double recoveryKey) {
        this.recoveryKey = recoveryKey;
    }

    public @Nullable String getLogOperation() {
        return logOperation;
    }

    public void setLogOperation(@Nullable String logOperation) {
        this.logOperation = logOperation;
    }

    public @Nullable String getTableName() {
        return tableName;
    }

    public void setTableName(@Nullable String tableName) {
        this.tableName = tableName;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public @Nullable byte[] getConfigMessage() {
        return configMessage;
    }

    public void setConfigMessage(@Nullable byte[] configMessage) {
        this.configMessage = configMessage;
    }
}
