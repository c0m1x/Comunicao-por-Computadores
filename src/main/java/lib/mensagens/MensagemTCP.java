package lib.mensagens;

import java.io.Serializable;
import lib.mensagens.payloads.PayloadTCP;

/**
 * Mensagem TCP (cabeçalho + payload).
 */

 public class MensagemTCP implements Serializable {

    public CabecalhoTCP header;
    public PayloadTCP payload;

    public MensagemTCP() {
        this.header = new CabecalhoTCP();
        this.payload = null;
    }

    @Override
    public String toString() {
        return String.format("MensagemTCP{header=%s, payload=%s}", header, payload);
    }
}