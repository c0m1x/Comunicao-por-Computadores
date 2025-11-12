package nave;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import rover.Rover;

/**
 * Gestão de estado central (armazenamento de rovers e missões).
 * Armazenamento thread-safe usando ConcurrentHashMap porque a Nave-Mãe
 * acede e modifica estes mapas a partir de várias threads.
 */
public class gestaoEstado {

	private final ConcurrentHashMap<Integer, Rover> rovers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Integer, Missao> missoes = new ConcurrentHashMap<>();

	// ----- Rovers -----

	/** Adiciona ou substitui um Rover com o id fornecido. */
	public void adicionarRover(int id, Rover ctx) {
		if (ctx == null) throw new NullPointerException("Rover não pode ser null");
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

}
