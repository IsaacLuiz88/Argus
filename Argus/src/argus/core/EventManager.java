package argus.core;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import argus.config.ConfigLoader;

public class EventManager {

	private final Display display;
	private final String serverUrl;

	private final String studentName;
    private final String examName;
    private final String prefix;
    private final EventLogger logger;
	
	private long lastFocusLostTime = 0;
	private long lastFocusGainedTime = 0;
	private long lastMouseClickTime = 0;
	private Shell shell;
	
	private static final long FOCUS_DEBOUNCE_MS = 300;
	private static final long GAINED_GRACE_PERIOD_MS = 500;
	private static final long FOCUS_THROTTLE_MS = 20;
	
	public EventManager(Display display, Shell shell, String studentName, String examName) {
		this.display = display;
		this.serverUrl = ConfigLoader.getServerUrl();
		this.shell = shell;
		
		this.studentName = studentName;
		this.examName = examName;
		this.prefix = studentName + "_" + examName;
		
		this.logger = new EventLogger(EventLogger.Format.JSON, prefix, this.serverUrl);
	}
	
	public void startListener() {
        // Listener de teclado
        display.addFilter(org.eclipse.swt.SWT.KeyDown, e -> {
            String keyName;
            if (e.character != 0 && Character.isDefined(e.character) && !Character.isISOControl(e.character)) {
                keyName = String.valueOf(e.character);
            } else {
                keyName = KeyTranslator.translate(e.keyCode);
            }

            String json = String.format(
                "{\"type\":\"keyboard\",\"action\":\"%s\",\"code\":%d,\"time\":%d,\"student\":\"%s\",\"exam\":\"%s\"}",
                keyName, e.keyCode, System.currentTimeMillis(), studentName, examName);
            sendEventAsync(json);
            System.out.println(json);
        });
		
		// Listener de mouse
        display.addFilter(SWT.MouseDown, e -> {
        	lastMouseClickTime = System.currentTimeMillis();
        	String json = String.format(
                "{\"type\":\"mouse\",\"action\":\"MOUSE\",\"x\":%d,\"y\":%d,\"time\":%d,\"student\":\"%s\",\"exam\":\"%s\"}",
                e.x, e.y, lastMouseClickTime, studentName, examName);
            sendEventAsync(json);
            System.out.println(json);
        });
        
     // Listener de PERDA de foco (Alt+Tab, clicar em outra janela)
        shell.addListener(SWT.Deactivate, e -> {
            long now = System.currentTimeMillis();
            
            if (now - lastFocusGainedTime < GAINED_GRACE_PERIOD_MS) return;
            if (now - lastFocusGainedTime > FOCUS_DEBOUNCE_MS) {
                if (now - lastFocusLostTime > FOCUS_THROTTLE_MS) {
                    lastFocusLostTime = now;
                    sendFocusLost();
                }
            }
        });

        
        // Listener de GANHO de foco (voltar para a janela)
        shell.addListener(SWT.Activate, e -> {
            long now = System.currentTimeMillis();

            if (now - lastFocusLostTime > FOCUS_DEBOUNCE_MS) {
                if (now - lastFocusGainedTime > FOCUS_THROTTLE_MS) {
                    lastFocusGainedTime = now;
                    sendFocusGained();
                }
            }
        });

    }
	
	private void sendEventAsync(String json) {
		logger.logEvent(json);
    }
	
	private void sendFocusLost() {
	    String json = String.format(
	        "{\"type\":\"focus\",\"action\":\"FOCUS_LOST\",\"time\":%d,\"student\":\"%s\",\"exam\":\"%s\"}",
	        System.currentTimeMillis(), studentName, examName);
	    sendEventAsync(json);
	    System.out.println(json);
	}
	
	private void sendFocusGained() {
	    String json = String.format(
	        "{\"type\":\"focus\",\"action\":\"FOCUS_GAINED\",\"time\":%d,\"student\":\"%s\",\"exam\":\"%s\"}",
	        System.currentTimeMillis(), studentName, examName);
	    sendEventAsync(json);
	    System.out.println(json);
	}
}
        /*new Thread(() -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(serverUrl))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                        .build();

                logger.logEvent(json);
                
                client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            System.out.println("Servidor respondeu: " + response.statusCode());
                        })
                        .exceptionally(ex -> {
                            System.err.println("Erro na comunicação com o servidor: " + ex.getMessage());
                            return null;
                        });
            } catch (Exception ex) {
                System.err.println("Falha ao enviar evento: " + ex.getMessage());
            }
        }).start();*/

