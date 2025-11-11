#include "maquina_estados.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <math.h>


static void transicionar_estado(RoverContext *ctx, EstadoRover novo_estado) {
    pthread_mutex_lock(&ctx->mutex);
    
    if (ctx->estado_atual != novo_estado) {
        ctx->estado_anterior = ctx->estado_atual;
        ctx->estado_atual = novo_estado;
        ctx->evento_pendente = EVENTO_MUDANCA_ESTADO;
        /*
        printf("[ROVER-%d] Estado: %s → %s\n",
               ctx->id_rover,
               rover_state_to_string(ctx->estado_anterior),
               rover_state_to_string(novo_estado));
    */
               }
    
    pthread_mutex_unlock(&ctx->mutex);
}

static void atualizar_estado_operacional(RoverContext *ctx, const char *estado) {
    pthread_mutex_lock(&ctx->mutex);
    strncpy(ctx->estado_operacional, estado, MAX_STR - 1);
    ctx->estado_operacional[MAX_STR - 1] = '\0';
    pthread_mutex_unlock(&ctx->mutex);
}

static void executar_passo_missao(RoverContext *ctx) {
    if (!ctx->tem_missao) return;
    
    // Calcula centro da área de missão
    float destino_x = (ctx->missao_atual.x1 + ctx->missao_atual.x2) / 2.0f;
    float destino_y = (ctx->missao_atual.y1 + ctx->missao_atual.y2) / 2.0f;
    
    // Calcula distância até destino
    float dx = destino_x - ctx->posicao_x;
    float dy = destino_y - ctx->posicao_y;
    float distancia = sqrtf(dx*dx + dy*dy);
    
    pthread_mutex_lock(&ctx->mutex);

    if (distancia > 0.5f) {
        float passo = 0.5f;  // 0.5 metros por tick
        ctx->posicao_x += (dx / distancia) * passo;
        ctx->posicao_y += (dy / distancia) * passo;
        ctx->velocidade = VELOCIDADE_ROVER;
    } else {
        ctx->velocidade = 0.0f;
    }
    
    // Consome bateria (0.1% por tick = 10% por segundo a 10 ticks/s)
    ctx->bateria -= 0.1f;
    if (ctx->bateria < 0.0f) ctx->bateria = 0.0f;

    if (ctx->bateria < 20.0f && ctx->ultimo_evento != EVENTO_BATERIA_BAIXA) {
        ctx->evento_pendente = EVENTO_BATERIA_BAIXA;
       // printf("[ROVER-%d] BATERIA BAIXA: %.1f%%\n", ctx->id_rover, ctx->bateria); PRINTS AQUI?????
    }

    time_t agora = time(NULL);
    double decorrido = difftime(agora, ctx->timestamp_inicio_missao);
    ctx->progresso_missao = (float)(decorrido / ctx->missao_atual.duracao_missao) * 100.0f;
    
    if (ctx->progresso_missao > 100.0f) {
        ctx->progresso_missao = 100.0f;
    }
    
    // Checkpoints a cada 25%
    int checkpoint = (int)(ctx->progresso_missao / 25.0f);
    static int ultimo_checkpoint = -1;
    if (checkpoint > ultimo_checkpoint) {
        ctx->evento_pendente = EVENTO_CHECKPOINT_MISSAO;
        ultimo_checkpoint = checkpoint;
        /*printf("[ROVER-%d] Checkpoint: %.0f%% concluído\n", 
               ctx->id_rover, ctx->progresso_missao);
    */
               }
    
    pthread_mutex_unlock(&ctx->mutex);
}

static bool missao_concluida(RoverContext *ctx) {
    pthread_mutex_lock(&ctx->mutex);
    bool concluida = ctx->progresso_missao >= 100.0f;
    pthread_mutex_unlock(&ctx->mutex);
    return concluida;
}


