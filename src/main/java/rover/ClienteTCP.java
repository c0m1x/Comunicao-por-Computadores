package rover;

import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import lib.mensagens.*;
import lib.TipoMensagem;

public class ClienteTCP implements Runnable {
 
    private final ContextoRover ctx;
    private final String serverIp;
    private final int serverPort;
    private volatile boolean running = true;

    public ClienteTCP(ContextoRover ctx, String serverIp, int serverPort) {
        this.ctx = ctx;   
        this.serverIp = serverIp;
        this.serverPort = serverPort;
    }


    //DUVIDA: é preciso implementar tentativa de Reconexão automática do cliente TCP se a conexão cair?
    @Override
    public void run() {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(serverIp, serverPort), 5000);
            System.out.println("[TCP] Conexão TCP estabelecida para telemetria -> " + serverIp + ":" + serverPort);
    
            try (ObjectOutputStream oos = new ObjectOutputStream(sock.getOutputStream())) {
                while (running && ctx.ativo) {
                    if (ctx.deveEnviarTelemetria()) {

                        MensagemTCP msg = new MensagemTCP();
                        msg.header.tipo = TipoMensagem.MSG_TELEMETRY; // Utilizar o tipo esperado pelo ServidorTCP
                        msg.header.idEmissor = ctx.idRover;
                        msg.header.idRecetor = ctx.idNave;
                        msg.header.idMissao = ctx.getMissaoId();
                        msg.payload = ctx.getTelemetria();
    
                        sendTCP(msg, oos);
                        ctx.telemetriaEnviada();
                        System.out.println("[TCP] Telemetria enviada: " + msg);
                    }
                    Thread.sleep(200);
                }
            }
        } catch (Exception e) {
            System.err.println("[TCP] Erro cliente telemetria: " + e.getMessage());
        } 
    }
    //nota: onde fechar socket

    public void stop() { 
        running = false; 
    }

    private void sendTCP(MensagemTCP msg, ObjectOutputStream oos) throws Exception {
        oos.writeObject(msg);
        oos.reset();
        oos.flush();
    }
}

