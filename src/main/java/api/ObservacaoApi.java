package api;

import nave.Rover;
import nave.Missao;
import lib.mensagens.payloads.PayloadTelemetria;
import lib.mensagens.payloads.PayloadProgresso;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * API REST de Observação da Nave-Mãe.
 *
 * Esta API não altera estado nenhum — apenas lê o estado atual das estruturas internas.
 */
@RestController
@RequestMapping("/api/observacao")
public class ObservacaoApi {



    public ObservacaoApi(Map<Integer, Rover> rovers, Map<Integer, Missao> missoes, List<PayloadTelemetria> historicoTelemetria, Map<Integer, PayloadProgresso> progressoMissoes, Set<Integer> missoesConcluidas) {
        this.rovers = rovers;
        this.missoes = missoes;
        this.historicoTelemetria = historicoTelemetria;
        this.progressoMissoes = progressoMissoes;
        this.missoesConcluidas = missoesConcluidas;
    }

    /** Lista todos os rovers com todos os atributos da classe Rover. */
    @GetMapping("/rovers")
    public Collection<Rover> listarRovers() {
        return rovers.values();
    }

    /** Devolve um rover específico. */
    @GetMapping("/rovers/{id}")
    public Rover obterRover(@PathVariable int id) {
        return rovers.get(id);
    }

    /** Lista todas as missões com todos os campos, incluindo estado. */
    @GetMapping("/missoes")
    public Collection<Missao> listarMissoes() {
        return missoes.values();
    }

    /** Detalhes completos de uma missão. */
    @GetMapping("/missoes/{id}")
    public Missao obterMissao(@PathVariable int id) {
        return missoes.get(id);
    }

    /** Progresso de uma missão individual. */
    @GetMapping("/missoes/{id}/progresso")
    public PayloadProgresso obterProgresso(@PathVariable int id) {
        return progressoMissoes.get(id);
    }

    /** Lista todo o progresso conhecido associado a todas as missoes. */
    @GetMapping("/missoes/progresso")
    public Collection<PayloadProgresso> listarProgresso() {
        return progressoMissoes.values();
    }

    @GetMapping("/missoes/concluidas")
    public Set<Integer> listarConcluidas() {
        return missoesConcluidas;
    }

    /** Devolve todo o histórico de telemetria recebido. */
    @GetMapping("/telemetria")
    public List<PayloadTelemetria> listarTelemetria() {
        return historicoTelemetria;
    }

    /** Últimas N telemetrias (por default as últimas 10). */
    @GetMapping("/telemetria/latest")
    public List<PayloadTelemetria> ultimasTelemetrias(
            @RequestParam(defaultValue = "10") int n) {

        if (historicoTelemetria.size() <= n)
            return historicoTelemetria;

        return historicoTelemetria.subList(
                historicoTelemetria.size() - n,
                historicoTelemetria.size()
        );
    }

    //todo: telemetria por rover
}
