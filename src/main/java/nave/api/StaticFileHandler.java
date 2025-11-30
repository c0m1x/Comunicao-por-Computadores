package nave.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class StaticFileHandler implements HttpHandler {

    private final String baseResource; // ex: "ui"

    public StaticFileHandler(String baseResource) {
        this.baseResource = baseResource;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requested = exchange.getRequestURI().getPath().replaceFirst("/ui", "");
        if (requested.isEmpty() || requested.equals("/")) requested = "/index.html";

        String resourcePath = baseResource + requested; // "ui/index.html"
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);

        if (is == null) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        // tenta adivinhar mime-type pela extensão
        String mime = URLConnection.guessContentTypeFromName(resourcePath);
        if (mime == null) mime = "application/octet-stream";
        exchange.getResponseHeaders().add("Content-Type", mime);

        byte[] buffer = is.readAllBytes();
        exchange.sendResponseHeaders(200, buffer.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(buffer);
        } finally {
            is.close();
        }
    }
}
