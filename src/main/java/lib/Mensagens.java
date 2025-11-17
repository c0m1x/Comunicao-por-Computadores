package lib;

import java.io.Serializable;
import java.sql.Time;
import java.util.Arrays;
import java.util.Calendar;


public class Mensagens {

    public enum TipoMensagem {
        MSG_HELLO(1),
        MSG_RESPONSE(2),
        MSG_MISSION(3),
        MSG_ACK(4),
        MSG_PROGRESS(5),
        MSG_COMPLETED(6),
        MSG_TELEMETRY(7);

        public final int value;
        TipoMensagem(int v) { value = v; }
    }
    
    /**
     * Cabeçalho para mensagens UDP.
     */

    public static class CabecalhoUDP implements Serializable {
        private static final long serialVersionUID = 1L;
        public TipoMensagem tipo;
        public int idEmissor;
        public int idRecetor;
        public int idMissao;
        public Time timestamp;
        public int seq;
        public int totalFragm;
        public boolean flagSucesso;

        public CabecalhoUDP() {
            this.timestamp = new Time(System.currentTimeMillis());
        }

        @Override
        public String toString() {
            return String.format("CabecalhoUDP{tipo=%s, e=%d, r=%d, missao=%d, ts=%d, seq=%d/%d, ok=%b}",
                    tipo, idEmissor, idRecetor, idMissao, timestamp.getTime(), seq, totalFragm, flagSucesso);
        }
    }

    /**
     * Cabeçalho para mensagens TCP.
     */

    public static class CabecalhoTCP implements Serializable {
        private static final long serialVersionUID = 1L;
        public TipoMensagem tipo;
        public int idEmissor;
        public int idRecetor;
        public int idMissao;
        public Time timestamp;

        public CabecalhoTCP() { 
            this.timestamp = new Time(System.currentTimeMillis());
        }

        @Override
        public String toString() {
            return String.format("CabecalhoTCP{tipo=%s, e=%d, r=%d, missao=%d, ts=%d}",
                    tipo, idEmissor, idRecetor, idMissao, timestamp.getTime());
        }
    }

    /**
     * Payloads relacionados com TCP.
     */
    public static abstract class PayloadTCP implements Serializable {

    }

    /**
     * Payload da telemetria TCP.
     */
    public static class PayloadTelemetria extends PayloadTCP {
        public float posicaoX;
        public float posicaoY;
        public String estadoOperacional = ""; //VER O TIPO DE DADOS DISTO
        public float bateria;
        public float velocidade;

        @Override
        public String toString() {
            return String.format("Telemetria{(%.2f,%.2f), estado=%s, bateria=%.1f%%, vel=%.2fm/s}",
                    posicaoX, posicaoY, estadoOperacional, bateria, velocidade);
        }
    }

    /**
     * Payloads relacionados com UDP.
     */
    public static abstract class PayloadUDP implements Serializable {

    }

    /**
     * Payload da missão UDP.
     */
    public static class PayloadMissao extends PayloadUDP {
        public int idMissao;
        public float x1, y1, x2, y2;
        public String tarefa = "";
        public Calendar duracaoMissao; //NOTA: VER O TIPO DE DADOS DISTO
        public Calendar intervaloAtualizacao; //em minutos
        public Calendar inicioMissao;
        public int prioridade; //1-5

        @Override
        public String toString() {
            return String.format("Missao{id=%d, area=(%.2f,%.2f)-(%.2f,%.2f), tarefa=%s, dur=%dmin, int=%dmin, prio=%d}",
                    idMissao, x1,y1,x2,y2, tarefa, duracaoMissao.get(Calendar.MINUTE), intervaloAtualizacao.get(Calendar.MINUTE), prioridade);
        }
    }

    /**
     * Payload do ACK UDP.
     */

    public static class PayloadAck extends PayloadUDP {
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

    /**
     * Payload do progresso UDP.
     */

    public static class PayloadProgresso extends PayloadUDP {
        public int idMissao;
        public Calendar tempoDecorrido;
        public float progressoPercentagem; // 0.0 a 100.0

        @Override
        public String toString() {
            return String.format("Progresso{missaoId=%d, tempoDecorrido=%dmin, progresso=%.2f%%}",
                    idMissao, tempoDecorrido.get(Calendar.MINUTE), progressoPercentagem);
        }
    }

    /**
     * Mensagem UDP (cabeçalho + payload).
     */

    public static class MensagemUDP implements Serializable {
        public CabecalhoUDP header;
        public PayloadUDP payload; //isto esta sus, mas so nao teria o ponto se forem ficheiros separados, ver se ha maneira de nao ter

        public MensagemUDP() {
            this.header = new CabecalhoUDP();
        }

        @Override
        public String toString() {
            return String.format("MensagemUDP{header=%s, payload=%s}", header, payload);
        }
    }

    /**
     * Mensagem TCP (cabeçalho + payload).
     */

    public static class MensagemTCP implements Serializable {
        private static final long serialVersionUID = 1L;
        public CabecalhoTCP header;
        public PayloadTCP payload;

        //NOTA: REVER O CONSTRUTOR E TALVEZ METER SETERS/GETTERS
        public MensagemTCP() {
            this.header = new CabecalhoTCP();
        }

        @Override
        public String toString() {
            return String.format("MensagemTCP{header=%s, payload=%s}", header, payload);
        }
    }

}

