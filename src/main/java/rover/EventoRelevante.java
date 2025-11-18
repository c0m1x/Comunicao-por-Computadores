package rover;

/**
 * Eventos relevantes emitidos pelo rover para telemetria/monitorização.
 */
public enum EventoRelevante {
    EVENTO_NENHUM,
    EVENTO_INICIO_MISSAO,
    EVENTO_FIM_MISSAO,
    EVENTO_BATERIA_BAIXA,
    EVENTO_MUDANCA_ESTADO,
    EVENTO_ERRO,
    EVENTO_CHECKPOINT_MISSAO
}
