package lib.mensagens.payloads;

import java.util.List;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import lib.mensagens.CampoSerializado;

/**
 * Payload para mensagens de erro UDP.
 * Usado quando o rover não consegue completar uma missão.
 */
public class PayloadErro extends PayloadUDP {

    /**
     * Códigos de erro possíveis.
     */
    public enum CodigoErro {
        ERRO_BATERIA_CRITICA(1, "Bateria crítica (<10%)"),
        ERRO_BATERIA_BAIXA(2, "Bateria baixa (<20%)"),
        ERRO_OBSTACULO(3, "Obstáculo intransponível"),
        ERRO_COMUNICACAO(4, "Falha de comunicação"),
        ERRO_HARDWARE(5, "Falha de hardware"),
        ERRO_TIMEOUT(6, "Timeout de missão"),
        ERRO_DESCONHECIDO(99, "Erro desconhecido");

        public final int codigo;
        public final String descricaoPadrao;

        CodigoErro(int codigo, String descricao) {
            this.codigo = codigo;
            this.descricaoPadrao = descricao;
        }

        public static CodigoErro fromCodigo(int codigo) {
            for (CodigoErro e : values()) {
                if (e.codigo == codigo) return e;
            }
            return ERRO_DESCONHECIDO;
        }
    }

    public int idMissao;
    public int codigoErro;           // Código numérico do erro
    public String descricao;         // Descrição detalhada do erro
    public float progressoAtual;     // Progresso quando o erro ocorreu
    public float bateria;            // Nível de bateria quando o erro ocorreu
    public float posicaoX;           // Posição X quando o erro ocorreu
    public float posicaoY;           // Posição Y quando o erro ocorreu
    public long timestampErro;       // Timestamp do erro 

    public PayloadErro() {
    }

    public PayloadErro(int idMissao, CodigoErro erro, String descricaoExtra,
                       float progressoAtual, float bateria, float posX, float posY) {
        this.idMissao = idMissao;
        this.codigoErro = erro.codigo;
        this.descricao = erro.descricaoPadrao + (descricaoExtra != null ? " - " + descricaoExtra : "");
        this.progressoAtual = progressoAtual;
        this.bateria = bateria;
        this.posicaoX = posX;
        this.posicaoY = posY;
        this.timestampErro = System.currentTimeMillis() / 1000;
    }

    @Override
    public String toString() {
        return String.format("Erro{missaoId=%d, codigo=%d, descricao='%s', progresso=%.2f%%, bateria=%.1f%%, pos=(%.2f,%.2f)}",
                idMissao, codigoErro, descricao, progressoAtual, bateria, posicaoX, posicaoY);
    }

    @Override
    public List<CampoSerializado> serializarCampos() {
        List<CampoSerializado> campos = new ArrayList<>();

        campos.add(new CampoSerializado("idMissao", ByteBuffer.allocate(4).putInt(idMissao).array()));
        campos.add(new CampoSerializado("codigoErro", ByteBuffer.allocate(4).putInt(codigoErro).array()));

        byte[] descBytes = descricao != null ? descricao.getBytes(StandardCharsets.UTF_8) : new byte[0];
        ByteBuffer descBuf = ByteBuffer.allocate(4 + descBytes.length);
        descBuf.putInt(descBytes.length);
        descBuf.put(descBytes);
        campos.add(new CampoSerializado("descricao", descBuf.array()));

        campos.add(new CampoSerializado("progressoAtual", ByteBuffer.allocate(4).putFloat(progressoAtual).array()));
        campos.add(new CampoSerializado("bateria", ByteBuffer.allocate(4).putFloat(bateria).array()));
        campos.add(new CampoSerializado("posicaoX", ByteBuffer.allocate(4).putFloat(posicaoX).array()));
        campos.add(new CampoSerializado("posicaoY", ByteBuffer.allocate(4).putFloat(posicaoY).array()));
        campos.add(new CampoSerializado("timestampErro", ByteBuffer.allocate(8).putLong(timestampErro).array()));

        return campos;
    }
}
