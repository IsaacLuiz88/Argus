package argus.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import argus.config.ConfigLoader;

// Inicia o ArgusVision como processo separado logo após a sessão do aluno ser
// registrada no servidor. O nome do aluno é passado por argumento — o
// ArgusVision nunca deve pedir isso de novo, já que a identidade já foi
// coletada aqui no login do plugin (ver ArgusApp).
public class ArgusVisionLauncher {

	public static void launch(String studentName) {
		if (!ConfigLoader.isArgusVisionEnabled()) {
			System.out.println("[Argus] ArgusVision desabilitado (argusvision.enabled=false) - pulando inicialização automática");
			return;
		}

		String jarPath = ConfigLoader.getArgusVisionJar();
		if (jarPath == null || jarPath.isBlank()) {
			System.err.println("[Argus] argusvision.jar não configurado - não é possível iniciar o ArgusVision");
			return;
		}

		String javaHome = ConfigLoader.getArgusVisionJavaHome();
		String javaBin = (javaHome != null && !javaHome.isBlank())
				? javaHome + File.separator + "bin" + File.separator + "java"
				: System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

		List<String> command = new ArrayList<>();
		command.add(javaBin);

		String libraryPath = ConfigLoader.getArgusVisionLibraryPath();
		if (libraryPath != null && !libraryPath.isBlank()) {
			command.add("-Djava.library.path=" + libraryPath);
		}

		command.add("-jar");
		command.add(jarPath);
		command.add(studentName);

		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.redirectErrorStream(true);

			File logDir = new File(System.getProperty("user.home"), "ArgusLogs");
			logDir.mkdirs();
			pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(logDir, "argusvision-output.log")));

			pb.start();
			System.out.println("[Argus] ArgusVision iniciado para: " + studentName);
		} catch (IOException e) {
			System.err.println("[Argus] Falha ao iniciar o ArgusVision: " + e.getMessage());
		}
	}
}
