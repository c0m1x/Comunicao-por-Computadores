package nave;

import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

import lib.mensagens.payloads.*;
import lib.mensagens.*;
import lib.*;

/**
 * Servidor TCP da Nave-Mãe (TelemetryLink).
 * Recebe telemetria contínua dos rovers via TCP.
 * faz: gestao de sockets, threads, rececao e parsing de mensagens, atualização de estado do river e missao e gestao de reconexoes
 */
public class ServidorTCP implements Runnable {

    private static final int PORTA_TCP = 5001;
    
    private ServerSocket serverSocket;
    private GestaoEstado estado;
    private boolean running = true;


    public ServidorTCP(GestaoEstado estado) {
        this.estado = estado;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(PORTA_TCP);
            System.out.println("[ServidorTCP] Iniciado na porta " + PORTA_TCP);
            
            while (running) {
                Socket client = serverSocket.accept();
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
                }
            }
            System.out.println("[ServidorTCP] Encerrado");
        }
    }

    private void handleClient(Socket client) {
        String remote = client.getRemoteSocketAddress().toString();
        System.out.println("[ServidorTCP] Nova conexão TCP: " + remote);
        
        Integer idRoverConexao = null;
        boolean roverIdentificado = false;

        try (ObjectInputStream ois = new ObjectInputStream(client.getInputStream())) {
            while (!client.isClosed() && running) {
                Object obj = ois.readObject();
                
                if (obj instanceof MensagemTCP) {
                    MensagemTCP msg = (MensagemTCP) obj;
                    
                    // Identificar rover apenas na primeira mensagem da conexão
                    if (!roverIdentificado) {
                        idRoverConexao = msg.header.idEmissor;
                        registarConexaoRover(idRoverConexao, remote);
                        roverIdentificado = true;
                    }

                    processarMensagemTCP(msg);
                    
                } else {
                    System.out.println("[ServidorTCP] Objeto desconhecido recebido: " + obj.getClass());
                }
            }
        } catch (Exception e) {
            if (running) {
                System.out.println("[ServidorTCP] Conexão perdida: " + remote + " (" + e.getMessage() + ")");
            }
        } finally {
            if (idRoverConexao != null) {
                marcarRoverDesconectado(idRoverConexao);
            }
            try {
                client.close();
            } catch (Exception e) {
            }
        }
    }

    private void registarConexaoRover(int idRover, String endereco) {
        Rover rover = estado.obterRover(idRover);
        if (rover == null) {
            rover = new Rover(idRover, 0.0f, 0.0f, extrairHost(endereco));
            estado.adicionarRover(idRover, rover);
            System.out.println("[ServidorTCP] Rover " + idRover + " registado (IP: " + extrairHost(endereco) + ")");
        } else {
            // Atualizar IP caso tenha mudado
            rover.enderecoHost = extrairHost(endereco);
            System.out.println("[ServidorTCP] Rover " + idRover + " reconectado (IP: " + rover.enderecoHost + ")");
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

        rover.posicaoX = tel.posicaoX;
        rover.posicaoY = tel.posicaoY;
        rover.bateria = tel.bateria;
        rover.velocidade = tel.velocidade;
        
        if (rover.estadoRover == Rover.EstadoRover.ESTADO_RECEBENDO_MISSAO) {
            // Rover está recebendo missão - não sobrescrever com telemetria antiga
            System.out.println("[ServidorTCP] Estado RECEBENDO_MISSAO mantido (ignorando telemetria de " + tel.estadoOperacional + ")");
        } else {
            rover.estadoRover = tel.estadoOperacional;
        }
        estado.atualizarTelemetria(idRover, tel);

        // Atualizar estado da missão com base na telemetria
        if (header.idMissao > 0) {
            if (header.idMissao != rover.idMissaoAtual) {
                rover.idMissaoAtual = header.idMissao;
            }
            rover.temMissao = true;
        } else {
            // Rover sem missão ativa (idMissao <= 0)
            rover.temMissao = false;
            rover.idMissaoAtual = -1;
        }
        
        System.out.printf("[ServidorTCP] Rover %d: pos=(%.2f, %.2f) bat=%.1f%% vel=%.2fm/s estado=%s missao=%d\n",
            idRover, tel.posicaoX, tel.posicaoY, tel.bateria, tel.velocidade, 
            tel.estadoOperacional, rover.idMissaoAtual);
    }

    public void parar() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (Exception e) {
            }
        }
    }
}