package nave;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lib.Missao;
import lib.Rover;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;

import lib.mensagens.payloads.PayloadProgresso;
import lib.mensagens.payloads.PayloadTelemetria;

/**
 * Gestão de estado central (armazenamento de rovers e missões).
 * Armazenamento thread-safe usando ConcurrentHashMap porque a Nave-Mãe
 * acede e modifica estes mapas a partir de várias threads.
 */
public class GestaoEstado {

    private ConcurrentHashMap<Integer, Rover> rovers;
    private ConcurrentHashMap<Integer, Missao> missoes;
    private ConcurrentHashMap<Integer, PayloadProgresso> progressoMissoes;
    private ConcurrentHashMap<Integer, PayloadTelemetria> ultimaTelemetria;
    private final ConcurrentLinkedQueue<PayloadTelemetria> historicoTelemetria;
    private ConcurrentSkipListSet<Integer> missoesConcluidas;

    public GestaoEstado(){
        this.rovers = new ConcurrentHashMap <>();
        this.missoes = new ConcurrentHashMap<>();
        this.progressoMissoes = new ConcurrentHashMap<>();
        this.ultimaTelemetria = new ConcurrentHashMap<>();
        this.historicoTelemetria = new ConcurrentLinkedQueue<>();
        this.missoesConcluidas = new ConcurrentSkipListSet<>();

        // uma missao que ocupe mais de 512 bytes para testar fragmentação
        Missao m1 = new Missao(1, "Explorar cratera A " + "x".repeat(500), Missao.EstadoMissao.PENDENTE, 2, 0.0f, 0.0f, 10.0f, 10.0f, 30, 3);
        Missao m2 = new Missao(2, "Coletar amostras do solo", Missao.EstadoMissao.PENDENTE, 1, 10.0f, 10.0f, 20.0f, 5.0f, 40, 3);
        Missao m3 = new Missao(3, "Analisar atmosfera", Missao.EstadoMissao.PENDENTE, 3, 20.0f, 5.0f, 15.0f, 15.0f, 25, 2);

        this.adicionarMissao(m1.idMissao, m1);
        this.adicionarMissao(m2.idMissao, m2);
        this.adicionarMissao(m3.idMissao, m3);
    }
// ----- Rovers -----

/** Adiciona ou substitui um Rover com o id fornecido. */
    public void adicionarRover(int id, Rover ctx) {
        rovers.put(id, ctx);
    }

    /** Remove e devolve o Rover associado ao id, ou null se não existir. */
    public Rover removerRover(int id) {
        return rovers.remove(id);
    }

    /** Devolve o Rover associado ao id, ou null se não existir. */
    public Rover obterRover(int id) {
        return rovers.get(id);
    }

    /** Devolve uma vista (live) de todos os Rovers armazenados. */
    public Collection<Rover> listarRovers() {
        return rovers.values();
    }

    /** Devolve o mapa (live) de rovers. Usar com cuidado: é thread-safe. */
    public Map<Integer, Rover> mapaRovers() {
        return rovers;
    }

    public boolean existeRover(int id) {
        return rovers.containsKey(id);
    }

    /** Insere o rover apenas se não existir já um com o mesmo id. Retorna true se inseriu. */
    public boolean inserirRoverSeAusente(int id, Rover ctx) {
        if (ctx == null) throw new NullPointerException("Rover não pode ser null");
        return rovers.putIfAbsent(id, ctx) == null;
    }
 
    public Rover obterRoverDisponivel() {
        for (Rover r : rovers.values()) {
            if (!r.temMissao && r.estadoRover == Rover.EstadoRover.ESTADO_DISPONIVEL) {
                return r;
            }
        }
        return null;
    }

    // ----- Missões -----

    /** Adiciona ou substitui uma missão com o id fornecido. */
    public void adicionarMissao(int id, Missao missao) {
        missoes.put(id, missao);
    }

    /**
     * Adiciona uma nova missão ao estado (via API HTTP)
     */
    public synchronized void adicionarMissao(Missao missao) {
        if (missoes.containsKey(missao.idMissao)) {
            System.out.println("[Estado] Missão #" + missao.idMissao + " já existe. Substituindo...");
        }

        missoes.put(missao.idMissao, missao);
        System.out.println("[Estado] Missão #" + missao.idMissao + " adicionada: " + missao.tarefa);
    }

    /** Remove e devolve a missão associada ao id, ou null se não existir. */
    public Missao removerMissao(int id) {
        return missoes.remove(id);
    }

    /** Devolve a missão associada ao id, ou null se não existir. */
    public Missao obterMissao(int id) {
        return missoes.get(id);
    }

    /** Devolve uma vista (live) de todas as missões armazenadas. */
    public Collection<Missao> listarMissoes() {
        return missoes.values();
    }

    /** Devolve o mapa (live) de missões. Usar com cuidado: é thread-safe. */
    public Map<Integer, Missao> mapaMissoes() {
        return missoes;
    }

    public void marcarMissaoComoConcluida(int idMissao) {
        Missao m = missoes.get(idMissao);
        if (m != null) {
            m.estadoMissao = Missao.EstadoMissao.CONCLUIDA;
            missoesConcluidas.add(idMissao);
        }
    }

    public Set<Integer> listarMissoesConcluidas() {
        return missoesConcluidas;
    }

    /** Devolve uma missão que ainda não tenha sido atribuida. */
    public Missao obterMissaoNaoAtribuida() {
        for (Missao missao : missoes.values()) {
            if (missao.estadoMissao == Missao.EstadoMissao.PENDENTE) {
                return missao;
            }
        }
        return null;
    }

