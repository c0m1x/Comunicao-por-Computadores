package rover;

import java.time.Instant;

import lib.Mensagens;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Contém RoverContext e logica da máquina de estados.
 * Tradução direta de maquina_estados.c.
 */
public class MaquinaEstados {

    public enum EstadoRover {
        ESTADO_INICIAL,
        ESTADO_DISPONIVEL,
        ESTADO_RECEBENDO_MISSAO,
        ESTADO_EM_MISSAO,
        ESTADO_CONCLUIDO,
        ESTADO_FALHA
    }

    public enum EventoRelevante {
        EVENTO_NENHUM,
        EVENTO_INICIO_MISSAO,
        EVENTO_FIM_MISSAO,
        EVENTO_BATERIA_BAIXA,
        EVENTO_MUDANCA_ESTADO,
        EVENTO_ERRO,
        EVENTO_CHECKPOINT_MISSAO
    }

    
}

