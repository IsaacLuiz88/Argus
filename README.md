# Argus — Eclipse Monitoring Plugin

Argus é um **plugin para Eclipse** voltado ao monitoramento de atividades do usuário durante avaliações, exames práticos ou projetos supervisionados.  
Ele atua como o **cliente principal** do ecossistema Argus, coletando eventos de interação e enviando-os para um servidor central para análise.

---

## 🎯 Objetivo

O objetivo do Argus é registrar **interações relevantes do usuário** durante um exame, como:

- Teclas pressionadas
- Cliques do mouse
- Perda e ganho de foco da janela (Alt+Tab, troca de janelas)
- Identificação de aluno e prova
- Associação de eventos a uma sessão única

Esses dados permitem **auditoria posterior**, análise de comportamento e integração com sistemas de monitoramento adicionais (como o ArgusVision).

---

## 🧩 Arquitetura

O Argus funciona como um **plugin Eclipse (RCP/SWT)** e é composto por:

- **ArgusApp**  
  Ponto de entrada do plugin. Solicita identificação do aluno e da prova, inicia a sessão no servidor e ativa o monitoramento.

- **SharedContext**  
  Armazena globalmente o aluno, prova e identificador da sessão.

- **EventManager**  
  Registra eventos de teclado, mouse e foco da janela usando listeners SWT.

- **EventLogger**  
  Responsável por:
  - Persistir eventos localmente em arquivos (`.log` ou `.json`)
  - Enviar eventos de forma assíncrona ao servidor HTTP

- **ConfigLoader**  
  Carrega configurações externas (ex: URL do servidor).

---

## 📡 Comunicação com o Servidor
- Envio de eventos:

O plugin envia eventos no formato JSON via HTTP para o backend:
Exemplo:
{"type":"focus","action":"FOCUS_LOST","time":1766104235598,"student":"Aldebaran","exam":"ProvaRedes"},
{"type":"focus","action":"FOCUS_GAINED","time":1766104402035,"student":"Aldebaran","exam":"ProvaRedes"}

## ArgusLogs

Os arquivos são separados por data e sessão, facilitando auditoria offline.

---

## ⚙️ Requisitos

- Java 11 ou superior
- Eclipse IDE (com suporte a plugins RCP)
- Servidor Argus em execução

---

## ▶️ Execução

1. Instale o plugin no Eclipse
2. Inicie o Eclipse normalmente
3. Ao ativar o Argus:
   - Informe o nome do aluno
   - Informe o nome da prova
4. O monitoramento começa automaticamente

---

## 🔐 Observações

- O Argus **não interfere** no funcionamento do Eclipse
- O envio de eventos é tolerante a falhas de rede
- O sistema foi projetado para ser extensível

---

## 📌 Projeto relacionado

- **ArgusServer** — Backend de coleta e classificação de eventos
- **ArgusVision** — Monitoramento visual via OpenCV
