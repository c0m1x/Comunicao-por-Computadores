package lib.mensagens.payloads;

import java.util.List;

/**
 * Classe base para payloads UDP estruturados.
 * Cada campo é serializado separadamente para permitir fragmentação não cega
 * (nunca dividir um campo entre fragmentos diferentes).
 */
public abstract class PayloadUDP implements Payload {
    
    /**
     * Serializa os campos do payload em blocos independentes.
     * Cada bloco representa um campo completo que não deve ser dividido.
     * @return Lista de arrays de bytes, um por campo
     */
    public abstract List<byte[]> serializarPorCampos();
}