package lib.mensagens.payloads;

import java.io.Serializable;
import java.util.List;

/**
 * Payloads relacionados com UDP.
 */

public abstract class PayloadUDP implements Serializable {
    public abstract List<byte[]> serializarPorCampos();
}