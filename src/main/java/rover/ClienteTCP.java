package rover;

import java.io.ObjectOutputStream;

import lib.Mensagens;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;


public class ClienteTCP implements Runnable {
 
    private final MaquinaEstados.RoverContext ctx;
    private final String serverIp;
    private final int serverPort;
    private volatile boolean running = true;

    public ClienteTCP(MaquinaEstados.RoverContext ctx, String serverIp, int serverPort) {
        this.ctx = ctx;   
        this.serverIp = serverIp;
        this.serverPort = serverPort;
    }

    @Override
    public void run() {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(serverIp, serverPort), 5000);
            ctx.socketTcp = sock.getLocalPort(); // apenas para referência
            System.out.println("Conexão TCP estabelecida para telemetria -> " + serverIp + ":" + serverPort);

            try (ObjectOutputStream oos = new ObjectOutputStream(sock.getOutputStream())) {
                while (running && ctx.ativo) {
                    if (ctx.deveEnviarTelemetria()) {
                        Mensagens.MensagemTCP msg = new Mensagens.MensagemTCP();
                        msg.tipo = Mensagens.TipoMensagem.MSG_ACK; // conforme o C
                        msg.idEmissor = ctx.idRover;
                        msg.idRecetor = ctx.idNave;
                        msg.idMissao = ctx.getMissaoId();
                        msg.timestampEpoch = Instant.now().getEpochSecond();
                        msg.payload = ctx.getTelemetria();

                        oos.writeObject(msg);
                        oos.flush();
                        ctx.telemetriaEnviada();
                        System.out.println("[ROVER] Telemetria enviada: " + msg);
                    }
                    // pequeno sleep para evitar busy-loop; no C usavam sleep entre envios
                    Thread.sleep(200); // 200 ms
                }
            }
        } catch (Exception e) {
            System.err.println("[ROVER] Erro cliente telemetria: " + e.getMessage());
        } finally {
            ctx.socketTcp = -1;
        }
    }

    public void stop() { running = false; }
}

