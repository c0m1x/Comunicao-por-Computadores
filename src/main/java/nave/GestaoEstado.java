package nave;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lib.Missao;
import lib.Rover;

/**
 * Gestão de estado central (armazenamento de rovers e missões).
 * Armazenamento thread-safe usando ConcurrentHashMap porque a Nave-Mãe
 * acede e modifica estes mapas a partir de várias threads.
 */
public class GestaoEstado {

    private ConcurrentHashMap<Integer, Rover> rovers;
    private ConcurrentHashMap<Integer, Missao> missoes;

    public GestaoEstado(){
        this.rovers = new ConcurrentHashMap <>();
        this.missoes = new ConcurrentHashMap<>();

        // NOTA: Missões para testar, depois tirar daqui

        Missao m1 = new Missao(1, "Explorar cratera A", Missao.EstadoMissao.PENDENTE);
        Missao m2 = new Missao(2, "Coletar amostras do solo", Missao.EstadoMissao.PENDENTE);
        Missao m3 = new Missao(3, "Analisar atmosfera", Missao.EstadoMissao.PENDENTE);

        this.adicionarMissao(m1.idMissao, m1);
        this.adicionarMissao(m2.idMissao, m2);
        this.adicionarMissao(m3.idMissao, m3);
    }
// ----- Rovers -----

/** Adiciona ou substitui um Rover com o id fornecido. */
    public void adicionarRover(int id, Rover ctx) {
        if (ctx == null) throw new NullPointerException("Rover não pode ser null");
        rovers.put(id, ctx);
        System.out.println("Rover adicionado");
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

    // ----- Missões -----

    /** Adiciona ou substitui uma missão com o id fornecido. */
    public void adicionarMissao(int id, Missao missao) {
        if (missao == null) throw new NullPointerException("missao não pode ser null");
        missoes.put(id, missao);
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

    // ----- Operações atómicas utilitárias -----

    /** Insere a missão apenas se não existir já uma com o mesmo id. Retorna true se inseriu. */
    public boolean inserirMissaoSeAusente(int id, Missao missao) {
        if (missao == null) throw new NullPointerException("missao não pode ser null");
        return missoes.putIfAbsent(id, missao) == null;
    }

    /** Insere o rover apenas se não existir já um com o mesmo id. Retorna true se inseriu. */
    public boolean inserirRoverSeAusente(int id, Rover ctx) {
        if (ctx == null) throw new NullPointerException("Rover não pode ser null");
        return rovers.putIfAbsent(id, ctx) == null;
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

    public Rover obterRoverDisponivel() {
        for (Rover r : rovers.values()) {
            // TODO: verificar estado operacional antes
            if (!r.temMissao) {
                return r;
            }
        }
        return null;
    }
}
