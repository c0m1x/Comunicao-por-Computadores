package lib.mensagens.payloads;
import java.util.List;
import java.nio.ByteBuffer;
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
        // rever se isto faz sentido, o metodo é abstrato mas os campos são especificos
        
        if (dados == null || dados.length == 0) {
            // Fragmento vazio - enviar só o tamanho 0
            blocos.add(ByteBuffer.allocate(4).putInt(0).array());
        } else {
            // Tamanho do fragmento (int - 4 bytes)
            blocos.add(ByteBuffer.allocate(4).putInt(dados.length).array());
            
            // Dados do fragmento
            blocos.add(dados);
        }
        return blocos;
    }

}
    