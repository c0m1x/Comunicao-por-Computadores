#ifndef MAQUINA_ESTADOS_H
#define MAQUINA_ESTADOS_H

#include <stdbool.h>
#include <time.h>
#include <pthread.h>
#include <netinet/in.h>
#include "mensagens.h"



#define INTERVALO_KEEPALIVE 10      // Segundos entre HELLOs
#define INTERVALO_TELEMETRIA_BASE 5 // Segundos entre telemetria
#define VELOCIDADE_ROVER 2.0        // Metros por segundo
#define TICK_SIMULACAO_MS 100       // Milissegundos por tick de simulação


// Estados do Rover
typedef enum {
    ESTADO_INICIAL,           // Rover acabado de ligar
    ESTADO_DISPONIVEL,        // Registado e aguardando missão
    ESTADO_RECEBENDO_MISSAO,  // Recebendo fragmentos de missão
    ESTADO_EM_MISSAO,         // Executando missão
    ESTADO_CONCLUIDO,         // Missão terminada com sucesso
    ESTADO_FALHA              // Erro/Falha
} EstadoRover;

// Eventos relevantes para telemetria (trigger de envio imediato)
typedef enum {
    EVENTO_NENHUM = 0,
    EVENTO_INICIO_MISSAO,
    EVENTO_FIM_MISSAO,
    EVENTO_BATERIA_BAIXA,
    EVENTO_MUDANCA_ESTADO,
    EVENTO_ERRO,
    EVENTO_CHECKPOINT_MISSAO
} EventoRelevante;

// Contexto completo do Rover (estado global)
typedef struct {

    int id_rover;
    int id_nave;  
    int socket_tcp;
    struct sockaddr_in nave_addr_udp;
    EstadoRover estado_atual;
    EstadoRover estado_anterior;
    bool ativo;  // false para terminar threads
    int id_missao_atual;
    PayloadMissao missao_atual;
    bool tem_missao;
    float progresso_missao;        // 0.0 a 100.0
    time_t timestamp_inicio_missao;
    float posicao_x;
    float posicao_y;
    float bateria;
    float velocidade;
    char estado_operacional[MAX_STR];  // "ACTIVE", "IN_MISSION", etc.    
    time_t ultimo_hello;
    time_t ultimo_envio_telemetria;
    EventoRelevante evento_pendente;
    EventoRelevante ultimo_evento;
    pthread_mutex_t mutex;  
} RoverContext;

/**
 * Inicializa o contexto do rover
 * @param ctx Ponteiro para o contexto
 * @param id_rover ID único do rover
 * @param pos_x Posição inicial X
 * @param pos_y Posição inicial Y
 */
void rover_init(RoverContext *ctx, int id_rover, float pos_x, float pos_y);

/**
 * Limpa recursos do contexto
 * @param ctx Ponteiro para o contexto
 */
void rover_cleanup(RoverContext *ctx);



/**
 * Executa um ciclo da máquina de estados
 * Deve ser chamada periodicamente no loop principal
 * @param ctx Ponteiro para o contexto
 */
void rover_update_state(RoverContext *ctx);

/**
 * Obtém o estado atual do rover (thread-safe)
 * @param ctx Ponteiro para o contexto
 * @return Estado atual
 */
EstadoRover rover_get_state(RoverContext *ctx);

/**
 * Converte estado para string
 * @param estado Estado a converter
 * @return String representando o estado
 */
const char* rover_state_to_string(EstadoRover estado);


/**
 * Processa mensagem RESPONSE recebida da Nave-Mãe
 * @param ctx Ponteiro para o contexto
 * @param msg Mensagem recebida
 */
void rover_handle_response(RoverContext *ctx, PayloadMissao *msg);

/**
 * Notifica que missão foi completamente recebida e desfragmentada
 * @param ctx Ponteiro para o contexto
 * @param missao Payload da missão completa
 * @param id_missao ID da missão
 */
void rover_missao_recebida(RoverContext *ctx, PayloadMissao *missao, int id_missao);

/**
 * Verifica se deve enviar HELLO (keepalive)
 * @param ctx Ponteiro para o contexto
 * @return true se deve enviar
 */
bool rover_deve_enviar_hello(RoverContext *ctx);


/**
 * Obtém dados de telemetria atual (thread-safe)
 * @param ctx Ponteiro para o contexto
 * @param payload Ponteiro para preencher com telemetria
 */
void rover_get_telemetria(RoverContext *ctx, PayloadTelemetria *payload);

/**
 * Verifica se deve enviar telemetria (baseado em tempo + eventos)
 * @param ctx Ponteiro para o contexto
 * @return true se deve enviar
 */
bool rover_deve_enviar_telemetria(RoverContext *ctx);

/**
 * Marca que telemetria foi enviada (atualiza timestamps)
 * @param ctx Ponteiro para o contexto
 */
void rover_telemetria_enviada(RoverContext *ctx);

/**
 * Obtém ID da missão atual
 * @param ctx Ponteiro para o contexto
 * @return ID da missão (-1 se nenhuma)
 */
int rover_get_missao_id(RoverContext *ctx);

/**
 * Obtém progresso da missão atual
 * @param ctx Ponteiro para o contexto
 * @return Progresso de 0.0 a 100.0
 */
float rover_get_progresso(RoverContext *ctx);

#endif // MAQUINA_ESTADOS_H