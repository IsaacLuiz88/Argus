# Argus — Plugin de Monitoramento para Eclipse

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Eclipse PDE](https://img.shields.io/badge/Eclipse-PDE%20Plugin-2C2255?logo=eclipseide&logoColor=white)
![Status](https://img.shields.io/badge/status-em%20desenvolvimento-yellow)
![TCC](https://img.shields.io/badge/projeto-TCC-blueviolet)

> *Na mitologia grega, Argos Panoptes era o gigante de cem olhos que nunca dormia por completo — sempre havia um olho vigiando.*
> *Este Argus não é tão exagerado, mas a ideia é a mesma: enquanto o aluno programa, alguém está de olho.*

**Argus** é o **cliente principal** do ecossistema Argus: um plugin instalado direto na IDE **Eclipse** que acompanha o aluno durante uma prova prática de programação (feita para a disciplina de POO), capturando sinais de possível cola em tempo real e falando com o [ArgusServer](../ArgusServer) o tempo todo.

Ele é a peça que "começa a história": sem ele, nem o servidor sabe que existe um aluno fazendo prova, nem o [ArgusVision](../ArgusVision) sabe de quem é a webcam.

---

## O que ele faz

- Pede o nome do aluno e o identificador da prova, uma única vez, no login.
- Registra a sessão no ArgusServer e mantém um "sinal de vida" (heartbeat) constante via WebSocket.
- Fica de olho em eventos da IDE que podem indicar cola:
  - `Ctrl+C` / `Ctrl+V` / `Ctrl+X` — capturados por dois sensores: o filtro de teclado (tecla pressionada) e o listener de comandos do Eclipse (copiar/colar/recortar executado, inclusive pelo menu). Os dois emitem a mesma ação (`CTRL_C`, `CTRL_V`), então um único atalho pode aparecer duas vezes no log.
  - Colagem de blocos grandes de texto: quando o comando `paste` do Eclipse executa (atalho, menu ou botão), lê o tamanho do texto que estava na área de transferência e alerta se passar de 50 caracteres — o clássico "colei o código todo".
  - Digitação anormalmente rápida (50 ou mais teclas de texto em cerca de 500 ms), que não é humana e indica macro/automação. É um alerta separado do de colagem.
  - Perda e ganho de foco da janela do Eclipse (o aluno saiu pra outro lugar?).
  - Abertura do Marketplace, "Install New Software" ou "Check for Updates" — tentativas de instalar algo no meio da prova. Além de registrar, o plugin **fecha essa janela** na hora (ver Observações).
  - Inatividade prolongada (3 minutos sem digitar nada).
- Varre os plugins instalados no Eclipse a cada 30 segundos e sinaliza se encontrar nomes de ferramentas de IA conhecidas (Copilot, Tabnine, Codeium, Amazon Q, ChatGPT/OpenAI, Blackbox...).
- Ao final, avisa o servidor que está fechando. Quando o professor manda encerrar, o plugin para o monitoramento e fecha a janela do Eclipse pelo caminho normal (o Eclipse pergunta sobre arquivos não salvos).
- Se o WebSocket cair (rede, servidor reiniciando), reconecta sozinho com espera crescente (1, 2, 4, 8 e 15 s de teto) e o heartbeat volta.
- **Lança o [ArgusVision](../ArgusVision) automaticamente** assim que a sessão é confirmada, se `argusvision.enabled=true` (vem `false` por padrão) — o aluno não precisa abrir mais nada por conta própria.
- Se o servidor recusar a sessão porque a prova já foi encerrada pelo professor (HTTP 409), mostra um diálogo de erro ao aluno e não inicia o monitoramento.

---

## Papel no ecossistema

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

O Argus **observa e reporta**; a única intervenção é fechar janelas de instalação/atualização de plugins (ver Observações). Quem decide o que fazer com essa informação é o professor, olhando o dashboard.

---

## Estrutura interna

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

## Configuração

**Por máquina, sem recompilar o plugin:** crie `~/.argus/config.properties` (na pasta do usuário; ou aponte outro caminho com `-Dargus.config=...`). O que estiver nesse arquivo sobrescreve os valores embutidos no `.jar`, e o **ArgusVision lê o mesmo arquivo**, então o endereço do servidor é configurado uma vez só por computador:

```properties
# Servidor (resolve HTTP e WebSocket; https vira wss)
server.url=https://argus.onrender.com

# Chave de acesso, se o servidor exigir (argus.security.client-key no ArgusServer)
security.clientKey=troque-por-uma-chave-longa

# Lançamento automático do ArgusVision (opcional)
argusvision.enabled=true
argusvision.jar=C:/argus/ArgusVision.jar
argusvision.libraryPath=C:/opencv/build/java/x64
```

**Valores padrão embutidos** em `src/main/resources/config/config.properties` (servidor na mesma máquina):

```properties
server.host=localhost
server.port=8080

server.base=http://${server.host}:${server.port}
server.event=${server.base}/api/event
server.session=${server.base}/api/session/start
server.ws=ws://${server.host}:${server.port}/ws-command

argusvision.enabled=false
argusvision.jar=
argusvision.javaHome=
argusvision.libraryPath=
```

`server.url`, quando presente, vale mais que `server.host/port/base/event/session/ws`. Sem arquivo externo, o comportamento é o padrão acima (localhost:8080).

Pra ligar o auto-launch do ArgusVision, troque `argusvision.enabled` para `true` e preencha:
- `argusvision.jar` — caminho do `ArgusVision.jar` gerado por `mvn package` no projeto ArgusVision. Mantenha a pasta `lib/` (com o jar do OpenCV) ao lado dele. O plugin roda `java -jar <esse jar> <aluno>`.
- `argusvision.libraryPath` — pasta com as bibliotecas nativas do OpenCV (necessário pro `System.loadLibrary` funcionar fora do Eclipse).
- `argusvision.javaHome` — opcional; se vazio, usa o mesmo Java que roda o Eclipse.

---

## Como rodar

### Requisitos
- Java 21
- Eclipse com suporte a PDE (Plug-in Development Environment)
- [ArgusServer](../ArgusServer) já rodando

### Passos
1. Abra o projeto no Eclipse (`File > Import > Existing Projects into Workspace`).
2. Se o servidor não estiver em `localhost:8080`, crie o `~/.argus/config.properties` com `server.url` (ver Configuração).
3. Rode como **Eclipse Application** (cria uma segunda instância do Eclipse com o plugin instalado) ou exporte como plugin implantável (`Export > Deployable plug-ins and fragments`) e coloque o `.jar` gerado na pasta `dropins/` de uma instalação do Eclipse.
4. Na instância com o plugin, use o atalho **`Ctrl+6`** ou o menu **Argus Menu > Argus Command** pra abrir o login.
5. Informe seu nome e o código da prova — a partir daí, o Argus assume o resto sozinho.

---

## Observações

- O plugin **observa e reporta**, com uma exceção: quando abre uma janela cujo título contém "marketplace", "install"/"instalar" ou "update"/"atualizar", ele registra o evento e **fecha a janela**. A checagem é só pelo título, então também pega usos legítimos (por exemplo "Update Maven Project"). O plugin não bloqueia nenhuma outra ação.
- Nomes de aluno/prova com espaço e acento funcionam: o servidor gera identificadores de sessão só com ASCII seguro (`Angela_Maria_Prova_1_ab12cd34`).
- Se o servidor estiver fora do ar no momento do login, o erro só aparece no console do Eclipse: o plugin não inicia o monitoramento e não mostra aviso ao aluno. Respostas de erro do servidor mostram diálogo (`409` prova encerrada, `401` computador recusado por chave inválida).
- Um servidor que só cai *depois* do login não é problema: o WebSocket reconecta sozinho. Se o servidor recusar a chave (`401`/`403`) no WebSocket, o plugin para de tentar e registra o motivo.
- O plugin não chama mais `System.exit`: ao encerrar, ele remove os listeners que registrou e fecha a janela do Eclipse normalmente. Se o aluno cancelar a pergunta sobre arquivos não salvos, o Eclipse continua aberto, mas o monitoramento já parou.
- As mensagens JSON são montadas à mão (`String.format`) sem escapar aspas; nomes de aluno/prova com `"` ou `\` quebram o envio.
- O `EventManager` e o restante do plugin foram compilados contra os jars do Eclipse e o cliente WebSocket foi exercitado contra o servidor, mas o comportamento dentro da IDE (por exemplo o alerta de colagem) depende de teste manual no Eclipse.

---

## Projetos relacionados

- **[ArgusServer](https://github.com/IsaacLuiz88/ArgusServer)** — backend central, dashboard e persistência.
- **[ArgusVision](https://github.com/IsaacLuiz88/ArgusVision)** — monitoramento visual via webcam, lançado automaticamente por este plugin.
