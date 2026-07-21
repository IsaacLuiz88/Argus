# 👁️ Argus — Plugin de Monitoramento para Eclipse

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Eclipse PDE](https://img.shields.io/badge/Eclipse-PDE%20Plugin-2C2255?logo=eclipseide&logoColor=white)
![Status](https://img.shields.io/badge/status-em%20desenvolvimento-yellow)
![TCC](https://img.shields.io/badge/projeto-TCC-blueviolet)

> *Na mitologia grega, Argos Panoptes era o gigante de cem olhos que nunca dormia por completo — sempre havia um olho vigiando.*
> *Este Argus não é tão exagerado, mas a ideia é a mesma: enquanto o aluno programa, alguém está de olho.*

**Argus** é o **cliente principal** do ecossistema Argus: um plugin instalado direto na IDE **Eclipse** que acompanha o aluno durante uma prova prática de programação (feita para a disciplina de POO), capturando sinais de possível cola em tempo real e falando com o [ArgusServer](../ArgusServer) o tempo todo.

Ele é a peça que "começa a história": sem ele, nem o servidor sabe que existe um aluno fazendo prova, nem o [ArgusVision](../ArgusVision) sabe de quem é a webcam.

---

## 🎯 O que ele faz

- Pede o nome do aluno e o identificador da prova, uma única vez, no login.
- Registra a sessão no ArgusServer e mantém um "sinal de vida" (heartbeat) constante via WebSocket.
- Fica de olho em eventos da IDE que podem indicar cola:
  - `Ctrl+C` / `Ctrl+V` / `Ctrl+X` — tanto pelo atalho puro quanto pelo comando do Eclipse (dois sensores, um só evento real, mais difícil de escapar).
  - Colagem de blocos grandes de texto de uma vez (mais de 50 caracteres numa janela curta) — o clássico "colei o código todo".
  - Perda e ganho de foco da janela do Eclipse (o aluno saiu pra outro lugar?).
  - Abertura do Marketplace, "Install New Software" ou "Check for Updates" — tentativas de instalar algo no meio da prova.
  - Inatividade prolongada (3 minutos sem digitar nada).
- Varre os plugins instalados no Eclipse a cada 30 segundos e sinaliza se encontrar nomes de ferramentas de IA conhecidas (Copilot, Tabnine, Codeium, Amazon Q, ChatGPT/OpenAI, Blackbox...).
- Ao final, avisa o servidor que está fechando (e recebe o comando de shutdown remoto do professor, se for o caso).
- **Lança o [ArgusVision](../ArgusVision) automaticamente** assim que a sessão é confirmada — o aluno não precisa abrir mais nada por conta própria.

---

## 🧩 Papel no ecossistema

```
        login do aluno
              │
              ▼
   ┌─────────────────────┐        heartbeat (WS) / eventos (HTTP)
   │   Argus (plugin)    │ ─────────────────────────────────────► ArgusServer
   └─────────────────────┘                                             │
              │ lança automaticamente                                  │
              ▼                                                        ▼
   ┌─────────────────────┐        frames + status facial (HTTP)   dashboard.html
   │     ArgusVision      │ ─────────────────────────────────────►  student.html
   └─────────────────────┘
```

O Argus não bloqueia o aluno, não impede nenhuma ação — ele só **observa e reporta**. Quem decide o que fazer com essa informação é o professor, olhando o dashboard.

---

## 🏗️ Estrutura interna

| Classe | Responsabilidade |
|---|---|
| `ArgusApp` | Ponto de entrada (comando do Eclipse). Faz o login, registra a sessão e liga tudo o resto. |
| `EventManager` | O "sensor" — escuta teclado, foco, comandos e janelas da IDE, e decide quando gerar um evento. |
| `CommandSocketClient` | Mantém a conexão WebSocket com `/ws-command/{session}`: envia heartbeat a cada 10s e escuta comandos do servidor (ex: shutdown remoto). |
| `PluginScanner` | Lista os bundles OSGi instalados no Eclipse — é a base da detecção de ferramentas de IA. |
| `EventLogger` | Grava cada evento em `~/ArgusLogs/` (backup local) e reenvia ao servidor. |
| `ArgusVisionLauncher` | Sobe o processo do ArgusVision (`ProcessBuilder`) assim que a sessão é confirmada. |
| `SessionHandoff` | Grava aluno/prova/sessão em `~/ArgusLogs/current_session.properties`, pra qualquer processo local (como o ArgusVision) saber quem está logado sem precisar perguntar de novo. |
| `SharedContext` | Estado global simples (aluno, prova, sessão) compartilhado entre as classes do plugin. |
| `ConfigLoader` | Lê `config.properties` (URLs do servidor, configuração do auto-launch do ArgusVision). |
| `KeyTranslator` | Traduz códigos de tecla do SWT para nomes legíveis. |

> Filosofia de código do plugin: **zero dependências externas em runtime**. Tudo é feito com `java.net.http` puro (HTTP e WebSocket nativos do JDK) — nada de Jackson, nada de bibliotecas de terceiros disputando classloader com o Eclipse.

---

## ⚙️ Configuração

Tudo em `src/main/resources/config/config.properties`, dentro do plugin:

```properties
server.host=localhost
server.port=8080

server.base=http://${server.host}:${server.port}
server.event=${server.base}/api/event
server.session=${server.base}/api/session/start
server.ws=ws://${server.host}:${server.port}/ws-command

# ArgusVision - iniciado automaticamente logo apos o login do aluno
argusvision.enabled=false
argusvision.jar=
argusvision.javaHome=
argusvision.libraryPath=
```

Pra ligar o auto-launch do ArgusVision, troque `argusvision.enabled` para `true` e preencha:
- `argusvision.jar` — caminho do `.jar` executável do ArgusVision nessa máquina.
- `argusvision.libraryPath` — pasta com as bibliotecas nativas do OpenCV (necessário pro `System.loadLibrary` funcionar fora do Eclipse).
- `argusvision.javaHome` — opcional; se vazio, usa o mesmo Java que roda o Eclipse.

---

## ▶️ Como rodar

### Requisitos
- Java 21
- Eclipse com suporte a PDE (Plug-in Development Environment)
- [ArgusServer](../ArgusServer) já rodando

### Passos
1. Abra o projeto no Eclipse (`File > Import > Existing Projects into Workspace`).
2. Ajuste `config.properties` com o endereço real do ArgusServer.
3. Rode como **Eclipse Application** (cria uma segunda instância do Eclipse com o plugin instalado) ou exporte como plugin implantável (`Export > Deployable plug-ins and fragments`) e coloque o `.jar` gerado na pasta `dropins/` de uma instalação do Eclipse.
4. Na instância com o plugin, use o atalho **`Ctrl+6`** ou o menu **Argus Menu > Argus Command** pra abrir o login.
5. Informe seu nome e o código da prova — a partir daí, o Argus assume o resto sozinho.

---

## 🔐 Observações

- O plugin não impede nem bloqueia nenhuma ação do aluno — ele só observa.
- Nomes de aluno/prova viram parte do UUID da sessão, então evite caracteres muito exóticos.
- Se o servidor estiver fora do ar no momento do login, o plugin avisa e não inicia o monitoramento.

---

## 🔗 Projetos relacionados

- **[ArgusServer](https://github.com/IsaacLuiz88/ArgusServer)** — backend central, dashboard e persistência.
- **[ArgusVision](https://github.com/IsaacLuiz88/ArgusVision)** — monitoramento visual via webcam, lançado automaticamente por este plugin.
