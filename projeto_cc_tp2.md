# Projeto CC - TP2: Sistema de Comunicação Nave-Mãe/Rovers
## Arquitetura Geral do Sistema

```
┌─────────────────────────────────────────────────────────────┐
│                     Ground Control (Python)                  │
│                  (HTTP/SSE Client - Visualização)            │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP/SSE
                       ↓
┌─────────────────────────────────────────────────────────────┐
│                   Nave-Mãe (C + Python)                      │
│  ┌────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │ Servidor UDP   │  │  Servidor TCP   │  │ API HTTP/SSE │ │
│  │ (MissionLink)  │  │(TelemetryStream)│  │  (Python)    │ │
│  └────────────────┘  └─────────────────┘  └──────────────┘ │
│            ↕                    ↕                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │        Gestão de Estado (Rovers + Missões)           │   │
│  │        - Memória partilhada ou IPC (pipes/fifos)     │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────────┬───────────────────┬──────────────────────┘
                   │                   │
            UDP    │                   │    TCP
          (ML)     │                   │    (TS)
                   ↓                   ↓
        ┌───────────────────┐  ┌───────────────────┐
        │   Rede Satélites  │  │   Rede Satélites  │
        │    (Routers)      │  │    (Routers)      │
        └─────────┬─────────┘  └─────────┬─────────┘
                  │                       │
         ┌────────┴────────┐     ┌───────┴────────┐
         ↓                 ↓     ↓                ↓
    ┌─────────┐       ┌─────────┐           ┌─────────┐
    │ Rover 1 │       │ Rover 2 │    ...    │ Rover N │
    │  (C)    │       │  (C)    │           │  (C)    │
    └─────────┘       └─────────┘           └─────────┘
     UDP+TCP           UDP+TCP               UDP+TCP
```

---

## 1. Protocolo MissionLink (ML) - UDP

### 1.1 Fluxo de Comunicação

#### Fase 1: Registo e Disponibilidade
```
Rover                                    Nave-Mãe
  │                                         │
  │─────── MSG_HELLO (disponível) ────────>│
  │                                         │ (regista rover)
  │<──────── MSG_RESPONSE (ACK) ───────────│
  │                                         │
  │                                         │
  │─── MSG_HELLO (keepalive periódico) ───>│
  │<──────── MSG_RESPONSE (ACK) ───────────│
  │                                         │
```

#### Fase 2: Atribuição de Missão
```
Rover                                    Nave-Mãe
  │                                         │
  │                                         │ (decide atribuir missão)
  │<────── MSG_MISSION (fragmento 1/N) ────│
  │─────────── MSG_ACK (frag 1) ──────────>│
  │                                         │
  │<────── MSG_MISSION (fragmento 2/N) ────│
  │─────────── MSG_ACK (frag 2) ──────────>│
  │                                         │
  │          ... (fragmentos 3 a N-1) ...  │
  │                                         │
  │<────── MSG_MISSION (fragmento N/N) ────│
  │─────────── MSG_ACK (frag N) ──────────>│
  │                                         │
  │ (desfragmenta e inicia missão)         │
  │                                         │
```

#### Fase 3: Execução de Missão
```
Rover                                    Nave-Mãe
  │                                         │
  │── MSG_ACK (progresso + telemetria) ───>│
  │<──────── MSG_RESPONSE (ACK) ───────────│
  │                                         │
  │   (intervalo ou evento relevante)      │
  │                                         │
  │── MSG_ACK (progresso + telemetria) ───>│
  │<──────── MSG_RESPONSE (ACK) ───────────│
  │                                         │
  │── MSG_ACK (missão concluída) ─────────>│
  │<──────── MSG_RESPONSE (ACK) ───────────│
  │                                         │
  │─────── MSG_HELLO (disponível) ────────>│
  │                                         │
```

### 1.2 Mecanismos de Fiabilidade (UDP)

