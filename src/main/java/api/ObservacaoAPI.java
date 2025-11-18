package api;

import nave.GestaoEstado;
import lib.Missao;
import lib.Rover;
import lib.mensagens.payloads.PayloadProgresso;
import lib.mensagens.payloads.PayloadTelemetria;

import java.util.Collection;
import java.util.Queue;

/*
* API de observação para aceder ao estado da Nave-Mãe.
 */
public class ObservacaoAPI {

    private final GestaoEstado estado;

    public ObservacaoAPI(GestaoEstado estado) {
        this.estado = estado;
    }

    /** Devolve todos os rovers. */
    public Collection<Rover> listarRovers() {
        return estado.listarRovers();
    }

    /** Devolve um rover específico ou null. */
    public Rover obterRover(int id) {
        return estado.obterRover(id);
    }

    /** Devolve todas as missões. */
    public Collection<Missao> listarMissoes() {
        return estado.listarMissoes();
    }

    /** Devolve uma missão específica ou null. */
    public Missao obterMissao(int id) {
        return estado.obterMissao(id);
    }

    /** Devolve o progresso de uma missão ou null. */
    public PayloadProgresso obterProgresso(int idMissao) {
        return estado.obterProgresso(idMissao);
    }

    /** Devolve a última telemetria de um rover. */
    public PayloadTelemetria obterUltimaTelemetria(int idRover) {
        return estado.obterUltimaTelemetria(idRover);
    }

    /** Devolve o histórico completo de telemetria. */
    public Queue<PayloadTelemetria> listarHistoricoTelemetria() {
        return estado.obterHistoricoTelemetria();
    }
}
