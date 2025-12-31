package argus.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

public class CommandSocketClient implements WebSocket.Listener {

    private final EventManager manager;

    public CommandSocketClient(EventManager manager) {
        this.manager = manager;
    }

    public void connect(String student) {
        HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(
                URI.create("ws://localhost:8080/ws-command/" + student),
                this
            );
    }

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
    	try {
    		System.out.println("[Argus] Payload recebido: " + data);
            if (data.toString().contains("\"cmd\":\"shutdown\"")) {
                System.out.println("[Argus] Shutdown recebido do servidor");
                manager.shutdown();
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return WebSocket.Listener.super.onText(ws, data, last);
    }
}
