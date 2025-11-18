package lib;

/**
 * Representação simples do Rover para a Nave-Mãe.
 * Contém apenas os campos básicos necessários à gestão e observação.
 */
public class Rover {

    public enum EstadoRover {
        ESTADO_INICIAL,
        ESTADO_DISPONIVEL,
        ESTADO_RECEBENDO_MISSAO,
        ESTADO_EM_MISSAO,
        ESTADO_CONCLUIDO,
        ESTADO_FALHA
    }

    public int idRover;
    public float posicaoX;
    public float posicaoY;
    public float bateria;
    public float velocidade;
    public EstadoRover estadoRover;
    public int idMissaoAtual;
    public float progressoMissao;
    public boolean temMissao;

    // Endpoint opcional do rover (se conhecido/configurado)
    public String enderecoHost; // ex: "192.168.1.50" (se null, usar sessão)
    public Integer portaUdp;    // talvez usar PORTA_BASE_ROVER + id já aqui em vez de meter depois

    public Rover(int id, float x, float y, String enderecoHost) {
        this.idRover = id;
        this.posicaoX = x;
        this.posicaoY = y;
        this.bateria = 100.0f;
        this.velocidade = 0.0f;
        this.estadoRover = EstadoRover.ESTADO_DISPONIVEL;
        idMissaoAtual = -1;
        progressoMissao = 0.0f;
        temMissao = false;
        this.enderecoHost = enderecoHost;
        this.portaUdp = null;
    }

    @Override
    public String toString() {
        return String.format("Rover{id=%d, pos=(%.2f,%.2f), bat=%.1f%%, vel=%.2fm/s, estado=%s, missao=%d, progresso=%.1f%%}",
                idRover, posicaoX, posicaoY, bateria, velocidade, estadoRover, idMissaoAtual, progressoMissao);
    }
}
