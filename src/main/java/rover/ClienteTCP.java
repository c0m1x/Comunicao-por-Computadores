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
    private Socket socket;
    
    private static final int INTERVALO_RECONEXAO = 5000; // 5 segundos entre tentativas
    private static final int MAX_TENTATIVAS_RECONEXAO = 10;

    public ClienteTCP(ContextoRover ctx, String serverIp, int serverPort) {
        this.ctx = ctx;   
        this.serverIp = serverIp;
        this.serverPort = serverPort;
    }

    @Override
    public void run() {
        int tentativasReconexao = 0;
        
        while (running && ctx.ativo) {
            try {
                //só entra aqui se não estiver conectado, ou se a conexão caiu
                socket = new Socket();
                socket.connect(new InetSocketAddress(serverIp, serverPort), 5000);
                System.out.println("[TCP] Conexão TCP estabelecida para telemetria -> " + serverIp + ":" + serverPort);
                tentativasReconexao = 0; // Reset após conexão bem-sucedida
                
                ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                
                while (running && ctx.ativo && !socket.isClosed()) {
                    if (ctx.deveEnviarTelemetria()) {
                        MensagemTCP msg = new MensagemTCP();
                        msg.header.tipo = TipoMensagem.MSG_TELEMETRY;
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
            } catch (Exception e) {
                if (running && ctx.ativo) {
                    tentativasReconexao++;
                    System.err.println("[TCP] Conexão perdida: " + e.getMessage() + 
                                       " (tentativa " + tentativasReconexao + "/" + MAX_TENTATIVAS_RECONEXAO + ")");
                    
                    if (tentativasReconexao >= MAX_TENTATIVAS_RECONEXAO) {
                        System.err.println("[TCP] Máximo de tentativas de reconexão atingido. Desistindo.");
                        break;
                    }
                    
                    // Fechar socket antigo antes de tentar reconectar
                    fecharSocket();
                    
                    try {
                        System.out.println("[TCP] Aguardando " + (INTERVALO_RECONEXAO/1000) + "s antes de reconectar...");
                        Thread.sleep(INTERVALO_RECONEXAO);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        fecharSocket();
        System.out.println("[TCP] Cliente TCP encerrado.");
    }
    
    private void fecharSocket() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (Exception e) {
                System.err.println("[TCP] Erro ao fechar socket: " + e.getMessage());
            }
        }
    }

    public void stop() { 
        running = false;
        fecharSocket();
    }

    private void sendTCP(MensagemTCP msg, ObjectOutputStream oos) throws Exception {
        oos.writeObject(msg);
        oos.reset();
        oos.flush();
    }
}