#### Stop-and-Wait com Timeout
```c
// Pseudo-código Nave-Mãe
void enviar_missao_confiavel(int socket, MissaoUDP *missao, struct sockaddr_in *rover_addr) {
    int tentativas = 0;
    int MAX_TENTATIVAS = 5;
    int TIMEOUT_MS = 2000;
    bool ack_recebido = false;
    
    while (!ack_recebido && tentativas < MAX_TENTATIVAS) {
        // Envia fragmento
        sendto(socket, missao, sizeof(MissaoUDP), 0, 
               (struct sockaddr*)rover_addr, sizeof(*rover_addr));
        
        // Aguarda ACK com timeout
        fd_set readfds;
        struct timeval tv = {.tv_sec = TIMEOUT_MS/1000, .tv_usec = (TIMEOUT_MS%1000)*1000};
        FD_ZERO(&readfds);
        FD_SET(socket, &readfds);
        
        if (select(socket + 1, &readfds, NULL, NULL, &tv) > 0) {
            MissaoUDP ack;
            recvfrom(socket, &ack, sizeof(ack), 0, NULL, NULL);
            
            if (ack.header.tipo == MSG_ACK && 
                ack.header.seq == missao->header.seq) {
                ack_recebido = true;
                printf("ACK recebido para seq=%d\n", ack.header.seq);
            }
        } else {
            tentativas++;
            printf("Timeout! Retransmitindo seq=%d (tentativa %d)\n", 
                   missao->header.seq, tentativas);
        }
    }
    
    if (!ack_recebido) {
        fprintf(stderr, "ERRO: Falha após %d tentativas\n", MAX_TENTATIVAS);
    }
}
```

#### Números de Sequência e Fragmentação
```c
// Estrutura para controlo de fragmentação
typedef struct {
    int seq;           // Número de sequência do fragmento (0 a total_fragm-1)
    int total_fragm;   // Total de fragmentos da mensagem completa
    uint32_t checksum; // Checksum simples para validação de integridade
} InfoFragmento;

// Função para calcular checksum
uint32_t calcular_checksum(void *data, size_t len) {
    uint32_t sum = 0;
    uint8_t *bytes = (uint8_t*)data;
    for (size_t i = 0; i < len; i++) {
        sum += bytes[i];
    }
    return sum;
}

// Fragmentar missão
void fragmentar_missao(PayloadNaveMae *payload, MissaoUDP fragmentos[], int *num_fragmentos) {
    size_t tamanho_fragmento = 512; // bytes
    size_t tamanho_total = sizeof(PayloadNaveMae);
    *num_fragmentos = (tamanho_total + tamanho_fragmento - 1) / tamanho_fragmento;
    
    uint8_t *data = (uint8_t*)payload;
    for (int i = 0; i < *num_fragmentos; i++) {
        fragmentos[i].header.seq = i;
        fragmentos[i].header.total_fragm = *num_fragmentos;
        
        size_t offset = i * tamanho_fragmento;
        size_t size = (i == *num_fragmentos - 1) ? 
                      (tamanho_total - offset) : tamanho_fragmento;
        
        memcpy(&fragmentos[i].payload, data + offset, size);
    }
}

// Desfragmentar no rover
bool desfragmentar_missao(MissaoUDP fragmentos[], int total, PayloadNaveMae *payload_completo) {
    // Verifica se tem todos os fragmentos
    bool fragmentos_recebidos[total];
    memset(fragmentos_recebidos, 0, sizeof(fragmentos_recebidos));
    
    for (int i = 0; i < total; i++) {
        int seq = fragmentos[i].header.seq;
        if (seq >= 0 && seq < total) {
            fragmentos_recebidos[seq] = true;
        }
    }
    
    // Verifica se todos foram recebidos
    for (int i = 0; i < total; i++) {
        if (!fragmentos_recebidos[i]) {
            return false;
        }
    }
    
    // Reconstrói payload
    uint8_t *dest = (uint8_t*)payload_completo;
    for (int i = 0; i < total; i++) {
        memcpy(dest + i * 512, &fragmentos[i].payload, 
               (i == total - 1) ? sizeof(PayloadNaveMae) % 512 : 512);
    }
    
    return true;
}
```