void rover_init(RoverContext *ctx, int id_rover, float pos_x, float pos_y) {
    memset(ctx, 0, sizeof(RoverContext));
    
    ctx->id_rover = id_rover;
    ctx->id_nave = 1;
    ctx->estado_atual = ESTADO_INICIAL;
    ctx->estado_anterior = ESTADO_INICIAL;
    ctx->ativo = true;
    
    ctx->posicao_x = pos_x;
    ctx->posicao_y = pos_y;
    ctx->bateria = 100.0f;
    ctx->velocidade = 0.0f;
    strcpy(ctx->estado_operacional, "INITIAL");
    
    ctx->id_missao_atual = -1;
    ctx->tem_missao = false;
    ctx->progresso_missao = 0.0f;
    
    ctx->ultimo_hello = 0;
    ctx->ultimo_envio_telemetria = 0;
    ctx->evento_pendente = EVENTO_NENHUM;
    ctx->ultimo_evento = EVENTO_NENHUM;
    
    pthread_mutex_init(&ctx->mutex, NULL);
    /*
    printf("[ROVER-%d] ✓ Contexto inicializado em (%.1f, %.1f)\n",
           id_rover, pos_x, pos_y);
    */
           }

void rover_cleanup(RoverContext *ctx) {
    ctx->ativo = false;
    pthread_mutex_destroy(&ctx->mutex);
    //printf("[ROVER-%d] ✓ Contexto limpo\n", ctx->id_rover);
}


void rover_update_state(RoverContext *ctx) {
    EstadoRover estado = rover_get_state(ctx);
    
    switch (estado) {
        case ESTADO_INICIAL:
            //printf("[ROVER-%d] Inicializando...\n", ctx->id_rover);
            atualizar_estado_operacional(ctx, "ACTIVE");
            transicionar_estado(ctx, ESTADO_DISPONIVEL);
            break;

        case ESTADO_DISPONIVEL:
            break;

        case ESTADO_RECEBENDO_MISSAO:
            break;
        
        case ESTADO_EM_MISSAO:
            executar_passo_missao(ctx);
            
            if (missao_concluida(ctx)) {
                /*printf("[ROVER-%d] ✓ Missão %d CONCLUÍDA!\n",
                       ctx->id_rover, ctx->id_missao_atual); */
                
                atualizar_estado_operacional(ctx, "SUCCESS");
                
                pthread_mutex_lock(&ctx->mutex);
                ctx->evento_pendente = EVENTO_FIM_MISSAO;
                pthread_mutex_unlock(&ctx->mutex);
                
                transicionar_estado(ctx, ESTADO_CONCLUIDO);
            }
            break;
        
        case ESTADO_CONCLUIDO:
            
            pthread_mutex_lock(&ctx->mutex);
            ctx->tem_missao = false;
            ctx->id_missao_atual = -1;
            ctx->progresso_missao = 0.0f;
            pthread_mutex_unlock(&ctx->mutex);
            
            atualizar_estado_operacional(ctx, "ACTIVE");
            //printf("[ROVER-%d] Voltando a DISPONÍVEL\n", ctx->id_rover);
            
            transicionar_estado(ctx, ESTADO_DISPONIVEL);
            break;
        
        case ESTADO_FALHA:
            //printf("[ROVER-%d] ✗ Estado de FALHA - tentando recuperar...\n",
            //      ctx->id_rover);
            
            sleep(5);  
            
            atualizar_estado_operacional(ctx, "ACTIVE");
            transicionar_estado(ctx, ESTADO_DISPONIVEL);
            break;
    }
}

EstadoRover rover_get_state(RoverContext *ctx) {
    pthread_mutex_lock(&ctx->mutex);
    EstadoRover estado = ctx->estado_atual;
    pthread_mutex_unlock(&ctx->mutex);
    return estado;
}

const char* rover_state_to_string(EstadoRover estado) {
    switch (estado) {
        case ESTADO_INICIAL:          return "INICIAL";
        case ESTADO_DISPONIVEL:       return "DISPONIVEL";
        case ESTADO_RECEBENDO_MISSAO: return "RECEBENDO_MISSAO";
        case ESTADO_EM_MISSAO:        return "EM_MISSAO";
        case ESTADO_CONCLUIDO:        return "CONCLUIDO";
        case ESTADO_FALHA:            return "FALHA";
        default:                      return "DESCONHECIDO";
    }
}


void rover_handle_response(RoverContext *ctx, PayloadMissao *msg) {
    (void)msg;  // Não usado por agora
    //printf("[ROVER-%d] ACK recebido da Nave-Mãe\n", ctx->id_rover);
}

