package argus.core;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import argus.config.ConfigLoader;

public class CommandSocketClient implements WebSocket.Listener {

	// Espera entre tentativas de reconexão: 1, 2, 4, 8 e depois 15 s (teto).
	private static final long MAX_RECONNECT_DELAY_SEC = 15;

	private final Runnable onShutdown;
	private final HttpClient httpClient = HttpClient.newHttpClient();
	private volatile WebSocket webSocket;
	private ScheduledExecutorService heartbeatScheduler;

	private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "Argus-WS-Reconnect");
		t.setDaemon(true);
		return t;
	});
	private final AtomicBoolean reconnectPending = new AtomicBoolean(false);
	private volatile boolean stopped = false;
	private volatile int attempt = 0;

	public CommandSocketClient(EventManager manager) {
		this(manager::shutdown);
	}

	// O que fazer quando o servidor manda encerrar (usado também em testes, sem Eclipse).
	public CommandSocketClient(Runnable onShutdown) {
		this.onShutdown = onShutdown;
	}

	public void connect() {
		openSocket();
	}

	// Para de vez: sem heartbeat e sem novas tentativas de reconexão.
	public void stop() {
		stopped = true;
		synchronized (this) {
			if (heartbeatScheduler != null) {
				heartbeatScheduler.shutdownNow();}
		}
		reconnectScheduler.shutdownNow();
	}

	private URI buildUri() {
		String wsBase = ConfigLoader.getWebSocketUrl();
		// O id da sessão pode ter espaço/acento (ex.: sessões antigas com o nome completo):
		// sem codificar, URI.create lança exceção e o WebSocket (heartbeat) nunca conecta.
		String sessionSegment = URLEncoder.encode(SharedContext.session(), StandardCharsets.UTF_8)
				.replace("+", "%20");
		try {
			return URI.create(wsBase + "/" + sessionSegment);
		} catch (IllegalArgumentException e) {
			System.err.println("[Argus] URL do WebSocket inválida: " + e.getMessage());
			return null;
		}
	}

	private void openSocket() {
		if (stopped) {
			return;
		}
		URI uri = buildUri();
		if (uri == null) {
			return; // URL inválida não melhora tentando de novo
		}

		java.net.http.WebSocket.Builder builder = httpClient.newWebSocketBuilder();
		String clientKey = ConfigLoader.getClientKey();
		if (!clientKey.isEmpty()) {
			builder.header("X-Argus-Key", clientKey);
		}

		builder.buildAsync(uri, this).thenAccept(ws -> {
			if (stopped) {
				ws.abort();
				return;
			}
			this.webSocket = ws;
			this.attempt = 0;
			System.out.println("[Argus] WebSocket conectado");
			startHeartbeatOnce();

		}).exceptionally(ex -> {
			Throwable cause = (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
			if (cause instanceof java.net.http.WebSocketHandshakeException handshake) {
				int status = handshake.getResponse().statusCode();
				if (status == 401 || status == 403) {
					// Chave recusada: tentar de novo não resolve, só gera ruído.
					System.err.println("[Argus] Servidor recusou o WebSocket (HTTP " + status
							+ "): verifique security.clientKey. Sem novas tentativas.");
					return null;
				}
			}
			System.err.println("[Argus] Falha WS: " + ex.getMessage());
			scheduleReconnect();
			return null;
		});
	}

	// Servidor reiniciando (ex.: hospedagem que "acorda" devagar) ou rede instável:
	// tenta de novo em vez de ficar sem heartbeat até o aluno reiniciar o plugin.
	private void scheduleReconnect() {
		if (stopped || !reconnectPending.compareAndSet(false, true)) {
			return;
		}
		long delay = Math.min(MAX_RECONNECT_DELAY_SEC, 1L << Math.min(attempt, 4));
		attempt++;
		System.out.println("[Argus] WebSocket indisponível; nova tentativa em " + delay + "s");
		try {
			reconnectScheduler.schedule(() -> {
				reconnectPending.set(false);
				openSocket();
			}, delay, TimeUnit.SECONDS);
		} catch (RejectedExecutionException e) {
			reconnectPending.set(false); // já foi parado
		}
	}

	// Heartbeat enviado pelo próprio WebSocket já conectado (sem HTTP, sem libs externas).
	// Roda uma vez só: depois de reconectar ele passa a usar o novo socket automaticamente.
	private synchronized void startHeartbeatOnce() {
		if (heartbeatScheduler != null || stopped) {
			return;
		}
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
	public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
		if (ws == webSocket) {
			System.out.println("[Argus] WebSocket fechado (" + statusCode + ")");
			scheduleReconnect();
		}
		return null;
	}

	@Override
	public void onError(WebSocket ws, Throwable error) {
		System.err.println("[Argus] Erro no WebSocket: " + error.getMessage());
		if (ws == webSocket) {
			scheduleReconnect();
		}
	}

	@Override
	public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
		try {
			System.out.println("[Argus] Payload recebido: " + data);
			if (data.toString().contains("\"cmd\":\"shutdown\"")) {
				System.out.println("[Argus] Shutdown recebido do servidor");

				stop(); // encerramento ordenado: não reconectar

				ws.sendText("{\"cmd\":\"SHUTDOWN_OK\"}", true);
				ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
				Thread.sleep(200);
				onShutdown.run();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return WebSocket.Listener.super.onText(ws, data, last);
	}
}
