package argus.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
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
	private volatile boolean shuttingDown = false;
	// true depois que o logger fechou: eventos que ainda chegarem viram no-op (sem excecao)
	private volatile boolean closed = false;

	// Guardados para poder REMOVER no shutdown (antes ficavam registrados para sempre)
	private Listener deactivateListener;
	private Listener activateListener;
	private Listener keyFilter;
	private Listener showFilter;
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

	// Atalho pressionado (filtro de teclado) e o comando do Eclipse que ele dispara
	// geram a mesma acao; sem isso um unico Ctrl+V apareceria duas vezes.
	private static final long SHORTCUT_DEDUPE_MS = 700;
	private String lastShortcutAction = "";
	private long lastShortcutTime = 0;

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
		deactivateListener = e -> {
			long now = System.currentTimeMillis();

			if (now - lastFocusLostTime < FOCUS_DEBOUNCE_MS)
				return;

			lastFocusLostTime = now;
			sendFocusLost();
		};
		shell.addListener(SWT.Deactivate, deactivateListener);

		// Listener de GANHO de foco
		activateListener = e -> {
			long now = System.currentTimeMillis();

			if (now - lastFocusGainedTime < FOCUS_DEBOUNCE_MS)
				return;

			lastFocusGainedTime = now;
			sendFocusGained();
		};
		shell.addListener(SWT.Activate, activateListener);

		keyFilter = e -> {
			long now = System.currentTimeMillis();
			lastKeyTime = now;

			if ((e.stateMask & SWT.CTRL) != 0) {

				if (e.keyCode == 'v' || e.keyCode == 'V') {
					sendShortcutEvent("CTRL_V");}

				if (e.keyCode == 'c' || e.keyCode == 'C') {
					sendShortcutEvent("CTRL_C");}
			}

			if (e.character != 0 && !Character.isISOControl(e.character)) {
				typedChars++;}

			if (now - lastCharWindow > WINDOW_MS) {

				// 50+ teclas de texto em ~500 ms nao e digitacao humana (macro/automacao).
				// NAO e colagem: um Ctrl+V gera uma tecla so; a colagem e tratada em
				// postExecuteSuccess (comando paste + tamanho da area de transferencia).
				if (typedChars >= PASTE_THRESHOLD) {
					sendSimpleEvent("Insercao_Copia_Cola", "Digitacao_Anormalmente_Rapida");}

				typedChars = 0;
				lastCharWindow = now;
			}
		};
		display.addFilter(SWT.KeyDown, keyFilter);

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
		showFilter = event -> {

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
		};
		display.addFilter(SWT.Show, showFilter);
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
					sendShortcutEvent("CTRL_C");
					break;

				case "org.eclipse.ui.edit.paste":
					sendShortcutEvent("CTRL_V");
					// Colagem grande de verdade: mede o texto que estava na area de
					// transferencia quando o comando paste executou (atalho, menu ou botao).
					if (clipboardTextLength() >= PASTE_THRESHOLD) {
						sendSimpleEvent("Insercao_Copia_Cola", "Inserção_de_Texto_Grande");
					}
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
		if (shuttingDown) {return;}
		shuttingDown = true;

		System.out.println("[Argus] Iniciando shutdown do plugin...");
		running = false;

		// 1) avisa o servidor enquanto o logger ainda aceita eventos
		sendSimpleEvent("state", "PLUGIN_SHUTDOWN");

		if (commandListener != null) {
			ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
			commandService.removeExecutionListener(commandListener);}

		if (pluginScheduler != null && !pluginScheduler.isShutdown()) {
			pluginScheduler.shutdownNow();}

		// 2) fecha o logger (executor.shutdown() deixa terminar o que ja foi enfileirado)
		logger.close();
		closed = true;

		try {
			Thread.sleep(500);
		} catch (InterruptedException ignored) {}

		// 3) na thread de UI: remove os listeners e fecha a janela do Eclipse. Sem System.exit:
		// o Eclipse fecha pelo caminho normal (pergunta sobre arquivos nao salvos) em vez de
		// a JVM ser morta na hora, o que fazia o aluno perder o trabalho aberto.
		display.asyncExec(() -> {
			removeUiListeners();
			if (shell != null && !shell.isDisposed()) {
				shell.close();
			}
		});
		System.out.println("[Argus] Sessão encerrada");
	}

	private void removeUiListeners() {
		if (display.isDisposed()) {return;}
		if (keyFilter != null) {display.removeFilter(SWT.KeyDown, keyFilter);}
		if (showFilter != null) {display.removeFilter(SWT.Show, showFilter);}
		if (shell != null && !shell.isDisposed()) {
			if (deactivateListener != null) {shell.removeListener(SWT.Deactivate, deactivateListener);}
			if (activateListener != null) {shell.removeListener(SWT.Activate, activateListener);}
		}
	}

	private void sendEventAsync(String json) {
		if (closed) {return;}
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

	// Envia CTRL_C/CTRL_V/CTRL_X evitando duplicar o mesmo atalho vindo das duas fontes
	// (filtro de teclado + comando do Eclipse) em um intervalo curto.
	private void sendShortcutEvent(String action) {
		long now = System.currentTimeMillis();
		synchronized (this) {
			if (action.equals(lastShortcutAction) && now - lastShortcutTime < SHORTCUT_DEDUPE_MS) {
				return;
			}
			lastShortcutAction = action;
			lastShortcutTime = now;
		}
		sendSimpleEvent("keyboard", action);
	}

	// Tamanho, em caracteres, do texto atualmente na area de transferencia (0 se nao for texto).
	// Precisa rodar na thread de UI (o comando do Eclipse ja executa nela).
	private int clipboardTextLength() {
		Clipboard clipboard = new Clipboard(display);
		try {
			Object contents = clipboard.getContents(TextTransfer.getInstance());
			return contents instanceof String text ? text.length() : 0;
		} catch (RuntimeException e) {
			return 0;
		} finally {
			clipboard.dispose();
		}
	}

	private void sendSimpleEvent(String type, String action) {
		String json = String.format(
				"{ \"type\": \"%s\", \"action\": \"%s\", \"timestamp\": %d, \"student\": \"%s\", \"exam\": \"%s\", \"session\": \"%s\"}",
				type, action, System.currentTimeMillis(), studentName, examName, SharedContext.session());
		sendEventAsync(json);
		System.out.println(json);
	}
}
