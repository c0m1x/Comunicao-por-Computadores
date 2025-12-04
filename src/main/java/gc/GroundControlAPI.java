package gc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import gc.models.MissaoModel;

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

        int status = con.getResponseCode();
        if (status != 200) {
            throw new Exception("HTTP " + status + ": " + con.getResponseMessage());
        }

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
    
    /**
     * Cria uma nova missão na Nave-Mãe via HTTP POST
     * @param missao Modelo da missão a criar
     * @return JSON com resposta (id da missão criada)
     * @throws Exception em caso de erro
     */
    public String criarMissao(MissaoModel missao) throws Exception {
        // Construir JSON manualmente (simples)
        String json = String.format(
            "{\"idMissao\":%d,\"tarefa\":\"%s\",\"estado\":\"%s\"," +
            "\"x1\":%.2f,\"y1\":%.2f,\"x2\":%.2f,\"y2\":%.2f,\"prioridade\":%d}",
            missao.idMissao,
            missao.tarefa,
            missao.estado,
            missao.x1,
            missao.y1,
            missao.x2,
            missao.y2,
            missao.prioridade
        );
        
        return post("/missoes", json);
    }
    
    /**
     * Método genérico para fazer POST requests
     */
    private String post(String endpoint, String jsonBody) throws Exception {
        URL url = new URL(baseUrl + endpoint);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Accept", "application/json");
        con.setDoOutput(true);
        
        // Enviar body
        try (OutputStream os = con.getOutputStream()) {
            byte[] input = jsonBody.getBytes("utf-8");
            os.write(input, 0, input.length);
        }
        
        // Verificar resposta
        int status = con.getResponseCode();
        if (status != 200 && status != 201) {
            // Tentar ler mensagem de erro
            BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getErrorStream(), "utf-8")
            );
            StringBuilder errorMsg = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                errorMsg.append(line);
            }
            throw new Exception("HTTP " + status + ": " + errorMsg.toString());
        }
        
        // Ler resposta
        BufferedReader br = new BufferedReader(
            new InputStreamReader(con.getInputStream(), "utf-8")
        );
        StringBuilder response = new StringBuilder();
        String line;
        
        while ((line = br.readLine()) != null) {
            response.append(line.trim());
        }
        
        br.close();
        return response.toString();
    }

    /**
     * Escapa caracteres especiais para JSON
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
