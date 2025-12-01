package lib.mensagens.payloads;

import java.util.Arrays;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Payload do ACK UDP.
 */

public class PayloadAck extends PayloadUDP {

    public int missingCount;
    public int[] missing; // indice de fragmentos em falta

    public PayloadAck() {
        this.missing = new int[0];
    }

    @Override
    public String toString() {
        return String.format("Ack{missingCount=%d, missing=%s}", missingCount, Arrays.toString(missing));
    }

    @Override
    public List<byte[]> serializarPorCampos() {
        List<byte[]> blocos = new ArrayList<>();

        // missingCount (int - 4 bytes)
        blocos.add(ByteBuffer.allocate(4).putInt(missingCount).array());

        // Tamanho do array (int - 4 bytes)
        if(missing==null) {
            missing = new int[0];
        }
        int arrayLength = missing.length;
        
        blocos.add(ByteBuffer.allocate(4).putInt(arrayLength).array());
        
        // Array de inteiros (cada int - 4 bytes)
        if (arrayLength > 0) {
            ByteBuffer buffer = ByteBuffer.allocate(arrayLength * 4);
            for (int seq : missing) {
                buffer.putInt(seq);
            }
            blocos.add(buffer.array());
        }
        return blocos;
    }

}