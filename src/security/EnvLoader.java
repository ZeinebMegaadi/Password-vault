package security;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class EnvLoader {

    private static final Map<String, String> vars = new HashMap<>();

    static {
        try (BufferedReader br = new BufferedReader(new FileReader(".env"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                vars.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        } catch (Exception ignored) {}
    }

    public static String get(String key) {
        return vars.getOrDefault(key, System.getenv(key));
    }
}
