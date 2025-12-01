package lib.mensagens;

import lib.mensagens.payloads.Payload;
import lib.mensagens.payloads.PayloadUDP;
import lib.TipoMensagem;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.List;

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

    /** 
     * Serializa esta mensagem completa (header + payload).
     * Apenas para PayloadUDP estruturados - FragmentoPayload já contém bytes raw.
     * @throws IllegalStateException se o payload não for PayloadUDP
     */
    public byte[] toBytes() {
  
        if (!(payload instanceof PayloadUDP)) {
            throw new IllegalStateException(
                "toBytes() só suporta PayloadUDP. FragmentoPayload já contém dados raw.");
        }
        
        List<byte[]> blocos = ((PayloadUDP) payload).serializarPorCampos();
        
        int totalBytes = 0;

        for (byte[] b : blocos){            
            totalBytes += (4 + b.length);
        }
        
        ByteBuffer buf = ByteBuffer.allocate(4 + totalBytes);
        buf.putInt(header.tipo.value);

        for (byte[] b : blocos) {
            buf.putInt(b.length);
            buf.put(b);
        }

        return buf.array();
    }
}