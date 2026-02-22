package sk.concentra.jcml.persistence;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.time.LocalDateTime;
import java.util.StringJoiner;

@MappedEntity(value = "Campaign")
@Serdeable
public class Campaign {
    @Id
    @MappedProperty(value = "CampaignID")
    private Integer campaignId;

    @MappedProperty(value = "CampaignName")
    private String campaignName;

    @MappedProperty(value = "Enabled")
    private String enabled;

    @MappedProperty(value = "Deleted")
    private String deleted;

    @MappedProperty(value = "LinesPerAgent")
    private Float linesPerAgent;

    @MappedProperty(value = "MaxAttempts")
    private Integer maxAttempts;

    @Nullable
    @MappedProperty(value = "Description")
    private String description;

    @MappedProperty(value = "CampaignPurposeType")
    private Integer campaignPurposeType;

    @MappedProperty(value = "IPAMDEnabled")
    private String ipamdEnabled;

    @MappedProperty(value = "AMDTreatmentMode")
    private Integer amdTreatmentMode;

    @MappedProperty(value = "UseGMTFromRegionPrefix")
    private String useGmtFromRegionPrefix;

    @Nullable
    @MappedProperty(value = "ConfigParam")
    private String configParam;

    @MappedProperty(value = "DisableCPA")
    private String disableCpa;

    @Nullable
    @MappedProperty(value = "DSTLocation")
    private Integer dstLocation;

    @MappedProperty(value = "ChangeStamp")
    private Integer changeStamp;

    @Nullable
    @MappedProperty(value = "DateTimeStamp")
    private LocalDateTime dateTimeStamp;

    public Campaign() {
    }

    public Campaign(Integer campaignId, String campaignName, String enabled, String deleted, Float linesPerAgent, Integer maxAttempts, @Nullable String description, Integer campaignPurposeType, String ipamdEnabled, Integer amdTreatmentMode, String useGmtFromRegionPrefix, @Nullable String configParam, String disableCpa, @Nullable Integer dstLocation, Integer changeStamp, @Nullable LocalDateTime dateTimeStamp) {
        this.campaignId = campaignId;
        this.campaignName = campaignName;
        this.enabled = enabled;
        this.deleted = deleted;
        this.linesPerAgent = linesPerAgent;
        this.maxAttempts = maxAttempts;
        this.description = description;
        this.campaignPurposeType = campaignPurposeType;
        this.ipamdEnabled = ipamdEnabled;
        this.amdTreatmentMode = amdTreatmentMode;
        this.useGmtFromRegionPrefix = useGmtFromRegionPrefix;
        this.configParam = configParam;
        this.disableCpa = disableCpa;
        this.dstLocation = dstLocation;
        this.changeStamp = changeStamp;
        this.dateTimeStamp = dateTimeStamp;
    }

    public Integer getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Integer campaignId) {
        this.campaignId = campaignId;
    }

    public String getCampaignName() {
        return campaignName;
    }

    public void setCampaignName(String campaignName) {
        this.campaignName = campaignName;
    }

    public String getEnabled() {
        return enabled;
    }

    public void setEnabled(String enabled) {
        this.enabled = enabled;
    }

    public String getDeleted() {
        return deleted;
    }

    public void setDeleted(String deleted) {
        this.deleted = deleted;
    }

    public Float getLinesPerAgent() {
        return linesPerAgent;
    }

    public void setLinesPerAgent(Float linesPerAgent) {
        this.linesPerAgent = linesPerAgent;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    public Integer getCampaignPurposeType() {
        return campaignPurposeType;
    }

    public void setCampaignPurposeType(Integer campaignPurposeType) {
        this.campaignPurposeType = campaignPurposeType;
    }

    public String getIpamdEnabled() {
        return ipamdEnabled;
    }

    public void setIpamdEnabled(String ipamdEnabled) {
        this.ipamdEnabled = ipamdEnabled;
    }

    public Integer getAmdTreatmentMode() {
        return amdTreatmentMode;
    }

    public void setAmdTreatmentMode(Integer amdTreatmentMode) {
        this.amdTreatmentMode = amdTreatmentMode;
    }

    public String getUseGmtFromRegionPrefix() {
        return useGmtFromRegionPrefix;
    }

    public void setUseGmtFromRegionPrefix(String useGmtFromRegionPrefix) {
        this.useGmtFromRegionPrefix = useGmtFromRegionPrefix;
    }

    public @Nullable String getConfigParam() {
        return configParam;
    }

    public void setConfigParam(@Nullable String configParam) {
        this.configParam = configParam;
    }

    public String getDisableCpa() {
        return disableCpa;
    }

    public void setDisableCpa(String disableCpa) {
        this.disableCpa = disableCpa;
    }

    public @Nullable Integer getDstLocation() {
        return dstLocation;
    }

    public void setDstLocation(@Nullable Integer dstLocation) {
        this.dstLocation = dstLocation;
    }

    public Integer getChangeStamp() {
        return changeStamp;
    }

    public void setChangeStamp(Integer changeStamp) {
        this.changeStamp = changeStamp;
    }

    public @Nullable LocalDateTime getDateTimeStamp() {
        return dateTimeStamp;
    }

    public void setDateTimeStamp(@Nullable LocalDateTime dateTimeStamp) {
        this.dateTimeStamp = dateTimeStamp;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Campaign.class.getSimpleName() + "[", "]")
                .add("campaignId=" + campaignId)
                .add("campaignName='" + campaignName + "'")
                .add("enabled='" + enabled + "'")
                .add("deleted='" + deleted + "'")
                .add("linesPerAgent=" + linesPerAgent)
                .add("maxAttempts=" + maxAttempts)
                .add("description='" + description + "'")
                .add("campaignPurposeType=" + campaignPurposeType)
                .add("ipamdEnabled='" + ipamdEnabled + "'")
                .add("amdTreatmentMode=" + amdTreatmentMode)
                .add("useGmtFromRegionPrefix='" + useGmtFromRegionPrefix + "'")
                .add("configParam='" + configParam + "'")
                .add("disableCpa='" + disableCpa + "'")
                .add("dstLocation=" + dstLocation)
                .add("changeStamp=" + changeStamp)
                .add("dateTimeStamp=" + dateTimeStamp)
                .toString();
    }
}
