# Argus — Eclipse Monitoring Plugin

Argus é um **plugin para Eclipse** desenvolvido para **monitoramento de atividades do usuário** durante avaliações, exames práticos ou projetos supervisionados.
Ele atua como o **cliente principal do ecossistema Argus**, sendo responsável por capturar eventos relevantes no ambiente de desenvolvimento e enviá-los para um servidor central para análise e auditoria.

---

## 🎯 Objetivo

O objetivo do Argus é registrar **comportamentos relevantes do usuário** durante uma avaliação, permitindo:

- Auditoria posterior
- Análise de conduta
- Correlação com monitoramento visual (ArgusVision)
- Associação de eventos a uma sessão única de prova

---

## 🧩 O que o Argus monitora

Atualmente, o Argus monitora:

- Teclas pressionadas
- Ganho e perda de foco da janela (Alt+Tab, troca de aplicações)
- Identificação do aluno
- Identificação da prova
- Associação de todos os eventos a uma **sessão ativa**

> ⚠️ **Observação:**  
> A monitoração de mouse foi **removida intencionalmente** do projeto, mantendo o foco em eventos realmente relevantes para análise de comportamento durante provas.

---

## 🏗️ Arquitetura

O Argus funciona como um **plugin Eclipse (RCP / SWT)** e é composto pelos seguintes módulos principais:

### 🔹 ArgusApp
Ponto de entrada do plugin.
- Solicita identificação do aluno e da prova
- Inicializa a sessão no servidor
- Ativa o monitoramento de eventos

### 🔹 SharedContext
Armazena globalmente:
- Nome do aluno
- Nome da prova
- Identificador da sessão ativa

### 🔹 EventManager
Responsável por capturar eventos do Eclipse:
- Teclado
- Foco da janela  
Utiliza listeners SWT nativos.

### 🔹 EventLogger
Responsável por:
- Persistir eventos localmente (TXT / JSON)
- Enviar eventos de forma assíncrona via HTTP para o servidor ArgusServer

### 🔹 ConfigLoader
Carrega configurações externas, como:
- URL do servidor
- Parâmetros de envio

---

## 📡 Comunicação com o Servidor

Os eventos são enviados ao backend no formato JSON via HTTP.

Exemplo:
```json
{
  "type": "focus",
  "action": "FOCUS_LOST",
  "timestamp": 1766104235598,
  "student": "Aldebaran",
  "exam": "ProvaRedes"
}
```
---

### 🗂️ Logs Locais
Os eventos também são registrados localmente:
- Arquivos separados por sessão
- Úteis para auditoria offline
- Servem como contingência em caso de falha de comunicação

---

### ⚙️ Requisitos
- Java 11 ou superior
- Eclipse IDE (com suporte a plugins RCP)
- ArgusServer em execução

---

### ▶️ Execução
Instale o plugin no Eclipse
Inicie o Eclipse normalmente
Ative o Argus

Informe:
- Nome do aluno
- Nome da prova
O monitoramento inicia automaticamente

---

### 🔐 Observações
O Argus não interfere no funcionamento do Eclipse
Nenhuma ação do usuário é bloqueada
O sistema foi projetado para ser extensível e modular

---

## 🔗 Projetos Relacionados
- [ArgusServer](https://github.com/IsaacLuiz88/ArgusServer) — Backend central de coleta e análise
- [ArgusVision](https://github.com/IsaacLuiz88/ArgusVision) — Monitoramento visual via OpenCV

---
