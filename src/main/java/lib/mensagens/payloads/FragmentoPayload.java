package lib.mensagens.payloads;
import java.util.List;
import java.util.ArrayList;

/**
 * Classe auxiliar para armazenar fragmentos de dados.
 */
public class FragmentoPayload extends PayloadUDP {
    public byte[] dados;

    /**Criei porque tornei o metodo abstrato - ainda por corrigir */
    @Override
    public List<byte[]> serializarPorCampos() {
        List<byte[]> blocos = new ArrayList<>();
        // TODO: serializar campos específicos
        return blocos;
    }

}
    