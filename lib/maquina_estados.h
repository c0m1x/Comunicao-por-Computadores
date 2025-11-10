#ifndef ROVER_PROTOCOL_H
#define ROVER_PROTOCOL_H

#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <time.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <mensagens.h>

#include "mensagens.h" /* fornece MissaoUDP, MissaoTCP, PayloadNaveMae, CabecalhoUDP/TCP, etc. */

/* -------------------------
   Constantes (definir se não estiverem definidas noutros headers)
   ------------------------- */
#ifndef MAX_FRAGMENTOS
#define MAX_FRAGMENTOS 1024
#endif

#ifndef INTERVALO_KEEPALIVE
#define INTERVALO_KEEPALIVE 30 /* segundos (padrão) */
#endif

#ifndef ID_ROVER
#define ID_ROVER 1
#endif

#ifndef STATUS_COMPLETO
#define STATUS_COMPLETO 1
#endif

#ifndef STATUS_INCOMPLETO
#define STATUS_INCOMPLETO 0
#endif



/* -------------------------
   Estado da máquina de estados do rover
   ------------------------- */
typedef enum {
    ESTADO_INICIAL,           /* Rover acabado de ligar */
    ESTADO_DISPONIVEL,        /* Registado e aguardando missão */
    ESTADO_RECEBENDO_MISSAO,  /* Recebendo fragmentos */
    ESTADO_EM_MISSAO,         /* Executando missão */
    ESTADO_REPORTANDO,        /* Enviando progresso */
    ESTADO_CONCLUIDO,         /* Missão terminada */
    ESTADO_FALHA              /* Erro/Falha */
} EstadoRover;


/* -------------------------
   Buffer para armazenar fragmentos de missão
   ------------------------- */
typedef struct {
    FragmentoUDP fragmentos[MAX_FRAGMENTOS];
    bool recebido[MAX_FRAGMENTOS];  
    int total_esperado;
    int total_recebido;
    uint32_t missao_id;
    time_t timestamp_inicio;
} BufferMissao;


extern BufferMissao buffer_atual;



/* Processamento de fragmento(recebe/valida e armazena) */
bool processar_fragmento(FragmentoUDP *fragmento);

/* Estado da missão */
bool missao_completa(void);

/* ACKs e reconstrução */
void enviar_ack_final(int socket, struct sockaddr_in *nave_addr);
bool reconstruir_missao(PayloadMissao *payload_completo);

/* Thread de recepção UDP (prototipo para criação de threads) */
void *thread_recepcao_udp(void *arg);

#endif 
