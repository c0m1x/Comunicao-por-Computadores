package nave;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import lib.*;
import lib.mensagens.CampoSerializado;
import lib.Condicao;
import lib.mensagens.MensagemUDP;
import lib.mensagens.SerializadorUDP;
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

            //Passo 6: Enviar ACK final 3 vezes para maior robustez (99.9% de entrega assumindo perda independente de 10%)
            for (int i = 0; i < 3; i++) {
                enviarAckFinalParaRover(sessao);
                try {
                    Thread.sleep(200); // 200ms entre repetições
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
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
        MensagemUDP msg = criarMensagemBase(TipoMensagem.MSG_HELLO, sessao, 1, false);
        msg.payload = null; // HELLO não tem payload
        return enviarMensagemUDP(msg, sessao);
    }
    
        // ==================== MÉTODOS DE ESPERA ====================
    
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
            // Timeout sem resposta - retry
            if (tentativas + 1 < MAX_RETRIES && !enviarHello(sessao)) {
                return false;
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
                
                MensagemUDP msg = criarMensagemBase(TipoMensagem.MSG_MISSION, sessao, 2, false);
                msg.header.totalFragm = 1;
                msg.payload = payload; // Payload direto, não fragmentado
                
                return enviarMensagemUDP(msg, sessao);
            }
            
            // Precisa fragmentação - serializar e fragmentar payload
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
        int indice = seq - 2; // seq começa em 2, indice em 0
        if (indice < 0 || sessao.fragmentosPayload == null || indice >= sessao.fragmentosPayload.size()) {
            return false;
        }
        
        return enviarFragmentoPayload(sessao, seq, sessao.fragmentosPayload.get(indice));
    }
    
    /**
     * Aguarda ACK completo, retransmitindo fragmentos perdidos se necessário.
     */
    private boolean aguardarAckCompleto(SessaoServidorMissionLink sessao) {
        for (int tentativas = 0; tentativas < MAX_RETRIES; tentativas++) {
            if (aguardarCondicao(() -> sessao.ackRecebido, TIMEOUT_MS)) {
                if (sessao.fragmentosPerdidos.isEmpty()) {
                    // ACK completo! Sucesso!
                    System.out.println("[ServidorUDP] Missão " + sessao.missao.idMissao + 
                         " enviada com sucesso para rover " + sessao.rover.idRover);
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
        
        // timeout = 2x intervalo de atualização (podes ajustar este fator)
        long timeoutProgressMs = intervaloAtualizacao * 2;

        long inicioJanela = System.currentTimeMillis();
        int ultimoSeq = sessao.ultimoSeq;
        // Continua enquanto NÃO recebeu COMPLETED E NÃO recebeu ERROR
        while (!sessao.completedRecebido && !sessao.erroRecebido) {
            //NOTA: SE o progresso viesse fragmentado podia aproveitar que era por campos e se não recebesse todos os campos mesmo depois de retries e cenas, guadava o progresso das informações que tivesse
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
            
            if (msg == null || msg.header == null) {
                return;
            }
            
            int idRover = msg.header.idEmissor;
            SessaoServidorMissionLink sessao = sessoesAtivas.get(idRover);
            
            if (sessao == null) {
                // Mensagem sem sessão ativa
                return;
            }
            
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
                        break;
                    }
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
        
        // Tratamento de duplicados ou mensagens antigas - SEMPRE enviar ACK
        if (seqRecebido <= sessao.ultimoSeq) {
            String tipo = (seqRecebido == sessao.ultimoSeq) ? "duplicado" : "antigo";
            System.out.println("[ServidorUDP] PROGRESS " + tipo + " (seq=" + seqRecebido + 
                             (seqRecebido < sessao.ultimoSeq ? ", último=" + sessao.ultimoSeq : "") + 
                             ") - Enviando ACK");
            int seqOriginal = sessao.ultimoSeq;
            sessao.ultimoSeq = seqRecebido;
            sessao.progressoPerdido = new ArrayList<>();
            enviarAckParaRover(sessao);
            if (seqRecebido < seqOriginal) {
                sessao.ultimoSeq = seqOriginal; // Restaurar para mensagens antigas
            }
            return;
        }

        // Verificar perdas: seq não é o próximo esperado
        int seqEsperado = sessao.ultimoSeq + 1;
        sessao.progressoPerdido = new ArrayList<>();
        
        if (seqRecebido > seqEsperado) {
            for (int s = seqEsperado; s < seqRecebido; s++) {
                sessao.progressoPerdido.add(s);
            }
            System.out.println("[ServidorUDP] PROGRESS perdido detectado: seqs " + sessao.progressoPerdido);
        }

        // Atualizar estado da missão
        estado.atualizarProgresso(progresso);
        sessao.recebendoProgresso = true;
        sessao.ultimoSeq = seqRecebido;
        
        enviarAckParaRover(sessao);
    }    /**
     * Processa mensagem COMPLETED do rover.
     * Trata duplicados reenviando ACK (rover pode não ter recebido).
     */
    private void processarCompleted(MensagemUDP msg, int idRover, DatagramPacket pacote) {
        SessaoServidorMissionLink sessao = sessoesAtivas.get(idRover);
        
        // Proteção contra COMPLETED duplicado - reenviar ACK
        if (sessao != null && sessao.completedRecebido) {
            System.out.println("[ServidorUDP] COMPLETED duplicado do rover " + idRover + 
                             " (seq=" + msg.header.seq + ") - Reenviando ACK");
            sessao.ultimoSeq = msg.header.seq;
            enviarAckParaRover(sessao);
            return;
        }
        
        System.out.println("[ServidorUDP] COMPLETED recebido do rover " + idRover + 
                         " (seq=" + msg.header.seq + ", missão=" + msg.header.idMissao + 
                         ", sucesso=" + msg.header.flagSucesso + ")");
        // Atualizar estado via GestaoEstado
        estado.concluirMissao(idRover, msg.header.idMissao, msg.header.flagSucesso);
        
        if (sessao != null) {
            sessao.completedRecebido = true;
            sessao.completedSucesso = msg.header.flagSucesso;
            sessao.ultimoSeq = msg.header.seq;
        }
    }
    
    /**
     * Processa mensagem ERROR do rover.
     * Indica que o rover não conseguiu completar a missão devido a erro.
     */
    private void processarErro(MensagemUDP msg, int idRover, DatagramPacket pacote) {
        SessaoServidorMissionLink sessao = sessoesAtivas.get(idRover);
        
        // Proteção contra ERROR duplicado - reenviar ACK
        if (sessao != null && sessao.erroRecebido) {
            System.out.println("[ServidorUDP] ERROR duplicado do rover " + idRover + 
                             " (seq=" + msg.header.seq + ") - Reenviar ACK final");
            sessao.ultimoSeq = msg.header.seq;
            enviarAckFinalParaRover(sessao);
            return;
        }

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
            
            // Enviar ACK final 3 vezes para garantir que rover recebe
            for (int i = 0; i < 3; i++) {
                enviarAckFinalParaRover(sessao);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
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
            payloadAck.missing = listaParaArray(sessao.progressoPerdido);
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
     * Em caso de insucesso na comunicação, reverte a missão para pendente.
     */
    private void finalizarSessao(SessaoServidorMissionLink sessao, boolean sucesso) {
        if (!sucesso && !sessao.completedRecebido && !sessao.erroRecebido) {
            // Falha de comunicação (não recebeu COMPLETED nem ERROR) - reverter missão para pendente
            estado.reverterMissaoParaPendente(sessao.missao.idMissao);
            System.out.println("[ServidorUDP] Missão " + sessao.missao.idMissao + 
                             " revertida para pendente (falha de comunicação)");
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

            byte[] dados = sessao.serializador.serializarObjeto(msg);
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
    
    // ==================== MÉTODOS DE SERIALIZAÇÃO ====================
    
    /**
     * Deserializa bytes para MensagemUDP.
     */
    private MensagemUDP deserializarMensagem(byte[] dados, int length) {
        return SerializadorUDP.deserializarMensagem(dados, length);
    }
    
    // ==================== MÉTODOS AUXILIARES ====================
    
    /**
     * Converte List<Integer> para int[].
     */
    private int[] listaParaArray(List<Integer> lista) {
        int[] array = new int[lista.size()];
        for (int i = 0; i < lista.size(); i++) {
            array[i] = lista.get(i);
        }
        return array;
    }
    
    public void parar() {
        running = false;
        if (socket != null && !socket.isClosed()) {
                socket.close();
        }
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
            payloadAck.missing = listaParaArray(sessao.progressoPerdido);
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

