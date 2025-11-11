#include "stdio.h"
#include "stdint.h"
#include "stdbool.h"
#include "mensagens.h"
#include "time.h"
#include "maquina_estados.h"

typedef enum {
    ESTADO_INICIAL,      // Rover acabado de ligar
    ESTADO_DISPONIVEL,   // Registado e aguardando missão
    ESTADO_RECEBENDO_MISSAO, // Recebendo fragmentos
    ESTADO_EM_MISSAO,    // Executando missão
    ESTADO_REPORTANDO,   // Enviando progresso
    ESTADO_CONCLUIDO,    // Missão terminada
    ESTADO_FALHA         // Erro/Falha
} EstadoRover;

void maquina_estados_rover(EstadoRover *estado, PayloadUDP *msg_recebida) {
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
                PayloadMissao missao;
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

typedef struct {
    FragmentoUDP fragmentos[MAX_FRAGMENTOS];
    bool recebido[MAX_FRAGMENTOS];  // Bitmap de fragmentos recebidos
    int total_esperado;
    int total_recebido;
    uint32_t missao_id;
    time_t timestamp_inicio;
} BufferMissao;

BufferMissao buffer_atual;

// Inicializa buffer para nova missão
void iniciar_recepcao_missao(uint32_t missao_id, int total_fragmentos) {
    memset(&buffer_atual, 0, sizeof(buffer_atual));
    buffer_atual.missao_id = missao_id;
    buffer_atual.total_esperado = total_fragmentos;
    buffer_atual.timestamp_inicio = time(NULL);
    
    printf("[ROVER] Iniciando recepção missão %u (%d fragmentos)\n", 
           missao_id, total_fragmentos);
}

// Processa fragmento recebido
bool processar_fragmento(FragmentoUDP *fragmento) {
    uint16_t seq = fragmento->header.seq_num;
    uint32_t missao_id = fragmento->header.missao_id;
    
    // Valida fragmento
    if (seq >= MAX_FRAGMENTOS || seq >= fragmento->header.total_fragments) {
        printf("[ROVER] ✗ Fragmento %d inválido\n", seq);
        return false;
    }
    
    // Verifica checksum
    uint32_t checksum_calculado = calcular_checksum(fragmento->payload, 512);
    if (checksum_calculado != fragmento->header.checksum) {
        printf("[ROVER] ✗ Checksum inválido no fragmento %d\n", seq);
        return false;
    }
    
    // Primeira vez vendo esta missão?
    if (buffer_atual.total_esperado == 0 || buffer_atual.missao_id != missao_id) {
        iniciar_recepcao_missao(missao_id, fragmento->header.total_fragments);
    }
    
    // Armazena fragmento se ainda não foi recebido
    if (!buffer_atual.recebido[seq]) {
        memcpy(&buffer_atual.fragmentos[seq], fragmento, sizeof(FragmentoUDP));
        buffer_atual.recebido[seq] = true;
        buffer_atual.total_recebido++;
        
        printf("[ROVER] ✓ Fragmento %d/%d recebido [%d/%d completos]\n", 
               seq, buffer_atual.total_esperado,
               buffer_atual.total_recebido, buffer_atual.total_esperado);
    } else {
        printf("[ROVER] ⚠ Fragmento %d duplicado (ignorado)\n", seq);
    }
    
    return true;
}

// Verifica se missão está completa
bool missao_completa() {
    return buffer_atual.total_recebido == buffer_atual.total_esperado;
}

// Envia ACK final
void enviar_ack_final(int socket, struct sockaddr_in *nave_addr) {
    AckFinal ack;
    memset(&ack, 0, sizeof(ack));
    
    ack.tipo = MSG_ACK;
    ack.id_emissor = ID_ROVER;
    ack.missao_id = buffer_atual.missao_id;
    ack.timestamp = time(NULL);
    
    if (missao_completa()) {
        ack.status = STATUS_COMPLETO;
        ack.total_recebidos = buffer_atual.total_esperado;
        printf("[ROVER] Enviando ACK: MISSÃO COMPLETA\n");
        
    } else {
        ack.status = STATUS_INCOMPLETO;
        
        // Lista fragmentos em falta
        int idx = 0;
        for (int i = 0; i < buffer_atual.total_esperado && idx < MAX_FRAGMENTOS; i++) {
            if (!buffer_atual.recebido[i]) {
                ack.fragmentos_perdidos[idx++] = i;
            }
        }
        ack.fragmentos_perdidos[idx] = 0xFFFF; // Marcador de fim
        ack.total_recebidos = idx;
        
        printf("[ROVER] Enviando ACK: FALTAM %d fragmentos\n", idx);
    }
    
    sendto(socket, &ack, sizeof(ack), 0, 
           (struct sockaddr*)nave_addr, sizeof(*nave_addr));
}

// Reconstrói missão completa
bool reconstruir_missao(PayloadMissao *payload_completo) {
    if (!missao_completa()) {
        return false;
    }
    
    uint8_t *dest = (uint8_t*)payload_completo;
    size_t tamanho_fragmento = 512;
    
    for (int i = 0; i < buffer_atual.total_esperado; i++) {
        size_t offset = i * tamanho_fragmento;
        size_t tamanho = (i == buffer_atual.total_esperado - 1) ? 
                         (sizeof(PayloadMissao) - offset) : tamanho_fragmento;
        
        memcpy(dest + offset, buffer_atual.fragmentos[i].payload, tamanho);
    }
    
    printf("[ROVER] ✓ Missão %u reconstruída com sucesso!\n", buffer_atual.missao_id);
    return true;
}

void *thread_recepcao_udp(void *arg) {
    RoverContext *ctx = (RoverContext*)arg;
    int sock_udp = ctx->socket_udp;
    struct sockaddr_in nave_addr;
    socklen_t addr_len = sizeof(nave_addr);
    
    // Timeout para detecção de fim de transmissão
    struct timeval tv = {.tv_sec = 3, .tv_usec = 0};
    setsockopt(sock_udp, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    
    while (ctx->ativo) {
        FragmentoUDP fragmento;
        ssize_t n = recvfrom(sock_udp, &fragmento, sizeof(fragmento), 0,
                            (struct sockaddr*)&nave_addr, &addr_len);
        
        if (n > 0) {
            if (fragmento.header.tipo == MSG_MISSION) {
                processar_fragmento(&fragmento);
                
                // Se recebeu o último fragmento esperado, aguarda um pouco mais
                // para garantir que não há fragmentos atrasados
                if (missao_completa()) {
                    usleep(500000); // 500ms de grace period
                    
                    // Envia ACK final
                    enviar_ack_final(sock_udp, &nave_addr);
                    
                    // Reconstrói e processa missão
                    PayloadMissao missao;
                    if (reconstruir_missao(&missao)) {
                        iniciar_execucao_missao(ctx, &missao);
                    }
                    
                    // Reseta buffer
                    memset(&buffer_atual, 0, sizeof(buffer_atual));
                }
            }
            
        } else if (errno == EAGAIN || errno == EWOULDBLOCK) {
            // Timeout - se já recebeu fragmentos, envia ACK parcial
            if (buffer_atual.total_recebido > 0 && !missao_completa()) {
                printf("[ROVER] Timeout - enviando ACK parcial\n");
                enviar_ack_final(sock_udp, &nave_addr);
            }
        }
    }
    
    return NULL;
}