### 1.3 Estados do Rover (Máquina de Estados)
```c
typedef enum {
    ESTADO_INICIAL,      // Rover acabado de ligar
    ESTADO_DISPONIVEL,   // Registado e aguardando missão
    ESTADO_RECEBENDO_MISSAO, // Recebendo fragmentos
    ESTADO_EM_MISSAO,    // Executando missão
    ESTADO_REPORTANDO,   // Enviando progresso
    ESTADO_CONCLUIDO,    // Missão terminada
    ESTADO_FALHA         // Erro/Falha
} EstadoRover;

void maquina_estados_rover(EstadoRover *estado, MissaoUDP *msg_recebida) {
    switch (*estado) {
        case ESTADO_INICIAL:
            enviar_hello();
            *estado = ESTADO_DISPONIVEL;
            break;
            
        case ESTADO_DISPONIVEL:
            if (msg_recebida && msg_recebida->header.tipo == MSG_MISSION) {
                iniciar_recepcao_fragmentos();
                *estado = ESTADO_RECEBENDO_MISSAO;
            }
            // Envia keepalive periódico
            if (tempo_desde_ultimo_hello() > INTERVALO_KEEPALIVE) {
                enviar_hello();
            }
            break;
            
        case ESTADO_RECEBENDO_MISSAO:
            if (todos_fragmentos_recebidos()) {
                PayloadNaveMae missao;
                if (desfragmentar_missao(buffer_fragmentos, total_frags, &missao)) {
                    iniciar_execucao_missao(&missao);
                    *estado = ESTADO_EM_MISSAO;
                } else {
                    *estado = ESTADO_FALHA;
                }
            }
            break;
            
        case ESTADO_EM_MISSAO:
            executar_passo_missao();
            if (deve_reportar_progresso()) {
                enviar_progresso();
                *estado = ESTADO_REPORTANDO;
            }
            if (missao_concluida()) {
                enviar_conclusao();
                *estado = ESTADO_CONCLUIDO;
            }
            break;
            
        case ESTADO_REPORTANDO:
            if (ack_progresso_recebido()) {
                *estado = ESTADO_EM_MISSAO;
            }
            break;
            
        case ESTADO_CONCLUIDO:
            enviar_hello(); // Volta a disponível
            *estado = ESTADO_DISPONIVEL;
            break;
            
        case ESTADO_FALHA:
            reportar_falha();
            tentar_recuperacao();
            break;
    }
}
```

---

## 2. Protocolo TelemetryStream (TS) - TCP

### 2.1 Conexão Persistente
```c
// Rover: Estabelece conexão TCP com Nave-Mãe
int conectar_telemetria(const char *ip_nave, int porta) {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    struct sockaddr_in nave_addr = {
        .sin_family = AF_INET,
        .sin_port = htons(porta),
    };
    inet_pton(AF_INET, ip_nave, &nave_addr.sin_addr);
    
    if (connect(sock, (struct sockaddr*)&nave_addr, sizeof(nave_addr)) < 0) {
        perror("Erro ao conectar telemetria");
        return -1;
    }
    
    printf("Conexão TCP estabelecida para telemetria\n");
    return sock;
}

// Thread de envio de telemetria
void *thread_telemetria(void *arg) {
    RoverContext *ctx = (RoverContext*)arg;
    int sock_tcp = ctx->socket_tcp;
    
    while (ctx->ativo) {
        MissaoTCP msg_telemetria;
        msg_telemetria.header.tipo = MSG_ACK; // Reusa estrutura
        msg_telemetria.header.id_emissor = ctx->id_rover;
        msg_telemetria.header.timestamp = time(NULL);
        
        // Preenche dados de telemetria
        obter_telemetria_atual(ctx, &msg_telemetria);
        
        // Envia via TCP (garantia de entrega)
        if (send(sock_tcp, &msg_telemetria, sizeof(msg_telemetria), 0) < 0) {
            perror("Erro ao enviar telemetria");
            break;
        }
        
        // Aguarda próximo intervalo ou evento
        aguardar_proximo_envio(ctx);
    }
    
    close(sock_tcp);
    return NULL;
}
```

