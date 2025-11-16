package nave;

import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lib.Mensagens;



public class ServidorTCP {

    private final int port;
    private final ExecutorService pool;
    private volatile boolean running = true;

    public ServidorTCP(int port) {
        this.port = port;
        this.pool = Executors.newCachedThreadPool();
    }

    public void start() throws Exception {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Servidor TCP telemetria escuta na porta " + port);
            while (running) {
                Socket client = server.accept();
                pool.submit(() -> handleClient(client));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void handleClient(Socket client) {
        String remote = client.getRemoteSocketAddress().toString();
        System.out.println("Nova conexão rover: " + remote);
        try (ObjectInputStream ois = new ObjectInputStream(client.getInputStream())) {
            while (!client.isClosed()) {
                Object obj = ois.readObject();
                if (obj instanceof Mensagens.MensagemTCP) {
                    Mensagens.MensagemTCP msg = (Mensagens.MensagemTCP) obj;
                    processarTelemetria(msg);
                    // atualizar estado interno se necessário (podes manter um map id->context)
                } else {
                    System.out.println("Recebido objeto desconhecido: " + obj.getClass());
                }
            }
        } catch (Exception e) {
            System.out.println("Rover desconectou: " + remote + " (" + e.getMessage() + ")");
        }
    }

    // Placeholder: implementar processamento real
    private void processarTelemetria(Mensagens.MensagemTCP msg) {
        System.out.println("Telemetria recebida: " + msg);
        if (msg.payload instanceof Mensagens.PayloadTelemetria) {
            Mensagens.PayloadTelemetria p = (Mensagens.PayloadTelemetria) msg.payload;
            System.out.println("-> " + p);
            // aqui podes atualizar estruturas internas (por ex. Map<Integer, RoverContext>)
        }
    }

    public void stop() { running = false; }
}
