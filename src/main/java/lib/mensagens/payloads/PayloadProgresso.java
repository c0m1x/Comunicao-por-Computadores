package lib.mensagens.payloads;
import java.util.List;
import java.util.ArrayList;

/**
 * Payload do progresso UDP.
 */

public class PayloadProgresso extends PayloadUDP {

    public int idMissao;
    public long tempoDecorrido; // tempo decorrido em segundos
    public float progressoPercentagem; // 0.0 a 100.0

    public PayloadProgresso() {
    }
    
    public PayloadProgresso(int idMissao, long tempoDecorrido, float progressoPercentagem) {
        this.idMissao = idMissao;
        this.tempoDecorrido = tempoDecorrido;
        this.progressoPercentagem = progressoPercentagem;
    }

    @Override
    public String toString() {
        return String.format("Progresso{missaoId=%d, tempoDecorrido=%ds, progresso=%.2f%%}",
            idMissao, tempoDecorrido, progressoPercentagem);
    }

/**Criei porque tornei o metodo abstrato - ainda por corrigir */
    @Override
    public List<byte[]> serializarPorCampos() {
        List<byte[]> blocos = new ArrayList<>();
        // TODO: serializar campos específicos
        return blocos;
    }

}
