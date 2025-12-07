package rover;

import lib.SessaoClienteMissionLink;
import lib.TipoMensagem;
import lib.mensagens.MensagemUDP;
import lib.mensagens.SerializadorUDP;
import lib.mensagens.payloads.*;
import lib.Rover.EstadoRover;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
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
    
    private static final int TIMEOUT_MS = 3000; // Reduzido para responder mais rápido a perdas
    private static final int MAX_RETRIES = 5;   // Aumentado para maior tolerância a perdas
    
    private int idRover;
    private int porta;
    private DatagramSocket socket;
    private boolean running = true;
    private MaquinaEstados maquina;
    
    // Controle da sessão completa da missão 
    private SessaoClienteMissionLink sessaoAtual = null;
    
    public ClienteUDP(int idRover, int porta, MaquinaEstados maquina) {
        this.idRover = idRover;
        this.porta = porta;
        this.maquina = maquina;
    }
    
    @Override
    public void run() {
        try {
            socket = new DatagramSocket(porta);
            socket.setSoTimeout(100);
            System.out.println("[ClienteUDP] Rover " + idRover + " iniciado na porta " + porta);

            // Thread que atualiza periodicamente a máquina de estados
            Thread maquinaUpdater = new Thread(() -> {
                while (running) {
                    try {
                        if (maquina != null) {
                            maquina.atualizar();
                        }
                        Thread.sleep(1000); // Atualizar a cada 1s
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            maquinaUpdater.setDaemon(true);
            maquinaUpdater.start();
            
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
        MensagemUDP msg = SerializadorUDP.deserializarMensagem(pacote.getData(), pacote.getLength());

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
     * Trata HELLOs duplicados e protege sessões em execução.
     */
    private void processarHello(MensagemUDP msg, InetAddress endereco, int porta) {
        System.out.println("[ClienteUDP] HELLO recebido - Missão ID: " + msg.header.idMissao);
        
        // Proteção contra HELLO duplicado para a mesma missão
        if (sessaoAtual != null && sessaoAtual.idMissao == msg.header.idMissao) {
            System.out.println("[ClienteUDP] HELLO duplicado para missão " + msg.header.idMissao + " - Reenviando RESPONSE");
            // Reenviar RESPONSE (servidor pode não ter recebido)
            enviarResponse(!sessaoAtual.emExecucao);
            return;
        }
        
        // Proteção: não aceitar nova missão se já está em execução
        if (sessaoAtual != null && sessaoAtual.emExecucao) {
            System.out.println("[ClienteUDP] HELLO ignorado - Rover já está em execução de missão " + sessaoAtual.idMissao);
            // Responder que não está disponível
            // Criar sessão temporária para enviar RESPONSE negativo
            SessaoClienteMissionLink sessaoTemp = new SessaoClienteMissionLink(msg.header.idMissao, endereco, porta);
            sessaoTemp.seqAtual = msg.header.seq;
            SessaoClienteMissionLink sessaoAnterior = sessaoAtual;
            sessaoAtual = sessaoTemp;
            enviarResponse(false);
            sessaoAtual = sessaoAnterior;
            return;
        }
        
        boolean disponivel = (maquina != null && maquina.getEstadoAtual() == EstadoRover.ESTADO_DISPONIVEL);
        
        if (disponivel){
            // Criar nova sessão de missão
            sessaoAtual = new SessaoClienteMissionLink(msg.header.idMissao, endereco, porta);
            sessaoAtual.seqAtual = msg.header.seq;
            System.out.println("[ClienteUDP] Rover disponível - Aguardando fragmentos MISSION");
        } else {
            System.out.println("[ClienteUDP] Rover não disponível para missão");
            // Criar sessão temporária para enviar RESPONSE negativo
            SessaoClienteMissionLink sessaoTemp = new SessaoClienteMissionLink(msg.header.idMissao, endereco, porta);
            sessaoTemp.seqAtual = msg.header.seq;
            sessaoAtual = sessaoTemp;
        }
        
        // Enviar RESPONSE
        enviarResponse(disponivel);
    }
    
    /**
     * Processa fragmento MISSION.
     * Suporta tanto PayloadMissao direto (sem fragmentação) quanto FragmentoPayload.
     */
    private void processarMission(MensagemUDP msg) {
        if (sessaoAtual == null || sessaoAtual.idMissao != msg.header.idMissao) {
            System.err.println("[ClienteUDP] Fragmento recebido sem sessão ativa");
            return;
        }
        
        int seq = msg.header.seq;
        
        // Verificar se é PayloadMissao direto (sem fragmentação)
        if (msg.payload instanceof PayloadMissao) {
            System.out.println("[ClienteUDP] Missão recebida diretamente (sem fragmentação)");
            PayloadMissao payload = (PayloadMissao) msg.payload;
            
            // intervalos já vêm em segundos, converter para ms (com mínimo de 200ms)
            sessaoAtual.intervaloAtualizacao = Math.max(200, (int) (payload.intervaloAtualizacao * 1000));
            sessaoAtual.duracaoMissao = payload.duracaoMissao * 1000;
            sessaoAtual.seqAtual = seq;
            sessaoAtual.totalFragmentos = 1;
            
            System.out.println("[ClienteUDP] Missão recebida: " + payload);

            // Atualizar máquina de estados
            if (maquina != null) {
                maquina.receberMissao(payload);
            }
            
            // Enviar ACK de confirmação
            sessaoAtual.fragmentosPerdidos = new ArrayList<>();
            enviarAck();
            
            System.out.println("[ClienteUDP] SeqAtual após ACK: " + sessaoAtual.seqAtual + 
                             " (próximo PROGRESS usará seq=" + (sessaoAtual.seqAtual + 1) + ")");
            
            // Iniciar reportagem
            Thread t = new Thread(this::reportarMissao);
            t.setDaemon(true);
            t.start();
            return;
        }
        
        // Caso contrário, é FragmentoPayload (com fragmentação)
        // Atualizar total de fragmentos se necessário (primeira vez que recebemos um MISSION)
        if (sessaoAtual.totalFragmentos == 0 && msg.header.totalFragm >= 1) {
            sessaoAtual.totalFragmentos = msg.header.totalFragm;
            System.out.println("[ClienteUDP] Total de fragmentos atualizado: " + sessaoAtual.totalFragmentos);
        }
        
        System.out.println("[ClienteUDP] Fragmento recebido: seq=" + seq + "/" + (msg.header.totalFragm + 1));
        
        // Extrair FragmentoPayload
        if (!(msg.payload instanceof FragmentoPayload)) {
            System.err.println("[ClienteUDP] Payload não é FragmentoPayload nem PayloadMissao");
            return;
        }
        
        FragmentoPayload fragmento = (FragmentoPayload) msg.payload;
        
        if (fragmento.temDados()) {
            sessaoAtual.fragmentosRecebidos.put(seq, fragmento);
            // Manter o maior seq recebido
            if (seq > sessaoAtual.seqAtual) {
                sessaoAtual.seqAtual = seq;
            }
            
            // Agregar campos no serializador
            sessaoAtual.serializador.agregarCampos(fragmento);
        }
        
        // Verificar se recebemos todos os fragmentos
        int fragmentosEsperados = sessaoAtual.totalFragmentos;
        int fragmentosRecebidos = sessaoAtual.fragmentosRecebidos.size();
        
        System.out.println("[ClienteUDP] Progresso: " + fragmentosRecebidos + "/" + fragmentosEsperados);
        
        // Após receber alguns fragmentos, enviar ACK
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
                    System.out.println("[ClienteUDP] SeqAtual após ACK: " + sessaoAtual.seqAtual + 
                                     " (próximo PROGRESS usará seq=" + (sessaoAtual.seqAtual + 1) + ")");
                    Thread t = new Thread(this::reportarMissao);
                    t.setDaemon(true);
                    t.start();
                } else {
                    System.err.println("[ClienteUDP] Erro ao reconstruir missão");
                }
            } else {
                System.out.println("[ClienteUDP] Solicitando retransmissão de " + sessaoAtual.fragmentosPerdidos.size() + " fragmentos");
                enviarAck();
            }
        }
    }
    
    /**
     * Processa mensagem ACK (para PROGRESS e COMPLETED).
     * Valida que o ACK corresponde ao seq esperado.
     */
    private void processarAck(MensagemUDP msg) {
        if (sessaoAtual == null || !sessaoAtual.emExecucao || msg.header.idMissao != sessaoAtual.idMissao) {
            return;
        }
        
        int seqRecebido = msg.header.seq;
        
        // PRIORITY: Verificar se é ACK final (para COMPLETED/ERROR)
        if (msg.payload instanceof PayloadAck) {
            PayloadAck ack = (PayloadAck) msg.payload;
            if (ack.finalAck) {
                System.out.println("[ClienteUDP] ACK FINAL recebido (seq=" + seqRecebido + 
                                 ") - parar todas as retransmissões");
                sessaoAtual.ultimoSeqConfirmado = seqRecebido;
                sessaoAtual.aguardandoAck = false;
                return; // Parar imediatamente - não processar missing progress
            }
        }
        
        // Verificar se é o ACK que estamos à espera
        if (sessaoAtual.aguardandoAck && seqRecebido == sessaoAtual.seqAckEsperado) {
            System.out.println("[ClienteUDP] ACK válido recebido para seq=" + seqRecebido + 
                             " (sucesso=" + msg.header.flagSucesso + ")");
            sessaoAtual.ultimoSeqConfirmado = seqRecebido;
            sessaoAtual.aguardandoAck = false;

            // Se o ACK veio com progresso perdido, reenviar os PROGRESS
            if (msg.payload instanceof PayloadAck) {
                PayloadAck ack = (PayloadAck) msg.payload;
                if (ack.missing != null && ack.missing.length > 0) {
                    System.out.println("[ClienteUDP] Servidor pediu reenvio de PROGRESS perdidos: " + Arrays.toString(ack.missing));
                    for (int seq : ack.missing) {
                        reenviarProgress(seq);
                    }
                }
            }
        } else if (seqRecebido < sessaoAtual.seqAckEsperado) {
            // ACK antigo/duplicado - ignorar
            System.out.println("[ClienteUDP] ACK antigo ignorado (recebido=" + seqRecebido + 
                             ", esperado=" + sessaoAtual.seqAckEsperado + ")");
        } else {
            // ACK para seq futuro - não deveria acontecer, mas registar
            System.out.println("[ClienteUDP] ACK inesperado para seq=" + seqRecebido + 
                             " (esperado=" + sessaoAtual.seqAckEsperado + ")");
        }
    }

    /**
     * Reenvia PROGRESS para o seq especificado, usando os dados guardados.
     */
    private void reenviarProgress(int seq) {
        if (sessaoAtual == null || !sessaoAtual.emExecucao) return;

        PayloadProgresso progresso = sessaoAtual.progressosEnviados.get(seq);
        if (progresso == null) {
            System.out.println("[ClienteUDP] Não há progresso salvo para seq=" + seq + ", ignorando reenvio.");
            return;
        }

        MensagemUDP msg = criarMensagemBase(TipoMensagem.MSG_PROGRESS, seq, true);
        msg.payload = progresso;

        enviarParaNave(msg);
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
     * Reconstrói a missão a partir dos campos identificados.
     * Usa o DesserializadorUDP para reconstruir o payload.
     */
    private boolean reconstruirMissao() {
        try {
            // Verificar se missão está completa
            if (!sessaoAtual.serializador.missaoCompleta()) {
                System.err.println("[ClienteUDP] Missão incompleta - campos em falta");
                return false;
            }
            
            // Reconstruir payload usando o protocolo
            PayloadMissao payload = sessaoAtual.serializador.reconstruirMissao();

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
    
    // ==================== MÉTODOS DE CRIAÇÃO DE MENSAGENS ====================
    
    /**
     * Cria uma mensagem UDP base com campos comuns preenchidos.
     */
    private MensagemUDP criarMensagemBase(TipoMensagem tipo, int seq, boolean flagSucesso) {
        MensagemUDP msg = new MensagemUDP();
        msg.header.tipo = tipo;
        msg.header.idEmissor = idRover;
        msg.header.idRecetor = 0; // Nave-Mãe
        msg.header.idMissao = sessaoAtual.idMissao;
        msg.header.seq = seq;
        msg.header.totalFragm = 1;
        msg.header.flagSucesso = flagSucesso;
        return msg;
    }
    
    /**
     * Envia mensagem RESPONSE.
     */
    private void enviarResponse(boolean sucesso) {
        MensagemUDP msg = criarMensagemBase(TipoMensagem.MSG_RESPONSE, sessaoAtual.seqAtual, sucesso);
        msg.payload = null;
        
        enviarParaNave(msg);
        System.out.println("[ClienteUDP] RESPONSE enviado (sucesso=" + sucesso + ")");
    }
    
    /**
     * Envia mensagem ACK.
     */
    private void enviarAck() {
        boolean semPerdas = sessaoAtual.fragmentosPerdidos.isEmpty();
        MensagemUDP msg = criarMensagemBase(TipoMensagem.MSG_ACK, sessaoAtual.seqAtual, semPerdas);
        
        PayloadAck ack = new PayloadAck();
        ack.missingCount = sessaoAtual.fragmentosPerdidos.size();
        ack.missing = listaParaArray(sessaoAtual.fragmentosPerdidos);
        msg.payload = ack;
        
        enviarParaNave(msg);
        System.out.println("[ClienteUDP] ACK enviado (seq=" + sessaoAtual.seqAtual + 
                         ", faltam " + sessaoAtual.fragmentosPerdidos.size() + " fragmentos)");
    }
    
    // ==================== MÉTODOS DE COMUNICAÇÃO ====================
    
    /**
     * Envia mensagem UDP para a Nave-Mãe (usando sessão atual).
     */
    private void enviarParaNave(MensagemUDP msg) {
        enviarMensagem(msg, sessaoAtual.enderecoNave, sessaoAtual.portaNave);
    }
    
    /**
     * Envia mensagem UDP para endereço específico.
     */
    private void enviarMensagem(MensagemUDP msg, InetAddress endereco, int porta) {
        try {
            byte[] dados = sessaoAtual.serializador.serializarObjeto(msg);
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, endereco, porta);
            socket.send(pacote);
        } catch (IOException e) {
            System.err.println("[ClienteUDP] Erro ao enviar mensagem: " + e.getMessage());
        }
    }
    
    /**
     * Envia mensagem com retransmissão até receber ACK ou atingir máximo de tentativas.
     * @return true se ACK foi recebido, false se esgotou tentativas
     */
    private boolean enviarComRetry(MensagemUDP msg, int seqParaEnviar, int maxTentativas, String nomeMensagem) {
        sessaoAtual.aguardandoAck = true;
        sessaoAtual.seqAckEsperado = seqParaEnviar;
        
        int tentativas = 0;
        while (tentativas < maxTentativas && sessaoAtual != null && sessaoAtual.aguardandoAck && running) {
            enviarParaNave(msg);
            
            if (tentativas == 0) {
                System.out.println("[ClienteUDP] " + nomeMensagem + " enviado (seq=" + seqParaEnviar + ")");
            } else {
                System.out.println("[ClienteUDP] " + nomeMensagem + " retransmitido (seq=" + seqParaEnviar + 
                                 ", tentativa " + tentativas + ")");
            }
            
            if (aguardarAckParaSeq(seqParaEnviar, TIMEOUT_MS)) {
                System.out.println("[ClienteUDP] ACK recebido para " + nomeMensagem + " seq=" + seqParaEnviar);
                return true;
            }
            
            tentativas++;
            if (tentativas < maxTentativas) {
                System.out.println("[ClienteUDP] Timeout aguardando ACK para " + nomeMensagem + " seq=" + seqParaEnviar +
                                    " - Tentando novamente...");
            }
        }
        
        if (sessaoAtual != null && sessaoAtual.aguardandoAck) {
            System.out.println("[ClienteUDP] AVISO: Máximo de retransmissões atingido para " + nomeMensagem + " seq=" + seqParaEnviar);
            sessaoAtual.aguardandoAck = false;
        }
        return false;
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
    
    /**
     * Inicia a reportagem da missão
     * TODO: ver se o progresso também deverá ter em conta fragmentação porque por exemplo se o rover tiver que enviar imagens pode utltrapassar tamanho do pacote
     */
    private void reportarMissao() {
        // Atualizar sessão 
        if (sessaoAtual == null) return;
        int missionId = sessaoAtual.idMissao;
        sessaoAtual.emExecucao = true;
        sessaoAtual.inicioMissao = System.currentTimeMillis();
        
        // Limpar dados (não mais necessários)
        sessaoAtual.fragmentosRecebidos = null;
        sessaoAtual.serializador.limpar();
        
        // Iniciar envio de progresso
        System.out.println("[ClienteUDP] Iniciada a execução da missão " + missionId);
        while (running && sessaoAtual != null && sessaoAtual.emExecucao) {
            try {
                Thread.sleep(sessaoAtual.intervaloAtualizacao);
                
                if (sessaoAtual == null || !sessaoAtual.emExecucao) {
                    System.out.println("[ClienteUDP] Sessão terminada - parando reportagem");
                    break;
                }

                // Atualizar lógica da missão antes de reportar
                if (maquina != null) {
                    maquina.atualizar();
                }

                float progressoPerc = maquina != null ? maquina.getContexto().getProgresso() : 0.0f;
                
                // Verificar condições de erro que impedem continuar a missão
                PayloadErro.CodigoErro erroDetectado = verificarCondicoesErro();
                if (erroDetectado != null) {
                    System.out.println("[ClienteUDP] Erro detectado: " + erroDetectado.descricaoPadrao);
                    enviarErro(erroDetectado, null);
                    return; // Termina a reportagem
                }
                
                long tempoDecorrido = System.currentTimeMillis() - sessaoAtual.inicioMissao;
                
                // Enviar PROGRESS
                if (progressoPerc >= 100.0f) {
                    // ← MISSÃO CONCLUÍDA
                    System.out.println("[ClienteUDP] Progresso atingiu 100% - enviando COMPLETED");
                    enviarCompleted(true);
                    System.out.println("[ClienteUDP] Reportagem terminada com sucesso");
                    return; // ← PARAR AQUI
                } else {
                    // Enviar PROGRESS normal
                    enviarProgress(progressoPerc, tempoDecorrido);
                }
                
            } catch (InterruptedException e) {
                break;
            }
        }
        
        System.out.println("[ClienteUDP] Execução da missão " + missionId + " terminada");
    }

    /**
     * Verifica condições de erro que impedem o rover de continuar a missão.
     * @return Código de erro se houver problema, null se tudo OK
     */
    private PayloadErro.CodigoErro verificarCondicoesErro() {
        if (maquina == null || maquina.getContexto() == null) {
            return null;
        }
        
        ContextoRover ctx = maquina.getContexto();
        
        // Bateria crítica (<10%) - não consegue terminar
        if (ctx.bateria < 10.0f) {
            return PayloadErro.CodigoErro.ERRO_BATERIA_CRITICA;
        }
        
        // Estado de falha na máquina de estados
        if (ctx.getEstado() == EstadoRover.ESTADO_FALHA) {
            return PayloadErro.CodigoErro.ERRO_HARDWARE;
        }
        
        return null; // Sem erros
    }

    /**
     * Envia mensagem de ERRO para a Nave-Mãe indicando que não consegue completar a missão.
     * Implementa retransmissão robusta como o COMPLETED.
     */
    private void enviarErro(PayloadErro.CodigoErro codigoErro, String descricaoExtra) {
        if (sessaoAtual == null || !sessaoAtual.emExecucao) 
            return;
        
        sessaoAtual.emExecucao = false;

        ContextoRover ctx = maquina.getContexto();
        int seqParaEnviar = ++sessaoAtual.seqAtual;
        
        MensagemUDP msg = criarMensagemBase(TipoMensagem.MSG_ERROR, seqParaEnviar, false);
        msg.payload = new PayloadErro(
            sessaoAtual.idMissao,
            codigoErro,
            descricaoExtra,
            ctx.progressoMissao,
            ctx.bateria,
            ctx.posicaoX,
            ctx.posicaoY
        );
        
        int maxTentativasErro = MAX_RETRIES * 3; // Mais tentativas para mensagem crítica
        boolean confirmado = enviarComRetry(msg, seqParaEnviar, maxTentativasErro, "ERRO (" + codigoErro.codigo + ")");
        
        if (!confirmado) {
            System.err.println("[ClienteUDP] AVISO: ERRO não foi confirmado após " + maxTentativasErro + " tentativas");
        }

        // Só actualizar estado DEPOIS de esgotar todas as tentativas
        finalizarMissaoComEstado(codigoErro.descricaoPadrao, EstadoRover.ESTADO_FALHA, EventoRelevante.EVENTO_ERRO_MISSAO);
    }

    
    /**
     * Envia mensagem PROGRESS para a Nave-Mãe.
     * Implementa retransmissão robusta com validação de seq do ACK.
     */
    private void enviarProgress(float progressoPerc, long tempoDecorrido) {
        if (sessaoAtual == null || !sessaoAtual.emExecucao) return;
        
        int seqParaEnviar = ++sessaoAtual.seqAtual;
        
        MensagemUDP msg = criarMensagemBase(TipoMensagem.MSG_PROGRESS, seqParaEnviar, true);
        
        PayloadProgresso progresso = new PayloadProgresso();
        progresso.idMissao = sessaoAtual.idMissao;
        progresso.tempoDecorrido = tempoDecorrido / 1000; // converter ms para segundos
        progresso.progressoPercentagem = progressoPerc;
        msg.payload = progresso;

        // Registrar progresso enviado para possível retransmissão
        sessaoAtual.progressosEnviados.put(seqParaEnviar, progresso);
        
        enviarComRetry(msg, seqParaEnviar, MAX_RETRIES, 
                      "PROGRESS (" + String.format("%.2f", progressoPerc) + "%)");
    }
    
    /**
     * Envia mensagem COMPLETED para a Nave-Mãe.
     * Implementa retransmissão robusta com validação de seq do ACK.
     */
    private void enviarCompleted(boolean sucesso) {
        if (sessaoAtual == null || !sessaoAtual.emExecucao) 
            return;
        
        sessaoAtual.emExecucao = false;
        int seqParaEnviar = ++sessaoAtual.seqAtual;
        
        MensagemUDP msg = criarMensagemBase(TipoMensagem.MSG_COMPLETED, seqParaEnviar, sucesso);
        msg.payload = null;
        
        // Aumentar drasticamente tentativas para mensagem crítica
        int maxTentativasCompleted = MAX_RETRIES * 3; // ~45 segundos de tentativas
        boolean confirmado = enviarComRetry(msg, seqParaEnviar, maxTentativasCompleted, "COMPLETED (sucesso=" + sucesso + ")");
        
        if (!confirmado) {
            System.err.println("[ClienteUDP] AVISO: COMPLETED não foi confirmado após " + maxTentativasCompleted + " tentativas");
        }

        // Só actualizar estado DEPOIS de esgotar todas as tentativas
        finalizarMissaoComEstado("concluída", EstadoRover.ESTADO_CONCLUIDO, EventoRelevante.EVENTO_FIM_MISSAO);
    }
    
    /**
     * Finaliza a missão atual e atualiza a máquina de estados.
     */
    private void finalizarMissaoComEstado(String descricao, EstadoRover novoEstado, EventoRelevante evento) {
        if (sessaoAtual == null)
            return;

        int missionId = sessaoAtual.idMissao;
        boolean semConfirmacao = sessaoAtual.aguardandoAck;
        
        sessaoAtual.emExecucao = false;
        sessaoAtual = null;
        
        System.out.println("[ClienteUDP] Missão " + missionId + " " + descricao + 
                         (semConfirmacao ? " (sem confirmação ACK)" : ""));
        
        if (maquina != null) {
            // Transicionar para o novo estado
            maquina.getContexto().transicionarEstado(novoEstado);
            maquina.getContexto().eventoPendente = evento;
            
            maquina.atualizar(); // atualização imediata
            System.out.println("[ClienteUDP] Rover " + maquina.getContexto().idRover + 
            " agora em estado " + novoEstado + 
            " (temMissao=" + maquina.getContexto().temMissao + ")");
        }
    }

    /**
     * Aguarda ACK para um seq específico até timeoutMs.
     * Retorna true se ACK válido foi recebido, false se timeout.
     */
    private boolean aguardarAckParaSeq(int seqEsperado, long timeoutMs) {
        long inicio = System.currentTimeMillis();
        while (System.currentTimeMillis() - inicio < timeoutMs && running) {
            if (sessaoAtual == null || !sessaoAtual.emExecucao) {
                return false;
            }
            
            // Verificar se o ACK para este seq foi confirmado
            if (!sessaoAtual.aguardandoAck && sessaoAtual.ultimoSeqConfirmado >= seqEsperado) {
                return true;
            }
            
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return false; // Timeout
    }
    
    public void parar() {
        running = false;
    }
    
}