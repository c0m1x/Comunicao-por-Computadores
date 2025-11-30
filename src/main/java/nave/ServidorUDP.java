package nave;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import lib.*;
import lib.mensagens.MensagemUDP;
import lib.mensagens.payloads.*;

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
    private boolean running;
    
    // Controlo de sessões ativas (idRover -> sessão)
    private ConcurrentHashMap<Integer, SessaoServidorMissionLink> sessoesAtivas;
    
    public ServidorUDP(GestaoEstado estado) {
        this.estado = estado;
        this.sessoesAtivas = new ConcurrentHashMap<>();
        this.running = true;
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
            this.parar();
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
                    roverDisponivel.portaUdp = PORTA_BASE_ROVER + roverDisponivel.idRover; // definir porta UDP do rover (valor padrão)
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

            //Passo 5: Aguarda PROGRESS enquanto não receber COMPLETED
            if(!aguardarProgress(sessao)) {
                System.err.println("[ServidorUDP] Falha ao aguardar PROGRESS do rover " + sessao.rover.idRover);
                finalizarSessao(sessao, false);
                return;
            }

            //Passo 6: Enviar ACK final para COMPLETED 
            if(!enviarAckParaRover(sessao)) {
                System.err.println("[ServidorUDP] Falha ao enviar ACK final para COMPLETED do rover " + sessao.rover.idRover);
                finalizarSessao(sessao, false);
                return;
            }

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

        return enviarMensagemUDP(msg, sessao);
    }
    
    /**
 * Aguarda RESPONSE do rover com retry automático do HELLO.
 */
