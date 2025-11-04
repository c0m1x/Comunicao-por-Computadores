#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <time.h>

#define MAX_STR 64
#define TIPO_MENSAGEM_SIZE 16

typedef enum {
    MSG_HELLO = 1,     // Rover -> Nave-Mãe : pedido inicial
    MSG_RESPONSE,      // Nave-Mãe -> Rover : resposta ao HELLO
    MSG_MISSION,       // Nave-Mãe -> Rover : atribuição de missão
    MSG_ACK            // Rover -> Nave-Mãe : confirmação de receção / execução
} TipoMensagem;


// Cabeçalho UDP
typedef struct {
    TipoMensagem tipo;      
    int id_emissor;        
    int id_recetor;       
    int id_missao;        
    time_t timestamp;      //instante de criação
    int seq;
    int total_fragm;
    bool flag_sucesso;      //Indica o sucesso ou não da receção da mensagem total, ou uma resposta positiva/negativa, por parte do rover
} CabecalhoUDP;

// -------------------------------
// Mensagem do Rover → Nave-Mãe
// -------------------------------
typedef struct {
    float posicaox, posicaoy;                //posicao_atual_rover
    char estado_operacional[MAX_STR];        //"FAILURE", "ACTIVE", "IN_MISSION", "INACTIVE", "SUCCESS"
    float bateria;
    float velocidade;
} PayloadRover;

typedef struct {
    CabecalhoUDP header;
    PayloadRover payload;
} MissaoUDP;

// -------------------------------
// Mensagem da Nave-Mãe → Rover
// -------------------------------
typedef struct {
    float x1, y1, x2, y2;           //area_geografica_missao - quadrado
    char tarefa[MAX_STR];
    time_t duracao_missao;
    time_t intervalo_atualizacao;
    int prioridade;                //0 a 5
} PayloadNaveMae;

// Cabeçalho TCP
typedef struct {
    TipoMensagem tipo;      
    int id_emissor;        
    int id_recetor;       
    int id_missao;        
    time_t timestamp;      
} CabecalhoTCP;

typedef struct {
    CabecalhoTCP header;
    PayloadNaveMae payload;
} MissaoTCP;