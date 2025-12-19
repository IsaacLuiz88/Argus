package argus.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {

	public static String getServerUrl() {
        try (InputStream input = ConfigLoader.class.getResourceAsStream("/config/config.properties")) {
        	if (input == null) {
                System.err.println("Arquivo config.properties não encontrado!");
                return "http://localhost:8080/api/event"; // fallback
            }

        	Properties prop = new Properties();
            prop.load(input);
            return prop.getProperty("server.url", "http://localhost:8080/api/event");
        } catch (IOException ex) {
            ex.printStackTrace();
            return "http://localhost:8080/api/event";
        } 
	}
}
