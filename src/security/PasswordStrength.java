package security;

import java.util.regex.Pattern;

/**
 * Evaluates password strength against NIST SP 800-63B guidelines.
 * Returns a numeric score (0-5) and a descriptive Level.
 */
public class PasswordStrength {

    public enum Level {
        VERY_WEAK("Very Weak"),
        WEAK("Weak"),
        FAIR("Fair"),
        STRONG("Strong"),
        VERY_STRONG("Very Strong");

        public final String label;
        Level(String label) { this.label = label; }
    }

    private static final Pattern HAS_UPPER   = Pattern.compile("[A-Z]");
    private static final Pattern HAS_LOWER   = Pattern.compile("[a-z]");
    private static final Pattern HAS_DIGIT   = Pattern.compile("[0-9]");
    private static final Pattern HAS_SPECIAL = Pattern.compile("[^A-Za-z0-9]");
    private static final String[] COMMON_PATTERNS = {
        "password", "123456", "qwerty", "abc123", "letmein",
        "111111", "iloveyou", "admin", "welcome", "monkey"
    };

    public static int score(String pw) {
        if (pw == null || pw.isEmpty()) return 0;
        int score = 0;

        if (pw.length() >= 8)  score++;
        if (pw.length() >= 12) score++;
        if (pw.length() >= 16) score++;
        if (HAS_UPPER.matcher(pw).find())   score++;
        if (HAS_LOWER.matcher(pw).find())   score++;
        if (HAS_DIGIT.matcher(pw).find())   score++;
        if (HAS_SPECIAL.matcher(pw).find()) score++;

        // Penalise trivially guessable passwords
        String lower = pw.toLowerCase();
        for (String bad : COMMON_PATTERNS) {
            if (lower.contains(bad)) { score -= 2; break; }
        }

        return Math.max(0, Math.min(5, score));
    }

    public static Level evaluate(String pw) {
        switch (score(pw)) {
            case 0: case 1: return Level.VERY_WEAK;
            case 2:         return Level.WEAK;
            case 3:         return Level.FAIR;
            case 4:         return Level.STRONG;
            default:        return Level.VERY_STRONG;
        }
    }
}
