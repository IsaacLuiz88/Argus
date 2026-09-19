package argus.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ConfigLoader {
    private static final Properties prop = new Properties();
    private static boolean invalidUrlWarned = false;

    static {
        loadBundled();
        loadExternal();
    }

    // Valores padrão, embutidos no .jar (config/config.properties).
    private static void loadBundled() {
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

    // Arquivo externo, por máquina, que sobrescreve o que veio embutido no .jar:
    // trocar o endereço do servidor não exige recompilar/reexportar o plugin.
    // Padrão: ~/.argus/config.properties (ou o caminho dado em -Dargus.config=...).
    private static void loadExternal() {
        String custom = System.getProperty("argus.config");
        Path file = (custom != null && !custom.isBlank())
                ? Paths.get(custom)
                : Paths.get(System.getProperty("user.home"), ".argus", "config.properties");

        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties external = new Properties();
            external.load(reader);
            prop.putAll(external);
            System.out.println("[Argus] Configuração externa carregada de " + file);
        } catch (IOException e) {
            System.err.println("[Argus] Falha ao ler " + file + ": " + e.getMessage());
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

    // "server.url" (ex.: https://argus.onrender.com) resolve HTTP e WebSocket de uma vez:
    // http -> ws, https -> wss. Quando presente, vale mais que server.host/port/base/ws.
    private static String serverUrl() {
        String url = prop.getProperty("server.url", "").trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.isEmpty()) {
            return null;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (!invalidUrlWarned) {
                invalidUrlWarned = true;
                System.err.println("[Argus] server.url ignorado (precisa começar com http:// ou https://): " + url);
            }
            return null;
        }
        return url;
    }

    // =========================
    // 🔹 GETTERS
    // =========================

    public static String getHost() { return prop.getProperty("server.host", "localhost");}

    public static String getPort() { return prop.getProperty("server.port", "8080");}

    public static String getBaseUrl() {
        String url = serverUrl();
        if (url != null) {return url;}
        return resolve(prop.getProperty(
                "server.base", "http://localhost:8080"));
    }

    public static String getEventUrl() {
        String url = serverUrl();
        if (url != null) {return url + "/api/event";}
        return resolve(prop.getProperty(
                "server.event", "http://localhost:8080/api/event"));
    }

    public static String getSessionUrl() {
        String url = serverUrl();
        if (url != null) {return url + "/api/session/start";}
        return resolve(prop.getProperty(
                "server.session", "http://localhost:8080/api/session/start"));
    }

    public static String getWebSocketUrl() {
        String url = serverUrl();
        if (url != null) {return "ws" + url.substring(4) + "/ws-command";}
        return resolve(prop.getProperty(
                "server.ws", "ws://localhost:8080/ws-command"));
    }

    // Chave de acesso do servidor (security.clientKey, ver AccessFilter no ArgusServer).
    // Vazia = o servidor não exige. Melhor no ~/.argus/config.properties do que no jar.
    public static String getClientKey() {
        return prop.getProperty("security.clientKey", "").trim();
    }

    public static HttpRequest.Builder withClientKey(HttpRequest.Builder builder) {
        String key = getClientKey();
        return key.isEmpty() ? builder : builder.header("X-Argus-Key", key);
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
