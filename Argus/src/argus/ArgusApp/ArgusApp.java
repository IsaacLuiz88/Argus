package argus.ArgusApp;

import java.net.URI;
import java.net.http.*;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import argus.core.SharedContext;
import argus.config.ConfigLoader;
import argus.core.CommandSocketClient;
import argus.core.EventManager;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;

public class ArgusApp extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		Shell shell = window.getShell();

		// --- 1. COLETAR DADOS DO ALUNO ---
		String studentName = showLoginDialog(shell, "Identificação do Aluno", "Insira seu nome ou matrícula:", "Anonimo");
		if (studentName == null) { MessageDialog.openInformation(shell, "Argus", "Inicialização cancelada."); return null;}

		String examName = showLoginDialog(shell, "Identificação da Prova", "Insira o nome do exame/projeto:", "Teste");
		if(examName == null) { MessageDialog.openInformation(shell, "Argus", "Inicialização cancelada."); return null;}
		
		// 2. SALVAR GLOBALMENTE PARA TODO O SISTEMA ---
		SharedContext.init(studentName, examName);

		// 3 Registrar sessão no servidor
		HttpClient client = HttpClient.newHttpClient();
		String json = String.format(
		    "{\"student\":\"%s\",\"exam\":\"%s\",\"session\":\"%s\"}",
		    SharedContext.student(),
		    SharedContext.exam(),
		    SharedContext.session()
		);

		String baseUrl = ConfigLoader.getServerUrl().replace("/api/event", "");
		HttpRequest request = HttpRequest.newBuilder()
		    .uri(URI.create(baseUrl + "/api/session/start"))
			//.uri(URI.create("http://localhost:8080/api/session/start"))
		    .header("Content-Type", "application/json")
		    .POST(HttpRequest.BodyPublishers.ofString(json))
		    .build();
		client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
	      .thenRun(() -> System.out.println("[Argus] Sessão registrada com sucesso"))
	      .exceptionally(ex -> {
	          System.err.println("[Argus] Falha ao registrar sessão: " + ex.getMessage());
	          return null;
	      });

		// Mensagem inicial
		MessageDialog.openInformation(shell, "Argus", "Good lucky, " + studentName);

		// 4 INICIAR GESTOR DE EVENTOS (EventManager) ---
		org.eclipse.swt.widgets.Display display = shell.getDisplay();
		EventManager manager = new EventManager(display, shell);
		manager.startListener();

		CommandSocketClient socket =
			    new CommandSocketClient(manager);
			socket.connect(SharedContext.student());

		return null;
	}

	// Método auxiliar para exibir o diálogo de input
	private String showLoginDialog(Shell shell, String title, String message, String defaultValue) {
		InputDialog dialog = new InputDialog(shell, title, message, defaultValue, null);

		// Se o usuário clicar OK
		if (dialog.open() == InputDialog.OK) {
			String value = dialog.getValue();
			// Retorna o valor, ou o padrão se estiver vazio
			return (value == null || value.trim().isEmpty()) ? defaultValue : value.trim();
		}
		return null;
	}
}
