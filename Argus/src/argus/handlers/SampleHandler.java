package argus.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

//import argus.config.ConfigLoader;
import argus.core.EventManager;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;

public class SampleHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		Shell shell = window.getShell();
		
		// --- 1. COLETAR DADOS DO ALUNO ---
        String studentName = showLoginDialog(shell, "Identificação do Aluno", "Insira seu nome ou matrícula:", "Anonimo");
        String examName = showLoginDialog(shell, "Identificação da Prova", "Insira o nome do exame/projeto:", "Teste");
       
		 // Mostra mensagem inicial
		MessageDialog.openInformation(
				shell,
				"Argus", "Good lucky, " + studentName);

				// --- 2. INICIAR O EVENT MANAGER COM OS DADOS ---
				org.eclipse.swt.widgets.Display display = shell.getDisplay();

		// Captura eventos do display principal
		EventManager manager = new EventManager(display, shell, studentName, examName);
		//EventManager manager = new EventManager(display, examName, studentName, examName);
		manager.startListener();
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
        
        // Se o usuário clicar Cancelar, retorna o padrão
        return defaultValue; 
    }
}
