package rover;

import lib.SessaoClienteMissionLink;
import lib.TipoMensagem;
import lib.mensagens.MensagemUDP;
import lib.mensagens.payloads.*;
import lib.Rover.EstadoRover;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List; 
import java.util.Arrays;



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
    
    // Controle da sessão completa da missão 
    private SessaoClienteMissionLink sessaoAtual = null;
    
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
        MensagemUDP msg = deserializarMensagem(pacote.getData(), pacote.getLength());

        if (msg == null || msg.header == null) {
            return;
        }

        if (msg.header.idRecetor != idRover) {
            return;
        }

        InetAddress endereco = pacote.getAddress();
        int porta = pacote.getPort();

        switch (msg.header.tipo) {
            case MSG_HELLO:
                processarHello(msg, endereco, porta);
                break;
            case MSG_MISSION:
                processarMission(msg);
                break;
            case MSG_ACK:
                processarAck(msg);
                break;
            default:
                System.out.println("[ClienteUDP] Mensagem inesperada: " + msg.header.tipo);
        }
    }
    
    /**
     * Processa mensagem HELLO.
     * Verifica se está disponível e responde.
     */
    private void processarHello(MensagemUDP msg, InetAddress endereco, int porta) {
        System.out.println("[ClienteUDP] HELLO recebido - Missão ID: " + msg.header.idMissao);
        
        boolean disponivel = (maquina != null && maquina.getEstadoAtual() == EstadoRover.ESTADO_DISPONIVEL);
        
        if (disponivel){
            // Criar nova sessão de missão
            sessaoAtual = new SessaoClienteMissionLink(msg.header.idMissao, endereco, porta);
            
            System.out.println("[ClienteUDP] Rover disponível - Aguardando fragmentos MISSION");
        } else {
            System.out.println("[ClienteUDP] Rover não disponível para missão");
        }
        sessaoAtual.seqAtual = msg.header.seq;
        // Enviar RESPONSE
        enviarResponse(disponivel);
    }
    
    /**
     * Processa fragmento MISSION.
     */
    private void processarMission(MensagemUDP msg) {
        if (sessaoAtual == null || sessaoAtual.idMissao != msg.header.idMissao) {
            System.err.println("[ClienteUDP] Fragmento recebido sem sessão ativa");
            return;
        }
        
        int seq = msg.header.seq;
        
        // Atualizar total de fragmentos se necessário (primeira vez que recebemos um MISSION)
        if (sessaoAtual.totalFragmentos == 0 && msg.header.totalFragm > 1) {
            sessaoAtual.totalFragmentos = msg.header.totalFragm;
            System.out.println("[ClienteUDP] Total de fragmentos atualizado: " + sessaoAtual.totalFragmentos);
        }
        
        System.out.println("[ClienteUDP] Fragmento recebido: seq=" + seq + "/" + (msg.header.totalFragm + 1));
        
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
            sessaoAtual.seqAtual = seq;
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
            sessaoAtual.fragmentosPerdidos = identificarFragmentosPerdidos();
            
            if (sessaoAtual.fragmentosPerdidos.isEmpty()) {
                // Todos recebidos - reconstruir missão
                if (reconstruirMissao()) {
                    System.out.println("[ClienteUDP] Missão recebida com sucesso!");
                    enviarAck();
                    // Iniciar a reportagem em thread separada para não bloquear a receção
                    Thread t = new Thread(this::reportarMissao);
                    t.setDaemon(true);
                    t.start();
                } else {
                    System.err.println("[ClienteUDP] Erro ao reconstruir missão");
                }
            } else {
                // Enviar ACK com fragmentos perdidos
                System.out.println("[ClienteUDP] Solicitando retransmissão de " + sessaoAtual.fragmentosPerdidos.size() + " fragmentos");
                enviarAck();
            }
        }
    }
    
    /**
     * Processa mensagem ACK (para PROGRESS e COMPLETED).
     */
    private void processarAck(MensagemUDP msg) {
        if (sessaoAtual != null && sessaoAtual.emExecucao && msg.header.idMissao == sessaoAtual.idMissao) {
            System.out.println("[ClienteUDP] ACK recebido para seq=" + msg.header.seq + 
                             " (sucesso=" + msg.header.flagSucesso + ")");
            sessaoAtual.aguardandoAck = false;

            // Se o ACK veio com progresso perdido, reenviar os PROGRESS
            if (msg.payload instanceof PayloadAck) {
                PayloadAck ack = (PayloadAck) msg.payload;
                if (ack.missing != null && ack.missing.length > 0) {
                    System.out.println("[ClienteUDP] Reenviando PROGRESS perdido: " + Arrays.toString(ack.missing));
                    for (int seq : ack.missing) {
                        // Reenviar PROGRESS correspondente ao seq
                        reenviarProgress(seq);
                    }
                }
            }
        }
    }

    /**
     * Reenvia PROGRESS para o seq especificado, usando os dados mais recentes.
     * (No modelo atual, reenvia o progresso atual, pois não armazena histórico)
     */
    private void reenviarProgress(int seq) {
        if (sessaoAtual == null || !sessaoAtual.emExecucao) return;

        PayloadProgresso progresso = sessaoAtual.progressosEnviados.get(seq);
        if (progresso == null) {
            System.out.println("[ClienteUDP] Não há progresso salvo para seq=" + seq + ", ignorando reenvio.");
            return;
        }

        MensagemUDP msg = new MensagemUDP();
        msg.header.tipo = TipoMensagem.MSG_PROGRESS;
        msg.header.idEmissor = idRover;
        msg.header.idRecetor = 0; // Nave-Mãe
        msg.header.idMissao = sessaoAtual.idMissao;
        msg.header.seq = seq;
        msg.header.totalFragm = 1;
        msg.header.flagSucesso = true;
        msg.payload = progresso;

        enviarMensagem(msg, sessaoAtual.enderecoNave, sessaoAtual.portaNave);
        System.out.println("[ClienteUDP] PROGRESS reenviado (seq=" + seq + ")");
    }
    
    /**
     * Identifica quais fragmentos ainda não foram recebidos.
     */
    private List<Integer> identificarFragmentosPerdidos() {
        List<Integer> perdidos = new ArrayList<>();
        
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

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (int seq : seqs) {
                byte[] fragmento = sessaoAtual.fragmentosRecebidos.get(seq);
                baos.write(fragmento);
            }

            byte[] dadosCompletos = baos.toByteArray();

            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(dadosCompletos));

            //TODO: depois de ler payload, passar a missao para máquina de estados
            PayloadMissao payload = new PayloadMissao();

            // int idMissao
            payload.idMissao = dis.readInt();

            // float x1,y1,x2,y2
            payload.x1 = dis.readFloat();
            payload.y1 = dis.readFloat();
            payload.x2 = dis.readFloat();
            payload.y2 = dis.readFloat();

            // String tarefa
            int tarefaLen = dis.readInt();
            if (tarefaLen > 0) {
                byte[] tb = new byte[tarefaLen];
                dis.readFully(tb);
                payload.tarefa = new String(tb, java.nio.charset.StandardCharsets.UTF_8);
            } else {
                payload.tarefa = "";
            }

            // tempos em segundos
            long durSeg = dis.readLong();
            long intSeg = dis.readLong();
            long iniSeg = dis.readLong();

            payload.duracaoMissao = durSeg;
            payload.intervaloAtualizacao = intSeg;
            payload.inicioMissao = iniSeg;

            // prioridade
            payload.prioridade = dis.readInt();

            // intervalos já vêm em segundos, converter para ms (com mínimo de 200ms)
            sessaoAtual.intervaloAtualizacao = Math.max(200, (int) (payload.intervaloAtualizacao * 1000));
            sessaoAtual.duracaoMissao = payload.duracaoMissao * 1000;



            System.out.println("[ClienteUDP] Missão reconstruída: " + payload);

            // Atualizar máquina de estados
            if (maquina != null) {
                maquina.receberMissao(payload);
            }

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
    private void enviarResponse(boolean sucesso) {
        MensagemUDP msg = new MensagemUDP();
        msg.header.tipo = TipoMensagem.MSG_RESPONSE;
        msg.header.idEmissor = idRover;
        msg.header.idRecetor = 0; // Nave-Mãe
        msg.header.idMissao = sessaoAtual.idMissao;
        //msg.header.timestamp = new Time(System.currentTimeMillis());
        msg.header.seq = sessaoAtual.seqAtual;
        msg.header.totalFragm = 1;
        msg.header.flagSucesso = sucesso;
        msg.payload = null;
        
        enviarMensagem(msg, sessaoAtual.enderecoNave, sessaoAtual.portaNave);
        System.out.println("[ClienteUDP] RESPONSE enviado (sucesso=" + sucesso + ")");
    }
    
    /**
     * Envia mensagem ACK.
     */
    private void enviarAck() {
        MensagemUDP msg = new MensagemUDP();
        msg.header.tipo = TipoMensagem.MSG_ACK;
        msg.header.idEmissor = idRover;
        msg.header.idRecetor = 0; // Nave-Mãe
        msg.header.idMissao = sessaoAtual.idMissao;
        //msg.header.timestamp = new Time(System.currentTimeMillis());
        msg.header.seq = sessaoAtual.seqAtual;
        msg.header.totalFragm = 1;
        msg.header.flagSucesso = sessaoAtual.fragmentosPerdidos.isEmpty();
        
        PayloadAck ack = new PayloadAck();
        ack.missingCount = sessaoAtual.fragmentosPerdidos.size();
        ack.missing = new int[sessaoAtual.fragmentosPerdidos.size()];
        for (int i = 0; i < sessaoAtual.fragmentosPerdidos.size(); i++) {
            ack.missing[i] = sessaoAtual.fragmentosPerdidos.get(i);
        }
        msg.payload = ack;
        
        enviarMensagem(msg, sessaoAtual.enderecoNave, sessaoAtual.portaNave);
        System.out.println("[ClienteUDP] ACK enviado (faltam " + sessaoAtual.fragmentosPerdidos.size() + " fragmentos)");
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
     * Inicia a reportagem da missão
     */
    private void reportarMissao() {
        // Atualizar sessão 
        if (sessaoAtual == null) return;
        int missionId = sessaoAtual.idMissao;
        sessaoAtual.emExecucao = true;
        sessaoAtual.inicioMissao = System.currentTimeMillis();
        
        // Limpar dados (não mais necessários)
        sessaoAtual.fragmentosRecebidos = null;
        
        // Iniciar envio de progresso
        System.out.println("[ClienteUDP] Iniciada a execução da missão " + missionId);
        
        while (running && sessaoAtual != null && sessaoAtual.emExecucao) {
            try {
                Thread.sleep(sessaoAtual.intervaloAtualizacao);
                
                if (sessaoAtual == null || !sessaoAtual.emExecucao) break;
                
                // Calcular progresso
                // Atualizar lógica da missão antes de reportar
                if (maquina != null) {
                    maquina.atualizar();
                }
                long tempoDecorrido = System.currentTimeMillis() - sessaoAtual.inicioMissao;
                float progressoPerc = maquina.getContexto().getProgresso();
                
                // Enviar PROGRESS
                if (progressoPerc < 100.0f) {
                    enviarProgress(progressoPerc, tempoDecorrido);
                } else {
                    // Missão concluída
                    enviarCompleted(true);
                    return;
                }
                
            } catch (InterruptedException e) {
                break;
            }
        }
        
        System.out.println("[ClienteUDP] Execução da missão " + missionId + " terminada");
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
        msg.header.seq = ++sessaoAtual.seqAtual;
        msg.header.totalFragm = 1;
        msg.header.flagSucesso = true;
        
        PayloadProgresso progresso = new PayloadProgresso();
        progresso.idMissao = sessaoAtual.idMissao;
        // converter ms para segundos
        progresso.tempoDecorrido = tempoDecorrido / 1000;
        progresso.progressoPercentagem = progressoPerc;
        msg.payload = progresso;

        // Registrar progresso enviado
        sessaoAtual.progressosEnviados.put(msg.header.seq, progresso);
        
        // Tentar enviar com retransmissão
        sessaoAtual.aguardandoAck = true;
        int tentativas = 0;
        while (tentativas < 3 && sessaoAtual.aguardandoAck) {
            enviarMensagem(msg, sessaoAtual.enderecoNave, sessaoAtual.portaNave);
            System.out.println("[ClienteUDP] PROGRESS enviado (seq=" + msg.header.seq + 
                             ", progresso=" + String.format("%.2f", progressoPerc) + "%%)");
            
            // Aguardar ACK (processamento acontece no laço principal de receção)
            aguardarAck(TIMEOUT_MS);
            
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
        msg.header.seq = ++sessaoAtual.seqAtual;
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
            
            // Aguardar ACK (processamento acontece no laço principal de receção)
            aguardarAck(TIMEOUT_MS);
            
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
            maquina.getContexto().transicionarEstado(EstadoRover.ESTADO_CONCLUIDO);
            maquina.getContexto().eventoPendente = EventoRelevante.EVENTO_FIM_MISSAO;
        }
    }

    /**
     * Aguarda pela limpeza do sinal de aguardandoAck até timeoutMs.
     * O processamento do ACK ocorre no laço principal de receção.
     */
    private void aguardarAck(long timeoutMs) {
        long inicio = System.currentTimeMillis();
        while (System.currentTimeMillis() - inicio < timeoutMs && sessaoAtual != null && sessaoAtual.aguardandoAck && running) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
    
    public void parar() {
        running = false;
    }
    
}