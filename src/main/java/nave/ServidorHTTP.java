package nave;

import com.sun.net.httpserver.*;

import nave.api.CriarJson;
import nave.api.ObservacaoAPI;
import nave.api.StaticFileHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.stream.Collectors;

import lib.Missao;

/*
 * Servidor HTTP para expor a API de observação.
 * Usa o HttpServer embutido no JDK.
 * 
 * separa lógica de transporte, protege o estado com a API de observação.
 */
public class ServidorHTTP {

    private final HttpServer server;
    private final ObservacaoAPI api;
    private final GestaoEstado estado;

    public ServidorHTTP(GestaoEstado estado) throws IOException {
        this.estado = estado;
        this.api = new ObservacaoAPI(estado);

        server = HttpServer.create(new InetSocketAddress(8080), 0);

        URL uiURL = ServidorHTTP.class.getClassLoader().getResource("ui");
        System.out.println("uiURL: " + uiURL);

        server.createContext("/rovers", this::handleRovers);
        server.createContext("/missoes", this::handleMissoes);
        //especifico antes do generico
        server.createContext("/telemetria/historico", this::handleTelemetriaHistorico);
        server.createContext("/telemetria", this::handleTelemetria);
        
        server.createContext("/ui", new StaticFileHandler("ui"));
        server.createContext("/ui/", new StaticFileHandler("ui"));
    }

    public void run() {
        System.out.println("[HTTP] Servidor HTTP ativo na porta 8080...");
        System.out.println("[HTTP] UI disponível em: http://localhost:8080/ui/");
        System.out.println("[HTTP] API disponível em: http://localhost:8080/rovers, /missoes, /telemetria");
        server.start();
    }

