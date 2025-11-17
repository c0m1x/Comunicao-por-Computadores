package lib.mensagens.payloads;

import java.util.Calendar;
import lib.mensagens.payloads.PayloadUDP;

/**
 * Payload da missão UDP.
 */

public class PayloadMissao extends PayloadUDP {

    public int idMissao;
    public float x1, y1, x2, y2;        //coordenadas da area da missao
    public String tarefa = "";
    public Calendar duracaoMissao; //NOTA: VER O TIPO DE DADOS DISTO - poria segundos (int)
    public Calendar intervaloAtualizacao; //em minutos - poria int
    public Calendar inicioMissao;
    public int prioridade; //1-5

    @Override
    public String toString() {
        return String.format("Missao{id=%d, area=(%.2f,%.2f)-(%.2f,%.2f), tarefa=%s, dur=%dmin, int=%dmin, prio=%d}",
                idMissao, x1,y1,x2,y2, tarefa, duracaoMissao.get(Calendar.MINUTE), intervaloAtualizacao.get(Calendar.MINUTE), prioridade);
    }
}