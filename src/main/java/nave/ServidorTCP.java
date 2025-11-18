package nave;

import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

import lib.mensagens.payloads.*;
import lib.mensagens.*;
import lib.*;
import lib.Rover.EstadoRover;

/**
 * Servidor TCP da Nave-Mãe (TelemetryLink).
 * Recebe telemetria contínua dos rovers via TCP.
 */
public class ServidorTCP implements Runnable {

    private static final int PORTA_TCP = 5001;
    
    private ServerSocket serverSocket;
    private GestaoEstado estado;
    private boolean running = true;
    //TODO: meter isto dos callbacks a dar
    private TelemetriaCallback callback;

    public interface TelemetriaCallback {
        void onTelemetriaRecebida(int idRover, PayloadTelemetria telemetria);
        void onBateriaBaixa(int idRover, float nivelBateria);
        void onRoverDesconectado(int idRover);
        void onMudancaEstado(int idRover, EstadoRover novoEstado);
    }

    public ServidorTCP(GestaoEstado estado) {
        this.estado = estado;
    }
    
    public void setCallback(TelemetriaCallback callback) {
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(PORTA_TCP);
            System.out.println("[ServidorTCP] Iniciado na porta " + PORTA_TCP);
            
            while (running) {
                Socket client = serverSocket.accept();
                
                // Criar thread daemon para cada cliente
                Thread t = new Thread(() -> handleClient(client));
                t.setDaemon(true);
                t.start();
            }
            
        } catch (Exception e) {
            if (running) {
                System.err.println("[ServidorTCP] Erro no servidor: " + e.getMessage());
            }
        } finally {
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (Exception e) {
                    // Ignorar
                }
            }
            System.out.println("[ServidorTCP] Encerrado");
        }
    }

    private void handleClient(Socket client) {
        String remote = client.getRemoteSocketAddress().toString();
        System.out.println("[ServidorTCP] Nova conexão: " + remote);
        
        Integer idRoverConexao = null;

        try (ObjectInputStream ois = new ObjectInputStream(client.getInputStream())) {
            while (!client.isClosed() && running) {
                Object obj = ois.readObject();
                
                if (obj instanceof MensagemTCP) {
                    MensagemTCP msg = (MensagemTCP) obj;
                    
                    // Nota: Identificar rover na primeira mensagem, ver se há maneira de isto ser feito so uma vez ao receber a primeira mensagem do socket
                    idRoverConexao = msg.header.idEmissor;
                    verificarOuCriarRover(idRoverConexao, remote);

                    processarMensagemTCP(msg);
                    
                } else {
                    System.out.println("[ServidorTCP] Objeto desconhecido recebido: " + obj.getClass());
                }
            }
        } catch (Exception e) {
            if (running) {
                System.out.println("[ServidorTCP] Rover desconectou: " + remote + " (" + e.getMessage() + ")");
            }
        } finally {
            if (idRoverConexao != null) {
                marcarRoverDesconectado(idRoverConexao); //NOTA: ver se é preciso dizer que conectou e desconectou de cada vez que manda mensagem
            }
            try {
                client.close();
            } catch (Exception e) {
                // Ignorar
            }
        }
    }

    private void verificarOuCriarRover(int idRover, String endereco) {
        Rover rover = estado.obterRover(idRover);
        if (rover == null) {
            // Criar novo rover se não existir (posição inicial padrão)
            rover = new Rover(idRover, 0.0f, 0.0f, extrairHost(endereco));
            estado.adicionarRover(idRover, rover);
            System.out.println("[ServidorTCP] Rover " + idRover + " registrado no sistema (conexão: " + endereco + ")");
        } else {
            System.out.println("[ServidorTCP] Rover " + idRover + " reconectado (conexão: " + endereco + ")");
        }
    }

    // Extrai apenas o host/IP de uma string no formato "/ip:porta"
    private String extrairHost(String remote) {
        if (remote == null) return null;
        String s = remote.trim();
        if (s.startsWith("/")) s = s.substring(1);
        int idx = s.indexOf(':');
        if (idx > 0) s = s.substring(0, idx);
        return s;
    }
    
    private void marcarRoverDesconectado(int idRover) {
        Rover rover = estado.obterRover(idRover);
        if (rover != null) {
            System.out.println("[ServidorTCP] Rover " + idRover + " desconectado");
            
            if (callback != null) {
                callback.onRoverDesconectado(idRover);
            }
        }
    }

    private void processarMensagemTCP(MensagemTCP msg) {
        int idRover = msg.header.idEmissor;

        switch (msg.header.tipo) {
            case MSG_TELEMETRY:
                if (msg.payload instanceof PayloadTelemetria) {
                    processarTelemetria(idRover, msg.header, (PayloadTelemetria) msg.payload);
                }
                break;
                
            default:
                System.out.println("[ServidorTCP] Tipo de mensagem TCP não esperado: " + msg.header.tipo);
                break;
        }
    }

    private void processarTelemetria(int idRover, CabecalhoTCP header, PayloadTelemetria tel) {
        Rover rover = estado.obterRover(idRover);
        
        if (rover == null) {
            System.out.println("[ServidorTCP] Telemetria recebida de rover desconhecido: " + idRover);
            return;
        }

        String estadoAnterior = rover.estadoRover.toString();

        rover.posicaoX = tel.posicaoX;
        rover.posicaoY = tel.posicaoY;
        rover.bateria = tel.bateria;
        rover.velocidade = tel.velocidade;
        rover.estadoRover = tel.estadoOperacional;

        if (header.idMissao > 0 && header.idMissao != rover.idMissaoAtual) {
            rover.idMissaoAtual = header.idMissao;
            rover.temMissao = true;
        }
        
        System.out.printf("[ServidorTCP] Rover %d: pos=(%.2f, %.2f) bat=%.1f%% vel=%.2fm/s estado=%s missao=%d\n",
            idRover, tel.posicaoX, tel.posicaoY, tel.bateria, tel.velocidade, 
            tel.estadoOperacional, rover.idMissaoAtual);

        if (callback != null) {
            callback.onTelemetriaRecebida(idRover, tel);

            if (tel.bateria < 20.0f && tel.bateria > 0.0f) {
                callback.onBateriaBaixa(idRover, tel.bateria);
            }

            if (estadoAnterior != null && !estadoAnterior.equals(tel.estadoOperacional.toString())) {
                callback.onMudancaEstado(idRover, tel.estadoOperacional);
            }
        }

        //nota: este equals nao sei se vai funcionar bem, testar
        if ("SUCCESS".equals(tel.estadoOperacional.toString()) && rover.idMissaoAtual > 0) {
            Missao missao = estado.obterMissao(rover.idMissaoAtual);

            if (missao != null && missao.estadoMissao == Missao.EstadoMissao.EM_ANDAMENTO) {
                missao.estadoMissao = Missao.EstadoMissao.CONCLUIDA;
                rover.temMissao = false;
                rover.progressoMissao = 100.0f;
                System.out.println("[ServidorTCP] Missão " + rover.idMissaoAtual + " concluída pelo Rover " + idRover);
            }
        }
    }

    public void parar() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (Exception e) {
                // Ignorar
            }
        }
    }
}