### 2.2 Servidor TCP na Nave-Mãe (Múltiplos Rovers)
```c
// Estrutura para cada conexão de rover
typedef struct {
    int socket;
    int id_rover;
    pthread_t thread;
    bool ativo;
} ConexaoRover;

ConexaoRover conexoes[MAX_ROVERS];
int num_conexoes = 0;
pthread_mutex_t mutex_conexoes = PTHREAD_MUTEX_INITIALIZER;

// Thread para cada rover
void *thread_handler_rover(void *arg) {
    ConexaoRover *conn = (ConexaoRover*)arg;
    MissaoTCP msg;
    
    while (conn->ativo) {
        ssize_t n = recv(conn->socket, &msg, sizeof(msg), 0);
        if (n <= 0) {
            printf("Rover %d desconectou\n", conn->id_rover);
            break;
        }
        
        // Processa telemetria
        processar_telemetria(&msg, conn->id_rover);
        
        // Atualiza estado interno
        atualizar_estado_rover(conn->id_rover, &msg);
    }
    
    close(conn->socket);
    conn->ativo = false;
    return NULL;
}

// Aceita conexões TCP
void servidor_tcp_telemetria(int porta) {
    int server_sock = socket(AF_INET, SOCK_STREAM, 0);
    struct sockaddr_in addr = {
        .sin_family = AF_INET,
        .sin_port = htons(porta),
        .sin_addr.s_addr = INADDR_ANY
    };
    
    bind(server_sock, (struct sockaddr*)&addr, sizeof(addr));
    listen(server_sock, 10);
    
    printf("Servidor TCP telemetria escuta na porta %d\n", porta);
    
    while (1) {
        struct sockaddr_in client_addr;
        socklen_t len = sizeof(client_addr);
        int client_sock = accept(server_sock, (struct sockaddr*)&client_addr, &len);
        
        pthread_mutex_lock(&mutex_conexoes);
        
        if (num_conexoes < MAX_ROVERS) {
            ConexaoRover *conn = &conexoes[num_conexoes++];
            conn->socket = client_sock;
            conn->ativo = true;
            pthread_create(&conn->thread, NULL, thread_handler_rover, conn);
            pthread_detach(conn->thread);
        }
        
        pthread_mutex_unlock(&mutex_conexoes);
    }
}
```

### 2.3 Granularidade Temporal (Mix)
```c
// Configuração de envio de telemetria
typedef struct {
    time_t intervalo_base;        // Ex: 5 segundos
    bool enviar_em_eventos;       // true = envia também em eventos
} ConfigTelemetria;

// Eventos relevantes que disparam envio
typedef enum {
    EVENTO_INICIO_MISSAO,
    EVENTO_FIM_MISSAO,
    EVENTO_BATERIA_BAIXA,        // < 20%
    EVENTO_MUDANCA_ESTADO,
    EVENTO_ERRO,
    EVENTO_CHECKPOINT_MISSAO      // Ex: cada 25% de progresso
} EventoRelevante;

// Lógica de decisão de envio
bool deve_enviar_telemetria(RoverContext *ctx, ConfigTelemetria *config) {
    time_t agora = time(NULL);
    
    // Critério 1: Intervalo de tempo
    if (difftime(agora, ctx->ultimo_envio_telemetria) >= config->intervalo_base) {
        return true;
    }
    
    // Critério 2: Evento relevante
    if (config->enviar_em_eventos && ctx->evento_pendente != EVENTO_NENHUM) {
        return true;
    }
    
    return false;
}

void aguardar_proximo_envio(RoverContext *ctx) {
    ConfigTelemetria *config = &ctx->config_telemetria;
    
    // Aguarda até próximo intervalo OU até evento
    while (!deve_enviar_telemetria(ctx, config)) {
        usleep(100000); // 100ms
        
        // Verifica eventos
        if (ctx->bateria < 20.0 && ctx->ultimo_evento != EVENTO_BATERIA_BAIXA) {
            ctx->evento_pendente = EVENTO_BATERIA_BAIXA;
            break;
        }
        
        if (ctx->estado_mudou) {
            ctx->evento_pendente = EVENTO_MUDANCA_ESTADO;
            ctx->estado_mudou = false;
            break;
        }
    }
    
    ctx->ultimo_envio_telemetria = time(NULL);
    ctx->evento_pendente = EVENTO_NENHUM;
}
```

---

