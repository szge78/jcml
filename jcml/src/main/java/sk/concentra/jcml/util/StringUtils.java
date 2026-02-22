package sk.concentra.jcml.util;

import java.security.SecureRandom;

/**
 * Utility class for string operations.
 */
public final class StringUtils {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
    private static final int LENGTH = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private StringUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Generates a 20 character length String in a thread safe way.
     * The String consists of uppercase ANSI characters, underscores, and numbers only.
     *
     * @return a random 20-character string
     */
    public static String generateRandomString() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(index));
        }
        return sb.toString();
    }
}
