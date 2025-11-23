package lib.mensagens;

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
    public PayloadUDP payload; //isto esta sus, mas so nao teria o ponto se forem ficheiros separados, ver se ha maneira de nao ter

    public MensagemUDP() {
        this.header = new CabecalhoUDP();
    }

    public MensagemUDP(TipoMensagem tipo, int idMissao, PayloadUDP payload) {
        this.header = new CabecalhoUDP();
        this.header.tipo = tipo;
        this.header.idMissao = idMissao;
        this.payload = payload;
    }
    
    @Override
    public String toString() {
        return String.format("MensagemUDP{header=%s, payload=%s}", header, payload);
    }

    /** Serializa esta mensagem completa (header + payload). */
    public byte[] toBytes() {

        List<byte[]> blocos = payload.serializarPorCampos();
        int totalBytes = 0;

        for (byte[] b : blocos)
            totalBytes += (4 + b.length);

        ByteBuffer buf = ByteBuffer.allocate(4 + totalBytes);
        buf.putInt(header.tipo.value);

        for (byte[] b : blocos) {
            buf.putInt(b.length);
            buf.put(b);
        }

        return buf.array();
    }
}