## 3. Encriptação ByteStream

### 3.1 Implementação de XOR Cipher Simples
```c
// cipher.h
#ifndef CIPHER_H
#define CIPHER_H

#include <stdint.h>
#include <stddef.h>

#define CHAVE_SIZE 16

// Encripta/Desencripta (XOR é simétrico)
void bytestream_encrypt_decrypt(uint8_t *data, size_t len, const uint8_t *chave);

// Encripta mensagem UDP
void encriptar_mensagem_udp(MissaoUDP *msg, const uint8_t *chave);
void desencriptar_mensagem_udp(MissaoUDP *msg, const uint8_t *chave);

// Encripta mensagem TCP
void encriptar_mensagem_tcp(MissaoTCP *msg, const uint8_t *chave);
void desencriptar_mensagem_tcp(MissaoTCP *msg, const uint8_t *chave);

#endif
```

```c
// cipher.c
#include "cipher.h"
#include <string.h>

void bytestream_encrypt_decrypt(uint8_t *data, size_t len, const uint8_t *chave) {
    for (size_t i = 0; i < len; i++) {
        data[i] ^= chave[i % CHAVE_SIZE];
    }
}

void encriptar_mensagem_udp(MissaoUDP *msg, const uint8_t *chave) {
    // Encripta apenas o payload, mantém header legível para roteamento
    bytestream_encrypt_decrypt((uint8_t*)&msg->payload, 
                               sizeof(msg->payload), chave);
}

void desencriptar_mensagem_udp(MissaoUDP *msg, const uint8_t *chave) {
    // XOR é simétrico, mesma função
    encriptar_mensagem_udp(msg, chave);
}

void encriptar_mensagem_tcp(MissaoTCP *msg, const uint8_t *chave) {
    bytestream_encrypt_decrypt((uint8_t*)&msg->payload, 
                               sizeof(msg->payload), chave);
}

void desencriptar_mensagem_tcp(MissaoTCP *msg, const uint8_t *chave) {
    encriptar_mensagem_tcp(msg, chave);
}
```

### 3.2 Distribuição de Chaves
```c
// Chave partilhada (simplificação - em produção usar troca de chaves)
const uint8_t CHAVE_MISSAO[CHAVE_SIZE] = {
    0xDE, 0xAD, 0xBE, 0xEF, 0xCA, 0xFE, 0xBA, 0xBE,
    0x12, 0x34, 0x56, 0x78, 0x9A, 0xBC, 0xDE, 0xF0
};

// Uso na Nave-Mãe (envio)
void enviar_missao_encriptada(int socket, MissaoUDP *missao, struct sockaddr_in *rover_addr) {
    encriptar_mensagem_udp(missao, CHAVE_MISSAO);
    sendto(socket, missao, sizeof(*missao), 0, 
           (struct sockaddr*)rover_addr, sizeof(*rover_addr));
}

// Uso no Rover (receção)
void receber_missao_encriptada(int socket, MissaoUDP *missao) {
    recvfrom(socket, missao, sizeof(*missao), 0, NULL, NULL);
    desencriptar_mensagem_udp(missao, CHAVE_MISSAO);
}
```

---

## 4. API de Observação (Python)

