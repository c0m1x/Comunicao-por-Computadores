package main.java.lib.Mensagens;

import java.io.Serializable;
import java.time.Instant;


public class Mensagens {

    public enum TipoMensagem {
        MSG_HELLO(1),
        MSG_RESPONSE(2),
        MSG_MISSION(3),
        MSG_ACK(4);

        public final int value;
        TipoMensagem(int v) { value = v; }
    }

    // --------- Payloads ---------
    public static class PayloadMissao implements Serializable {
        private static final long serialVersionUID = 1L;
        public int idMissao;
        public float x1, y1, x2, y2;
        public String tarefa = "";
        public long duracaoMissaoSecs;
        public long intervaloAtualizacaoSecs;
        public long inicioMissaoEpoch;
        public int prioridade;

        @Override
        public String toString() {
            return String.format("Missao{id=%d, area=(%.2f,%.2f)-(%.2f,%.2f), tarefa=%s, dur=%ds, int=%ds, prio=%d}",
                    idMissao, x1,y1,x2,y2, tarefa, duracaoMissaoSecs, intervaloAtualizacaoSecs, prioridade);
        }
    }

    public static class PayloadTelemetria implements Serializable {
        private static final long serialVersionUID = 1L;
        public float posicaoX;
        public float posicaoY;
        public String estadoOperacional = "";
        public float bateria;
        public float velocidade;

        @Override
        public String toString() {
            return String.format("Telemetria{(%.2f,%.2f), estado=%s, bateria=%.1f%%, vel=%.2fm/s}",
                    posicaoX, posicaoY, estadoOperacional, bateria, velocidade);
        }
    }

    // --------- Mensagem TCP (usada para telemetria) ---------
    public static class MensagemTCP implements Serializable {
        private static final long serialVersionUID = 1L;

        public TipoMensagem tipo;
        public int idEmissor;
        public int idRecetor;
        public int idMissao;
        public long timestampEpoch;
        public Object payload; // PayloadTelemetria or other

        public MensagemTCP() {
            this.timestampEpoch = Instant.now().getEpochSecond();
        }

        @Override
        public String toString() {
            return String.format("MensagemTCP{tipo=%s, emissor=%d, recetor=%d, missao=%d, ts=%d, payload=%s}",
                    tipo, idEmissor, idRecetor, idMissao, timestampEpoch, payload);
        }
    }
}
