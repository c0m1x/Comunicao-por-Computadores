package api.gc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Cliente REST que consulta a Nave-Mãe. 
 * Lê informações da nave mae através do HTTP REST
 */
public class GroundControlAPI {

    private final String baseUrl;

    public GroundControlAPI(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    private String get(String endpoint) throws Exception {
        URL url = new URL(baseUrl + endpoint);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Accept", "application/json");

        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null)
            sb.append(line);

        br.close();
        return sb.toString();
    }

    public String listarRovers() throws Exception {
        return get("/rovers");
    }

    public String obterRover(int id) throws Exception {
        return get("/rovers/" + id);
    }

    public String listarMissoes() throws Exception {
        return get("/missoes");
    }

    public String obterMissao(int id) throws Exception {
        return get("/missoes/" + id);
    }

    public String obterProgresso(int id) throws Exception {
        return get("/missoes/progresso/" + id);
    }

    public String obterTelemetria(int roverId) throws Exception {
        return get("/telemetria/" + roverId);
    }

    public String obterHistoricoTelemetria() throws Exception {
        return get("/telemetria/historico");
    }
}
