#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <netinet/in.h>   
#include <arpa/inet.h>    
#include <mensagens.h>
#include <maquina_estados.h>

//ROVER establece conexão com a Nave Mãe para enviar telemetria via TCP
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

void *thread_telemetria(void *arg) {
    RoverContext *ctx = (RoverContext*)arg;
    int sock_tcp = ctx->socket_tcp;

    while (ctx->ativo) {
       
        MensagemTCP msg_telemetria;
        memset(&msg_telemetria, 0, sizeof(msg_telemetria));

        
        msg_telemetria.header.tipo = MSG_ACK; 
        msg_telemetria.header.id_emissor = ctx->id_rover;
        msg_telemetria.header.id_recetor = ctx->id_nave;     
        msg_telemetria.header.id_missao = ctx->id_missao_atual;
        msg_telemetria.header.timestamp = time(NULL);

        obter_telemetria_atual(ctx, &msg_telemetria.payload);

        ssize_t bytes_enviados = send(sock_tcp, &msg_telemetria, sizeof(msg_telemetria), 0);
        if (bytes_enviados < 0) {
            perror("[ROVER] Erro ao enviar telemetria");
            break;
        }

        printf("[ROVER] Telemetria enviada (%zd bytes)\n", bytes_enviados);

        aguardar_proximo_envio(ctx);
    }

    close(sock_tcp);
    return NULL;
}


