package lib.mensagens.payloads;

import java.util.Arrays;
import lib.mensagens.payloads.PayloadUDP;

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
}