void rover_missao_recebida(RoverContext *ctx, PayloadMissao *missao, int id_missao) {
    pthread_mutex_lock(&ctx->mutex);

    memcpy(&ctx->missao_atual, missao, sizeof(PayloadMissao));
    ctx->id_missao_atual = id_missao;
    ctx->tem_missao = true;
    ctx->progresso_missao = 0.0f;
    ctx->timestamp_inicio_missao = time(NULL);
    ctx->evento_pendente = EVENTO_INICIO_MISSAO;
    
    pthread_mutex_unlock(&ctx->mutex);
    
    /*
    printf("[ROVER-%d] ═══════════════════════════════════════\n", ctx->id_rover);
    printf("[ROVER-%d] MISSÃO %d RECEBIDA\n", ctx->id_rover, id_missao);
    printf("[ROVER-%d] Área: (%.1f,%.1f) → (%.1f,%.1f)\n",
           ctx->id_rover, missao->x1, missao->y1, missao->x2, missao->y2);
    printf("[ROVER-%d] Tarefa: %s\n", ctx->id_rover, missao->tarefa);
    printf("[ROVER-%d] Duração: %ld seg | Update: %ld seg\n",
           ctx->id_rover, missao->duracao_missao, missao->intervalo_atualizacao);
    printf("[ROVER-%d] Prioridade: %d\n", ctx->id_rover, missao->prioridade);
    printf("[ROVER-%d] ═══════════════════════════════════════\n", ctx->id_rover);
    */
    atualizar_estado_operacional(ctx, "IN_MISSION");
    transicionar_estado(ctx, ESTADO_EM_MISSAO);
}

bool rover_deve_enviar_hello(RoverContext *ctx) {
    pthread_mutex_lock(&ctx->mutex);
    time_t agora = time(NULL);
    bool deve_enviar = difftime(agora, ctx->ultimo_hello) >= INTERVALO_KEEPALIVE;
    
    if (deve_enviar) {
        ctx->ultimo_hello = agora;
    }
    
    pthread_mutex_unlock(&ctx->mutex);
    return deve_enviar;
}

void rover_get_telemetria(RoverContext *ctx, PayloadTelemetria *payload) {
    pthread_mutex_lock(&ctx->mutex);
    
    payload->posicaox = ctx->posicao_x;
    payload->posicaoy = ctx->posicao_y;
    strncpy(payload->estado_operacional, ctx->estado_operacional, MAX_STR - 1);
    payload->estado_operacional[MAX_STR - 1] = '\0';
    payload->bateria = ctx->bateria;
    payload->velocidade = ctx->velocidade;
    
    pthread_mutex_unlock(&ctx->mutex);
}

bool rover_deve_enviar_telemetria(RoverContext *ctx) {
    pthread_mutex_lock(&ctx->mutex);
    
    time_t agora = time(NULL);
    bool deve_enviar = false;

    if (difftime(agora, ctx->ultimo_envio_telemetria) >= INTERVALO_TELEMETRIA_BASE) {
        deve_enviar = true;
    }
    
    if (ctx->evento_pendente != EVENTO_NENHUM) {
        deve_enviar = true;
        ctx->ultimo_evento = ctx->evento_pendente;
        ctx->evento_pendente = EVENTO_NENHUM;
    }
    
    pthread_mutex_unlock(&ctx->mutex);
    return deve_enviar;
}

void rover_telemetria_enviada(RoverContext *ctx) {
    pthread_mutex_lock(&ctx->mutex);
    ctx->ultimo_envio_telemetria = time(NULL);
    pthread_mutex_unlock(&ctx->mutex);
}

int rover_get_missao_id(RoverContext *ctx) {
    pthread_mutex_lock(&ctx->mutex);
    int id = ctx->id_missao_atual;
    pthread_mutex_unlock(&ctx->mutex);
    return id;
}

float rover_get_progresso(RoverContext *ctx) {
    pthread_mutex_lock(&ctx->mutex);
    float progresso = ctx->progresso_missao;
    pthread_mutex_unlock(&ctx->mutex);
    return progresso;
}