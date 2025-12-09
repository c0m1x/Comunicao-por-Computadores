package nave.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler HTTP para servir ficheiros estáticos (HTML, CSS, JS, etc.)
 * da pasta de recursos do projeto.

 * Isto permite aceder a recursos em src/main/resources/ui/
 */
public class StaticFileHandler implements HttpHandler {
    
    private static final String DEFAULT_FILE = "/index.html";
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
    private static final int HTTP_OK = 200;
    private static final int HTTP_NOT_FOUND = 404;
    
    private final String resourceBaseFolder;
    
    //Cria um handler para servir ficheiros estáticos.
    public StaticFileHandler(String resourceBaseFolder) {
        this.resourceBaseFolder = resourceBaseFolder;
    }
    
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String requestedPath = getRequestedFilePath(exchange);
            String fullResourcePath = buildFullResourcePath(requestedPath);
            
            InputStream fileStream = loadResource(fullResourcePath);
            if (fileStream == null) {
                sendNotFound(exchange);
                return;
            }
            
            sendFile(exchange, fileStream, fullResourcePath);
        } catch (Exception e) {
            System.err.println("[StaticFileHandler] Erro ao servir ficheiro: " + e.getMessage());
            sendNotFound(exchange);
        }
    }
    
    //Extrai o caminho do ficheiro pedido a partir do URI.
    private String getRequestedFilePath(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        
        // Remover prefixo "/ui" (ou outro contexto)
        path = path.replaceFirst("/" + resourceBaseFolder, "");
        
        // Se vazio ou raiz, servir index.html
        if (path.isEmpty() || path.equals("/")) {
            path = DEFAULT_FILE;
        }
        
        return path;
    }
    
    //Constrói o caminho completo do recurso. Exemplo: "ui" + "/style.css" → "ui/style.css"
    private String buildFullResourcePath(String requestedPath) {
        return resourceBaseFolder + requestedPath;
    }
    
    //Carrega o recurso do classpath. Retorna null se não encontrado.
    private InputStream loadResource(String resourcePath) {
        return getClass().getClassLoader().getResourceAsStream(resourcePath);
    }
    
    //Envia o ficheiro como resposta HTTP 200.
    private void sendFile(HttpExchange exchange, InputStream fileStream, String resourcePath) 
            throws IOException {
        
        try {
            String mimeType = detectMimeType(resourcePath);
            byte[] fileContent = fileStream.readAllBytes();
            
            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.sendResponseHeaders(HTTP_OK, fileContent.length);
            
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(fileContent);
            }
            
        } finally {
            fileStream.close();
        }
    }
    
    // Envia resposta HTTP 404 (Not Found).
    private void sendNotFound(HttpExchange exchange) throws IOException {
        String notFoundMessage = "404 - Ficheiro não encontrado";
        byte[] response = notFoundMessage.getBytes();
        
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(HTTP_NOT_FOUND, response.length);
        
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(response);
        }
    }
    
    /**
     * Detecta o tipo MIME baseado na extensão do ficheiro.
     * Adiciona suporte explícito para tipos comuns da web.
     */
    private String detectMimeType(String filePath) {
        // Tentar detecção automática primeiro
        String mimeType = URLConnection.guessContentTypeFromName(filePath);
        
        if (mimeType != null) {
            return mimeType;
        }
        
        // Fallback manual para tipos comuns que podem não ser detectados
        if (filePath.endsWith(".html") || filePath.endsWith(".htm")) {
            return "text/html";
        }
        if (filePath.endsWith(".css")) {
            return "text/css";
        }
        if (filePath.endsWith(".js")) {
            return "application/javascript";
        }
        if (filePath.endsWith(".json")) {
            return "application/json";
        }
        if (filePath.endsWith(".png")) {
            return "image/png";
        }
        if (filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (filePath.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (filePath.endsWith(".ico")) {
            return "image/x-icon";
        }
        
        // Tipo genérico se não conseguir identificar
        return DEFAULT_MIME_TYPE;
    }
}