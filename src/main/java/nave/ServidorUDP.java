package nave;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lib.mensagens.*;
import lib.mensagens.payloads.*;
import lib.TipoMensagem;
import lib.*;
import lib.Rover.EstadoRover;

/**
 * Servidor UDP da Nave-Mãe (MissionLink).
 * Implementa o protocolo de comunicação fiável para envio de missões.
 * 
 * Fluxo:
 * 1. HELLO → Inicia contato com rover (mission_id)
 * 2. Aguarda RESPONSE do rover
 * 3. MISSION → Envia fragmentos da missão
 * 4. Aguarda ACK → Retransmite fragmentos perdidos se necessário
 */


public class ServidorUDP implements Runnable {
    
    private static final int PORTA_UDP = 9001;
    private static final int PORTA_BASE_ROVER = 9010;
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_RETRIES = 3;
    private static final int TAMANHO_FRAGMENTO = 512; // bytes por fragmento
    
    private DatagramSocket socket;
    private GestaoEstado estado;
    private boolean running = true;
    
    // Controlo de sessões ativas (idRover -> sessão)
    private ConcurrentHashMap<Integer, SessaoServidorMissionLink> sessoesAtivas;


    
    public ServidorUDP(GestaoEstado estado) {
        this.estado = estado;
        this.sessoesAtivas = new ConcurrentHashMap<>();
    }
    
    @Override
    public void run() {
        try {
            socket = new DatagramSocket(PORTA_UDP);
            socket.setSoTimeout(100); // timeout curto para verificar running
            System.out.println("[ServidorUDP] Iniciado na porta " + PORTA_UDP);
            
            // Thread para iniciar novas missões
            Thread iniciadorMissoes = new Thread(this::iniciadorMissoes);
            iniciadorMissoes.setDaemon(true);
            iniciadorMissoes.start();
            
            // Loop principal - recebe respostas dos rovers
            while (running) {
                try {
                    byte[] buffer = new byte[4096];
                    DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                    socket.receive(pacote);
                    
                    // Processar mensagem recebida
                    processarMensagemRecebida(pacote);
                    
                } catch (SocketTimeoutException e) {
                    // Normal, continuar
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[ServidorUDP] Erro ao receber: " + e.getMessage());
                    }
                }
            }
            
        } catch (SocketException e) {
            System.err.println("[ServidorUDP] Erro ao criar socket: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("[ServidorUDP] Encerrado");
        }
    }
    
