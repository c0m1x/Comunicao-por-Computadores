package api.gc;

import api.gc.models.*;
import java.util.*;

public class ParserJson {

    /* -------------------- ROVER -------------------- */
    public static RoverModel parseRover(String json) {
        RoverModel r = new RoverModel();
        Map<String, String> map = parseObject(json);

        r.idRover = getInt(map, "idRover");
        r.posicaoX = getFloat(map, "posicaoX");
        r.posicaoY = getFloat(map, "posicaoY");
        r.bateria = getInt(map, "bateria");
        r.velocidade = getFloat(map, "velocidade");
        r.estadoOperacional = map.get("estadoOperacional");
        r.idMissaoAtual = getInt(map, "idMissaoAtual");
        r.progressoMissao = getFloat(map, "progressoMissao");
        r.temMissao = Boolean.parseBoolean(map.get("temMissao"));

        return r;
    }

    public static List<RoverModel> parseRovers(String json) {
        List<String> objs = splitArray(json);
        List<RoverModel> list = new ArrayList<>();
        for (String o : objs) list.add(parseRover(o));
        return list;
    }

    /* -------------------- MISSAO -------------------- */
    public static MissaoModel parseMissao(String json) {
        MissaoModel m = new MissaoModel();
        Map<String, String> map = parseObject(json);

        m.idMissao = getInt(map, "idMissao");
        m.tarefa = map.get("tarefa");
        m.estado = map.get("estado");
        m.x1 = getFloat(map, "x1");
        m.y1 = getFloat(map, "y1");
        m.x2 = getFloat(map, "x2");
        m.y2 = getFloat(map, "y2");
        m.prioridade = getInt(map, "prioridade");

        return m;
    }

    public static List<MissaoModel> parseMissoes(String json) {
        List<String> objs = splitArray(json);
        List<MissaoModel> list = new ArrayList<>();
        for (String o : objs) list.add(parseMissao(o));
        return list;
    }

    /* -------------------- PROGRESSO -------------------- */
    public static ProgressoModel parseProgresso(String json) {
        ProgressoModel p = new ProgressoModel();
        Map<String, String> map = parseObject(json);

        p.idMissao = getInt(map, "idMissao");
        p.tempoDecorridoSeg = getLong(map, "tempoDecorridoSeg");
        p.progressoPercentagem = getFloat(map, "progressoPercentagem");

        return p;
    }

    /* -------------------- TELEMETRIA -------------------- */
    public static TelemetriaModel parseTelemetria(String json) {
        TelemetriaModel t = new TelemetriaModel();
        Map<String, String> map = parseObject(json);

        t.posicaoX = getFloat(map, "posicaoX");
        t.posicaoY = getFloat(map, "posicaoY");
        t.estadoOperacional = map.get("estadoOperacional");
        t.bateria = getInt(map, "bateria");
        t.velocidade = getFloat(map, "velocidade");

        return t;
    }

    public static List<TelemetriaModel> parseTelemetriaHistorico(String json) {
        List<String> objs = splitArray(json);
        List<TelemetriaModel> lista = new ArrayList<>();
        for (String o : objs) lista.add(parseTelemetria(o));
        return lista;
    }

    /* -------------------- MINI JSON PARSER -------------------- */

    private static Map<String, String> parseObject(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim();

        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        String[] parts = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

        for (String p : parts) {
            String[] kv = p.split(":", 2);
            if (kv.length != 2) continue;

            String key = kv[0].replace("\"", "").trim();
            String val = kv[1].replace("\"", "").trim();

            map.put(key, val);
        }

        return map;
    }

    private static List<String> splitArray(String json) {
        List<String> list = new ArrayList<>();
        json = json.trim();

        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);

        if (json.isBlank()) return list;

        int depth = 0;
        int start = 0;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == '{') depth++;
            else if (c == '}') depth--;

            if (depth == 0 && c == '}') {
                list.add(json.substring(start, i + 1));
                if (i + 2 < json.length()) start = i + 2;
            }
        }

        return list;
    }

    /* Helpers */

    private static int getInt(Map<String, String> map, String key) {
        try { return Integer.parseInt(map.get(key)); }
        catch (Exception e) { return 0; }
    }

    private static float getFloat(Map<String, String> map, String key) {
        try { return Float.parseFloat(map.get(key)); }
        catch (Exception e) { return 0f; }
    }

    private static long getLong(Map<String, String> map, String key) {
        try { return Long.parseLong(map.get(key)); }
        catch (Exception e) { return 0L; }
    }
}
