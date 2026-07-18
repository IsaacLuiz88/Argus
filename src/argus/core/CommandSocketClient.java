package argus.core;

import java.net.URI;
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
		URI uri = URI.create(wsBase + "/" + SharedContext.session());

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
