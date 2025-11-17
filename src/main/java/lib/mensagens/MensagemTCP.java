package lib.mensagens;

import java.io.Serializable;
import lib.mensagens.payloads.PayloadTCP;
import lib.mensagens.CabecalhoTCP;

/**
 * Mensagem TCP (cabeçalho + payload).
 */

 public class MensagemTCP implements Serializable {

    private static final long serialVersionUID = 1L;
    public CabecalhoTCP header;
    public PayloadTCP payload;
    //NOTA: REVER O CONSTRUTOR E TALVEZ METER SETERS/GETTERS

    public MensagemTCP() {
        this.header = new CabecalhoTCP();
    }

    @Override
    public String toString() {
        return String.format("MensagemTCP{header=%s, payload=%s}", header, payload);
    }
}