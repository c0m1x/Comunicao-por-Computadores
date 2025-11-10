#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <netinet/in.h>   
#include <arpa/inet.h>    
#include <mensagens.h>
#include <pthread.h>
#include <stdbool.h>

// Estrutura para cada conexão de rover
typedef struct {
    int socket;
    int id_rover;
    pthread_t thread;
    bool ativo;
} ConexaoRover;

int const MAX_ROVERS = 10;

ConexaoRover conexoes[MAX_ROVERS];
int num_conexoes = 0;
pthread_mutex_t mutex_conexoes = PTHREAD_MUTEX_INITIALIZER;

// Thread para cada rover
void *thread_handler_rover(void *arg) {
    ConexaoRover *conn = (ConexaoRover*)arg;
    MensagemTCP msg;
    
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


