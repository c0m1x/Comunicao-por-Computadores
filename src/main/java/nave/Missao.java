package nave;

import java.io.Serializable;
import java.util.Calendar;

import lib.Mensagens.PayloadMissao;

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
        CANCELADA
    }

    public int idMissao;
    public float x1, y1, x2, y2;
    public String tarefa = "";
    public Calendar duracaoMissao; // duração (como Calendar, conforme Payload)
    public Calendar intervaloAtualizacao; // em minutos
    public Calendar inicioMissao;
    public int prioridade; // 1-5

    public EstadoMissao estadoMissao;

    public Missao() {
        // vazio
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

    }

    /** Converte esta Missao para um Mensagens.PayloadMissao (compatibilidade).
     * Nota: campos de estado não são copiados para o Payload.
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
