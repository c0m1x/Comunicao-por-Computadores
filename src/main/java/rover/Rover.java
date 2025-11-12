package rover;

/**
 * Representação simples do Rover para a Nave-Mãe.
 * Contém apenas os campos básicos necessários à gestão e observação.
 */
public class Rover {
    public int idRover;
    public float posicaoX;
    public float posicaoY;
    public float bateria;
    public float velocidade;
    public String estadoOperacional; //rever este tipo de dados
    public int idMissaoAtual = -1;
    public boolean temMissao = false;

    public Rover(int id, float x, float y) {
        this.idRover = id;
        this.posicaoX = x;
        this.posicaoY = y;
        this.bateria = 100.0f;
        this.velocidade = 0.0f;
        estadoOperacional = "INICIAL";
    }

    /** Construtor a partir de um ContextoRover (cópia dos campos públicos relevantes). */
    public Rover (ContextoRover ctx) {
        if (ctx == null) return;
        this.idRover = ctx.idRover;
        this.posicaoX = ctx.posicaoX;
        this.posicaoY = ctx.posicaoY;
        this.bateria = ctx.bateria;
        this.velocidade = ctx.velocidade;
        this.estadoOperacional = ctx.estadoOperacional;
        this.idMissaoAtual = ctx.idMissaoAtual;
        this.temMissao = ctx.temMissao;

    }

    @Override
    public String toString() {
        return String.format("Rover{id=%d, pos=(%.2f,%.2f), bat=%.1f, vel=%.2f, missao=%d}",
                idRover, posicaoX, posicaoY, bateria, velocidade, idMissaoAtual);
    }
}
