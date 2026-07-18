package argus.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IExecutionListener;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import argus.config.ConfigLoader;

public class EventManager {

	private final Display display;
	private final String serverUrl;
	private volatile boolean running = true;
	private final String studentName;
	private final String examName;
	@SuppressWarnings("unused")
	private final String sessionId;
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

	private ScheduledExecutorService pluginScheduler;

	private IExecutionListener commandListener;

	public EventManager(Display display, Shell shell) {
		this.display = display;
		this.serverUrl = ConfigLoader.getEventUrl();
		this.shell = shell;

		this.studentName = SharedContext.student();
		this.examName = SharedContext.exam();
		this.sessionId = SharedContext.session();

		this.logger = new EventLogger(EventLogger.Format.JSON, SharedContext.session(), this.serverUrl);
	}

	public void startListener() {

		// Listener de PERDA de foco
		shell.addListener(SWT.Deactivate, e -> {
			long now = System.currentTimeMillis();

			if (now - lastFocusLostTime < FOCUS_DEBOUNCE_MS)
				return;

			lastFocusLostTime = now;
			sendFocusLost();
		});

		// Listener de GANHO de foco
		shell.addListener(SWT.Activate, e -> {
			long now = System.currentTimeMillis();

			if (now - lastFocusGainedTime < FOCUS_DEBOUNCE_MS)
				return;

			lastFocusGainedTime = now;
			sendFocusGained();
		});

		display.addFilter(SWT.KeyDown, e -> {
			long now = System.currentTimeMillis();
			lastKeyTime = now;

			if ((e.stateMask & SWT.CTRL) != 0) {

				if (e.keyCode == 'v' || e.keyCode == 'V') {
					sendSimpleEvent("keyboard", "CTRL_V_RAW");}

				if (e.keyCode == 'c' || e.keyCode == 'C') {
					sendSimpleEvent("keyboard", "CTRL_C_RAW");}
			}

			if (e.character != 0 && !Character.isISOControl(e.character)) {
				typedChars++;}

			if (now - lastCharWindow > WINDOW_MS) {

				if (typedChars >= PASTE_THRESHOLD) {
					sendSimpleEvent("Insercao_Copia_Cola", "Inserção_de_Texto_Grande");}

				typedChars = 0;
				lastCharWindow = now;
			}
		});

		// captura robusta de comandos Eclipse
		registerCommandListener();
		// detecta abertura de janelas
		registerShellListener();

		// THREAD DE INATIVIDADE
		new Thread(() -> {
			while (running) {
				long now = System.currentTimeMillis();

				if (now - lastKeyTime > INACTIVITY_LIMIT) {
					sendSimpleEvent("state", "Longa_Inatividade");
					lastKeyTime = now;}

				try {
					Thread.sleep(5000);
				} catch (InterruptedException ignored) {}
			}
		}, "Argus-Inactivity-Watcher").start();

		pluginScheduler = Executors.newSingleThreadScheduledExecutor();
		pluginScheduler.scheduleAtFixedRate(() -> {
			sendPluginScan();
		}, 0, 30, TimeUnit.SECONDS);
	}

	private void registerShellListener() {
		display.addFilter(SWT.Show, event -> {

			if (!(event.widget instanceof Shell openedShell)) {return;}

			String title = openedShell.getText();
			if (title == null || title.isEmpty()) {return;}

			String normalized = title.toLowerCase();
			if (normalized.contains("marketplace")) {
				sendSimpleEvent("security", "MARKETPLACE_OPENED");
				display.asyncExec(() -> {openedShell.close();});
				return;
			}

			if (normalized.contains("install") || normalized.contains("instalar")) {
				sendSimpleEvent("security", "INSTALL_NEW_SOFTWARE_OPENED");
				display.asyncExec(() -> {openedShell.close();});
				return;
			}

			if (normalized.contains("update") || normalized.contains("atualizar")) {
				sendSimpleEvent("security", "CHECK_FOR_UPDATE_OPENED");
				display.asyncExec(() -> {openedShell.close();});
				return;
			}
		});
	}

	private void registerCommandListener() {
		ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);

