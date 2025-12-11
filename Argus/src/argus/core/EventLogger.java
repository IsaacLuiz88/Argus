package argus.core;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventLogger {

	private static final String LOG_DIR = System.getProperty("user.home") + "/ArgusLogs/";
	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

	public enum Format { LOG, JSON }

	private final Format format;
	private final String fileNamePrefix;
    private final String serverUrl;
    private final ExecutorService executor;
    private final HttpClient httpClient;

	public EventLogger(Format format, String fileNamePrefix, String serverUrl) {
        this.format = format;
        this.fileNamePrefix = fileNamePrefix != null ? fileNamePrefix : "events";
        this.serverUrl = serverUrl;
        this.executor = Executors.newSingleThreadExecutor();
        this.httpClient = HttpClient.newHttpClient();

        try {
            Files.createDirectories(Paths.get(LOG_DIR));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	public synchronized void logEvent(String json) {
		Path file = Paths.get(LOG_DIR + getFileName());
        try (FileWriter fw = new FileWriter(file.toFile(), true)) {
            if (format == Format.LOG) {
                fw.write("[" + LocalDateTime.now().format(FORMATTER) + "] " + json + "\n");
            } else {
                fw.write(json + ",\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 2. Envia para o servidor de forma assíncrona
        if (serverUrl != null && !serverUrl.isEmpty()) {
        	executor.submit(() -> sendToServer(json));
        }
	}

	private void sendToServer(String json) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        System.out.println("[Argus] Servidor respondeu: " + response.statusCode());
                    })
                    .exceptionally(ex -> {
                        System.err.println("[Argus] Erro na comunicação com o servidor: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception ex) {
            System.err.println("[Argus] Falha ao criar requisição: " + ex.getMessage());
        }
    }

	private String getFileName() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyyyy"));
        // Correção: Você não estava usando o 'fileNamePrefix'
        return fileNamePrefix + "_" + date + (format == Format.LOG ? ".log" : ".json");
    }

	public void shutdown() {
        executor.shutdown();
    }
}