package lib.mensagens.payloads;

import java.util.Arrays;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import lib.mensagens.CampoSerializado;

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
    public List<CampoSerializado> serializarCampos() {
        List<CampoSerializado> campos = new ArrayList<>();

        campos.add(new CampoSerializado("missingCount", ByteBuffer.allocate(4).putInt(missingCount).array()));

        int[] arr = missing != null ? missing : new int[0];
        ByteBuffer buf = ByteBuffer.allocate(4 + arr.length * 4);
        buf.putInt(arr.length);
        for (int seq : arr) {
            buf.putInt(seq);
        }
        campos.add(new CampoSerializado("missing", buf.array()));

        return campos;
    }
}