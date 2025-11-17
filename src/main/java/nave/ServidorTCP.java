package nave;

import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.Instant;

import lib.mensagens.payloads.*;
import lib.mensagens.*;
import nave.GestaoEstado;
import nave.Rover;
import nave.Missao;

public class ServidorTCP {

    private final int port;
    private final ExecutorService pool;
    private volatile boolean running = true;
    private final GestaoEstado estado;
    

    public interface TelemetriaCallback {
        void onTelemetriaRecebida(int idRover, PayloadTelemetria telemetria);
        void onBateriaBaixa(int idRover, float nivelBateria);
        void onRoverDesconectado(int idRover);
        void onMudancaEstado(int idRover, String novoEstado);
    }
    
    private TelemetriaCallback callback;

    public ServidorTCP(int port, GestaoEstado estado) {
        this.port = port;
        this.estado = estado;
        this.pool = Executors.newCachedThreadPool();
    }
    
    public void setCallback(TelemetriaCallback callback) {
        this.callback = callback;
    }

    public void start() throws Exception {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("✓ Servidor TCP telemetria escuta na porta " + port);
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
        System.out.println("→ Nova conexão TCP: " + remote);
        
        Integer idRoverConexao = null;
        
        try (ObjectInputStream ois = new ObjectInputStream(client.getInputStream())) {
            while (!client.isClosed() && running) {
                Object obj = ois.readObject();
                
                if (obj instanceof MensagemTCP) {
                    MensagemTCP msg = (MensagemTCP) obj;
                    
                    // Identificar rover na primeira mensagem
                    if (idRoverConexao == null) {
                        idRoverConexao = msg.header.idEmissor;
                        verificarOuCriarRover(idRoverConexao, remote);
                    }
                    
                    processarMensagemTCP(msg);
                    
                } else {
                    System.out.println("⚠ Objeto desconhecido recebido: " + obj.getClass());
                }
            }
        } catch (Exception e) {
            if (running) {
                System.out.println("✗ Rover desconectou: " + remote + " (" + e.getMessage() + ")");
            }
        } finally {
            if (idRoverConexao != null) {
                marcarRoverDesconectado(idRoverConexao);
            }
        }
    }

    private void verificarOuCriarRover(int idRover, String endereco) {
        Rover rover = estado.obterRover(idRover);
        
        if (rover == null) {
            // Criar novo rover se não existir (posição inicial padrão)
            rover = new Rover(idRover, 0.0f, 0.0f);
            
            estado.adicionarRover(idRover, rover);
            System.out.println("✓ Rover " + idRover + " registrado no sistema (conexão: " + endereco + ")");
        } else {
            System.out.println("✓ Rover " + idRover + " reconectado (conexão: " + endereco + ")");
        }
    }
    
    private void marcarRoverDesconectado(int idRover) {
        Rover rover = estado.obterRover(idRover);
        if (rover != null) {
            System.out.println("✗ Rover " + idRover + " desconectado");
            
            if (callback != null) {
                callback.onRoverDesconectado(idRover);
            }
        }
    }

    private void processarMensagemTCP(MensagemTCP msg) {
        int idRover = msg.header.idEmissor;

        switch (msg.header.tipo) {
            case MSG_RESPONSE:
                if (msg.payload instanceof PayloadTelemetria) {
                    processarTelemetria(idRover, msg.header, (PayloadTelemetria) msg.payload);
                }
                break;
                
            default:
                System.out.println("⚠ Tipo de mensagem TCP não esperado: " + msg.header.tipo);
                break;
        }
    }

    private void processarTelemetria(int idRover, CabecalhoTCP header, PayloadTelemetria tel) {
        Rover rover = estado.obterRover(idRover);
        
        if (rover == null) {
            System.out.println("Telemetria recebida de rover desconhecido: " + idRover);
            return;
        }

        String estadoAnterior = rover.estadoOperacional;

        rover.posicaoX = tel.posicaoX;
        rover.posicaoY = tel.posicaoY;
        rover.bateria = tel.bateria;
        rover.velocidade = tel.velocidade;
        rover.estadoOperacional = tel.estadoOperacional;

        if (header.idMissao > 0 && header.idMissao != rover.idMissaoAtual) {
            rover.idMissaoAtual = header.idMissao;
            rover.temMissao = true;
        }
        //Possivel DEBUG AQUI
        System.out.printf("[Rover %d] pos=(%.2f, %.2f) bat=%.1f%% vel=%.2fm/s estado=%s missao=%d\n",
            idRover, tel.posicaoX, tel.posicaoY, tel.bateria, tel.velocidade, 
            tel.estadoOperacional, rover.idMissaoAtual);

        if (callback != null) {
            callback.onTelemetriaRecebida(idRover, tel);

            if (tel.bateria < 20.0f && tel.bateria > 0.0f) {
                callback.onBateriaBaixa(idRover, tel.bateria);
            }

            if (estadoAnterior != null && !estadoAnterior.equals(tel.estadoOperacional)) {
                callback.onMudancaEstado(idRover, tel.estadoOperacional);
            }
        }

        if ("SUCCESS".equals(tel.estadoOperacional) && rover.idMissaoAtual > 0) {

            Missao missao = estado.obterMissao(rover.idMissaoAtual);

            if (missao != null && missao.estadoMissao == Missao.EstadoMissao.EM_ANDAMENTO) {
                missao.estadoMissao = Missao.EstadoMissao.CONCLUIDA;
                rover.temMissao = false;
                rover.progressoMissao = 100.0f;
                //depois tiramos este print, para já pode servir de debug
                System.out.println("Missão " + rover.idMissaoAtual + " concluída pelo Rover " + idRover);
            }
        }
    }

    
    public int getNumeroRoversAtivos() {
        return estado.listarRovers().size();
    }
    
    public void imprimirEstadoRovers() {
        System.out.println("\n========== Estado dos Rovers ==========");
        var rovers = estado.listarRovers();
        
        if (rovers.isEmpty()) {
            System.out.println("Nenhum rover registrado");
        } else {
            for (Rover r : rovers) {
                String status = (r.bateria > 20.0f) ? "ATIVO" : "BATERIA BAIXA";
                
                System.out.printf("  [%s] Rover %d: pos=(%.2f,%.2f) bat=%.1f%% estado=%s missao=%d\n",
                    status, r.idRover, r.posicaoX, r.posicaoY, r.bateria, 
                    r.estadoOperacional, r.idMissaoAtual);
            }
        }
        System.out.println("=======================================\n");
    }

    public void stop() { 
        running = false;
        pool.shutdownNow();
        System.out.println("✗ Servidor TCP encerrado");
    }
}