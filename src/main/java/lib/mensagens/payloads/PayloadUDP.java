package lib.mensagens.payloads;

import lib.mensagens.CampoSerializado;
import java.util.List;

/**
 * Classe base para payloads UDP estruturados.
 * Cada campo é serializado separadamente para permitir fragmentação não cega
 * (nunca dividir um campo entre fragmentos diferentes).
 */
public abstract class PayloadUDP implements Payload {
    
    /**
     * Serializa os campos do payload em CampoSerializado com nome identificador.
     * Usado para fragmentação e reconstrução independente da ordem.
     * @return Lista de campos serializados com nomes
     */
    public abstract List<CampoSerializado> serializarCampos();
}