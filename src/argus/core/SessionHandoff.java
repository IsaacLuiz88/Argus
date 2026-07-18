package argus.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

// Grava a identidade da sessao atual (aluno/prova/sessao) num arquivo local,
// dentro da mesma pasta que o EventLogger ja usa (~/ArgusLogs). E assim que o
// ArgusVision descobre quem esta rodando na maquina, sem precisar de janela
// perguntando o nome de novo nem depender só do argumento de linha de comando.
public class SessionHandoff {

	private static final Path FILE = Paths.get(
			System.getProperty("user.home"), "ArgusLogs", "current_session.properties");

	public static void write(String student, String exam, String session) {
		Properties prop = new Properties();
		prop.setProperty("student", student != null ? student : "");
		prop.setProperty("exam", exam != null ? exam : "");
		prop.setProperty("session", session != null ? session : "");

		try {
			Files.createDirectories(FILE.getParent());
			try (OutputStream out = Files.newOutputStream(FILE)) {
				prop.store(out, "Sessao Argus atual - gerado automaticamente, nao editar");
			}
			System.out.println("[Argus] Sessão local gravada em " + FILE);
		} catch (IOException e) {
			System.err.println("[Argus] Falha ao gravar sessão local: " + e.getMessage());
		}
	}
}
