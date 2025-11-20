package lib.mensagens.payloads;

/**
 * Payload do progresso UDP.
 */

public class PayloadProgresso extends PayloadUDP {

    public int idMissao;
    public long tempoDecorrido; // tempo decorrido em segundos
    public float progressoPercentagem; // 0.0 a 100.0

    @Override
    public String toString() {
        return String.format("Progresso{missaoId=%d, tempoDecorrido=%ds, progresso=%.2f%%}",
            idMissao, tempoDecorrido, progressoPercentagem);
    }
}