### 4.1 Estrutura da API Flask + SSE
```python
# api_observacao.py
from flask import Flask, jsonify, Response, request
from flask_cors import CORS
import json
import time
import threading
from queue import Queue
from dataclasses import dataclass, asdict
from typing import List, Dict
import struct
import socket

app = Flask(__name__)
CORS(app)

# Estruturas de dados (sincronizadas com C)
@dataclass
class EstadoRover:
    id: int
    posicao_x: float
    posicao_y: float
    estado: str  # "ACTIVE", "IN_MISSION", "INACTIVE", etc.
    bateria: float
    velocidade: float
    ultimo_update: float

@dataclass
class Missao:
    id: int
    id_rover: int
    x1: float
    y1: float
    x2: float
    y2: float
    tarefa: str
    duracao: int
    prioridade: int
    estado: str  # "ATRIBUIDA", "EM_CURSO", "CONCLUIDA", "FALHADA"
    timestamp_inicio: float
    progresso: float  # 0.0 a 100.0

# Estado global (thread-safe)
class EstadoSistema:
    def __init__(self):
        self.rovers: Dict[int, EstadoRover] = {}
        self.missoes: Dict[int, Missao] = {}
        self.lock = threading.Lock()
        self.event_queue = Queue()
        
    def atualizar_rover(self, rover: EstadoRover):
        with self.lock:
            self.rovers[rover.id] = rover
            self.event_queue.put({
                'tipo': 'rover_update',
                'data': asdict(rover)
            })
    
    def atualizar_missao(self, missao: Missao):
        with self.lock:
            self.missoes[missao.id] = missao
            self.event_queue.put({
                'tipo': 'missao_update',
                'data': asdict(missao)
            })
    
    def get_rovers(self):
        with self.lock:
            return [asdict(r) for r in self.rovers.values()]
    
    def get_missoes(self):
        with self.lock:
            return [asdict(m) for m in self.missoes.values()]

estado_sistema = EstadoSistema()

# ========== ENDPOINTS HTTP (REST) ==========

@app.route('/api/rovers', methods=['GET'])
def get_rovers():
    """Lista todos os rovers e seu estado atual"""
    return jsonify({
        'success': True,
        'rovers': estado_sistema.get_rovers(),
        'timestamp': time.time()
    })

@app.route('/api/rovers/<int:rover_id>', methods=['GET'])
def get_rover(rover_id):
    """Detalhes de um rover específico"""
    with estado_sistema.lock:
        rover = estado_sistema.rovers.get(rover_id)
        if not rover:
            return jsonify({'success': False, 'error': 'Rover não encontrado'}), 404
        return jsonify({
            'success': True,
            'rover': asdict(rover),
            'timestamp': time.time()
        })

@app.route('/api/missoes', methods=['GET'])
def get_missoes():
    """Lista todas as missões"""
    estado_filter = request.args.get('estado')  # filtro opcional
    missoes = estado_sistema.get_missoes()
    
    if estado_filter:
        missoes = [m for m in missoes if m['estado'] == estado_filter]
    
    return jsonify({
        'success': True,
        'missoes': missoes,
        'timestamp': time.time()
    })

@app.route('/api/missoes/<int:missao_id>', methods=['GET'])
def get_missao(missao_id):
    """Detalhes de uma missão específica"""
    with estado_sistema.lock:
        missao = estado_sistema.missoes.get(missao_id)
        if not missao:
            return jsonify({'success': False, 'error': 'Missão não encontrada'}), 404
        return jsonify({
            'success': True,
            'missao': asdict(missao),
            'timestamp': time.time()
        })

@app.route('/api/status', methods=['GET'])
def get_status():
    """Visão geral do sistema"""
    missoes = estado_sistema.get_missoes()
    rovers = estado_sistema.get_rovers()
    
    return jsonify({
        'success': True,
        'resumo': {
            'total_rovers': len(rovers),
            'rovers_ativos': len([r for r in rovers if r['estado'] in ['ACTIVE', 'IN_MISSION']]),
            'total_missoes': len(missoes),
            'missoes_ativas': len([m for m in missoes if m['estado'] == 'EM_CURSO']),
            'missoes_concluidas': len([m for m in missoes if m['estado'] == 'CONCLUIDA'])
        },
        'timestamp': time.time()
    })

# ========== SERVER-SENT EVENTS (SSE) ==========

@app.route('/api/stream')
def stream():
    """Stream de eventos em tempo real"""
    def event_stream():
        yield f"data: {json.dumps({'tipo': 'init', 'rovers': estado_sistema.get_rovers(), 'missoes': estado_sistema.get_missoes()})}\n\n"

        while True:
            if not estado_sistema.event_queue.empty():
                event = estado_sistema.event_queue.get()
                yield f"data: {json.dumps(event)}\n\n"
            else:
                # Heartbeat a cada 15 segundos
                yield f": heartbeat\n\n"
                time.sleep(15)
    
    return Response(event_stream(), mimetype='text/event-stream')

# ========== IPC com Programa C (Pipe/FIFO) ==========

def ler_dados_ipc():
    """Thread que lê dados do programa C via named pipe"""
    FIFO_PATH = '/tmp/nave_mae_ipc'
    
    while True:
        try:
            with open(FIFO_PATH, 'rb') as fifo:
                while True:
                    # Formato esperado: tipo(1) + id(4) + dados(...)
                    tipo_msg = struct.unpack('B', fifo.read(1))[0]
                    
                    if tipo_msg == 1:  # Atualização de rover
                        dados = struct.unpack('iffff', fifo.read(20))
                        rover = EstadoRover(
                            id=dados[0],
                            posicao_x=dados[1],
                            posicao_y=dados[2],
                            bateria=dados[3],
                            velocidade=dados[4],
                            estado="ACTIVE",
                            ultimo_update=time.time()
                        )
                        estado_sistema.atualizar_rover(rover)
                    
                    elif tipo_msg == 2:  # Atualização de missão
                        dados = struct.unpack('iiiffffi', fifo.read(32))
                        missao = Missao(
                            id=dados[0],
                            id_rover=dados[1],
                            x1=dados[2],
                            y1=dados[3],
                            x2=dados[4],
                            y2=dados[5],
                            tarefa="",
                            duracao=dados[6],
                            prioridade=dados[7],
                            estado="EM_CURSO",
                            timestamp_inicio=time.time(),
                            progresso=0.0
                        )
                        estado_sistema.atualizar_missao(missao)
                        
        except Exception as e:
            print(f"Erro IPC: {e}")
            time.sleep(1)

# Inicia thread IPC
threading.Thread(target=ler_dados_ipc, daemon=True).start()

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, threaded=True)
```

