# Sistema de Controlo de Missões Espaciais

**Trabalho Prático 2 - Comunicações por Computador**  
Universidade do Minho | Licenciatura em Engenharia Informática | 2025

Sistema completo de gestão de missões espaciais que simula a comunicação entre uma **Nave-Mãe** em órbita e múltiplos **rovers** na superfície de um planeta. O sistema implementa protocolos de comunicação robustos sobre **UDP** e **TCP**, com uma interface web de observação em tempo real.

### Características Principais

- **MissionLink (ML)**: Protocolo fiável sobre UDP para envio de missões
- **TelemetryStream (TS)**: Streaming contínuo de telemetria via TCP
- **API RESTful**: Observação em tempo real via HTTP
- **Ground Control**: Interface web moderna e responsiva
- **Fragmentação**: Suporte para missões grandes (>512 bytes)
- **Retransmissão**: Mecanismos de retry e controlo de perdas
- **Detecção de Ambiente**: Funciona em localhost e CORE automaticamente


## Requisitos

### Software Necessário

| Componente | Versão Mínima | Descrição |
|------------|---------------|-----------|
| **Java JDK** | 17+ | Compilação e execução |
| **Gradle** | 7.0+ | Build system (wrapper incluído) |
| **Navegador Web** | Moderno | Firefox, Chrome ou Edge para Ground Control |
| **CORE Emulator** | 9.1.0+ | Apenas para testes em rede simulada |

### Sistema Operativo

- **Linux**: Ubuntu 20.04+, Debian 11+ (recomendado)
- **macOS**: 11+

### Devcontainer (Recomendado)

Este repositório foi configurado para funcionar dentro de um **devcontainer** com todas as dependências pré-instaladas.

**Como usar:**
1. Abra o projeto no VS Code
2. Clique em "Reopen in Container" quando solicitado
3. Aguarde o container inicializar (Debian GNU/Linux 11)

O devcontainer inclui: Git, JDK 17, Gradle, Node.js e todas as ferramentas necessárias.

### Dependências Java

Todas as dependências são geridas automaticamente pelo Gradle:
```groovy
dependencies {
    implementation 'com.google.code.gson:gson:2.10.1'  // Serialização JSON
    testImplementation 'junit:junit:4.13.2'            // Testes unitários
}
```

> **Nota:** Se `./gradlew` não existir, use `gradle` diretamente.

##  Instalação e Compilação

### 1. Obter o Código

**Opção A - Clonar repositório:**
```bash
git clone <url-do-repositorio>
```

**Opção B - Download ZIP:**
1. Baixe o ZIP do repositório
2. Extraia para uma pasta local
3. Abra a pasta do projeto

### 2. Compilar o Projeto

#### Usando Make (recomendado)
```bash
# Compilação completa
make build
```

#### Usando Gradle diretamente
```bash
# Com wrapper (se disponível)
./gradlew build

# Ou com instalação local
gradle build
```

**O que acontece durante a compilação:**
-  Compila todo o código Java (`src/main/java`)
-  Gera JARs executáveis em `build/libs/`
-  Copia recursos (HTML, CSS, JS) para `build/resources/`


### 4. Limpar Build (opcional)

Para recompilar do zero:
```bash
make clean       # Remove build/
make build       # Recompila tudo
```

---

## Execução

### Modo 1: Execução Manual Local

Execute cada componente num terminal separado na seguinte ordem:

#### 1. Iniciar Nave-Mãe
```bash
# Terminal 1
make run-nave
```

**Configuração padrão:**
- Porta TCP: `5001` (telemetria)
- Porta UDP: `9000` (missões)
- IP: `127.0.0.1`

---

#### 2. Iniciar Rovers

**Opção A - Rovers pré-configurados:**

```bash
# Terminal 2 - Rover 1
make run-rover1
# ID: 1, Posição: (10.0, 5.0), Porta UDP: 9011

# Terminal 3 - Rover 2
make run-rover2
# ID: 2, Posição: (15.0, 10.0), Porta UDP: 9012

# Terminal 4 - Rover 3
make run-rover3
# ID: 3, Posição: (20.0, 15.0), Porta UDP: 9013
```


**Opção B - Rover personalizado:**

