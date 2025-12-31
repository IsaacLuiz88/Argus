package argus.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import argus.config.ConfigLoader;

public class EventManager {

	private final Display display;
	private final String serverUrl;
	private volatile boolean running = true;
    private ScheduledExecutorService scheduler;
    private EventLogger eventLogger;

	private final String studentName;
	private final String examName;
	private final String prefix;
	private final EventLogger logger;

	private long lastFocusLostTime = 0;
	private long lastFocusGainedTime = 0;
	private Shell shell;

	private static final long FOCUS_DEBOUNCE_MS = 300;
	private long lastKeyTime = System.currentTimeMillis();
	private static final long INACTIVITY_LIMIT = 3 * 60_000; // 3 minutos

	private int typedChars = 0;
	private long lastCharWindow = System.currentTimeMillis();
	private static final int PASTE_THRESHOLD = 50;
	private static final long WINDOW_MS = 500;

	private ScheduledExecutorService heartbeatScheduler;

	public EventManager(Display display, Shell shell) {
		this.display = display;
		this.serverUrl = ConfigLoader.getServerUrl();
		this.shell = shell;

		this.studentName = SharedContext.student();
		this.examName = SharedContext.exam();
		this.prefix = SharedContext.session();

		this.logger = new EventLogger(EventLogger.Format.JSON, prefix, this.serverUrl);
	}

	public void startListener() {
     // Listener de PERDA de foco da IDE (Alt+Tab, outra janela, minimizar)
        shell.addListener(SWT.Deactivate, e -> {
            long now = System.currentTimeMillis();

            if (now - lastFocusLostTime < FOCUS_DEBOUNCE_MS) return;
			lastFocusLostTime = now;
			sendFocusLost();
        });

        // Listener de GANHO de foco da IDE (volta para a aplicação)
        shell.addListener(SWT.Activate, e -> {
            long now = System.currentTimeMillis();

            if (now - lastFocusGainedTime < FOCUS_DEBOUNCE_MS) return;
            lastFocusGainedTime = now;
            sendFocusGained();
        });

        display.addFilter(SWT.KeyDown, e -> {
        	long now = System.currentTimeMillis();
        	lastKeyTime = now;

        	boolean alt  = (e.stateMask & SWT.ALT)  != 0;
            boolean ctrl = (e.stateMask & SWT.CTRL) != 0;

            // ALT + TAB
            if (alt && e.keyCode == SWT.TAB) {
                sendSimpleEvent("keyboard", "ALT_TAB");
            }

            // CTRL + C
            if (ctrl && (e.keyCode == 'c' || e.keyCode == 'C')) {
                sendSimpleEvent("keyboard", "CTRL_C");
            }

            // CTRL + V
         // CTRL + V (SWT consome o keyCode, então usamos character)
            if (ctrl && (e.character == 22 || e.character == 'v' || e.character == 'V')) {
                sendSimpleEvent("keyboard", "CTRL_V");
            }


            // 6.5 DETECÇÃO DE BLOCO GRANDE DE TEXTO
            if(e.character != 0 && !Character.isISOControl(e.character)) {
            	typedChars++;
            }

            if (now - lastCharWindow > WINDOW_MS) {
                if (typedChars >= PASTE_THRESHOLD) {
                    sendSimpleEvent("Insercao_Copia_Cola", "Inserção_de_Texto_Grande");
                }
                typedChars = 0;
                lastCharWindow = now;
            }
        });

        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

        heartbeatScheduler.scheduleAtFixedRate(() -> {
            String json = String.format(
                "{ \"type\": \"heartbeat\", \"timestamp\": %d, \"student\": \"%s\", \"exam\": \"%s\" }",
                System.currentTimeMillis(), studentName, examName
            );
            sendEventAsync(json);
        }, 0, 5, TimeUnit.SECONDS); // a cada 5s

     // 3️⃣ THREAD DE INATIVIDADE
        new Thread(() -> {
            while (true) {
                long now = System.currentTimeMillis();
                if (now - lastKeyTime > INACTIVITY_LIMIT) {
                    sendSimpleEvent("state", "Longa_Inatividade");
                    lastKeyTime = now; // evita spam
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {}
            }
        }, "Argus-Inactivity-Watcher").start();
    }

	public void shutdown() {
	    System.out.println("[Argus] Iniciando shutdown do plugin...");

	    running = false;
	    
	    // 1️⃣ Log de encerramento
	    sendSimpleEvent("state", "PLUGIN_SHUTDOWN");

	    // 2️⃣ Fecha logger (flush final)
	    logger.close();

	    if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
	        heartbeatScheduler.shutdownNow();
	    }

	    if (scheduler != null && !scheduler.isShutdown()) {
	        scheduler.shutdownNow();
	    }
	    
	    if (eventLogger != null) {
            eventLogger.close();
        }

	    // 3️⃣ Fecha UI de forma segura no SWT
	    display.asyncExec(() -> {
	        if (shell != null && !shell.isDisposed()) {
	        	shell.dispose();
	            shell.close();
	        }
	        if (!display.isDisposed()) {
	            display.dispose();
	        }
	    });

        if (display != null && !display.isDisposed()) {
            display.asyncExec(() -> display.dispose());
        }

        System.out.println("[Argus] Sessão encerrada");

	    // 4️⃣ Finaliza JVM do plugin
	    System.exit(0);
	}

	private void sendEventAsync(String json) {
		logger.logEvent(json);
	}

	private void sendFocusLost() {
		String json = String.format(
		        "{ \"type\": \"focus\", \"action\": \"IDE_FOCUS_LOST\", \"timestamp\": %d, \"student\": \"%s\", \"exam\": \"%s\" }",
				System.currentTimeMillis(), studentName, examName);
		sendEventAsync(json);
		System.out.println(json);
	}

	private void sendFocusGained() {
		String json = String.format(
		        "{ \"type\": \"focus\", \"action\": \"IDE_FOCUS_GAINED\", \"timestamp\": %d, \"student\": \"%s\", \"exam\": \"%s\" }",
				System.currentTimeMillis(), studentName, examName);
		sendEventAsync(json);
		System.out.println(json);
	}

	private void sendSimpleEvent(String type, String action) {
		String json = String.format(
				"{ \"type\": \"%s\", \"action\": \"%s\", \"timestamp\": %d, \"student\": \"%s\", \"exam\": \"%s\" }",
		        type, action, System.currentTimeMillis(), studentName, examName);
		sendEventAsync(json);
	    System.out.println(json);
	}
}