### 4.2 Cliente Ground Control (Python)
```python
# ground_control.py
import requests
import sseclient
import time
from rich.console import Console
from rich.table import Table
from rich.live import Live
from rich.layout import Layout
from rich.panel import Panel
import threading

console = Console()

class GroundControl:
    def __init__(self, api_url='http://localhost:5000'):
        self.api_url = api_url
        self.rovers = {}
        self.missoes = {}
        self.running = True
        
    def conectar_stream(self):
        """Conecta ao stream SSE"""
        url = f"{self.api_url}/api/stream"
        response = requests.get(url, stream=True)
        client = sseclient.SSEClient(response)
        
        for event in client.events():
            if not self.running:
                break
                
            data = json.loads(event.data)
            
            if data['tipo'] == 'init':
                self.rovers = {r['id']: r for r in data['rovers']}
                self.missoes = {m['id']: m for m in data['missoes']}
            elif data['tipo'] == 'rover_update':
                self.rovers[data['data']['id']] = data['data']
            elif data['tipo'] == 'missao_update':
                self.missoes[data['data']['id']] = data['data']
    
    def gerar_display(self):
        
        layout = Layout()
        layout.split_column(
            Layout(name="header", size=3),
            Layout(name="body"),
            Layout(name="footer", size=3)
        )
        
        # Header
        layout["header"].update(Panel("Ground Control - Sistema de Monitorização"))
        
        # Body - Tabela de rovers
        table_rovers = Table(title="Rovers Ativos")
        table_rovers.add_column("ID", style="cyan")
        table_rovers.add_column("Posição", style="magenta")
        table_rovers.add_column("Estado", style="green")
        table_rovers.add_column("Bateria", style="yellow")
        
        for rover in self.rovers.values():
            table_rovers.add_row(
                str(rover['id']),
                f"({rover['posicao_x']:.2f}, {rover['posicao_y']:.2f})",
                rover['estado'],
                f"{rover['bateria']:.1f}%"
            )
        
        # Tabela de missões
        table_missoes = Table(title="Missões")
        table_missoes.add_column("ID", style="cyan")
        table_missoes.add_column("Rover", style="magenta")
        table_missoes.add_column("Estado", style="green")
        table_missoes.add_column("Progresso", style="yellow")
        
        for missao in self.missoes.values():
            table_missoes.add_row(
                str(missao['id']),
                str(missao['id_rover']),
                missao['estado'],
                f"{missao['progresso']:.1f}%"
            )
        
        layout["body"].split_row(
            Layout(table_rovers),
            Layout(table_missoes)
        )
        
        # Footer
        layout["footer"].update(
            Panel(f"Última atualização: {time.strftime('%H:%M:%S')}")
        )
        
        return layout
    
    def run(self):
        """Executa Ground Control"""
        # Thread para stream SSE
        thread_stream = threading.Thread(target=self.conectar_stream, daemon=True)
        thread_stream.start()
        
        # Display ao vivo
        with Live(self.gerar_display(), refresh_per_second=2, console=console) as live:
            while self.running:
                try:
                    live.update(self.gerar_display())
                    time.sleep(0.5)
                except KeyboardInterrupt:
                    self.running = False
                    break

if __name__ == '__main__':
    gc = GroundControl()
    gc.run()
```