    /**
     * Thread que verifica periodicamente se há missões PENDENTES
     * para atribuir a rovers "disponivel".
     */
    private void iniciadorMissoes() {
        while (running) {
            try {
                Thread.sleep(2000); // Verifica a cada 2 segundos
                
                // Procurar missão pendente
                Missao missao = estado.obterMissaoNaoAtribuida();
                if (missao == null){
                    System.out.println("[ServidorUDP] Nenhuma missão pendente encontrada.");
                    continue;
                }
                
                System.out.println("[ServidorUDP] Missão pendente encontrada: " + missao);

                // Procurar rover disponível
                Rover roverDisponivel = estado.obterRoverDisponivel();
                if (roverDisponivel == null) {
                    System.out.println("[ServidorUDP] Nenhum rover disponível no momento.");
                    continue;
                } else {
                    System.out.println("[ServidorUDP] Atribuindo missão " + missao.idMissao + " ao rover " + roverDisponivel.idRover);
                    iniciarEnvioMissao(roverDisponivel, missao);
                }
                
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    /**
     * Inicia o processo de envio de missão para um rover.
     */
    private void iniciarEnvioMissao(Rover rover, Missao missao) {

        // Verificar se já existe sessão ativa para este rover
        if (sessoesAtivas.containsKey(rover.idRover)) {
            System.out.println("[ServidorUDP] Rover " + rover.idRover + " já tem sessão ativa! Possivel problema no ML.");
            return;
        }
        
        // Criar nova sessão
        SessaoServidorMissionLink sessao = new SessaoServidorMissionLink(rover, missao);
        sessoesAtivas.put(rover.idRover, sessao);
        
        // Enviar mensagens em thread separada
        Thread t = new Thread(() -> executarSessaoMissao(sessao));
        t.setDaemon(true);
        t.start();
    }
    
    /**
     * Executa o fluxo completo de envio de missão.
     */
    private void executarSessaoMissao(SessaoServidorMissionLink sessao) {
        try {
            // Passo 1: Enviar HELLO
            if (!enviarHello(sessao)) {
                System.err.println("[ServidorUDP] Falha ao enviar HELLO para rover " + sessao.rover.idRover);
                finalizarSessao(sessao, false);
                return;
            }
            
            // Passo 2: Aguardar RESPONSE (processado em processarMensagemRecebida)
            if (!aguardarResponse(sessao)) {
                System.err.println("[ServidorUDP] Timeout aguardando RESPONSE do rover " + sessao.rover.idRover);
                //todo: implementar retry ou tratamento de erro
                finalizarSessao(sessao, false);
                return;
            }
            
            // Passo 3: Enviar fragmentos MISSION
            if (!enviarFragmentosMissao(sessao)) {
                System.err.println("[ServidorUDP] Falha ao enviar fragmentos para rover " + sessao.rover.idRover);
                finalizarSessao(sessao, false);
                return;
            }
            
            // Passo 4: Aguardar ACK e retransmitir se necessário
            if (!aguardarAckCompleto(sessao)) {
                System.err.println("[ServidorUDP] Falha na confirmação da missão para rover " + sessao.rover.idRover);
                finalizarSessao(sessao, false);
                return;
            }

            //TODO: continuar aqui o resto em vez de ser so no processar mensagens, para que nao finalize a sessao antes do COMPLETED

            //Passo 5: Aguarda PROGRESS (processado em processarMensagemRecebida)

            //Passo 6: Aguarda COMPLETED (processado em processarMensagemRecebida)
            
            // Sucesso!
            System.out.println("[ServidorUDP] Missão " + sessao.missao.idMissao + 
                             " enviada com sucesso para rover " + sessao.rover.idRover);
            finalizarSessao(sessao, true);
            
        } catch (Exception e) {
            System.err.println("[ServidorUDP] Erro na sessão: " + e.getMessage());
            finalizarSessao(sessao, false);
        }
    }
    
    /**
     * Envia mensagem HELLO para o rover.
     */
    private boolean enviarHello(SessaoServidorMissionLink sessao) {
        MensagemUDP msg = new MensagemUDP();
        msg.header.tipo = TipoMensagem.MSG_HELLO;
        msg.header.idEmissor = 0; // Nave-Mãe
        msg.header.idRecetor = sessao.rover.idRover;
        msg.header.idMissao = sessao.missao.idMissao;
        //msg.header.timestamp = new Time(System.currentTimeMillis());
        msg.header.seq = 1;
        msg.header.totalFragm = 1;
        msg.header.flagSucesso = false;
        msg.payload = null; // HELLO não tem payload
        
        return enviarMensagem(msg, sessao.rover);
    }
    
    /**
     * Aguarda RESPONSE do rover.
     */
    private boolean aguardarResponse(SessaoServidorMissionLink sessao) {
        long inicio = System.currentTimeMillis();
        while (System.currentTimeMillis() - inicio < TIMEOUT_MS) {
            if (sessao.responseRecebido) {
                return sessao.responseSucesso;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return false;
    }
    
    /**
     * Fragmenta e envia todos os fragmentos da missão.
     * TODO: ver maneiras melhores de fazer a fragmentação por campos
     */
    private boolean enviarFragmentosMissao(SessaoServidorMissionLink sessao) {
        try {
            // 1) Obter payload da missão
            PayloadMissao payload = sessao.missao.toPayload();

            // 2) Serializar campo a campo 
            List<byte[]> blocos = payload.serializarPorCampos();

            // 3) Empacotar blocos em fragmentos de até TAMANHO_FRAGMENTO sem dividir campos
            List<byte[]> frags = new ArrayList<>();
            ByteArrayOutputStream atual = new ByteArrayOutputStream();
            for (byte[] bloco : blocos) {
                if (bloco.length > TAMANHO_FRAGMENTO) {
                    // Campo maior que o tamanho de fragmento — enviar sozinho (nota académica)
                    if (atual.size() > 0) {
                        frags.add(atual.toByteArray());
                        atual.reset();
                    }
                    frags.add(bloco);
                    continue;
                }

                if (atual.size() + bloco.length > TAMANHO_FRAGMENTO) {
                    frags.add(atual.toByteArray());
                    atual.reset();
                }
                atual.write(bloco);
            }
            if (atual.size() > 0) {
                frags.add(atual.toByteArray());
            }

            // 4) Guardar na sessão
            int totalFragmentos = frags.size();
            sessao.totalFragmentos = totalFragmentos;
            sessao.fragmentos = new byte[totalFragmentos][];
            for (int i = 0; i < totalFragmentos; i++) sessao.fragmentos[i] = frags.get(i);

            System.out.println("[ServidorUDP] Enviando missão " + sessao.missao.idMissao +
                    " em " + totalFragmentos + " fragmentos (empacotado por campos)");

            // 5) Enviar todos os fragmentos
            for (int i = 0; i < totalFragmentos; i++) {
                if (!enviarFragmento(sessao, i + 2)) { // seq começa em 2
                    return false;
                }
                Thread.sleep(10); // Pequeno delay entre fragmentos
            }

            return true;
            
        } catch (Exception e) {
            System.err.println("[ServidorUDP] Erro ao enviar fragmentos: " + e.getMessage());
            return false;
        }
    }

    
    
    /**
     * Envia um fragmento específico.
     */
    private boolean enviarFragmento(SessaoServidorMissionLink sessao, int seq) {
        int indice = seq - 2; // seq começa em 2, indice em 0
        if (indice < 0 || indice >= sessao.fragmentos.length) {
            return false;
        }
        
        MensagemUDP msg = new MensagemUDP();
        msg.header.tipo = TipoMensagem.MSG_MISSION;
        msg.header.idEmissor = 0;
        msg.header.idRecetor = sessao.rover.idRover;
        msg.header.idMissao = sessao.missao.idMissao;
        //msg.header.timestamp = new Time(System.currentTimeMillis());
        msg.header.seq = seq;
        msg.header.totalFragm = sessao.totalFragmentos + 1; // +1 por causa do HELLO (seq=1)
        msg.header.flagSucesso = false;
        
        // Payload é o fragmento raw (será tratado especialmente na serialização)
        FragmentoPayload frag = new FragmentoPayload();
        frag.dados = sessao.fragmentos[indice];
        msg.payload = frag;
        
        return enviarMensagem(msg, sessao.rover);
    }
    
    /**
     * Aguarda ACK completo, retransmitindo fragmentos perdidos se necessário.
     */
    private boolean aguardarAckCompleto(SessaoServidorMissionLink sessao) {
        int tentativas = 0;
        
        while (tentativas < MAX_RETRIES) {
            long inicio = System.currentTimeMillis();
            
            while (System.currentTimeMillis() - inicio < TIMEOUT_MS) { //TODO: REVER se aqui não temos que usar outro timeout
                if (sessao.ackRecebido) {
                    if (sessao.fragmentosPerdidos.isEmpty()) {
                        // ACK completo!
                        return true;
                    } else {
                        // Retransmitir fragmentos perdidos
                        System.out.println("[ServidorUDP] Retransmitindo " + 
                                         sessao.fragmentosPerdidos.size() + " fragmentos perdidos");
                        
                        for (int seq : sessao.fragmentosPerdidos) {
                            enviarFragmento(sessao, seq);
                        }
                        
                        // Reset para aguardar novo ACK
                        sessao.ackRecebido = false;
                        sessao.fragmentosPerdidos.clear();
                        tentativas++;
                        break;
                    }
                }
                
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return false;
                }
            }
            
            if (!sessao.ackRecebido) {
                tentativas++;
            }
        }
        
        return false;
    }
    
    /**
     * Processa mensagem recebida do rover (RESPONSE ou ACK).
     */
    private void processarMensagemRecebida(DatagramPacket pacote) {
        try {
            MensagemUDP msg = deserializarMensagem(pacote.getData(), pacote.getLength());
            
            if (msg == null || msg.header == null) {
                return;
            }
            
            int idRover = msg.header.idEmissor;
            SessaoServidorMissionLink sessao = sessoesAtivas.get(idRover);
            
            if (sessao == null) {
                // Mensagem sem sessão ativa
                return;
            }
            
            switch (msg.header.tipo) {
                case MSG_RESPONSE:
                    sessao.responseRecebido = true;
                    sessao.responseSucesso = msg.header.flagSucesso;
                    System.out.println("[ServidorUDP] RESPONSE recebido do rover " + idRover + 
                                     " (sucesso=" + msg.header.flagSucesso + ")");
                    break;
                    
                case MSG_ACK:
                    sessao.ackRecebido = true;
                    if (msg.payload instanceof PayloadAck) {
                        PayloadAck ack = (PayloadAck) msg.payload;
                        sessao.fragmentosPerdidos.clear();
                        if (ack.missing != null && ack.missing.length > 0) {
                            for (int seq : ack.missing) {
                                sessao.fragmentosPerdidos.add(seq);
                            }
                        }
                        System.out.println("[ServidorUDP] ACK recebido do rover " + idRover + 
                                         " (faltam " + ack.missing.length + " fragmentos)");
                    }
                    break;
                    
                case MSG_PROGRESS:
                    processarProgress(msg, idRover, pacote);
                    break;
                    
                case MSG_COMPLETED:
                    processarCompleted(msg, idRover, pacote);
                    break;
                    
                default:
                    System.out.println("[ServidorUDP] Mensagem inesperada: " + msg.header.tipo);
            }
            
        } catch (Exception e) {
            System.err.println("[ServidorUDP] Erro ao processar mensagem: " + e.getMessage());
        }
    }
    
    /**
     * Processa mensagem PROGRESS do rover.
     */
    private void processarProgress(MensagemUDP msg, int idRover, DatagramPacket pacote) {
        if (msg.payload instanceof PayloadProgresso) {
            PayloadProgresso progresso = (PayloadProgresso) msg.payload;
            System.out.println("[ServidorUDP] PROGRESS recebido do rover " + idRover + 
                             " (seq=" + msg.header.seq + ", missão=" + progresso.idMissao + 
                             ", progresso=" + String.format("%.2f", progresso.progressoPercentagem) + "%%)");
            
            // Atualizar estado da missão no GestaoEstado
            Rover rover = estado.obterRover(idRover);
            if (rover != null) {
                // TODO: atualizar progresso da missão no estado
                System.out.println("[ServidorUDP] Progresso da missão " + progresso.idMissao + ": " + 
                                 String.format("%.2f", progresso.progressoPercentagem) + "%%");
            }
            
            // Enviar ACK
            enviarAckParaRover(msg, pacote.getAddress(), pacote.getPort());
        }
    }
    
    /**
     * Processa mensagem COMPLETED do rover.
     */
    private void processarCompleted(MensagemUDP msg, int idRover, DatagramPacket pacote) {
        System.out.println("[ServidorUDP] COMPLETED recebido do rover " + idRover + 
                         " (seq=" + msg.header.seq + ", missão=" + msg.header.idMissao + 
                         ", sucesso=" + msg.header.flagSucesso + ")");
        
        // Atualizar estado da missão e rover
        Rover rover = estado.obterRover(idRover);
        Missao missao = estado.obterMissao(msg.header.idMissao);
        
        if (rover != null && missao != null) {
            if (msg.header.flagSucesso) {
                missao.estadoMissao = Missao.EstadoMissao.CONCLUIDA;
                System.out.println("[ServidorUDP] Missão " + msg.header.idMissao + " marcada como CONCLUÍDA");
            } else {
                missao.estadoMissao = Missao.EstadoMissao.CANCELADA; // Usar CANCELADA como estado de falha
                System.out.println("[ServidorUDP] Missão " + msg.header.idMissao + " marcada como CANCELADA (falha)");
            }
            
            rover.temMissao = false;
            rover.idMissaoAtual = -1;
            rover.estadoRover = EstadoRover.ESTADO_DISPONIVEL;
            System.out.println("[ServidorUDP] Rover " + idRover + " agora está disponível");
        }
        
        // Enviar ACK
        //TODO: VER se deixamos aqui ou se metemos no executar sessao missao
        enviarAckParaRover(msg, pacote.getAddress(), pacote.getPort());
    }
    
    /**
     * Envia ACK para o rover (usado para PROGRESS e COMPLETED).
     */
    private void enviarAckParaRover(MensagemUDP msgOriginal, InetAddress endereco, int porta) {
        MensagemUDP ack = new MensagemUDP();
        ack.header.tipo = TipoMensagem.MSG_ACK;
        ack.header.idEmissor = 0; // Nave-Mãe
        ack.header.idRecetor = msgOriginal.header.idEmissor;
        ack.header.idMissao = msgOriginal.header.idMissao;
        ack.header.seq = msgOriginal.header.seq;
        ack.header.totalFragm = 1;
        ack.header.flagSucesso = true;
        ack.payload = null;
        
        //TODO: usar a enviar mensagem para nao repetir codigo
        try {
            byte[] dados = serializarObjeto(ack);
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, endereco, porta);
            socket.send(pacote);
            System.out.println("[ServidorUDP] ACK enviado para rover " + msgOriginal.header.idEmissor + 
                             " (seq=" + ack.header.seq + ")");
        } catch (IOException e) {
            System.err.println("[ServidorUDP] Erro ao enviar ACK: " + e.getMessage());
        }
    }
    
    /**
     * Finaliza a sessão de missão.
     */
    private void finalizarSessao(SessaoServidorMissionLink sessao, boolean sucesso) {
        if (sucesso) {
            // Atualizar estado da missão e rover
            sessao.missao.estadoMissao = Missao.EstadoMissao.EM_ANDAMENTO;
            sessao.rover.temMissao = true;
            sessao.rover.idMissaoAtual = sessao.missao.idMissao;
            sessao.rover.estadoRover = EstadoRover.ESTADO_EM_MISSAO;
        }
        
        sessoesAtivas.remove(sessao.rover.idRover);
    }
    
    /**
     * Envia mensagem UDP para o rover.
     */
    private boolean enviarMensagem(MensagemUDP msg, Rover rover) {
        try {
            // Obter endereço do rover (assumindo localhost para testes)
            InetAddress endereco = InetAddress.getByName("localhost");
            int porta = PORTA_BASE_ROVER + rover.idRover; // Porta baseada no ID do rover
            
            byte[] dados = serializarObjeto(msg);
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, endereco, porta);
            socket.send(pacote);

            System.out.println("[ServidorUDP] Enviada mensagem " + msg.header.tipo + " para rover " + rover.idRover + " (seq=" + msg.header.seq + ")");
            
            return true;
            
        } catch (IOException e) {
            System.err.println("[ServidorUDP] Erro ao enviar mensagem: " + e.getMessage());
            return false;
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
            System.err.println("[ServidorUDP] Erro ao deserializar: " + e.getMessage());
            return null;
        }
    }
    
    public void parar() {
        running = false;
    }
    
    /**
     * Classe auxiliar para armazenar fragmentos de dados.
     */
    private static class FragmentoPayload extends PayloadUDP {
        private static final long serialVersionUID = 1L;
        public byte[] dados;
    }
    
   
}

