package rover;

import lib.mensagens.payloads.*;

import java.time.Instant;

import lib.Rover.EstadoRover;

/**
 * Contém ContextoRover e a lógica da máquina de estados.
 */
public class MaquinaEstados {

    private final ContextoRover contexto;

    public MaquinaEstados(int idRover, float posX, float posY) {
        this.contexto = new ContextoRover(idRover, posX, posY);
    }

    // Consulta de estado atual
    public EstadoRover getEstadoAtual() {
        return contexto.getEstado();
    }

    // Atualização da máquina de estados (chamada periódica)
    public void atualizar() {
        EstadoRover e = getEstadoAtual();
        switch (e) {
            case ESTADO_INICIAL:
                contexto.transicionarEstado(EstadoRover.ESTADO_DISPONIVEL);
                break;
            case ESTADO_DISPONIVEL:
                break;
            case ESTADO_RECEBENDO_MISSAO:
                // Timeout de 30 segundos para receber missão
                if (contexto.timestampInicioRecepcao == 0) {
                    contexto.timestampInicioRecepcao = Instant.now().getEpochSecond();
                }
                
                long tempoEspera = Instant.now().getEpochSecond() - contexto.timestampInicioRecepcao;
                if (tempoEspera > 30) {
                    System.out.println("[MaquinaEstados] Timeout ao receber missão. Revertendo para DISPONIVEL.");
                    contexto.timestampInicioRecepcao = 0;
                    contexto.transicionarEstado(EstadoRover.ESTADO_DISPONIVEL);
                }
                break;
            case ESTADO_EM_MISSAO:
                contexto.atualizarDuranteMissao();
                if (contexto.bateria <= 0.0f) {
                    System.out.println("[MaquinaEstados] Bateria esgotada! Missão falhada.");
                    contexto.transicionarEstado(EstadoRover.ESTADO_FALHA);
                    contexto.eventoPendente = EventoRelevante.EVENTO_ERRO_MISSAO;
                }
                break;
            case ESTADO_CONCLUIDO:
                contexto.concluirMissao();
                contexto.transicionarEstado(EstadoRover.ESTADO_DISPONIVEL);
                break;
            case ESTADO_FALHA:
                // Usar timestamp para controlar recuperação
                if (contexto.timestampEntradaFalha == 0) {
                    contexto.timestampEntradaFalha = Instant.now().getEpochSecond();
                    System.out.println("[MaquinaEstados] Rover em estado de falha.");
                }

                long tempoEmFalha = Instant.now().getEpochSecond() - contexto.timestampEntradaFalha;
                if (tempoEmFalha >= 5) {
                    contexto.timestampEntradaFalha = 0;
                    contexto.transicionarEstado(EstadoRover.ESTADO_DISPONIVEL);
                    System.out.println("[MaquinaEstados] Rover recuperado.");
                }
                break;
        }
    }

    // Processa recepção de missão
    public void receberMissao(PayloadMissao missao) {
        if (missao == null) {
            System.err.println("[MaquinaEstados] Missão nula recebida");
            return;
        }

        System.out.println("[MaquinaEstados] Rover " + contexto.idRover +
                " recebeu missão: " + missao);

        contexto.timestampInicioRecepcao = 0;
        contexto.iniciarMissao(missao);
        contexto.transicionarEstado(EstadoRover.ESTADO_EM_MISSAO);
    }

    // Exposição do contexto
    public ContextoRover getContexto() {
        return contexto;
    }

    private boolean missaoConcluida() {
        return contexto.progressoMissao >= 100.0f;
    }
}


