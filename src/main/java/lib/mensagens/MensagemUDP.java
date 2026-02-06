package lib.mensagens;

import lib.mensagens.payloads.Payload;
import lib.TipoMensagem;

import java.io.Serializable;

/**
 * Mensagem UDP (cabeçalho + payload).
 */
public class MensagemUDP implements Serializable {

    public CabecalhoUDP header;
    public Payload payload; // Pode ser PayloadUDP (estruturado) ou FragmentoPayload (raw)

    public MensagemUDP() {
        this.header = new CabecalhoUDP();
    }

    public MensagemUDP(TipoMensagem tipo, int idMissao, Payload payload) {
        this.header = new CabecalhoUDP();
        this.header.tipo = tipo;
        this.header.idMissao = idMissao;
        this.payload = payload;
    }
    
    @Override
    public String toString() {
        return String.format("MensagemUDP{header=%s, payload=%s}", header, payload);
    }

}