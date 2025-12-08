
package nave.api;

import lib.Rover;
import lib.Missao;
import lib.mensagens.payloads.*;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;

public class CriarJson {

    // ----- Rover -----
    public static String rovers(Collection<Rover> lista) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        boolean first = true;
        for (Rover r : lista) {
            if (!first) sb.append(",");
            first = false;
            sb.append(rover(r));
        }
        sb.append("]");
        return sb.toString();
    }

    public static String rover(Rover r) {
        if (r == null) return "null";

        // Normalizar estado do rover (remover "ESTADO_" do início)
        String estadoNormalizado = r.estadoRover.toString().replace("ESTADO_", "");

        return "{"
                + "\"idRover\":" + r.idRover + ","
                + "\"posicaoX\":" + r.posicaoX + ","
                + "\"posicaoY\":" + r.posicaoY + ","
                + "\"bateria\":" + r.bateria + ","
                + "\"velocidade\":" + r.velocidade + ","
                + "\"estadoOperacional\":\"" + escape(estadoNormalizado) + "\","
                + "\"idMissaoAtual\":" + r.idMissaoAtual + ","
                + "\"progressoMissao\":" + (r.progressoMissao != null ? r.progressoMissao : 0.0f) + ","
                + "\"temMissao\":" + r.temMissao
                + "}";
    }

    // ----- Missão -----

    public static String missoes(Collection<Missao> lista) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        boolean first = true;
        for (Missao m : lista) {
            if (!first) sb.append(",");
            first = false;
            sb.append(missao(m));
        }
        sb.append("]");
        return sb.toString();
    }

    public static String missao(Missao m) {
        if (m == null) return "null";

        // Converter estado para formato consistente
        String estado = m.estadoMissao != null ? m.estadoMissao.toString() : m.estadoMissao.toString();
        
        return "{"
                + "\"idMissao\":" + m.idMissao + ","
                + "\"tarefa\":\"" + escape(m.tarefa) + "\","
                + "\"estado\":\"" + estado + "\","
                + "\"x1\":" + m.x1 + ","
                + "\"y1\":" + m.y1 + ","
                + "\"x2\":" + m.x2 + ","
                + "\"y2\":" + m.y2 + ","
                + "\"prioridade\":" + m.prioridade
                + "}";
    }

    public static String progresso(PayloadProgresso p) {
        if (p == null) return "null";

        return "{"
                + "\"idMissao\":" + p.idMissao + ","
                + "\"tempoDecorridoSeg\":" + p.tempoDecorrido + ","
                + "\"progressoPercentagem\":" + p.progressoPercentagem
                + "}";
    }

    public static String progressoMap(Map<Integer, PayloadProgresso> mapa) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        boolean first = true;
        for (PayloadProgresso p : mapa.values()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(progresso(p));
        }

        sb.append("]");
        return sb.toString();
    }
    // ----- Telemetria -----

    public static String telemetria(PayloadTelemetria p) {
        if (p == null) return "null";

        // Normalizar estado (remover "ESTADO_" se existir)
        String estadoNormalizado = p.estadoOperacional.toString().replace("ESTADO_", "");
        
        return "{"
                + "\"posicaoX\":" + p.posicaoX + ","
                + "\"posicaoY\":" + p.posicaoY + ","
                + "\"estadoOperacional\":\"" + escape(estadoNormalizado) + "\","
                + "\"bateria\":" + p.bateria + ","
                + "\"velocidade\":" + p.velocidade
                + "}";
    }

    public static String historicoTelemetria(Queue<PayloadTelemetria> lista) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        boolean first = true;
        for (PayloadTelemetria p : lista) {
            if (!first) sb.append(",");
            first = false;
            sb.append(telemetria(p));
        }
        sb.append("]");
        return sb.toString();
    }

    // ----- uteis ----

    /** Escapa caracteres problemáticos para JSON. */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