		commandListener = new IExecutionListener() {
			@Override
			public void preExecute(String commandId, ExecutionEvent event) {}

			@Override
			public void postExecuteSuccess(String commandId, Object returnValue) {
				switch (commandId) {

				case "org.eclipse.ui.edit.copy":
					sendSimpleEvent("keyboard", "CTRL_C");
					break;

				case "org.eclipse.ui.edit.paste":
					sendSimpleEvent("keyboard", "CTRL_V");
					break;

				case "org.eclipse.ui.edit.cut":
					sendSimpleEvent("keyboard", "CTRL_X");
					break;

				case "org.eclipse.equinox.p2.ui.discovery.commands.ShowBundleCatalog":
					sendSimpleEvent("security", "MARKETPLACE_COMMAND_TRIGGERED");
					break;

				case "org.eclipse.equinox.p2.ui.sdk.install":
					sendSimpleEvent("security", "INSTALL_NEW_SOFTWARE_COMMAND_TRIGGERED");
					break;

				case "org.eclipse.equinox.p2.ui.sdk.update":
					sendSimpleEvent("security", "CHECK_FOR_UPDATES_COMMAND_TRIGGERED");
					break;
				}
			}

			@Override
			public void postExecuteFailure(String commandId, ExecutionException exception) {}

			@Override
			public void notHandled(String commandId, NotHandledException exception) {}
		};
		commandService.addExecutionListener(commandListener);
	}

	private void sendPluginScan() {
		try {
			var plugins = PluginScanner.scanPlugins();

			StringBuilder pluginsJson = new StringBuilder("[");
			for (int i = 0; i < plugins.size(); i++) {
				pluginsJson.append("\"").append(plugins.get(i)).append("\"");
				if (i < plugins.size() - 1) {
					pluginsJson.append(",");
				}
			}
			pluginsJson.append("]");

			String json = String.format(
					"{ \"type\": \"plugin_scan\", \"plugins\": %s, \"timestamp\": %d, \"student\": \"%s\", \"exam\": \"%s\", \"session\": \"%s\"}",
					pluginsJson.toString(), System.currentTimeMillis(), studentName, examName, SharedContext.session());

			sendEventAsync(json);
			System.out.println("[Argus] Plugin scan enviado");

		} catch (Exception e) {
			System.err.println("[Argus] Erro ao escanear plugins: " + e.getMessage());
		}
	}

	public void shutdown() {
		System.out.println("[Argus] Iniciando shutdown do plugin...");
		running = false;

		// 1️⃣ Log de encerramento
		sendSimpleEvent("state", "PLUGIN_SHUTDOWN");

		if (commandListener != null) {
			ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
			commandService.removeExecutionListener(commandListener);}

		if (pluginScheduler != null && !pluginScheduler.isShutdown()) {
			pluginScheduler.shutdownNow();}

		// 2️⃣ Fecha logger (flush final)
		logger.close();

		try {
			Thread.sleep(500);
		} catch (InterruptedException ignored) {}

		// 3️⃣ Fecha UI de forma segura no SWT
		display.asyncExec(() -> {
			if (shell != null && !shell.isDisposed()) {
				shell.close();
			}
		});
		System.out.println("[Argus] Sessão encerrada");

		// 4️⃣ Finaliza JVM do plugin
		System.exit(0);
	}

	private void sendEventAsync(String json) {
		logger.logEvent(json);
	}

	private void sendFocusLost() {
		String json = String.format(
				"{ \"type\": \"focus\", \"action\": \"IDE_FOCUS_LOST\", \"timestamp\": %d, \"student\": \"%s\", \"exam\": \"%s\", \"session\": \"%s\"}",
				System.currentTimeMillis(), studentName, examName, SharedContext.session());
		sendEventAsync(json);
		System.out.println(json);
	}

	private void sendFocusGained() {
		String json = String.format(
				"{ \"type\": \"focus\", \"action\": \"IDE_FOCUS_GAINED\", \"timestamp\": %d, \"student\": \"%s\", \"exam\": \"%s\", \"session\": \"%s\"}",
				System.currentTimeMillis(), studentName, examName, SharedContext.session());
		sendEventAsync(json);
		System.out.println(json);
	}

	private void sendSimpleEvent(String type, String action) {
		String json = String.format(
				"{ \"type\": \"%s\", \"action\": \"%s\", \"timestamp\": %d, \"student\": \"%s\", \"exam\": \"%s\", \"session\": \"%s\"}",
				type, action, System.currentTimeMillis(), studentName, examName, SharedContext.session());
		sendEventAsync(json);
		System.out.println(json);
	}
}
