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

//TODO: atualizações da maquina de estados, porque quando termina a missao tem de voltar a disponivel, e como a maquina nao etsa a ser atualizada, o servidor depois acha sempre que o rover nao esta disponivel
//mas também nao pode atualizar antes de enviar o completed, porque senao o servidor pensa que o rover ja esta disponivel e manda outra missao antes de enviar o completed da missao anterior
//a maquina talvez tambem devesse ser atualizada sempre em intervalos de tempo para detetar os erros (bateria fraca e afins) 


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
     */
    private void processarMission(MensagemUDP msg) {
        if (sessaoAtual == null || sessaoAtual.idMissao != msg.header.idMissao) {
            System.err.println("[ClienteUDP] Fragmento recebido sem sessão ativa");
            return;
        }
        
        int seq = msg.header.seq;
        
        // Atualizar total de fragmentos se necessário (primeira vez que recebemos um MISSION)
        if (sessaoAtual.totalFragmentos == 0 && msg.header.totalFragm >= 1) {
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
            // Manter o maior seq recebido (mesmo com retransmissões, para evitar regressões)
            if (seq > sessaoAtual.seqAtual) {
                sessaoAtual.seqAtual = seq;
            }
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
                    // Guardar o seq do ACK para que o primeiro PROGRESS use seq+1
                    // O servidor guarda ultimoSeq = seqAtual do ACK, e espera PROGRESS com ultimoSeq+1
                    System.out.println("[ClienteUDP] SeqAtual após ACK: " + sessaoAtual.seqAtual + 
                                     " (próximo PROGRESS usará seq=" + (sessaoAtual.seqAtual + 1) + ")");
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
     * Valida que o ACK corresponde ao seq esperado.
     */
    private void processarAck(MensagemUDP msg) {
        if (sessaoAtual == null || !sessaoAtual.emExecucao || msg.header.idMissao != sessaoAtual.idMissao) {
            return;
        }
        
        int seqRecebido = msg.header.seq;
        
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
            byte[] dados = serializarObjeto(msg);
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
    
    // ==================== MÉTODOS DE SERIALIZAÇÃO ====================
    
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
                
                // Atualizar lógica da missão antes de reportar
                if (maquina != null) {
                    maquina.atualizar();
                }
                
                // Verificar condições de erro que impedem continuar a missão
                PayloadErro.CodigoErro erroDetectado = verificarCondicoesErro();
                if (erroDetectado != null) {
                    System.out.println("[ClienteUDP] Erro detectado: " + erroDetectado.descricaoPadrao);
                    enviarErro(erroDetectado, null);
                    return; // Termina a reportagem
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
        if (sessaoAtual == null || !sessaoAtual.emExecucao) return;
        
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
        
        int maxTentativasErro = MAX_RETRIES + 2; // Mais tentativas para mensagem crítica
        enviarComRetry(msg, seqParaEnviar, maxTentativasErro, "ERRO (" + codigoErro.codigo + ")");
        
        // Finalizar sessão (missão falhou)
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
     * TODO: REVER - por vezes no CORE o COMPLETED nunca é recebido na nave mãe
     */
    private void enviarCompleted(boolean sucesso) {
        if (sessaoAtual == null || !sessaoAtual.emExecucao) return;
        
        int seqParaEnviar = ++sessaoAtual.seqAtual;
        
        MensagemUDP msg = criarMensagemBase(TipoMensagem.MSG_COMPLETED, seqParaEnviar, sucesso);
        msg.payload = null; // COMPLETED não precisa payload
        
        int maxTentativasCompleted = MAX_RETRIES + 2; // Mais tentativas para mensagem crítica
        enviarComRetry(msg, seqParaEnviar, maxTentativasCompleted, "COMPLETED (sucesso=" + sucesso + ")");
        
        // Finalizar sessão
        finalizarMissaoComEstado("concluída", EstadoRover.ESTADO_CONCLUIDO, EventoRelevante.EVENTO_FIM_MISSAO);
    }
    
    /**
     * Finaliza a missão atual e atualiza a máquina de estados.
     */
    private void finalizarMissaoComEstado(String descricao, EstadoRover novoEstado, EventoRelevante evento) {
        int missionId = sessaoAtual.idMissao;
        boolean semConfirmacao = sessaoAtual.aguardandoAck;
        
        sessaoAtual.emExecucao = false;
        sessaoAtual = null;
        
        System.out.println("[ClienteUDP] Missão " + missionId + " " + descricao + 
                         (semConfirmacao ? " (sem confirmação ACK)" : ""));
        
        if (maquina != null) {
            // Limpar dados da missão no contexto (temMissao, idMissaoAtual, etc.)
            maquina.getContexto().concluirMissao();
            // Transicionar para o novo estado
            maquina.getContexto().transicionarEstado(novoEstado);
            maquina.getContexto().eventoPendente = evento;
            
            System.out.println("[ClienteUDP] Rover " + maquina.getContexto().idRover + 
                             " agora em estado " + novoEstado + 
                             " (temMissao=" + maquina.getContexto().temMissao + ")");

            maquina.atualizar(); // atualização imediata
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