package lib.mensagens.payloads;

import java.util.Calendar;
import lib.mensagens.payloads.PayloadUDP;

/**
 * Payload do progresso UDP.
 */

public class PayloadProgresso extends PayloadUDP {

    public int idMissao;
    public Calendar tempoDecorrido;
    public float progressoPercentagem; // 0.0 a 100.0

    @Override
    public String toString() {
        return String.format("Progresso{missaoId=%d, tempoDecorrido=%dmin, progresso=%.2f%%}",
                idMissao, tempoDecorrido.get(Calendar.MINUTE), progressoPercentagem);
    }
}
