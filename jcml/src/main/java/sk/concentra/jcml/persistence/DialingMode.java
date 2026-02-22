package sk.concentra.jcml.persistence;

import io.micronaut.data.annotation.Id;
import io.micronaut.serde.annotation.Serdeable;

import java.util.StringJoiner;

/**
 * In-memory domain class representing outbound dialing modes.
 * Not backed by a database table — values are hardcoded in
 * {@link DialingModeRepository}.
 *
 * <p>{@code @Id} is kept so that {@code EntityPreloadAction.findIdField()}
 * can locate the key field via reflection for map indexing. It does NOT
 * imply any database mapping.</p>
 */
@Serdeable
public class DialingMode {

    @Id
    private Integer dialingMode;

    private String dialingModeText;

    public DialingMode() {}

    public DialingMode(Integer dialingMode, String dialingModeText) {
        this.dialingMode     = dialingMode;
        this.dialingModeText = dialingModeText;
    }

    public Integer getDialingMode() { return dialingMode; }
    public void setDialingMode(Integer dialingMode) { this.dialingMode = dialingMode; }

    public String getDialingModeText() { return dialingModeText; }
    public void setDialingModeText(String dialingModeText) { this.dialingModeText = dialingModeText; }

    @Override
    public String toString() {
        return new StringJoiner(", ", DialingMode.class.getSimpleName() + "[", "]")
                .add("dialingMode=" + dialingMode)
                .add("dialingModeText='" + dialingModeText + "'")
                .toString();
    }
}