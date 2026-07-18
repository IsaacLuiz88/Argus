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
import argus.core.ArgusVisionLauncher;
import argus.core.CommandSocketClient;
import argus.core.EventManager;
import argus.core.SessionHandoff;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Display;

public class ArgusApp extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		Shell shell = window.getShell();
		Display display = shell.getDisplay();

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
			"{\"student\":\"%s\",\"exam\":\"%s\"}",
		    SharedContext.student(),
		    SharedContext.exam()
		);

		HttpRequest request = HttpRequest.newBuilder()
			    .uri(URI.create(ConfigLoader.getSessionUrl()))
			    .header("Content-Type", "application/json")
			    .POST(HttpRequest.BodyPublishers.ofString(json))
			    .build();

		client.sendAsync(request,HttpResponse.BodyHandlers.ofString()
		).thenAccept(response -> {
		    if(response.statusCode() != 200) {
		    	System.err.println("[Argus] Erro no servidor: " + response.statusCode());
	            return;
		    }
		    String body = response.body();
	        System.out.println("[Argus] Resposta: " + body);
			System.out.println("[Argus] Sessão registrada");

		        try {
		        String session = body
		                .split("\"session\":\"")[1]
		                .split("\"")[0];
		        SharedContext.setSession(session);
		        System.out.println("[Argus] SESSION DEFINIDA: " + session);
		        SessionHandoff.write(studentName, examName, session);
		        } catch(Exception e) {
		        	System.err.println("[Argus] Falha parse SESSION");
		            System.err.println(body);
		            e.printStackTrace();
		            return;
		        }
		        display.asyncExec(() -> {
		            try {
		                EventManager manager = new EventManager(display, shell);
		                manager.startListener();

		                CommandSocketClient socket = new CommandSocketClient(manager);
		                socket.connect();

		                // Último passo do fluxo: o ArgusVision só faz sentido depois que
		                // a sessão já está registrada e o plugin está monitorando.
		                ArgusVisionLauncher.launch(studentName);

		                MessageDialog.openInformation(
		                    shell,
		                    "Argus",
		                    "Monitoramento ativado para: " + studentName
		                );
		            } catch (Exception e) {
		                e.printStackTrace();
		            }
		        });
		}).exceptionally(ex -> {
		    System.err.println("[Argus] Falha ao registrar sessão: " + ex.getMessage());
		    return null;
		});
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
