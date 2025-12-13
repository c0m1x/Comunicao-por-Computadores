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

### Software

- **Java**: JDK 17 ou superior
- **Gradle**: 7.0+ (wrapper incluído)
- **Navegador**: Firefox (para Ground Control)
- **CORE**: 9.1.0+ 

### Sistema Operativo

- Linux (Ubuntu 20.04+, Debian 11+)
- macOS 11+

### Dependências

Todas as dependências são geridas pelo Gradle:
```groovy
dependencies {
    implementation 'com.google.code.gson:gson:2.10.1'
    testImplementation 'junit:junit:4.13.2'
}
```

## Instalação e Compilação

### 1. Clonar o Repositório
```bash
git clone <url-do-repositorio>
```

### 2. Compilar o Projeto
```bash
# Compilação completa
make build

# Ou diretamente com Gradle
./gradlew build
```

---

## Execução

### **Modo 1: Execução Manual (Componentes Separados)**

#### 1. Iniciar Nave-Mãe
```bash
make run-nave
```

#### 2. Iniciar Rovers
```bash
# Terminal 2 - Rover 1
make run-rover1

# Terminal 3 - Rover 2
make run-rover2

# Terminal 4 - Rover 3
make run-rover3
```

**Sintaxe personalizada - se pretendido:**
```bash
make run-rover ARGS="<id> <posX> <posY> <ipNave> <portaTcpNave> <portaUdpRover>"

# Exemplo:
make run-rover ARGS="4 20.0 15.0 127.0.0.1 5001 9014"
```

#### 3. Iniciar Ground Control
```bash
# Terminal 5
make run-ground-control
```

#### 4. Aceder à Interface Web

Abrir no navegador: **http://localhost:8080/ui/**

---

### **Modo 2: Demo Rápida**

Inicia todos os componentes automaticamente:
```bash
make run-demo
```

Isto executa:
1. Nave-Mãe (fundo)
2. Ground Control CLI (fundo)
3. Rover 1 (fundo)

Para parar tudo:
```bash
make demo-kill
```

---

### **Modo 3: Execução no CORE**



## Autores

- **Alice Soares** - A106804
- **Beatriz Freitas** - A106853
- **Tiago Martins** - A106927
