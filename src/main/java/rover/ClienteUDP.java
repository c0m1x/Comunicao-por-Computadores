package rover;

import lib.Mensagens.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Cliente UDP do Rover (MissionLink).
 * Recebe missões da Nave-Mãe seguindo o protocolo fiável.
 * 
 * Fluxo:
 * 1. Recebe HELLO da Nave-Mãe
 * 2. Responde com RESPONSE (confirma disponibilidade)
 * 3. Recebe fragmentos MISSION
 * 4. Envia ACK (completo ou com lista de fragmentos perdidos)
 * 5. Recebe retransmissões se necessário
 * 6. Envia ACK final (missing=[])
 */
public class ClienteUDP implements Runnable {
    
    private static final int TIMEOUT_MS = 10000;
    
    private int idRover;
    private int porta;
    private DatagramSocket socket;
    private boolean running = true;
    private MaquinaEstados maquina;
    
    // Controle da sessão completa da missão (Fase 1 + Fase 2)
    private SessaoClienteMissionLink sessaoAtual = null;
    private Thread threadProgresso = null;
    
    public ClienteUDP(int idRover, MaquinaEstados maquina) {
        this.idRover = idRover;
        this.porta = 9010 + idRover; // Porta baseada no ID
        this.maquina = maquina;
    }
    
    @Override
    public void run() {
        try {
            socket = new DatagramSocket(porta);
            socket.setSoTimeout(100);
            System.out.println("[ClienteUDP] Rover " + idRover + " iniciado na porta " + porta);
            
            while (running) {
                try {
                    byte[] buffer = new byte[4096];
                    DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                    socket.receive(pacote);
                    
                    // Processar mensagem recebida
                    processarMensagem(pacote);
                    
                } catch (SocketTimeoutException e) {
                    // Normal, continuar
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[ClienteUDP] Erro ao receber: " + e.getMessage());
                    }
                }
            }
            
        } catch (SocketException e) {
            System.err.println("[ClienteUDP] Erro ao criar socket: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("[ClienteUDP] Rover " + idRover + " encerrado");
        }
    }
    
