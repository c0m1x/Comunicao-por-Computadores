#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <time.h>

#define MAX_STR 64
#define TIPO_MENSAGEM_SIZE 16
#define MAX_MISSING_FRAGMENTS 32

typedef enum {
    MSG_HELLO = 1,     // Rover -> Nave-Mãe : pedido inicial
    MSG_RESPONSE,      // Nave-Mãe -> Rover : resposta ao HELLO
    MSG_MISSION,       // Nave-Mãe -> Rover : atribuição de missão
    MSG_ACK            // Rover -> Nave-Mãe : confirmação de receção / execução
} TipoMensagem;

// -------------------------------
// Mensagem UDP
// -------------------------------

// Cabeçalho UDP
typedef struct {
    TipoMensagem tipo;      
    int id_emissor;
    int id_recetor;
    int id_missao;
    time_t timestamp;      // instante de criação
    int seq;
    int total_fragm;
    bool flag_sucesso;      // Indica o sucesso ou não da receção da mensagem total, ou uma resposta positiva/negativa, por parte do rover
} CabecalhoUDP;

// Payloads possíveis para mensagens UDP

// Payload para mensagens de missão (Nave-Mãe -> Rover)
typedef struct {
    int id_missao;
    float x1, y1, x2, y2;         //area_geografica_missao - quadrado
    char tarefa[MAX_STR];
    time_t duracao_missao;
    time_t intervalo_atualizacao; //intervalos minimos de atualização de telemetria
    time_t inicio_missao;         //instante de início da missão (DateTime seria melhor talvez?)
    int prioridade;               //0 a 5
} PayloadMissao;

// ACK payload: lista de fragmentos em falta (pode ser empty se nenhum em falta)
typedef struct {
    int missing_count;                         //número de fragmentos em falta
    int missing[MAX_MISSING_FRAGMENTS];        //lista de índices de fragmentos em falta
} PayloadAck;

// Union discriminado com os possíveis payloads UDP.
// O campo `header.tipo` deve ser usado para determinar qual membro é válido.
typedef union {
    PayloadAck ack;        // ACK com segmentos em falta
    PayloadMissao mission;
} PayloadUDP;

// Mensagem UDP genérica (tanto Rover -> Nave-Mãe quanto Nave-Mãe -> Rover)
typedef struct {
    CabecalhoUDP header;
    PayloadUDP payload;
} MensagemUDP;

// -------------------------------
// Mensagem TCP
// -------------------------------

// Cabeçalho TCP
typedef struct {
    TipoMensagem tipo;      
    int id_emissor;        
    int id_recetor;       
    int id_missao;        
    time_t timestamp;      
} CabecalhoTCP;


// Telemetria do Rover (agora enviada por TCP)
typedef struct {
    float posicaox, posicaoy;                // posicao_atual_rover
    char estado_operacional[MAX_STR];        // "FAILURE", "ACTIVE", "IN_MISSION", "INACTIVE", "SUCCESS" (talvez passar isto para um enum?)
    float bateria;
    float velocidade;
} PayloadTelemetria;

//NOTA: nao sei se há mais algum tipo de payload usado no TCP para além deste

// Mensagem TCP para Telemetria enviada pelo Rover → Nave-Mãe
typedef struct {
    CabecalhoTCP header;
    PayloadTelemetria payload;
} MensagemTCP;