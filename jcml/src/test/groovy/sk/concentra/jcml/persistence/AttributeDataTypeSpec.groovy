package sk.concentra.jcml.persistence

import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Unroll

@Ignore
class AttributeDataTypeSpec extends Specification {

    @Unroll
    def "should map attributeDataType #value to #expectedEnum"() {
        given:
        def attribute = new Attribute()
        attribute.attributeDataType = value

        expect:
        attribute.getAttributeDataTypeEnum() == expectedEnum

        where:
        value | expectedEnum
        0     | AttributeDataType.UNKNOWN
        1     | AttributeDataType.INTEGER
        2     | AttributeDataType.STRING
        3     | AttributeDataType.BOOLEAN
        4     | AttributeDataType.SKILL
        5     | AttributeDataType.UNKNOWN
        -1    | AttributeDataType.UNKNOWN
        null  | AttributeDataType.UNKNOWN
    }

    @Unroll
    def "should map enum #enumValue to int value #expectedInt"() {
        given:
        def attribute = new Attribute()
        attribute.setAttributeDataTypeEnum(enumValue)

        expect:
        attribute.attributeDataType == expectedInt

        where:
        enumValue                  | expectedInt
        AttributeDataType.UNKNOWN  | 0
        AttributeDataType.INTEGER  | 1
        AttributeDataType.STRING   | 2
        AttributeDataType.BOOLEAN  | 3
        AttributeDataType.SKILL    | 4
        null                       | 0
    }
}
