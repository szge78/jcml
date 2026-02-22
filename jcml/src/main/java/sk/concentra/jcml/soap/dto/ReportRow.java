package sk.concentra.jcml.soap.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Represents a single row in the getReport response.
 * Used by both the REST controller (serialized to JSON) and the SOAP service (serialized to XML via JAXB).
 */
@Serdeable
@XmlAccessorType(XmlAccessType.FIELD)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportRow {

    @XmlElement private java.math.BigDecimal recoveryKey;
    @XmlElement private String humanReadableTimestamp;
    @XmlElement private String dateTime;
    @XmlElement private String logOperation;
    @XmlElement private String tableName;
    @XmlElement private String userName;
    @XmlElement private String messageType;
    @XmlElement private String fullDescription;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public java.math.BigDecimal getRecoveryKey()             { return recoveryKey; }
    public void setRecoveryKey(java.math.BigDecimal v) { this.recoveryKey = v; }

    public String getHumanReadableTimestamp()          { return humanReadableTimestamp; }
    public void setHumanReadableTimestamp(String v)    { this.humanReadableTimestamp = v; }

    public String getDateTime()                { return dateTime; }
    public void setDateTime(String v)          { this.dateTime = v; }

    public String getLogOperation()            { return logOperation; }
    public void setLogOperation(String v)      { this.logOperation = v; }

    public String getTableName()               { return tableName; }
    public void setTableName(String v)         { this.tableName = v; }

    public String getUserName()                { return userName; }
    public void setUserName(String v)          { this.userName = v; }

    public String getMessageType()             { return messageType; }
    public void setMessageType(String v)       { this.messageType = v; }

    public String getFullDescription()         { return fullDescription; }
    public void setFullDescription(String v)   { this.fullDescription = v; }
}