    // -------- UTILITARIOS --------
    private void responderJson(HttpExchange ex, String json) throws IOException {
        if (json == null) json = "{}";

        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().add("Content-Type", "application/json");
        
        byte[] bytes = json.getBytes();
        ex.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void responder404(HttpExchange ex) throws IOException {
        String msg = "{\"erro\":\"endpoint desconhecido\"}";
        byte[] bytes = msg.getBytes();

        ex.sendResponseHeaders(404, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void responderErro(HttpExchange ex, int status, String mensagem) throws IOException {
        String msg = "{\"erro\":\"" + mensagem + "\"}";
        byte[] bytes = msg.getBytes();

        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ------ ROVERS -------

    private void handleRovers(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        // OPTIONS - CORS preflight
        if (method.equals("OPTIONS")) {
            handleCorsOptions(ex);
            return;
        }

        // GET /rovers
        if (path.equals("/rovers")) {
            responderJson(ex, CriarJson.rovers(api.listarRovers()));
            return;
        }

        // GET /rovers/{id}
        if (path.matches("/rovers/\\d+")) {
            int id = Integer.parseInt(path.replace("/rovers/", ""));
            responderJson(ex, CriarJson.rover(api.obterRover(id)));
            return;
        }

        responder404(ex);
    }

    // ----- MISSÕES ----------

    private void handleMissoes(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        
        // OPTIONS - CORS preflight
        if (method.equals("OPTIONS")) {
            handleCorsOptions(ex);
            return;
        }

        // POST /missoes - Criar nova missão
        if (method.equals("POST") && path.equals("/missoes")) {
            handleCriarMissao(ex);
            return;
        }

        // GET /missoes
        if (path.equals("/missoes")) {
            responderJson(ex, CriarJson.missoes(api.listarMissoes()));
            return;
        }

        // GET /missoes/{id}
        if (path.matches("/missoes/\\d+")) {
            int id = Integer.parseInt(path.replace("/missoes/", ""));
            responderJson(ex, CriarJson.missao(api.obterMissao(id)));
            return;
        }

        // GET /missoes/progresso/{id}
        if (path.matches("/missoes/progresso/\\d+")) {
            int id = Integer.parseInt(path.replace("/missoes/progresso/", ""));
            responderJson(ex, CriarJson.progresso(api.obterProgresso(id)));
            return;
        }

        responder404(ex);
    }
    
    /**
     * Handler para criar nova missão via POST
     */
    private void handleCriarMissao(HttpExchange ex) throws IOException {
        try {
            String body = new BufferedReader(new InputStreamReader(ex.getRequestBody()))
                                                .lines().collect(Collectors.joining("\n"));
            
            System.out.println("[HTTP] POST /missoes - Body: " + body);
            if (body == null || body.trim().isEmpty()) {
                responderErro(ex, 400, "Body vazio");
                return;
            }
            
            // Parse JSON para Missao
            Missao missao = parseMissaoFromJson(body);
            
            // Adicionar missão ao estado
            estado.adicionarMissao(missao);
            System.out.println("[HTTP] Missão criada: #" + missao.idMissao + " - " + missao.tarefa);
            
            // Responder sucesso
            String response = String.format(
                "{\"status\":\"success\",\"id\":%d,\"mensagem\":\"Missão criada com sucesso\"}",
                missao.idMissao
            );
            
            responderJson(ex, response);
            
        } catch (IllegalArgumentException e) {
            System.err.println("[HTTP] ✗ Erro de validação: " + e.getMessage());
            responderErro(ex, 400, "Dados inválidos: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[HTTP] ✗ Erro ao criar missão: " + e.getMessage());
            e.printStackTrace();
            responderErro(ex, 500, "Erro interno: " + e.getMessage());
        }
    }

    /**
     * Parse JSON para Missao
     * Formato esperado: {"idMissao":1,"tarefa":"...","estado":"PENDENTE",...}
     */
    private Missao parseMissaoFromJson(String json) {
        Missao missao = new Missao();
        
        // Remove espaços, chaves e quebras de linha
        json = json.trim().replaceAll("[{}\\n\\r]", "");
        
        // Split por vírgulas (fora de strings com aspas)
        String[] fields = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        
        for (String field : fields) {
            String[] kv = field.split(":", 2);
            if (kv.length != 2) continue;
            
            String key = kv[0].replace("\"", "").trim();
            String value = kv[1].replace("\"", "").trim();
            
            try {
                switch (key) {
                    case "idMissao" -> missao.idMissao = Integer.parseInt(value);
                    case "tarefa" -> missao.tarefa = value;
                    case "estado" -> missao.estadoMissao = Missao.EstadoMissao.valueOf(value);
                    case "x1" -> missao.x1 = Float.parseFloat(value);
                    case "y1" -> missao.y1 = Float.parseFloat(value);
                    case "x2" -> missao.x2 = Float.parseFloat(value);
                    case "y2" -> missao.y2 = Float.parseFloat(value);
                    case "prioridade" -> missao.prioridade = Integer.parseInt(value);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Valor inválido para campo '" + key + "': " + value);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Estado inválido: " + value);
            }
        }
        
        // Validações básicas
        if (missao.idMissao <= 0) {
            throw new IllegalArgumentException("idMissao deve ser > 0");
        }
        if (missao.tarefa == null || missao.tarefa.isEmpty()) {
            throw new IllegalArgumentException("tarefa não pode estar vazia");
        }
        
        return missao;
    }
    
    /**
     * Handler para CORS preflight requests
     */
    private void handleCorsOptions(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        ex.sendResponseHeaders(204, -1); // No content
    }


    // ------ TELEMETRIA -------------

    private void handleTelemetria(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        // OPTIONS - CORS preflight
        if (method.equals("OPTIONS")) {
            handleCorsOptions(ex);
            return;
        }

        // GET /telemetria/{id}
        if (path.matches("/telemetria/\\d+")) {
            int id = Integer.parseInt(path.replace("/telemetria/", ""));
            responderJson(ex, CriarJson.telemetria(api.obterUltimaTelemetria(id)));
            return;
        }

        responder404(ex);
    }

    private void handleTelemetriaHistorico(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        
        // OPTIONS
        if (method.equals("OPTIONS")) {
            handleCorsOptions(ex);
            return;
        }
        
        // GET /telemetria/historico
        if (method.equals("GET")) {
            responderJson(ex, CriarJson.historicoTelemetria(api.listarHistoricoTelemetria()));
            return;
        }
        
        responder404(ex);
    }

    public void parar() {
        server.stop(0);
        System.out.println("[HTTP] Servidor HTTP parado.");
    }
}
