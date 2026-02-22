package sk.concentra.jcml.util

import spock.lang.Specification

class StringUtilsSpec extends Specification {

    def "should generate a random string of length 20"() {
        when:
        String result = StringUtils.generateRandomString()

        then:
        result.length() == 20
    }

    def "should only contain uppercase letters, numbers, and underscores"() {
        given:
        String allowedCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_"

        when:
        String result = StringUtils.generateRandomString()

        then:
        result.chars().allMatch { ch -> allowedCharacters.indexOf(ch as int) != -1 }
    }

    def "should generate unique strings"() {
        when:
        Set<String> strings = (1..1000).collect { StringUtils.generateRandomString() } as Set

        then:
        strings.size() == 1000
    }
}
