package argus.core;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.*;

import argus.config.ConfigLoader;

public class CommandSocketClient implements WebSocket.Listener {

	private final EventManager manager;
	private volatile WebSocket webSocket;
	private ScheduledExecutorService heartbeatScheduler;

	public CommandSocketClient(EventManager manager) {
		this.manager = manager;
	}

	public void connect() {
		String wsBase = ConfigLoader.getWebSocketUrl();
		// O id da sessão pode ter espaço/acento (ex.: sessões antigas com o nome completo):
		// sem codificar, URI.create lança exceção e o WebSocket (heartbeat) nunca conecta.
		String sessionSegment = URLEncoder.encode(SharedContext.session(), StandardCharsets.UTF_8)
				.replace("+", "%20");
		URI uri;
		try {
			uri = URI.create(wsBase + "/" + sessionSegment);
		} catch (IllegalArgumentException e) {
			System.err.println("[Argus] URL do WebSocket inválida: " + e.getMessage());
			return;
		}

		HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(uri, this).thenAccept(ws -> {
			this.webSocket = ws;
			System.out.println("[Argus] WebSocket conectado");
			startHeartbeat();

		}).exceptionally(ex -> {
			System.err.println("[Argus] Falha WS: " + ex.getMessage());
			ex.printStackTrace();
			return null;
		});
	}

	// Heartbeat enviado pelo próprio WebSocket já conectado (sem HTTP, sem libs externas)
	private void startHeartbeat() {
		heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

		heartbeatScheduler.scheduleAtFixedRate(() -> {
			try {
				WebSocket ws = webSocket;
				if (ws == null || ws.isOutputClosed()) {
					return;
				}

				String json = String.format(
						"{ \"type\": \"heartbeat\", \"student\": \"%s\", \"exam\": \"%s\", \"session\": \"%s\", \"source\": \"plugin\", \"timestamp\": %d }",
						SharedContext.student(), SharedContext.exam(), SharedContext.session(),
						System.currentTimeMillis());

				ws.sendText(json, true);
				System.out.println("[Argus] Heartbeat WS enviado: " + json);
			} catch (Exception e) {
				System.err.println("[Argus] Erro no heartbeat WS: " + e.getMessage());
			}
		}, 0, 10, TimeUnit.SECONDS);
	}

	@Override
	public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
		try {
			System.out.println("[Argus] Payload recebido: " + data);
			if (data.toString().contains("\"cmd\":\"shutdown\"")) {
				System.out.println("[Argus] Shutdown recebido do servidor");

				if (heartbeatScheduler != null) {
					heartbeatScheduler.shutdownNow();}

				ws.sendText("{\"cmd\":\"SHUTDOWN_OK\"}", true);
				ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
				Thread.sleep(200);
				manager.shutdown();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return WebSocket.Listener.super.onText(ws, data, last);
	}
}
