package argus.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    private static final Properties prop = new Properties();

    static {
        try (InputStream input =
                ConfigLoader.class.getResourceAsStream("/config/config.properties")) {
            if (input == null) {
                System.err.println("[Argus] config.properties não encontrado!");
            }else{
            	prop.load(input);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // =========================
    // 🔹 RESOLVE ${variavel}
    // =========================
    private static String resolve(String value) {

        if (value == null) {return null;}

        while (value.contains("${")) {

            int start = value.indexOf("${");
            int end = value.indexOf("}", start);

            if (end == -1) {break;}

            String key = value.substring(start + 2, end);
            String replacement = prop.getProperty(key, "");

            value = value.substring(0, start) + replacement + value.substring(end + 1);
        }
        return value;
    }

    // =========================
    // 🔹 GETTERS
    // =========================

    public static String getHost() { return prop.getProperty("server.host", "localhost");}

    public static String getPort() { return prop.getProperty("server.port", "8080");}

    public static String getBaseUrl() {
        return resolve(prop.getProperty(
                "server.base", "http://localhost:8080"));
    }

    public static String getEventUrl() {
        return resolve(prop.getProperty(
                "server.event", "http://localhost:8080/api/event"));
    }

    public static String getSessionUrl() {
        return resolve(prop.getProperty(
                "server.session", "http://localhost:8080/api/session/start"));
    }

    public static String getWebSocketUrl() {
        return resolve(prop.getProperty(
                "server.ws", "ws://localhost:8080/ws-command"));
    }

    public static boolean isArgusVisionEnabled() {
        return Boolean.parseBoolean(prop.getProperty("argusvision.enabled", "false"));
    }

    public static String getArgusVisionJar() {
        return prop.getProperty("argusvision.jar", "");
    }

    public static String getArgusVisionJavaHome() {
        return prop.getProperty("argusvision.javaHome", "");
    }

    public static String getArgusVisionLibraryPath() {
        return prop.getProperty("argusvision.libraryPath", "");
    }
}