```bash
make run-rover ARGS="<id> <posX> <posY> <ipNave> <portaTcpNave> <portaUdpRover>"

# Exemplo: Rover ID 4 na posição (25.0, 20.0)
make run-rover ARGS="4 25.0 20.0 127.0.0.1 5001 9014"
```

**Parâmetros do Rover:**
- `id`: Identificador único (inteiro)
- `posX`: Coordenada X (double)
- `posY`: Coordenada Y (double)
- `ipNave`: IP da Nave-Mãe
- `portaTcpNave`: Porta TCP da Nave (telemetria)
- `portaUdpRover`: Porta UDP local do rover (missões)

---

#### 3. Iniciar Ground Control
```bash
# Terminal 5
make run-ground-control
```

**Configuração padrão:**
- Porta HTTP: `8080`
- API Endpoint: `http://localhost:8080` (local)

#### 4. Aceder à Interface Web

Abrir no navegador: **http://localhost:8080/ui/**

---

### Modo 2: Demo Rápida

Inicia componentes automaticamente:
```bash
make run-demo
```

Isto executa:
1. Nave-Mãe (fundo)
2. Ground Control CLI (fundo)
3. Rover 1 (fundo)



**Parar a demo:**
```bash
make demo-kill
```

---

### Modo 3: Execução no CORE

#### Preparação

1. **Deploy dos ficheiros para o CORE:**
```bash
make run-deploy
```
Isto copia todos os JARs necessários para `volume/`.

2. **Configurar o CORE (apenas na primeira vez):**

>  **IMPORTANTE:** O CORE está dockerizado e deve ser executado **FORA do devcontainer** do VS Code.

```bash
# Saia do devcontainer do VS Code ou abra um terminal fora dele
cd Dockerized-Coreemu-Template-main

# Primeira vez: executar setup
./setup.sh

# Este script irá:
# - Instalar dependências do Docker
# - Construir a imagem do CORE
```

3. **Iniciar o CORE:**

```bash
# Certifique-se que está FORA do devcontainer
cd Dockerized-Coreemu-Template-main
./core-gui
```

4. **Carregar topologia:**
   - Abra uma topologia em `volume/` (ex: `TopologiaBaseTP2.xml`)
   - Ou use topologias de teste:
     - `test-dup.xml` (10% duplicados)
     - `test-low-loss.xml` (5% perda)
     - `test-medium-loss.xml` (10% perda)
     - `test-high-loss.xml` (20% perda)

5. **Iniciar simulação no CORE:**
   - Clique no botão **Start** 

#### **Execução nos Nós**

**No nó da Nave-Mãe:**
```bash
./run-nave.sh
```

**Nos nós dos Rovers:**
```bash
# Rover 1
./run-rover1.sh

# Rover 2
./run-rover2.sh

# Rover 3
./run-rover3.sh

# Ou rover personalizado
./run-rover.sh <id> <posX> <posY> <ipNave> <portaTCP> <portaUDP>
```

**No nó do Ground Control:**
```bash
./run-ground-control.sh <endpoint>
#Exemplo 
#./run-ground-control.sh 10.0.0.1:8080
```

#### **Aceder à Interface no CORE**

1. Abra o navegador num nó
   ```bash
   firefox

   # Alternativa
   firefox --safe-mode
   ```
2. Aceda a: `http://<IP-Nave-Mae>:8080/ui/`

---

##  Testes

### **Testes Unitários**

Executa todos os testes JUnit do projeto:

```bash
make run-unit-test
```

---

##  Problemas Comuns

### **1. CORE não inicia ou erros de Docker**

**Causa:** CORE está dockerizado e precisa ser executado fora do devcontainer.

**Solução:**
```bash
# 1. Saia do devcontainer do VS Code
# 2. Abra um terminal normal no sistema host
# 3. Navegue até a pasta do CORE
cd Dockerized-Coreemu-Template-main

# 4. Se for a primeira vez, execute o setup
./setup.sh

# 5. Depois inicie o CORE
./core-gui
```

---

### **2. `./gradlew` não encontrado**

**Causa:** O wrapper do Gradle não está presente.

**Solução:**
```bash
# Usar gradle diretamente
gradle build
```

---



## Autores

- **Alice Soares** - A106804
- **Beatriz Freitas** - A106853
- **Tiago Martins** - A106927
