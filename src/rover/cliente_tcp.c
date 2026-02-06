#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <netinet/in.h>   
#include <arpa/inet.h>    
#include <mensagens.h>

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
    //FALTA MUDAR ISTO
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


