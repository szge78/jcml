package sk.concentra.jcml.persistence;

public enum AttributeDataType {
    UNKNOWN(0),
    INTEGER(1),
    STRING(2),
    BOOLEAN(3),
    SKILL(4);

    private final int value;

    AttributeDataType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static AttributeDataType fromInt(Integer value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (AttributeDataType type : AttributeDataType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
