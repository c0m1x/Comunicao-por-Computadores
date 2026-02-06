package nave;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lib.Condicao;
import lib.MetricasUDP;
import lib.Missao;
import lib.Rover;
import lib.SessaoServidorMissionLink;
import lib.TipoMensagem;
import lib.mensagens.CampoSerializado;
import lib.mensagens.MensagemUDP;
import lib.mensagens.SerializadorUDP;
import lib.mensagens.payloads.FragmentoPayload;
import lib.mensagens.payloads.PayloadAck;
import lib.mensagens.payloads.PayloadErro;
import lib.mensagens.payloads.PayloadMissao;
import lib.mensagens.payloads.PayloadProgresso;

/**
 * Servidor UDP da Nave-Mãe (MissionLink).
 * Implementa o protocolo de comunicação fiável para envio de missões.
 * 
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
    
    // Métricas de comunicação
    private MetricasUDP metricas;
    
    public ServidorUDP(GestaoEstado estado) {
        this.estado = estado;
        this.sessoesAtivas = new ConcurrentHashMap<>();
        this.running = true;
        this.metricas = new MetricasUDP("ServidorUDP");
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
            
            // Loop principal: recebe respostas dos rovers
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
        long ultimaLimpeza = System.currentTimeMillis();

        while (running) {
            try {
                Thread.sleep(2000); // Verifica a cada 2 segundos
                
                // Limpar sessões órfãs a cada 10 segundos
                if (System.currentTimeMillis() - ultimaLimpeza > 10000) {
                    limparSessoesOrfas();
                    ultimaLimpeza = System.currentTimeMillis();
                }

                // Procurar missão pendente
                Missao missao = estado.obterMissaoNaoAtribuida();
                if (missao == null){
                    continue;
                }
                System.out.println("[ServidorUDP] Missão pendente encontrada: " + missao);

                // Procurar rover disponível
                Rover roverDisponivel = estado.obterRoverDisponivel();
                if (!roverPodeReceberMissao(roverDisponivel)) {
                    System.out.println("[ServidorUDP] Nenhum rover disponível no momento.");
                    continue;
                } else {
                    roverDisponivel.portaUdp = PORTA_BASE_ROVER + roverDisponivel.idRover; // definir porta UDP do rover (valor padrão)
                    System.out.println("[ServidorUDP] Atribuindo missão " + missao.idMissao + " ao rover " + roverDisponivel.idRover);
                    iniciarEnvioMissao(roverDisponivel, missao);
                }
                
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("[ServidorUDP] Erro no iniciador de missões: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Inicia o processo de envio de missão para um rover.
     */
    private void iniciarEnvioMissao(Rover rover, Missao missao) {

        // Verificar se já existe sessão ativa para este rover
        if (sessoesAtivas.containsKey(rover.idRover)) {
            return;
        }
        
        //Mudar estado aqui, quando realmente vai iniciar envio
        missao.estadoMissao = Missao.EstadoMissao.EM_ANDAMENTO;
        rover.estadoRover = Rover.EstadoRover.ESTADO_RECEBENDO_MISSAO;
        System.out.println("[ServidorUDP] Rover " + rover.idRover + 
                     " mudou para ESTADO_RECEBENDO_MISSAO");
      
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
                System.err.println("[ServidorUDP] Timeout: rover " + sessao.rover.idRover + 
                                 " não confirmou recepção da missão " + sessao.missao.idMissao + 
                                 " após " + MAX_RETRIES + " tentativas");
                finalizarSessao(sessao, false);
                return;
            }

            //Passo 5: Aguarda PROGRESS enquanto não receber COMPLETED
            if(!aguardarProgress(sessao)) {
                System.err.println("[ServidorUDP] Falha ao aguardar PROGRESS do rover " + sessao.rover.idRover);
                finalizarSessao(sessao, false);
                return;
            }

            // Finalizar sessão com sucesso
            finalizarSessao(sessao, true);
            
            System.out.println("[ServidorUDP] Sessão de missão " + sessao.missao.idMissao + 
            " para rover " + sessao.rover.idRover + " concluída com sucesso");

        } catch (Exception e) {
            System.err.println("[ServidorUDP] Erro na sessão: " + e.getMessage());
            if (sessoesAtivas.containsKey(sessao.rover.idRover)) {
                finalizarSessao(sessao, false);
            }
        }
    }
    
    /**
     * Envia mensagem HELLO para o rover.
     */
    private boolean enviarHello(SessaoServidorMissionLink sessao) {
        MensagemUDP msg = criarMensagemBase(TipoMensagem.MSG_HELLO, sessao, 1, false);
        msg.payload = null; // HELLO não tem payload
        return enviarMensagemUDP(msg, sessao);
    }
    
    /**
     * Aguarda uma condição ser satisfeita dentro de um timeout.
     * @return true se a condição foi satisfeita, false se timeout
     */
    private boolean aguardarCondicao(Condicao condicao, long timeoutMs) {
        long inicio = System.currentTimeMillis();
        while (System.currentTimeMillis() - inicio < timeoutMs) {
            if (condicao.verificar()) {
                return true;
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
     * Aguarda RESPONSE do rover com retry automático do HELLO.
     */
    private boolean aguardarResponse(SessaoServidorMissionLink sessao) {
        for (int tentativas = 0; tentativas < MAX_RETRIES; tentativas++) {
            if (aguardarCondicao(() -> sessao.responseRecebido, TIMEOUT_MS)) {
                return sessao.responseSucesso;
            }
            // Timeout sem resposta
            if (tentativas + 1 < MAX_RETRIES) {
                metricas.incrementarMensagensRetransmitidas();
                if (!enviarHello(sessao)) {
                    return false;
                }
            }
        }
        return false;
    }
    
    /**
     * Fragmenta e envia todos os fragmentos da missão.
     * Usa o SerializadorUDP para fragmentação automática.
     */
    private boolean enviarFragmentosMissao(SessaoServidorMissionLink sessao) {
        try {
            PayloadMissao payload = sessao.missao.toPayload();
            
            // Verificar se precisa de fragmentação
            if (!SerializadorUDP.precisaFragmentacao(payload, TAMANHO_FRAGMENTO)) {
                // Enviar missão diretamente sem fragmentação
                System.out.println("[ServidorUDP] Missão " + sessao.missao.idMissao + 
                        " não precisa de fragmentação - enviando diretamente");
                
                sessao.totalFragmentos = 1;
                // Armazenar o payload completo para permitir retransmissão
                sessao.payloadCompleto = payload;
                
                MensagemUDP msg = criarMensagemBase(TipoMensagem.MSG_MISSION, sessao, 2, false);
                msg.header.totalFragm = 1;
                msg.payload = payload; // Payload direto, não fragmentado
                
                return enviarMensagemUDP(msg, sessao);
            }
            
            // Precisa fragmentação
            List<FragmentoPayload> fragmentos = SerializadorUDP.fragmentarPayload(payload, TAMANHO_FRAGMENTO);
            
            sessao.totalFragmentos = fragmentos.size();
            sessao.fragmentosPayload = fragmentos;

            // Calcular tamanho total para log
            List<CampoSerializado> campos = SerializadorUDP.serializarPayload(payload);
            int tamanhoTotal = SerializadorUDP.calcularTamanhoTotal(campos);
            
            System.out.println("[ServidorUDP] Missão " + sessao.missao.idMissao + 
                    ": " + tamanhoTotal + " bytes em " + sessao.totalFragmentos + 
                    " fragmentos (máx " + TAMANHO_FRAGMENTO + " bytes cada)");

            // Enviar todos os fragmentos
            for (int i = 0; i < sessao.totalFragmentos; i++) {
                if (!enviarFragmentoPayload(sessao, i + 2, fragmentos.get(i))) {
                    return false;
                }
                Thread.sleep(10);
            }

            return true;
            
        } catch (Exception e) {
            System.err.println("[ServidorUDP] Erro ao enviar fragmentos: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Envia um FragmentoPayload específico.
     */
    private boolean enviarFragmentoPayload(SessaoServidorMissionLink sessao, int seq, FragmentoPayload frag) {
        MensagemUDP msg = criarMensagemBase(TipoMensagem.MSG_MISSION, sessao, seq, false);
        msg.header.totalFragm = sessao.totalFragmentos;
        msg.payload = frag;
        
        return enviarMensagemUDP(msg, sessao);
    }
    
    /**
     * Envia um fragmento específico por índice (para retransmissões).
     */
    private boolean enviarFragmento(SessaoServidorMissionLink sessao, int seq) {
        // Caso especial: missão completa sem fragmentação (seq=2, totalFragm=1)
        if (sessao.totalFragmentos == 1 && seq == 2 && sessao.payloadCompleto != null) {
            MensagemUDP msg = criarMensagemBase(TipoMensagem.MSG_MISSION, sessao, seq, false);
            msg.header.totalFragm = 1;
            msg.payload = sessao.payloadCompleto;
            return enviarMensagemUDP(msg, sessao);
        }
        
        // Caso normal: retransmissão de fragmento específico
        int indice = seq - 2; // seq começa em 2, indice em 0
        if (indice < 0 || sessao.fragmentosPayload == null || indice >= sessao.fragmentosPayload.size()) {
            return false;
        }
        
        return enviarFragmentoPayload(sessao, seq, sessao.fragmentosPayload.get(indice));
    }
    
    /**
     * Aguarda ACK completo, retransmitindo fragmentos perdidos se necessário.
     * Se o ACK não chegar (timeout), retransmite TODA a missão.
     */
    private boolean aguardarAckCompleto(SessaoServidorMissionLink sessao) {
        for (int tentativas = 0; tentativas < MAX_RETRIES; tentativas++) {
            if (aguardarCondicao(() -> sessao.ackRecebido, TIMEOUT_MS)) {
                if (sessao.fragmentosPerdidos.isEmpty()) {
                    // ACK completo
                    System.out.println("[ServidorUDP] Missão " + sessao.missao.idMissao + 
                         " enviada com sucesso para rover " + sessao.rover.idRover);
                    estado.atribuirMissaoARover(sessao.rover.idRover, sessao.missao.idMissao);
                    return true;
                } else {
                    // Retransmitir fragmentos perdidos
                    System.out.println("[ServidorUDP] Retransmitindo " + 
                                     sessao.fragmentosPerdidos.size() + " fragmentos perdidos");
                    metricas.incrementarMensagensRetransmitidas();
                    for (int seq : sessao.fragmentosPerdidos) {
                        enviarFragmento(sessao, seq);
                    }
                    // Reset para aguardar novo ACK 
                    sessao.ackRecebido = false;
                    // Não incrementar tentativas; aguardar novo ACK na próxima iteração
                    tentativas--;
                }
            } else {
                // Timeout sem ACK, verificar se temos fragmentos conhecidos para reenviar
                if (!sessao.fragmentosPerdidos.isEmpty()) {
                    // Reenviar os mesmos fragmentos
                    System.out.println("[ServidorUDP] Timeout - retransmitindo " + 
                                     sessao.fragmentosPerdidos.size() + " fragmentos perdidos");
                    metricas.incrementarMensagensRetransmitidas();
                    for (int seq : sessao.fragmentosPerdidos) {
                        enviarFragmento(sessao, seq);
                    }
                    sessao.ackRecebido = false;
                    tentativas--; // Não consumir tentativa
                } else {
                    // Reenviar missão completa
                    if (tentativas + 1 < MAX_RETRIES) {
                        System.out.println("[ServidorUDP] Timeout aguardando ACK - retransmitindo missão completa (tentativa " + 
                                         (tentativas + 2) + "/" + MAX_RETRIES + ")");
                        metricas.incrementarMensagensRetransmitidas();
                        if (!enviarFragmentosMissao(sessao)) {
                            return false;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Aguarda receção de mensagens PROGRESS até receber COMPLETED ou timeout.
     * Reinicia janela de timeout sempre que chega novo progresso.
     */

    private boolean aguardarProgress(SessaoServidorMissionLink sessao) {

        long intervaloAtualizacao = TIMEOUT_MS;

        if (sessao.missao != null && sessao.missao.intervaloAtualizacao > 0){
            intervaloAtualizacao = sessao.missao.intervaloAtualizacao * 1000; // converter para ms
        }
        
        // timeout = 8x intervalo de atualização (aumentado para tolerar atrasos na rede)
        long timeoutProgressMs = intervaloAtualizacao * 8;

        long inicioJanela = System.currentTimeMillis();
        int ultimoSeq = sessao.ultimoSeq;
        // Continua enquanto não recebeu COMPLETED nem recebeu ERROR
        while (!sessao.completedRecebido && !sessao.erroRecebido) {

            //Verificar se sessão ainda existe 
            if (!sessoesAtivas.containsKey(sessao.rover.idRover)) {
                System.out.println("[ServidorUDP] Sessão do rover " + sessao.rover.idRover + 
                                 " foi removida (COMPLETED/ERROR recebido em outro handler)");
                return true; //missão foi concluída
            }

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

        return true; // COMPLETED ou ERROR recebido
    }
    
    /**
     * Processa mensagem recebida do rover (RESPONSE ou ACK).
     */
    private void processarMensagemRecebida(DatagramPacket pacote) {
        try {
            MensagemUDP msg = deserializarMensagem(pacote.getData(), pacote.getLength());
            if (msg == null || msg.header == null)
                return;
            
            metricas.incrementarMensagensRecebidas();
            
            int idRover = msg.header.idEmissor;
            SessaoServidorMissionLink sessao = sessoesAtivas.get(idRover);
            
            if (sessao == null)
                // Mensagem sem sessão ativa
                return;
            
            sessao.atualizarAtividade();
            
            // Atualizar endpoint da sessão apenas na primeira mensagem recebida.
            // Após a primeira mensagem, mantemos o endereço fixo para consistência.
            if (sessao.enderecoRover == null && pacote.getAddress() != null) {
                sessao.enderecoRover = pacote.getAddress();
                sessao.portaRover = pacote.getPort();
                System.out.println("[ServidorUDP] Endpoint do rover " + idRover + 
                                 " estabelecido: " + sessao.enderecoRover.getHostAddress() + 
                                 ":" + sessao.portaRover);
            }

            switch (msg.header.tipo) {
                case MSG_RESPONSE:
                    // Proteção contra RESPONSE duplicado
                    if (sessao.responseRecebido) {
                        System.out.println("[ServidorUDP] RESPONSE duplicado ignorado do rover " + idRover);
                        metricas.incrementarMensagensDuplicadas();
                        break;
                    }
                    sessao.responseRecebido = true;
                    sessao.responseSucesso = msg.header.flagSucesso;
                    sessao.ultimoSeq = msg.header.seq;
                    metricas.incrementarResponseRecebidos();
                    System.out.println("[ServidorUDP] RESPONSE recebido do rover " + idRover + 
                                     " (sucesso=" + msg.header.flagSucesso + ", seq=" + msg.header.seq + ")");
                    break;
                    
                case MSG_ACK:
                    sessao.ackRecebido = true;
                    if (msg.header.seq > sessao.ultimoSeq) {
                    sessao.ultimoSeq = msg.header.seq;
                    }
                    metricas.incrementarAcksRecebidos();
                    if (msg.payload instanceof PayloadAck) {
                        PayloadAck ack = (PayloadAck) msg.payload;
                        sessao.fragmentosPerdidos.clear();
                        if (ack.missing != null && ack.missing.length > 0) {
                            for (int seq : ack.missing) {
                                sessao.fragmentosPerdidos.add(seq);
                            }
                            metricas.incrementarMensagensPerdidas(ack.missing.length);
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
                
                case MSG_ERROR:
                    processarErro(msg, idRover, pacote);
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
        if (!(msg.payload instanceof PayloadProgresso)) return;
        
        PayloadProgresso progresso = (PayloadProgresso) msg.payload;
        int seqRecebido = msg.header.seq;
        
        System.out.println("[ServidorUDP] PROGRESS recebido do rover " + idRover + 
                         " (seq=" + seqRecebido + ", missão=" + progresso.idMissao + 
                         ", progresso=" + String.format("%.2f", progresso.progressoPercentagem) + "%%)");

        SessaoServidorMissionLink sessao = sessoesAtivas.get(idRover);
        if (sessao == null) return;
        
        // Verificar se é da missão correta
        if (progresso.idMissao != sessao.missao.idMissao) return;
    
        // Verificar se já recebeu COMPLETED 
        if (sessao.completedRecebido || sessao.erroRecebido) {
            System.out.println("[ServidorUDP] PROGRESS ignorado - rover " + idRover + 
                             " já enviou " + (sessao.completedRecebido ? "COMPLETED" : "ERROR"));
            return;
        }

        // Tratamento de duplicados ou mensagens antigas 
        if (seqRecebido <= sessao.ultimoSeq) {
            String tipo = (seqRecebido == sessao.ultimoSeq) ? "duplicado" : "antigo";
            if (seqRecebido == sessao.ultimoSeq) {
                metricas.incrementarMensagensDuplicadas();
            } else {
                metricas.incrementarMensagensEmAtraso();
            }
            System.out.println("[ServidorUDP] PROGRESS " + tipo + " (seq=" + seqRecebido + 
                             (seqRecebido < sessao.ultimoSeq ? ", último=" + sessao.ultimoSeq : "") + 
                             ") - Enviando ACK");
            int seqOriginal = sessao.ultimoSeq;
            sessao.ultimoSeq = seqRecebido;
            sessao.progressoPerdido = new HashSet<>();
            enviarAckParaRover(sessao);
            if (seqRecebido < seqOriginal) {
                sessao.ultimoSeq = seqOriginal; // Restaurar para mensagens antigas
            }
            return;
        }

        // Verificar perdas: seq não é o próximo esperado
        int seqEsperado = sessao.ultimoSeq + 1;
        sessao.progressoPerdido = new HashSet<>();
        
        if (seqRecebido > seqEsperado) {
            for (int s = seqEsperado; s < seqRecebido; s++) {
                sessao.progressoPerdido.add(s);
            }
            metricas.incrementarMensagensPerdidas(sessao.progressoPerdido.size());
            System.out.println("[ServidorUDP] PROGRESS perdido detectado: seqs " + sessao.progressoPerdido);
        }

        // Atualizar estado da missão
        metricas.incrementarProgressRecebidos();
        estado.atualizarProgresso(progresso);
        sessao.recebendoProgresso = true;
        sessao.ultimoSeq = seqRecebido;
        
        enviarAckParaRover(sessao);
    }   
    
    /**
     * Processa mensagem COMPLETED do rover.
     * Trata duplicados reenviando ACK (rover pode não ter recebido).
     */
    private void processarCompleted(MensagemUDP msg, int idRover, DatagramPacket pacote) {
        SessaoServidorMissionLink sessao = sessoesAtivas.get(idRover);
        
        // Proteção contra COMPLETED duplicado
        if (sessao != null && sessao.completedRecebido) {
            System.out.println("[ServidorUDP] COMPLETED duplicado do rover " + idRover + 
                             " (seq=" + msg.header.seq + ") - Reenviando ACK FINAL");
            metricas.incrementarMensagensDuplicadas();
            sessao.ultimoSeq = msg.header.seq;
            // Marcar que estamos a reenviar ACK final para evitar que a
            // rotina de limpeza remova a sessão enquanto fazemos retransmissões.
            sessao.finalAckPending = true;
            try {
                enviarAckFinalParaRover(sessao);
            } finally {
                sessao.finalAckPending = false;
            }
            return;
        }
        
        System.out.println("[ServidorUDP] COMPLETED recebido do rover " + idRover + 
                         " (seq=" + msg.header.seq + ", missão=" + msg.header.idMissao + 
                         ", sucesso=" + msg.header.flagSucesso + ")");
        metricas.incrementarCompletedRecebidos();
        // Atualizar estado via GestaoEstado
        estado.concluirMissao(idRover, msg.header.idMissao, msg.header.flagSucesso);
        
        if (sessao != null) {
            sessao.completedRecebido = true;
            sessao.completedSucesso = msg.header.flagSucesso;
            sessao.ultimoSeq = msg.header.seq;
            
            // Enviar ACK final 3 vezes para garantir entrega
            sessao.finalAckPending = true;
            try {
                for (int i = 0; i < 3; i++) {
                    enviarAckFinalParaRover(sessao);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                sessao.finalAckPending = false;
            }

        }
    }
    
    /**
     * Processa mensagem ERROR do rover.
     * Indica que o rover não conseguiu completar a missão devido a erro.
     */
    private void processarErro(MensagemUDP msg, int idRover, DatagramPacket pacote) {
        SessaoServidorMissionLink sessao = sessoesAtivas.get(idRover);
        
        // Proteção contra ERROR duplicado 
        if (sessao != null && sessao.erroRecebido) {
            System.out.println("[ServidorUDP] ERROR duplicado do rover " + idRover + 
                             " (seq=" + msg.header.seq + ") - Reenviando ACK FINAL");
            metricas.incrementarMensagensDuplicadas();
            sessao.ultimoSeq = msg.header.seq;
            enviarAckFinalParaRover(sessao);
            return;
        }

        metricas.incrementarErrorRecebidos();
        // Extrair detalhes do erro 
        String descricaoErro = "Erro desconhecido";
        int codigoErro = 0;
        float progressoNoErro = 0;
        float bateriaNoErro = 0;

        if (msg.payload instanceof PayloadErro) {
            PayloadErro erro = (PayloadErro) msg.payload;
            codigoErro = erro.codigoErro;
            descricaoErro = erro.descricao;
            progressoNoErro = erro.progressoAtual;
            bateriaNoErro = erro.bateria;

            System.out.println("[ServidorUDP] ERROR recebido do rover " + idRover + 
                         " (seq=" + msg.header.seq + ", missão=" + msg.header.idMissao + ")");
            System.out.println("[ServidorUDP] Detalhes do erro: código=" + codigoErro + 
                             ", descrição=" + descricaoErro + 
                             ", progressoMissão=" + String.format("%.2f", progressoNoErro) + "%" +
                             ", bateria=" + String.format("%.2f", bateriaNoErro) + "%");
        } else {
            System.out.println("[ServidorUDP] ERROR recebido do rover " + idRover + 
                             " (seq=" + msg.header.seq + ", missão=" + msg.header.idMissao + 
                             " - Payload não reconhecido)");
        }

        estado.falharMissao(idRover, msg.header.idMissao, codigoErro, descricaoErro);
        
        if (sessao != null) {
            sessao.erroRecebido = true;
            sessao.ultimoSeq = msg.header.seq;

            // Marcar que estamos a enviar ACK final; evitar que o metodo
            // de limpeza de sessões orfãs remova a sessão enquanto enviamos retransmissões.
            sessao.finalAckPending = true;
            try {
                // Enviar ACK final 3 vezes para garantir que rover recebe
                for (int i = 0; i < 3; i++) {
                    enviarAckFinalParaRover(sessao);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                sessao.finalAckPending = false;
            }

            // Remover sessão imediatamente após enviar ACKs
            sessoesAtivas.remove(idRover);
            System.out.println("[ServidorUDP] Sessão do rover " + idRover + 
                             " removida (missão " + msg.header.idMissao + " falhou com erro)");
            System.out.println("[ServidorUDP] Sessões ativas restantes: " + sessoesAtivas.keySet());
        }

    }

    /**
     * Envia ACK para o rover (usado para PROGRESS, COMPLETED e ERROR).
     */
    private boolean enviarAckParaRover(SessaoServidorMissionLink sessao) {
        boolean temProgressoPerdido = sessao.progressoPerdido != null && !sessao.progressoPerdido.isEmpty();
        MensagemUDP ack = criarMensagemBase(TipoMensagem.MSG_ACK, sessao, sessao.ultimoSeq, !temProgressoPerdido);

        if (temProgressoPerdido) {
            PayloadAck payloadAck = new PayloadAck();
            payloadAck.missingCount = sessao.progressoPerdido.size();
            payloadAck.missing = setParaArray(sessao.progressoPerdido);
            ack.payload = payloadAck;
        } else {
            ack.payload = null;
        }

        boolean enviado = enviarMensagemUDP(ack, sessao);
        if (enviado && sessao.progressoPerdido != null) {
            sessao.progressoPerdido.clear();
        }
        return enviado;
    }
    
    /**
     * Finaliza a sessão de missão.
     * Em caso de insucesso na comunicação, reverte a missão E estado do rover
     */
    private void finalizarSessao(SessaoServidorMissionLink sessao, boolean sucesso) {

        while (sessao.finalAckPending) {
            // Aguardar até que o ACK final seja enviado, se aplicável
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!sucesso && !sessao.completedRecebido && !sessao.erroRecebido) {
            if (sessao.recebendoProgresso) {
                // Rover já tinha começado execução: não reverter, fica como falhada, futuramente o rover poderia recomeçar a missão a partir daqui
                System.out.println("[ServidorUDP] Missão " + sessao.missao.idMissao + 
                                 " do rover " + sessao.rover.idRover + 
                                 " perdeu comunicação mas rover já estava a executar - NÃO reverter");
            } else {
                // Falha ANTES de começar execução 
                estado.reverterMissaoParaPendente(sessao.missao.idMissao);
                System.out.println("[ServidorUDP] Missão " + sessao.missao.idMissao + 
                                 " revertida para pendente (rover " + sessao.rover.idRover + 
                                 " nunca confirmou/começou execução)");
            }
        }
        int idRover = sessao.rover.idRover;
        sessoesAtivas.remove(idRover);
        System.out.println("[ServidorUDP] Sessão do rover " + idRover + " removida");
    }
    
    /**
     * Envia mensagem UDP para o rover.
     */
    private boolean enviarMensagemUDP(MensagemUDP msg, SessaoServidorMissionLink sessao) {
        try {
            InetAddress endereco = sessao.enderecoRover;
            int porta = sessao.portaRover;

            // Caso não esteja em sessão tenta ir buscar os dados no rover

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

            byte[] dados = sessao.serializador.serializarObjeto(msg);
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, endereco, porta);
            socket.send(pacote);

            metricas.incrementarMensagensEnviadas();
            // Incrementar contadores específicos por tipo
            switch (msg.header.tipo) {
                case MSG_HELLO:
                    metricas.incrementarHelloEnviados();
                    break;
                case MSG_MISSION:
                    metricas.incrementarMissionEnviados();
                    break;
                case MSG_ACK:
                    metricas.incrementarAcksEnviados();
                    break;
            }
            
            System.out.println("[ServidorUDP] Enviada mensagem " + msg.header.tipo +
                               " para rover " + sessao.rover.idRover + " (seq=" + msg.header.seq + ")");
            return true;
        } catch (IOException e) {
            System.err.println("[ServidorUDP] Erro ao enviar mensagem: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Verifica se rover está realmente disponível para receber missão.
     * Considera estado do rover E existência de sessão ativa.
     */
    private boolean roverPodeReceberMissao(Rover rover) {
        if (rover == null) {
            return false;
        }
        int idRover = rover.idRover;

        // Verificar sessões ativas (fallback para detectar inconsistências)
        if (sessoesAtivas.containsKey(idRover)) {
            return false;
        }

        return true;
    }

    /**
     * Remove sessões órfãs (rovers disponíveis mas com sessão ativa).
     * Chame periodicamente para prevenir bloqueios.
     */
    private void limparSessoesOrfas() {
        List<Integer> paraRemover = new ArrayList<>();

        for (Map.Entry<Integer, SessaoServidorMissionLink> entry : sessoesAtivas.entrySet()) {
            int idRover = entry.getKey();
            SessaoServidorMissionLink sessao = entry.getValue();
            Rover rover = estado.obterRover(idRover);

            if (rover == null) {
                paraRemover.add(idRover);
                continue;
            }

            long agora = System.currentTimeMillis();
            long inatividade = agora - sessao.ultimaAtividade;

            // Remover se sessão está inativa há mais de 30 segundos
            if (inatividade > sessao.limiteInatividade) {
                paraRemover.add(idRover);
                System.out.println("[ServidorUDP] Limpando sessão órfã do rover " + idRover + 
                                 " (inatividade=" + (inatividade/1000) + "s)");

                // Reverter estado do rover para DISPONIVEL
                if (rover.estadoRover == Rover.EstadoRover.ESTADO_RECEBENDO_MISSAO) {
                    rover.estadoRover = Rover.EstadoRover.ESTADO_DISPONIVEL;
                    System.out.println("[ServidorUDP] Rover " + idRover + 
                                     " revertido para ESTADO_DISPONIVEL");
                }
            }

            //Detectar inconsistência (rover disponível mas com sessão)
            boolean roverDisponivel = (rover.estadoRover == Rover.EstadoRover.ESTADO_DISPONIVEL && 
                                      !rover.temMissao);
            boolean sessaoFinalizada = sessao.completedRecebido || sessao.erroRecebido;

            if (roverDisponivel && sessaoFinalizada) {
                // Não remover se ainda estivermos a enviar ACK final para este rover,
                // para evitar remoção concorrente antes de terminar retransmissões.
                if (sessao.finalAckPending) {
                    System.out.println("[ServidorUDP] Mantendo sessão do rover " + idRover + 
                                     " porque ACK final está em trânsito");
                } else {
                    paraRemover.add(idRover);
                    System.out.println("[ServidorUDP] Limpando sessão órfã do rover " + idRover + 
                                     " (sessão já finalizada: COMPLETED/ERROR)");
                }
            }
        }

        // Também verificar rovers em RECEBENDO_MISSAO sem sessão
        for (Rover rover : estado.listarRovers()) {
            if (rover.estadoRover == Rover.EstadoRover.ESTADO_RECEBENDO_MISSAO && 
                !sessoesAtivas.containsKey(rover.idRover)) {
                
                System.out.println("[ServidorUDP] Rover " + rover.idRover + 
                                 " está em RECEBENDO_MISSAO mas sem sessão - revertendo");
                rover.estadoRover = Rover.EstadoRover.ESTADO_DISPONIVEL;
            }
        }

        for (int idRover : paraRemover) {
            sessoesAtivas.remove(idRover);
        }

        if (!paraRemover.isEmpty()) {
            System.out.println("[ServidorUDP] Sessões órfãs removidas: " + paraRemover);
        }
    }
    // ==================== MÉTODOS DE SERIALIZAÇÃO ====================
    
    /**
     * Deserializa bytes para MensagemUDP.
     */
    private MensagemUDP deserializarMensagem(byte[] dados, int length) {
        return SerializadorUDP.deserializarMensagem(dados, length);
    }
    
    // ==================== MÉTODOS AUXILIARES ====================
    
    /**
     * Converte Set<Integer> para int[].
     */
    private int[] setParaArray(Set<Integer> set) {
        int[] array = new int[set.size()];
        int i = 0;
        for (Integer valor : set) {
            array[i++] = valor;
        }
        return array;
    }
    
    public void parar() {
        running = false;
        if (socket != null && !socket.isClosed()) {
                socket.close();
        }
    }
    
    public MetricasUDP getMetricas() {
        return metricas;
    }
    
    // ==================== MÉTODOS DE CRIAÇÃO DE MENSAGENS ====================
    
    /**
     * Cria uma mensagem UDP base com campos comuns preenchidos.
     */
    private MensagemUDP criarMensagemBase(TipoMensagem tipo, SessaoServidorMissionLink sessao, int seq, boolean flagSucesso) {
        MensagemUDP msg = new MensagemUDP();
        msg.header.tipo = tipo;
        msg.header.idEmissor = 0; // Nave-Mãe
        msg.header.idRecetor = sessao.rover.idRover;
        msg.header.idMissao = sessao.missao.idMissao;
        msg.header.seq = seq;
        msg.header.totalFragm = 1;
        msg.header.flagSucesso = flagSucesso;
        return msg;
    }
    
    /**
     * Envia ACK final para o rover com flag finalAck=true.
     * Este ACK indica ao rover que pode parar de retransmitir COMPLETED/ERROR.
     */
    private boolean enviarAckFinalParaRover(SessaoServidorMissionLink sessao) {
        boolean temProgressoPerdido = sessao.progressoPerdido != null && !sessao.progressoPerdido.isEmpty();
        MensagemUDP ack = criarMensagemBase(TipoMensagem.MSG_ACK, sessao, sessao.ultimoSeq, !temProgressoPerdido);

        PayloadAck payloadAck = new PayloadAck();
        payloadAck.finalAck = true; // Marca como ACK final
        
        if (temProgressoPerdido) {
            payloadAck.missingCount = sessao.progressoPerdido.size();
            payloadAck.missing = setParaArray(sessao.progressoPerdido);
        } else {
            payloadAck.missingCount = 0;
            payloadAck.missing = new int[0];
        }
        
        ack.payload = payloadAck;

        boolean enviado = enviarMensagemUDP(ack, sessao);
        if (enviado) {
            System.out.println("[ServidorUDP] ACK final enviado para rover " + sessao.rover.idRover + 
                             " (seq=" + sessao.ultimoSeq + ", finalAck=true)");
        }
        return enviado;
    }

}

