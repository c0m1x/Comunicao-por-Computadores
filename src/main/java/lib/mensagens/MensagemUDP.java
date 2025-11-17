package lib.mensagens;

import java.io.Serializable;
import lib.mensagens.payloads.PayloadUDP;
import lib.mensagens.CabecalhoUDP;

/**
 * Mensagem UDP (cabeçalho + payload).
 */

 public class MensagemUDP implements Serializable {

    public CabecalhoUDP header;
    public PayloadUDP payload; //isto esta sus, mas so nao teria o ponto se forem ficheiros separados, ver se ha maneira de nao ter

    public MensagemUDP() {
        this.header = new CabecalhoUDP();
    }

    @Override
    public String toString() {
        return String.format("MensagemUDP{header=%s, payload=%s}", header, payload);
    }
}