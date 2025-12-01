package lib;

import java.io.Serializable;

import lib.mensagens.payloads.PayloadMissao;

/**
 * Representação de uma missão com estado local.
 * Contém todos os campos presentes em Mensagens.PayloadMissao
 * e acrescenta um campo de estado para uso pela Nave-Mãe.
 */
public class Missao implements Serializable {

    public enum EstadoMissao {
        PENDENTE,
        EM_ANDAMENTO,
        CONCLUIDA,
        CANCELADA,
        FALHADA       // Missão abortada devido a erro (bateria baixa, obstáculo, etc.)
    }

    public int idMissao;
    public float x1, y1, x2, y2;
    public String tarefa = "";
    // tempos em segundos
    public long duracaoMissao;        // duração da missão em segundos
    public long intervaloAtualizacao; // intervalo de atualização em segundos
    public long inicioMissao;         // instante de início em segundos (epoch)
    public int prioridade; // 1-5

    public EstadoMissao estadoMissao;
    public float progressoMissao;

    public Missao() {
        this.idMissao = 0;
        this.tarefa = null;
        this.estadoMissao = EstadoMissao.PENDENTE;
        this.duracaoMissao = 0;
        this.intervaloAtualizacao = 0;
        this.progressoMissao = 0.0f;
    }

    public Missao(int idMissao, String tarefa, EstadoMissao estadoMissao, long duracaoMissao, long intervaloAtualizacao) {
        this.idMissao = idMissao;
        this.tarefa = tarefa;
        this.estadoMissao = estadoMissao;
        this.x1 = 0.0f; this.y1 = 0.0f; this.x2 = 0.0f; this.y2 = 0.0f;
        if (duracaoMissao <= 0) {
            this.duracaoMissao = 60; // 60s por omissão
        } else {
            this.duracaoMissao = duracaoMissao;
        }
        if (intervaloAtualizacao <= 0) {
            this.intervaloAtualizacao = 2; // 2s por omissão
        } else {
            this.intervaloAtualizacao = intervaloAtualizacao;
        }
        this.inicioMissao = 0;
        this.progressoMissao = 0.0f;
    }

    /** Constrói uma Missao a partir de um PayloadMissao. */
    public Missao(PayloadMissao p) {
        if (p == null) return;

        this.idMissao = p.idMissao;
        this.x1 = p.x1; this.y1 = p.y1; this.x2 = p.x2; this.y2 = p.y2;
        this.tarefa = p.tarefa;
        this.duracaoMissao = p.duracaoMissao;
        this.intervaloAtualizacao = p.intervaloAtualizacao;
        this.inicioMissao = p.inicioMissao;
        this.prioridade = p.prioridade;
        this.estadoMissao = EstadoMissao.PENDENTE;
        this.progressoMissao = 0.0f;
    }

    /** Converte esta Missao para um PayloadMissao (compatibilidade).
     */
    public PayloadMissao toPayload() {
        PayloadMissao p = new PayloadMissao();
        p.idMissao = this.idMissao;
        p.x1 = this.x1; p.y1 = this.y1; p.x2 = this.x2; p.y2 = this.y2;
        p.tarefa = this.tarefa;
        p.duracaoMissao = this.duracaoMissao;
        p.intervaloAtualizacao = this.intervaloAtualizacao;
        p.inicioMissao = this.inicioMissao;
        p.prioridade = this.prioridade;

        return p;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Missao{id=").append(idMissao)
          .append(", area=(")
          .append(x1).append(',').append(y1)
          .append(")-(")
          .append(x2).append(',').append(y2)
          .append("), tarefa=").append(tarefa)
          .append(", prio=").append(prioridade)
          .append(", estado=").append(estadoMissao)
          .append('}');
        return sb.toString();
    }

}
