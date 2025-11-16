package rover;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import lib.Mensagens;
import lib.Mensagens.MensagemTCP;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import rover.MaquinaEstados.ContextoRover;
import java.net.DatagramSocket;



public class ClienteTCP implements Runnable {
 
    private final ContextoRover ctx;
    private final String serverIp;
    private final int serverPort;
    private volatile boolean running = true;
    private DatagramSocket socket;

    public ClienteTCP(ContextoRover ctx, String serverIp, int serverPort) {
        this.ctx = ctx;   
        this.serverIp = serverIp;
        this.serverPort = serverPort;
    }

    @Override
    public void run() {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(serverIp, serverPort), 5000);
            ctx.socketTcp = sock.getLocalPort();
            System.out.println("Conexão TCP estabelecida para telemetria -> " + serverIp + ":" + serverPort);
    
            try (ObjectOutputStream oos = new ObjectOutputStream(sock.getOutputStream())) {
                while (running && ctx.ativo) {
                    if (ctx.deveEnviarTelemetria()) {
                        Mensagens.MensagemTCP msg = new Mensagens.MensagemTCP();
                        // preencher header
                        msg.header.tipo = Mensagens.TipoMensagem.MSG_ACK;
                        msg.header.idEmissor = ctx.idRover;
                        msg.header.idRecetor = ctx.idNave;
                        msg.header.idMissao = ctx.getMissaoId();
                        msg.header.timestamp = java.sql.Time.valueOf(Instant.now().toString());
                        msg.payload = ctx.getTelemetria();
    
                        sendTCP(msg, oos);
                        ctx.telemetriaEnviada();
                        System.out.println("[ROVER] Telemetria enviada: " + msg);
                    }
                    Thread.sleep(200);
                }
            }
        } catch (Exception e) {
            System.err.println("[ROVER] Erro cliente telemetria: " + e.getMessage());
        } finally {
            ctx.socketTcp = -1;
        }
    }


    public void stop() { 
        running = false; 
    }

    private void sendTCP(MensagemTCP msg, ObjectOutputStream oos) throws Exception {
        oos.writeObject(msg);
        oos.reset();
        oos.flush();
    }
}

