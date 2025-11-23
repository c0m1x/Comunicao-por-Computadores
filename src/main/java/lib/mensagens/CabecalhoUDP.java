package lib.mensagens;

import java.io.Serializable;
import java.sql.Time;
import lib.TipoMensagem;

/**
 * Cabeçalho para mensagens UDP.
 */
public class CabecalhoUDP implements Serializable{

    public TipoMensagem tipo;
    public int idEmissor;
    public int idRecetor;
    public int idMissao;
    public Time timestamp;
    public int seq;
    public int totalFragm;
    public boolean flagSucesso;

    public CabecalhoUDP() {
        this.timestamp = new Time(System.currentTimeMillis());
    }
    @Override
    public String toString() {
        return String.format("CabecalhoUDP{tipo=%s, e=%d, r=%d, missao=%d, ts=%d, seq=%d/%d, ok=%b}",
                tipo, idEmissor, idRecetor, idMissao, timestamp.getTime(), seq, totalFragm, flagSucesso);
    }
}
