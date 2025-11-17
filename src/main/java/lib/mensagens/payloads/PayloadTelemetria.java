package lib.mensagens.payloads;

import java.io.Serializable;
import lib.mensagens.payloads.PayloadTCP;
import lib.Rover.EstadoRover;

/**
 * Payload da telemetria TCP.
 */

public class PayloadTelemetria extends PayloadTCP {
    
    public float posicaoX;
    public float posicaoY;
    public EstadoRover estadoOperacional;
    public float bateria;
    public float velocidade;

    @Override
    public String toString() {
        return String.format("Telemetria{(%.2f,%.2f), estado=%s, bateria=%.1f%%, vel=%.2fm/s}",
                posicaoX, posicaoY, estadoOperacional, bateria, velocidade);
    }
}