    /**
     * Processa mensagem recebida da Nave-Mãe.
     */
    private void processarMensagem(DatagramPacket pacote) {
        try {
            MensagemUDP msg = deserializarMensagem(pacote.getData(), pacote.getLength());
            
            if (msg == null || msg.header == null) {
                return;
            }
            
            // Verificar se a mensagem é para este rover
            if (msg.header.idRecetor != idRover) {
                return;
            }
            
            switch (msg.header.tipo) {
                case MSG_HELLO:
                    processarHello(msg, pacote.getAddress(), pacote.getPort());
                    break;
                    
                case MSG_MISSION:
                    processarMission(msg, pacote.getAddress(), pacote.getPort());
                    break;
                    
                case MSG_ACK:
                    processarAck(msg, pacote.getAddress(), pacote.getPort());
                    break;
                    
                default:
                    System.out.println("[ClienteUDP] Mensagem inesperada: " + msg.header.tipo);
            }
            
        } catch (Exception e) {
            System.err.println("[ClienteUDP] Erro ao processar mensagem: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Processa mensagem HELLO.
     * Verifica se está disponível e responde.
     */
    private void processarHello(MensagemUDP msg, InetAddress endereco, int porta) {
        System.out.println("[ClienteUDP] HELLO recebido - Missão ID: " + msg.header.idMissao);
        
        // Verificar se rover está disponível para receber missão
        boolean disponivel = maquina != null; //TODO:  verificar estado real do rover
        
        if (disponivel) {
            // Criar nova sessão de missão
            sessaoAtual = new SessaoClienteMissionLink();
            sessaoAtual.idMissao = msg.header.idMissao;
            sessaoAtual.enderecoNave = endereco;
            sessaoAtual.portaNave = porta;
            sessaoAtual.totalFragmentos = 0; // Será atualizado ao receber primeiro MISSION
            sessaoAtual.fragmentosRecebidos = new HashMap<>();
            
            System.out.println("[ClienteUDP] Rover disponível - Aguardando fragmentos MISSION");
        } else {
            System.out.println("[ClienteUDP] Rover não disponível para missão");
        }
        
        // Enviar RESPONSE
        enviarResponse(msg.header.idMissao, msg.header.seq, disponivel, endereco, porta);
    }
    
    /**
     * Processa fragmento MISSION.
     */
    private void processarMission(MensagemUDP msg, InetAddress endereco, int porta) {
        if (sessaoAtual == null || sessaoAtual.idMissao != msg.header.idMissao) {
            System.err.println("[ClienteUDP] Fragmento recebido sem sessão ativa");
            return;
        }
        
        int seq = msg.header.seq;
        
        // Atualizar total de fragmentos se necessário (primeira vez que recebemos um MISSION)
        if (sessaoAtual.totalFragmentos == 0 && msg.header.totalFragm > 1) {
            sessaoAtual.totalFragmentos = msg.header.totalFragm - 1; // -1 porque HELLO (seq=1) não conta
            System.out.println("[ClienteUDP] Total de fragmentos atualizado: " + sessaoAtual.totalFragmentos);
        }
        
        System.out.println("[ClienteUDP] Fragmento recebido: seq=" + seq + "/" + msg.header.totalFragm);
        
        // Extrair dados do fragmento
        byte[] dados = null;
        try {
            dados = extrairDadosFragmento(msg.payload);
        } catch (Exception e) {
            System.err.println("[ClienteUDP] Erro ao extrair fragmento: " + e.getMessage());
            return;
        }
        
        if (dados != null) {
            sessaoAtual.fragmentosRecebidos.put(seq, dados);
        }
        
        // Verificar se recebemos todos os fragmentos
        int fragmentosEsperados = sessaoAtual.totalFragmentos;
        int fragmentosRecebidos = sessaoAtual.fragmentosRecebidos.size();
        
        System.out.println("[ClienteUDP] Progresso: " + fragmentosRecebidos + "/" + fragmentosEsperados);
        
        // Após receber alguns fragmentos, enviar ACK
        // Só processar se já soubermos quantos fragmentos esperar
        if (fragmentosEsperados > 0 && 
            (fragmentosRecebidos >= fragmentosEsperados || 
             (fragmentosRecebidos > 0 && fragmentosRecebidos % 5 == 0))) {
            
            // Identificar fragmentos perdidos
            List<Integer> perdidos = identificarFragmentosPerdidos();
            
            if (perdidos.isEmpty()) {
                // Todos recebidos - reconstruir missão
                if (reconstruirMissao()) {
                    System.out.println("[ClienteUDP] Missão recebida com sucesso!");
                    enviarAck(msg.header.idMissao, msg.header.seq, perdidos, endereco, porta);
                    sessaoAtual = null; // Finalizar sessão
                } else {
                    System.err.println("[ClienteUDP] Erro ao reconstruir missão");
                }
            } else {
                // Enviar ACK com fragmentos perdidos
                System.out.println("[ClienteUDP] Solicitando retransmissão de " + perdidos.size() + " fragmentos");
                enviarAck(msg.header.idMissao, msg.header.seq, perdidos, endereco, porta);
            }
        }
    }
    
    /**
     * Processa mensagem ACK (para PROGRESS e COMPLETED).
     */
    private void processarAck(MensagemUDP msg, InetAddress endereco, int porta) {
        if (sessaoAtual != null && sessaoAtual.emExecucao && msg.header.idMissao == sessaoAtual.idMissao) {
            System.out.println("[ClienteUDP] ACK recebido para seq=" + msg.header.seq + 
                             " (sucesso=" + msg.header.flagSucesso + ")");
            sessaoAtual.aguardandoAck = false;
        }
    }
    
    /**
     * Identifica quais fragmentos ainda não foram recebidos.
     */
    private List<Integer> identificarFragmentosPerdidos() {
        List<Integer> perdidos = new ArrayList<>();
        
        // Fragmentos começam em seq=2 (seq=1 é HELLO)
        for (int i = 2; i <= sessaoAtual.totalFragmentos + 1; i++) {
            if (!sessaoAtual.fragmentosRecebidos.containsKey(i)) {
                perdidos.add(i);
            }
        }
        
        return perdidos;
    }
    
    /**
     * Reconstrói a missão a partir dos fragmentos.
     */
    private boolean reconstruirMissao() {
        try {
            // Ordenar fragmentos por sequência
            List<Integer> seqs = new ArrayList<>(sessaoAtual.fragmentosRecebidos.keySet());
            Collections.sort(seqs);
            
            // Concatenar dados
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (int seq : seqs) {
                byte[] fragmento = sessaoAtual.fragmentosRecebidos.get(seq);
                baos.write(fragmento);
            }
            
            // Deserializar PayloadMissao
            byte[] dadosCompletos = baos.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(dadosCompletos);
            ObjectInputStream ois = new ObjectInputStream(bais);
            PayloadMissao payload = (PayloadMissao) ois.readObject();
            
            System.out.println("[ClienteUDP] Missão reconstruída: " + payload);
            
            // Atualizar máquina de estados
            if (maquina != null) {
                maquina.receberMissao(payload);
            }
            
            // Iniciar Fase 2: Execução e Reportagem de Progresso
            iniciarFase2Execucao(payload);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("[ClienteUDP] Erro ao reconstruir missão: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Envia mensagem RESPONSE.
     */
    private void enviarResponse(int idMissao, int seq, boolean sucesso, InetAddress endereco, int porta) {
        MensagemUDP msg = new MensagemUDP();
        msg.header.tipo = TipoMensagem.MSG_RESPONSE;
        msg.header.idEmissor = idRover;
        msg.header.idRecetor = 0; // Nave-Mãe
        msg.header.idMissao = idMissao;
        //msg.header.timestamp = new Time(System.currentTimeMillis());
        msg.header.seq = seq;
        msg.header.totalFragm = 1;
        msg.header.flagSucesso = sucesso;
        msg.payload = null;
        
        enviarMensagem(msg, endereco, porta);
        System.out.println("[ClienteUDP] RESPONSE enviado (sucesso=" + sucesso + ")");
    }
    
    /**
     * Envia mensagem ACK.
     */
    private void enviarAck(int idMissao, int seq, List<Integer> perdidos, InetAddress endereco, int porta) {
        MensagemUDP msg = new MensagemUDP();
        msg.header.tipo = TipoMensagem.MSG_ACK;
        msg.header.idEmissor = idRover;
        msg.header.idRecetor = 0; // Nave-Mãe
        msg.header.idMissao = idMissao;
        //msg.header.timestamp = new Time(System.currentTimeMillis());
        msg.header.seq = seq;
        msg.header.totalFragm = 1;
        msg.header.flagSucesso = perdidos.isEmpty();
        
        PayloadAck ack = new PayloadAck();
        ack.missingCount = perdidos.size();
        ack.missing = new int[perdidos.size()];
        for (int i = 0; i < perdidos.size(); i++) {
            ack.missing[i] = perdidos.get(i);
        }
        msg.payload = ack;
        
        enviarMensagem(msg, endereco, porta);
        System.out.println("[ClienteUDP] ACK enviado (faltam " + perdidos.size() + " fragmentos)");
    }
    
    /**
     * Envia mensagem UDP.
     */
    private void enviarMensagem(MensagemUDP msg, InetAddress endereco, int porta) {
        try {
            byte[] dados = serializarObjeto(msg);
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, endereco, porta);
            socket.send(pacote);
        } catch (IOException e) {
            System.err.println("[ClienteUDP] Erro ao enviar mensagem: " + e.getMessage());
        }
    }
    
    /**
     * Serializa objeto para bytes.
     */
    private byte[] serializarObjeto(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.flush();
        return baos.toByteArray();
    }
    
    /**
     * Deserializa bytes para MensagemUDP.
     */
    private MensagemUDP deserializarMensagem(byte[] dados, int length) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(dados, 0, length);
            ObjectInputStream ois = new ObjectInputStream(bais);
            return (MensagemUDP) ois.readObject();
        } catch (Exception e) {
            System.err.println("[ClienteUDP] Erro ao deserializar: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Extrai dados de um payload de fragmento.
     */
    private byte[] extrairDadosFragmento(PayloadUDP payload) throws Exception {
        // Tentar acessar campo 'dados' via reflexão
        java.lang.reflect.Field field = payload.getClass().getDeclaredField("dados");
        field.setAccessible(true);
        return (byte[]) field.get(payload);
    }
    
    /**
     * Inicia a Fase 2: Execução da missão.
     */
    private void iniciarFase2Execucao(PayloadMissao payload) {
        // Atualizar sessão para Fase 2
        sessaoAtual.emExecucao = true;
        sessaoAtual.seqAtual = 1; // Começa em 1 para PROGRESS
        sessaoAtual.intervaloAtualizacao = payload.intervaloAtualizacao.get(Calendar.MINUTE) * 60 * 1000; // converter minutos para ms
        sessaoAtual.duracaoMissao = payload.duracaoMissao.get(Calendar.MINUTE) * 60 * 1000; // converter minutos para ms
        sessaoAtual.inicioMissao = System.currentTimeMillis();
        
        // Limpar dados da Fase 1 (não mais necessários)
        sessaoAtual.fragmentosRecebidos = null;
        
        // Iniciar thread de envio de progresso
        threadProgresso = new Thread(this::enviarProgressoPeriodicamente);
        threadProgresso.setDaemon(true);
        threadProgresso.start();
        
        System.out.println("[ClienteUDP] Iniciada Fase 2 - Execução da missão " + payload.idMissao);
    }
    
    /**
     * Thread que envia mensagens PROGRESS periodicamente.
     */
    private void enviarProgressoPeriodicamente() {
        while (running && sessaoAtual != null && sessaoAtual.emExecucao) {
            try {
                Thread.sleep(sessaoAtual.intervaloAtualizacao);
                
                if (sessaoAtual == null || !sessaoAtual.emExecucao) break;
                
                // Calcular progresso
                long tempoDecorrido = System.currentTimeMillis() - sessaoAtual.inicioMissao;
                float progressoPerc = Math.min(100.0f, (tempoDecorrido * 100.0f) / sessaoAtual.duracaoMissao);
                
                // Enviar PROGRESS
                if (progressoPerc < 100.0f) {
                    enviarProgress(progressoPerc, tempoDecorrido);
                } else {
                    // Missão concluída
                    enviarCompleted(true);
                    break;
                }
                
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    /**
     * Envia mensagem PROGRESS para a Nave-Mãe.
     */
    private void enviarProgress(float progressoPerc, long tempoDecorrido) {
        if (sessaoAtual == null || !sessaoAtual.emExecucao) return;
        
        MensagemUDP msg = new MensagemUDP();
        msg.header.tipo = TipoMensagem.MSG_PROGRESS;
        msg.header.idEmissor = idRover;
        msg.header.idRecetor = 0; // Nave-Mãe
        msg.header.idMissao = sessaoAtual.idMissao;
        msg.header.seq = sessaoAtual.seqAtual++;
        msg.header.totalFragm = 1;
        msg.header.flagSucesso = true;
        
        PayloadProgresso progresso = new PayloadProgresso();
        progresso.idMissao = sessaoAtual.idMissao;
        progresso.tempoDecorrido = Calendar.getInstance();
        progresso.tempoDecorrido.setTimeInMillis(tempoDecorrido);
        progresso.progressoPercentagem = progressoPerc;
        msg.payload = progresso;
        
        // Tentar enviar com retransmissão
        sessaoAtual.aguardandoAck = true;
        int tentativas = 0;
        while (tentativas < 3 && sessaoAtual.aguardandoAck) {
            enviarMensagem(msg, sessaoAtual.enderecoNave, sessaoAtual.portaNave);
            System.out.println("[ClienteUDP] PROGRESS enviado (seq=" + msg.header.seq + 
                             ", progresso=" + String.format("%.2f", progressoPerc) + "%%)");
            
            // Aguardar ACK com timeout
            long inicio = System.currentTimeMillis();
            while (System.currentTimeMillis() - inicio < TIMEOUT_MS && sessaoAtual.aguardandoAck) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }
            
            if (sessaoAtual.aguardandoAck) {
                System.out.println("[ClienteUDP] Timeout aguardando ACK - Retransmitindo PROGRESS (tentativa " + (tentativas + 1) + ")");
                tentativas++;
            } else {
                break; // ACK recebido
            }
        }
    }
    
    /**
     * Envia mensagem COMPLETED para a Nave-Mãe.
     */
    private void enviarCompleted(boolean sucesso) {
        if (sessaoAtual == null || !sessaoAtual.emExecucao) return;
        
        MensagemUDP msg = new MensagemUDP();
        msg.header.tipo = TipoMensagem.MSG_COMPLETED;
        msg.header.idEmissor = idRover;
        msg.header.idRecetor = 0; // Nave-Mãe
        msg.header.idMissao = sessaoAtual.idMissao;
        msg.header.seq = sessaoAtual.seqAtual++;
        msg.header.totalFragm = 1;
        msg.header.flagSucesso = sucesso;
        msg.payload = null; // COMPLETED não precisa payload
        
        // Tentar enviar com retransmissão
        sessaoAtual.aguardandoAck = true;
        int tentativas = 0;
        while (tentativas < 3 && sessaoAtual.aguardandoAck) {
            enviarMensagem(msg, sessaoAtual.enderecoNave, sessaoAtual.portaNave);
            System.out.println("[ClienteUDP] COMPLETED enviado (seq=" + msg.header.seq + 
                             ", sucesso=" + sucesso + ")");
            
            // Aguardar ACK com timeout
            long inicio = System.currentTimeMillis();
            while (System.currentTimeMillis() - inicio < TIMEOUT_MS && sessaoAtual.aguardandoAck) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }
            
            if (sessaoAtual.aguardandoAck) {
                System.out.println("[ClienteUDP] Timeout aguardando ACK - Retransmitindo COMPLETED (tentativa " + (tentativas + 1) + ")");
                tentativas++;
            } else {
                break; // ACK recebido
            }
        }
        
        // Finalizar sessão completa
        System.out.println("[ClienteUDP] Missão " + sessaoAtual.idMissao + " concluída");
        sessaoAtual = null;
        
        // Atualizar máquina de estados
        if (maquina != null) {
            // TODO: notificar máquina de estados que missão foi concluída
        }
    }
    
    public void parar() {
        running = false;
        if (threadProgresso != null) {
            threadProgresso.interrupt();
        }
    }
    
    /**
     * Classe que representa uma sessão completa de missão no cliente.
     * Gerencia tanto a Fase 1 (recepção) quanto a Fase 2 (execução).
     */
    private static class SessaoClienteMissionLink {
        // Comum a todas as fases
        int idMissao;
        InetAddress enderecoNave;
        int portaNave;
        
        // Fase 1: Recepção de missão
        int totalFragmentos;
        Map<Integer, byte[]> fragmentosRecebidos;
        
        // Fase 2: Execução de missão
        boolean emExecucao = false;
        int seqAtual;
        long intervaloAtualizacao; // em ms
        long duracaoMissao; // em ms
        long inicioMissao; // timestamp de início
        boolean aguardandoAck = false;
    }
}

