package api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import nave.GestaoEstado;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * API REST HTTP para observação da Nave-Mãe.
 * Expõe o estado dos rovers, missões e telemetria. 
 */
public class ObservacaoApi {

    private final GestaoEstado estado;
    private HttpServer server;

    public ObservacaoApi(GestaoEstado estado) {
        this.estado = estado;
    }

    // ----- UTILITÁRIOS -------

    private void responderJson(HttpExchange ex, String json) throws IOException {
        if (json == null) json = "{}";

        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, json.getBytes().length);

        OutputStream os = ex.getResponseBody();
        os.write(json.getBytes());
        os.close();
    }

    private void responder404(HttpExchange ex) throws IOException {
        String msg = "{\"erro\":\"endpoint desconhecido\"}";
        ex.sendResponseHeaders(404, msg.length());
        ex.getResponseBody().write(msg.getBytes());
        ex.getResponseBody().close();
    }


    public void iniciar(int porto) throws IOException {
        server = HttpServer.create(new InetSocketAddress(porto), 0);

        server.createContext("/rovers", this::handleRovers);
        server.createContext("/missoes", this::handleMissoes);
        server.createContext("/telemetria", this::handleTelemetria);

        System.out.println("API de Observação ativa em http://localhost:" + porto);
        server.start();
    }

    private void handleRovers(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        // /rovers
        if (path.equals("/rovers")) {
            responderJson(ex, CriarJson.rovers(estado.listarRovers()));
            return;
        }

        // /rovers/{id}
        if (path.matches("/rovers/\\d+")) {
            int id = Integer.parseInt(path.replace("/rovers/", ""));
            responderJson(ex, CriarJson.rover(estado.obterRover(id)));
            return;
        }

        responder404(ex);
    }

    private void handleMissoes(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        // /missoes
        if (path.equals("/missoes")) {
            responderJson(ex, CriarJson.missoes(estado.listarMissoes()));
            return;
        }

        // /missoes/{id}
        if (path.matches("/missoes/\\d+")) {
            int id = Integer.parseInt(path.replace("/missoes/", ""));
            responderJson(ex, CriarJson.missao(estado.obterMissao(id)));
            return;
        }

        // /missoes/progresso/{id}
        if (path.matches("/missoes/progresso/\\d+")) {
            int id = Integer.parseInt(path.replace("/missoes/progresso/", ""));
            responderJson(ex, CriarJson.progresso(estado.obterProgresso(id)));
            return;
        }

        responder404(ex);
    }

    private void handleTelemetria(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        // /telemetria/historico
        if (path.equals("/telemetria/historico")) {
            responderJson(ex, CriarJson.historicoTelemetria(estado.obterHistoricoTelemetria()));
            return;
        }

        // /telemetria/{idRover}
        if (path.matches("/telemetria/\\d+")) {
            int id = Integer.parseInt(path.replace("/telemetria/", ""));
            responderJson(ex, CriarJson.telemetria(estado.obterUltimaTelemetria(id)));
            return;
        }

        responder404(ex);
    }


    //todo: telemetria por rover
}