    /** Insere a missão apenas se não existir já uma com o mesmo id. Retorna true se inseriu. */
    public boolean inserirMissaoSeAusente(int id, Missao missao) {
        if (missao == null) throw new NullPointerException("missao não pode ser null");
        return missoes.putIfAbsent(id, missao) == null;
    }

    // ------ Telemetria -------

    public void atualizarTelemetria(int idRover, PayloadTelemetria p) {
        ultimaTelemetria.put(idRover, p);

        Rover r = rovers.get(idRover);
        if (r != null) {
            r.posicaoX = p.posicaoX;
            r.posicaoY = p.posicaoY;
            r.estadoRover = p.estadoOperacional;
            r.bateria = p.bateria;
            r.velocidade = p.velocidade;
        }

        historicoTelemetria.add(p);
    }

    public PayloadTelemetria obterUltimaTelemetria(int idRover) {
        return ultimaTelemetria.get(idRover);
    }

    public Queue<PayloadTelemetria> obterHistoricoTelemetria() {
        return historicoTelemetria;
    }

    // ----- Progresso -----

    public PayloadProgresso obterProgresso(int idMissao) {
        return progressoMissoes.get(idMissao);
    }

    public Map<Integer, PayloadProgresso> listarProgressoMissoes() {
        return progressoMissoes;
    }
    
    /**
     * Atualiza o progresso de uma missão. Atualiza estado da missao e do rover
     */
    public synchronized void atualizarProgresso(PayloadProgresso p) {
        if (p == null) return;

        progressoMissoes.put(p.idMissao, p);

        // IMPORTANTE: Atualizar também o progressoMissao do Rover
        for (Rover rover : rovers.values()) {
            if (rover.temMissao && rover.idMissaoAtual == p.idMissao) {
                rover.progressoMissao = p.progressoPercentagem;
                break;
            }
        }
        // atualizar a Missao
        Missao missao = missoes.get(p.idMissao);
        if (missao != null) {
            if (missao.estadoMissao == Missao.EstadoMissao.PENDENTE) {
                missao.estadoMissao = Missao.EstadoMissao.EM_ANDAMENTO;
            }

            if (p.progressoPercentagem >= 100.0f) {
                missao.estadoMissao = Missao.EstadoMissao.CONCLUIDA;
                missoesConcluidas.add(p.idMissao);
            }
        }
    }

    /** Atualiza o progresso de uma missão associada a um rover. - funciona como wrapper*/
    public void atualizarProgressoMissao(int idRover, int idMissao, float progressoPercent) {
        PayloadProgresso p = new PayloadProgresso(idMissao, 0, progressoPercent);

        atualizarProgresso(p);
    }

    /** Marca uma missão como EM_ANDAMENTO quando é atribuída a um rover. */
    public void atribuirMissaoARover(int idRover, int idMissao) {
        Rover rover = obterRover(idRover);
        Missao missao = obterMissao(idMissao);
        if (rover == null || missao == null) return;

        // Atualizar estado da missão
        missao.estadoMissao = Missao.EstadoMissao.EM_ANDAMENTO;

        // Atualizar estado do rover
        rover.temMissao = true;
        rover.idMissaoAtual = idMissao;
        rover.estadoRover = Rover.EstadoRover.ESTADO_EM_MISSAO;
    }

    /** Conclui ou cancela uma missão, atualizando estado do rover e da missão. */
    public synchronized void concluirMissao(int idRover, int idMissao, boolean sucesso) {
        Rover rover = obterRover(idRover);
        Missao missao = obterMissao(idMissao);
        if (rover == null || missao == null) return;

        if (sucesso) {
            missao.estadoMissao = Missao.EstadoMissao.CONCLUIDA;
        } else {
            missao.estadoMissao = Missao.EstadoMissao.FALHADA;
        }

        rover.temMissao = false;
        rover.idMissaoAtual = -1;
        rover.estadoRover = Rover.EstadoRover.ESTADO_DISPONIVEL;
        rover.progressoMissao = 0.0f;
    }

    /** 
     * Marca uma missão como falhada devido a um erro do rover.
     * Atualiza estado do rover e da missão, registando o motivo da falha.
     */
    public void falharMissao(int idRover, int idMissao, int codigoErro, String descricaoErro) {
        Rover rover = obterRover(idRover);
        Missao missao = obterMissao(idMissao);
        
        System.out.println("[GestaoEstado] Missão " + idMissao + " falhou - Rover " + idRover + 
                         ": " + descricaoErro + " (código " + codigoErro + ")");

        if (missao != null) {
            missao.estadoMissao = Missao.EstadoMissao.FALHADA;
        }

        if (rover != null) {
            rover.temMissao = false;
            rover.idMissaoAtual = -1;
            // Manter rover em estado de FALHA até recuperar
            rover.estadoRover = Rover.EstadoRover.ESTADO_FALHA;
        }
    }

    /**
     * Reverte uma missão para o estado PENDENTE.
     * Usado quando há falha de comunicação e a missão deve ser reatribuída.
     */
    public void reverterMissaoParaPendente(int idMissao) {
        Missao missao = obterMissao(idMissao);
        if (missao != null && missao.estadoMissao != Missao.EstadoMissao.CONCLUIDA) {
            missao.estadoMissao = Missao.EstadoMissao.PENDENTE;
            System.out.println("[GestaoEstado] Missão " + idMissao + " revertida para PENDENTE");
        }
    }
}

