package lib.mensagens;

import java.io.Serializable;
import java.sql.Time;
import lib.TipoMensagem;


/**
 * Cabeçalho para mensagens TCP.
 */
public class CabecalhoTCP implements Serializable {
    //removi o TODO: o serialVersionUID é necessário para Serializable e é uma boa prática defini-lo explicitamente
    private static final long serialVersionUID = 1L;
    public TipoMensagem tipo;
    public int idEmissor;
    public int idRecetor;
    public int idMissao;
    public Time timestamp;

    public CabecalhoTCP() { 
        this.timestamp = new Time(System.currentTimeMillis());
    }

    @Override
    public String toString() {
        return String.format("CabecalhoTCP{tipo=%s, e=%d, r=%d, missao=%d, ts=%d}",
                tipo, idEmissor, idRecetor, idMissao, timestamp.getTime());
    }
}