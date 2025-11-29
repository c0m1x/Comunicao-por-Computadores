package nave;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;

import api.CriarJson;
import api.ObservacaoAPI;
import api.StaticFileHandler;

/*
 * Servidor HTTP para expor a API de observação.
 * Usa o HttpServer embutido no JDK.
 * 
 * separa lógica de transporte, protege o estado com a API de observação.
 */
public class ServidorHTTP {

    private final HttpServer server;
    private final ObservacaoAPI api;

    public ServidorHTTP(GestaoEstado estado) throws IOException {
        this.api = new ObservacaoAPI(estado);

        URL uiURL = ServidorHTTP.class.getClassLoader()
                                  .getResource("ui");
        System.out.println("uiURL: " + uiURL);

        server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/rovers", this::handleRovers);
        server.createContext("/missoes", this::handleMissoes);
        server.createContext("/telemetria", this::handleTelemetria);
        server.createContext("/telemetria/historico", this::handleTelemetria);
        server.createContext("/ui", new StaticFileHandler("ui"));
        server.createContext("/ui/", new StaticFileHandler("ui"));

    }

    public void run() {
        System.out.println("[HTTP] Servidor HTTP ativo na porta 8080...");
        server.start();
    }

    // -------- UTILITARIOS --------
    private void responderJson(HttpExchange ex, String json) throws IOException {
        if (json == null) json = "{}";

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

    // ------ ROVERS -------

    private void handleRovers(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        // /rovers
        if (path.equals("/rovers")) {
            responderJson(ex, CriarJson.rovers(api.listarRovers()));
            return;
        }

        // /rovers/{id}
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

        // /missoes
        if (path.equals("/missoes")) {
            responderJson(ex, CriarJson.missoes(api.listarMissoes()));
            return;
        }

        // /missoes/{id}
        if (path.matches("/missoes/\\d+")) {
            int id = Integer.parseInt(path.replace("/missoes/", ""));
            responderJson(ex, CriarJson.missao(api.obterMissao(id)));
            return;
        }

        // /missoes/progresso/{id}
        if (path.matches("/missoes/progresso/\\d+")) {
            int id = Integer.parseInt(path.replace("/missoes/progresso/", ""));
            responderJson(ex, CriarJson.progresso(api.obterProgresso(id)));
            return;
        }

        responder404(ex);
    }

    // ------ TELEMETRIA -------------

    private void handleTelemetria(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        // /telemetria/historico
        if (path.equals("/telemetria/historico")) {
            responderJson(ex, CriarJson.historicoTelemetria(api.listarHistoricoTelemetria()));
            return;
        }

        // /telemetria/{id}
        if (path.matches("/telemetria/\\d+")) {
            int id = Integer.parseInt(path.replace("/telemetria/", ""));
            responderJson(ex, CriarJson.telemetria(api.obterUltimaTelemetria(id)));
            return;
        }

        responder404(ex);
    }
}