private boolean aguardarResponse(SessaoServidorMissionLink sessao) {
    int tentativas = 0;
    
    while (tentativas < MAX_RETRIES) {
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
        
        // Timeout sem resposta - retry
        tentativas++;
        if (tentativas < MAX_RETRIES) {
            if (!enviarHello(sessao)) {
                return false;
            }
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
                    // Campo maior que o tamanho de fragmento — enviar sozinho
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
        msg.header.totalFragm = sessao.totalFragmentos; 
        msg.header.flagSucesso = false;
        
        // Payload é o fragmento raw (será tratado especialmente na serialização)
        FragmentoPayload frag = new FragmentoPayload();
        frag.dados = sessao.fragmentos[indice];
        msg.payload = frag;
        
        return enviarMensagemUDP(msg, sessao);
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
                        // ACK completo! Sucesso!
                        System.out.println("[ServidorUDP] Missão " + sessao.missao.idMissao + 
                             " enviada com sucesso para rover " + sessao.rover.idRover);
                        
                        // Atualizar estado da missão e do rover
                        estado.atribuirMissaoARover(sessao.rover.idRover, sessao.missao.idMissao);
                        
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
            //aqui se calhar, depois de passar o timeout e antes de passar á proxima tentativa mandar uma mensagem a confirmar que o rover ainda está lá
            
            if (!sessao.ackRecebido) {
                tentativas++;
            }
        }
        
        return false;
    }

    /**
     * Aguarda receção de mensagens PROGRESS até receber COMPLETED ou timeout.
     * Reinicia janela de timeout sempre que chega novo progresso.
     */

     //TODO: talvez verificar se o seq é incremental, porque se nao for quer dizer que perdeu um pacote pelo meio e diz qual deles foi para pedir retransmissao
     //tratar tambem de progressos duplicados a partir de seq
    private boolean aguardarProgress(SessaoServidorMissionLink sessao) {

        long intervaloAtualizacao = TIMEOUT_MS;

        if (sessao.missao != null && sessao.missao.intervaloAtualizacao > 0){
            intervaloAtualizacao = sessao.missao.intervaloAtualizacao * 1000; // converter para ms
        }
        
        // timeout = 2x intervalo de atualização (podes ajustar este fator)
        long timeoutProgressMs = intervaloAtualizacao * 2;

        long inicioJanela = System.currentTimeMillis();
        int ultimoSeq = sessao.ultimoSeq;
        while (!sessao.completedRecebido) {
            // Se chegou novo progresso, reinicia janela
            if (sessao.ultimoSeq != ultimoSeq) {
                ultimoSeq = sessao.ultimoSeq;
                inicioJanela = System.currentTimeMillis();
            }
            // Timeout sem progresso nem completed
            if (System.currentTimeMillis() - inicioJanela > timeoutProgressMs) {
                return false; // Falhou aguardar progress/completed
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return true; // COMPLETED recebido
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
            /*  nota: nao sei se isto é preciso
            // Atualizar endpoint da sessão com base no pacote recebido
            if (sessao != null) {
                if (pacote.getAddress() != null) {
                    sessao.enderecoRover = pacote.getAddress();
                }
                if (pacote.getPort() > 0) {
                    sessao.portaRover = pacote.getPort();
                }
            }
                */

            switch (msg.header.tipo) {
                case MSG_RESPONSE:
                    sessao.responseRecebido = true;
                    sessao.responseSucesso = msg.header.flagSucesso;
                    sessao.ultimoSeq = msg.header.seq;
                    System.out.println("[ServidorUDP] RESPONSE recebido do rover " + idRover + 
                                     " (sucesso=" + msg.header.flagSucesso + ", seq=" + msg.header.seq + ")");
                    break;
                    
                case MSG_ACK:
                    sessao.ackRecebido = true;
                    sessao.ultimoSeq = msg.header.seq;
                    if (msg.payload instanceof PayloadAck) {
                        PayloadAck ack = (PayloadAck) msg.payload;
                        sessao.fragmentosPerdidos.clear();
                        if (ack.missing != null && ack.missing.length > 0) {
                            for (int seq : ack.missing) {
                                sessao.fragmentosPerdidos.add(seq);
                            }
                        }
                        System.out.println("[ServidorUDP] ACK recebido do rover " + idRover + 
                                         " (faltam " + ack.missing.length + " fragmentos" + ", seq=" + msg.header.seq + ")");
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
     * Trata duplicados, perdas e envia ACK de confirmação.
     */
    private void processarProgress(MensagemUDP msg, int idRover, DatagramPacket pacote) {
        if (msg.payload instanceof PayloadProgresso) {
            PayloadProgresso progresso = (PayloadProgresso) msg.payload;
            int seqRecebido = msg.header.seq;
            
            System.out.println("[ServidorUDP] PROGRESS recebido do rover " + idRover + 
                             " (seq=" + seqRecebido + ", missão=" + progresso.idMissao + 
                             ", progresso=" + String.format("%.2f", progresso.progressoPercentagem) + "%%)");

            SessaoServidorMissionLink sessao = sessoesAtivas.get(idRover);
            if (sessao != null) {
                // Tratamento de duplicados - SEMPRE enviar ACK para duplicados
                // (o rover pode não ter recebido o ACK anterior)
                if (seqRecebido == sessao.ultimoSeq) {
                    System.out.println("[ServidorUDP] PROGRESS duplicado (seq=" + seqRecebido + ") - Reenviando ACK");
                    sessao.progressoPerdido = new ArrayList<>(); // Limpar lista de perdidos
                    enviarAckParaRover(sessao);
                    return;
                }
                
                // Se seq é menor que o último processado, é retransmissão muito antiga - enviar ACK
                if (seqRecebido < sessao.ultimoSeq) {
                    System.out.println("[ServidorUDP] PROGRESS antigo (seq=" + seqRecebido + 
                                     ", último=" + sessao.ultimoSeq + ") - Enviando ACK");
                    // Usar o seq recebido para o ACK
                    int seqOriginal = sessao.ultimoSeq;
                    sessao.ultimoSeq = seqRecebido;
                    sessao.progressoPerdido = new ArrayList<>();
                    enviarAckParaRover(sessao);
                    sessao.ultimoSeq = seqOriginal; // Restaurar
                    return;
                }

                // Verificar perdas: seq não é o próximo esperado
                int seqEsperado = sessao.ultimoSeq + 1;
                sessao.progressoPerdido = new ArrayList<>();
                
                if (seqRecebido > seqEsperado) {
                    // Houve salto de sequência - alguns PROGRESS foram perdidos
                    for (int s = seqEsperado; s < seqRecebido; s++) {
                        sessao.progressoPerdido.add(s);
                    }
                    System.out.println("[ServidorUDP] PROGRESS perdido detectado: seqs " + sessao.progressoPerdido);
                }

                // Atualizar estado da missão no GestaoEstado
                estado.atualizarProgresso(progresso);

                // Atualizar sessão
                sessao.recebendoProgresso = true;
                sessao.ultimoSeq = seqRecebido;
                
                // Enviar ACK, informando progresso perdido se houver
                enviarAckParaRover(sessao);
            }
        }
}
    
    /**
     * Processa mensagem COMPLETED do rover.
     */
    private void processarCompleted(MensagemUDP msg, int idRover, DatagramPacket pacote) {
        System.out.println("[ServidorUDP] COMPLETED recebido do rover " + idRover + 
                         " (seq=" + msg.header.seq + ", missão=" + msg.header.idMissao + 
                         ", sucesso=" + msg.header.flagSucesso + ")");
        // Atualizar estado via GestaoEstado
        estado.concluirMissao(idRover, msg.header.idMissao, msg.header.flagSucesso);
        
        // Marcar na sessão
        SessaoServidorMissionLink sessao = sessoesAtivas.get(idRover);
        if (sessao != null) {
            sessao.completedRecebido = true;
            sessao.completedSucesso = msg.header.flagSucesso;
            sessao.ultimoSeq = msg.header.seq;
        }
    }
    
    /**
     * Envia ACK para o rover (usado para PROGRESS e COMPLETED).
     */
    private boolean enviarAckParaRover(SessaoServidorMissionLink sessao) {
        MensagemUDP ack = new MensagemUDP();
        ack.header.tipo = TipoMensagem.MSG_ACK;
        ack.header.idEmissor = 0; // Nave-Mãe
        ack.header.idRecetor = sessao.rover.idRover;
        ack.header.idMissao = sessao.missao.idMissao;
        ack.header.seq = sessao.ultimoSeq;
        ack.header.totalFragm = 1;
        // Se houver progresso perdido, flagSucesso = false
        boolean temProgressoPerdido = sessao.progressoPerdido != null && !sessao.progressoPerdido.isEmpty();
        ack.header.flagSucesso = !temProgressoPerdido;

        if (temProgressoPerdido) {
            PayloadAck payloadAck = new PayloadAck();
            payloadAck.missingCount = sessao.progressoPerdido.size();
            payloadAck.missing = new int[sessao.progressoPerdido.size()];
            for (int i = 0; i < sessao.progressoPerdido.size(); i++) {
                payloadAck.missing[i] = sessao.progressoPerdido.get(i);
            }
            ack.payload = payloadAck;
        } else {
            ack.payload = null;
        }

        //de onde vem esta variavel enviado?
       // if (enviado && sessao.progressoPerdido != null) {
            sessao.progressoPerdido.clear();
      //  }

        return enviarMensagemUDP(ack, sessao);
    }
    
    /**
     * Finaliza a sessão de missão.
     */
    private void finalizarSessao(SessaoServidorMissionLink sessao, boolean sucesso) {
        if (sucesso) {
            //talvez no caso de insucesso, reverter a missao para pendente ou para cancelada, dependendo do caso para ser reatribuida ao mesmo rover ou a outro diferente(implementar isto depois com msg erros)
        }
        sessoesAtivas.remove(sessao.rover.idRover);
    }
    
    /**
     * Envia mensagem UDP para o rover.
     */
    private boolean enviarMensagemUDP(MensagemUDP msg, SessaoServidorMissionLink sessao) {
        try {
            InetAddress endereco = sessao.enderecoRover;
            int porta = sessao.portaRover;

            //caso nao esteja em sessao tenta ir busar os dados no rover

            if (endereco == null && sessao.rover != null && sessao.rover.enderecoHost != null) {
                endereco = InetAddress.getByName(sessao.rover.enderecoHost);
            }
            if (porta <= 0) {
                if (sessao.rover != null && sessao.rover.portaUdp != null && sessao.rover.portaUdp > 0) {
                    porta = sessao.rover.portaUdp;
                } else {
                    porta = PORTA_BASE_ROVER + sessao.rover.idRover; // porta padrão
                }
            }

            if (endereco == null || porta <= 0) {
                System.err.println("[ServidorUDP] Endpoint do rover desconhecido para envio (id=" +
                                   (sessao.rover != null ? sessao.rover.idRover : -1) + ")");
                return false;
            }

            byte[] dados = serializarObjeto(msg);
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, endereco, porta);
            socket.send(pacote);

            System.out.println("[ServidorUDP] Enviada mensagem " + msg.header.tipo +
                               " para rover " + sessao.rover.idRover + " (seq=" + msg.header.seq + ")");
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
        if (socket != null && !socket.isClosed()) {
                socket.close();
        }
    }
   
}