---

## 5. Estrutura de Código C

### 5.1 Organização de Ficheiros
```
projeto/
├── common/
│   ├── mensagens.h          (já temos isto criado)
│   ├── cipher.h / cipher.c
│   └── utils.h / utils.c
├── nave_mae/
│   ├── main.c
│   ├── servidor_udp.c / .h
│   ├── servidor_tcp.c / .h
│   ├── gestao_estado.c / .h
│   └── ipc_api.c / .h
├── rover/
│   ├── main.c
│   ├── cliente_udp.c / .h
│   ├── cliente_tcp.c / .h
│   ├── simulacao_missao.c / .h
│   └── maquina_estados.c / .h
├── api/
│   ├── api_observacao.py
│   └── requirements.txt
├── ground_control/
│   ├── ground_control.py
│   └── requirements.txt
├── core/
│   └── topologia.imn
├── scripts/
│   ├── compilar.sh
│   ├── executar_nave.sh
│   ├── executar_rover.sh
│   └── testes.sh
└── docs/
    └── relatorio.md
```

### 5.2 Makefile
```makefile
CC = gcc
CFLAGS = -Wall -Wextra -pthread -O2
LDFLAGS = -lm

COMMON_DIR = common
NAVE_DIR = nave_mae
ROVER_DIR = rover
BUILD_DIR = build

COMMON_SRCS = $(COMMON_DIR)/cipher.c $(COMMON_DIR)/utils.c
COMMON_OBJS = $(patsubst $(COMMON_DIR)/%.c,$(BUILD_DIR)/%.o,$(COMMON_SRCS))

NAVE_SRCS = $(wildcard $(NAVE_DIR)/*.c)
NAVE_OBJS = $(patsubst $(NAVE_DIR)/%.c,$(BUILD_DIR)/nave_%.o,$(NAVE_SRCS))

ROVER_SRCS = $(wildcard $(ROVER_DIR)/*.c)
ROVER_OBJS = $(patsubst $(ROVER_DIR)/%.c,$(BUILD_DIR)/rover_%.o,$(ROVER_SRCS))

all: $(BUILD_DIR) nave_mae rover

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(BUILD_DIR)/%.o: $(COMMON_DIR)/%.c
	$(CC) $(CFLAGS) -c $< -o $@

$(BUILD_DIR)/nave_%.o: $(NAVE_DIR)/%.c
	$(CC) $(CFLAGS) -I$(COMMON_DIR) -c $< -o $@

nave_mae: $(COMMON_OBJS) $(NAVE_OBJS)
	$(CC) $(CFLAGS) $^ -o $(BUILD_DIR)/$@ $(LDFLAGS)

$(BUILD_DIR)/rover_%.o: $(ROVER_DIR)/%.c
	$(CC) $(CFLAGS) -I$(COMMON_DIR) -c $< -o $@

rover: $(COMMON_OBJS) $(ROVER_OBJS)
	$(CC) $(CFLAGS) $^ -o $(BUILD_DIR)/$@ $(LDFLAGS)

clean:
	rm -rf $(BUILD_DIR)

.PHONY: all clean



#Comandos

# Compilar tudo
make clean && make

# Executar Nave-Mãe
./build/nave_mae 9000 9001

# Executar Rover
./build/rover 1 10.0.0.1 9000 9001

# API Python
cd api && python3 api_observacao.py

# Ground Control
cd ground_control && python3 ground_control.py
```