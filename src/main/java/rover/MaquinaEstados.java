package rover;

import lib.mensagens.payloads.*;
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
                break;
            case ESTADO_EM_MISSAO:
                contexto.atualizarDuranteMissao();
                if (missaoConcluida()) {
                    contexto.transicionarEstado(EstadoRover.ESTADO_CONCLUIDO);
                    contexto.eventoPendente = EventoRelevante.EVENTO_FIM_MISSAO;
                }
                break;
            case ESTADO_CONCLUIDO:
                contexto.concluirMissao();
                contexto.transicionarEstado(EstadoRover.ESTADO_DISPONIVEL);
                break;
            case ESTADO_FALHA:
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                contexto.transicionarEstado(EstadoRover.ESTADO_DISPONIVEL);
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


