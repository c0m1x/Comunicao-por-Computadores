package rover;

import java.time.Instant;

import lib.Mensagens;
import lib.Mensagens.PayloadMissao;

import java.util.concurrent.locks.ReentrantLock;


//TODO: limpar maquina estados, diria para separar a classe contextorover da classe maquinaestados

/**
 * Contém RoverContext e logica da máquina de estados.
 * Tradução direta de maquina_estados.c.
 */
public class MaquinaEstados {

    public enum EstadoRover {
        ESTADO_INICIAL,
        ESTADO_DISPONIVEL,
        ESTADO_RECEBENDO_MISSAO,
        ESTADO_EM_MISSAO,
        ESTADO_CONCLUIDO,
        ESTADO_FALHA
    }

    public enum EventoRelevante {
        EVENTO_NENHUM,
        EVENTO_INICIO_MISSAO,
        EVENTO_FIM_MISSAO,
        EVENTO_BATERIA_BAIXA,
        EVENTO_MUDANCA_ESTADO,
        EVENTO_ERRO,
        EVENTO_CHECKPOINT_MISSAO
    }

    /**
     * Contexto interno do Rover — contém lógica da máquina de estados e
     * campos detalhados usados internamente pelo rover.
     */
    public static class ContextoRover {

        // Identificadores
        public int idRover;
        public int idNave = 1;
        public volatile int socketTcp = -1; // opcional se integrares com Java sockets
        // Estado
        private EstadoRover estadoAtual;
        private EstadoRover estadoAnterior;
        public volatile boolean ativo;
        public volatile int idMissaoAtual = -1;
        public Mensagens.PayloadMissao missaoAtual;
        public volatile boolean temMissao;
        public volatile float progressoMissao;
        public volatile long timestampInicioMissaoEpoch;
        // Telemetria / posição
        public volatile float posicaoX;
        public volatile float posicaoY;
        public volatile float bateria;
        public volatile float velocidade;
        public volatile String estadoOperacional = "INITIAL";
        // timing / eventos
        public volatile long ultimoHelloEpoch = 0;
        public volatile long ultimoEnvioTelemetriaEpoch = 0;
        public volatile EventoRelevante eventoPendente = EventoRelevante.EVENTO_NENHUM;
        public volatile EventoRelevante ultimoEvento = EventoRelevante.EVENTO_NENHUM;

        private final ReentrantLock lock = new ReentrantLock();

        // constantes (ajustar conforme necessário)
        public static final int INTERVALO_KEEPALIVE = 10;
        public static final int INTERVALO_TELEMETRIA_BASE = 5;
        public static final float VELOCIDADE_ROVER = 2.0f;

        public ContextoRover(int id, float posX, float posY) {
            this.idRover = id;
            this.posicaoX = posX;
            this.posicaoY = posY;
            this.bateria = 100.0f;
            this.velocidade = 0.0f;
            this.estadoAtual = EstadoRover.ESTADO_INICIAL;
            this.estadoAnterior = EstadoRover.ESTADO_INICIAL;
            this.ativo = true;
            this.missaoAtual = new Mensagens.PayloadMissao();
        }

        // Métodos thread-safe
        public EstadoRover getEstado() {
            lock.lock();
            try {
                return estadoAtual;
            } finally {
                lock.unlock();
            }
        }

        private void transicionarEstado(EstadoRover novo) {
            lock.lock();
            try {
                if (estadoAtual != novo) {
                    estadoAnterior = estadoAtual;
                    estadoAtual = novo;
                    eventoPendente = EventoRelevante.EVENTO_MUDANCA_ESTADO;
                }
            } finally {
                lock.unlock();
            }
        }

        public void atualizarEstadoOperacional(String estado) {
            lock.lock();
            try {
                this.estadoOperacional = estado;
            } finally {
                lock.unlock();
            }
        }

        // chamada periódica para atualizar máquina de estados
        public void updateState() {
            EstadoRover e = getEstado();
            switch (e) {
                case ESTADO_INICIAL:
                    atualizarEstadoOperacional("ACTIVE");
                    transicionarEstado(EstadoRover.ESTADO_DISPONIVEL);
                    break;
                case ESTADO_DISPONIVEL:
                    // idle
                    break;
                case ESTADO_RECEBENDO_MISSAO:
                    // logic de fragmentos se necessário
                    break;
                case ESTADO_EM_MISSAO:
                    executarPassoMissao();
                    if (missaoConcluida()) {
                        atualizarEstadoOperacional("SUCCESS");
                        lock.lock();
                        try {
                            eventoPendente = EventoRelevante.EVENTO_FIM_MISSAO;
                        } finally {
                            lock.unlock();
                        }
                        transicionarEstado(EstadoRover.ESTADO_CONCLUIDO);
                    }
                    break;
                case ESTADO_CONCLUIDO:
                    lock.lock();
                    try {
                        temMissao = false;
                        idMissaoAtual = -1;
                        progressoMissao = 0.0f;
                    } finally {
                        lock.unlock();
                    }
                    atualizarEstadoOperacional("ACTIVE");
                    transicionarEstado(EstadoRover.ESTADO_DISPONIVEL);
                    break;
                case ESTADO_FALHA:
                    // tenta recuperar
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    atualizarEstadoOperacional("ACTIVE");
                    transicionarEstado(EstadoRover.ESTADO_DISPONIVEL);
                    break;
            }
        }

        private void executarPassoMissao() {
            if (!temMissao || missaoAtual == null)
                return;

            // centro da área de missão
            float destinoX = (missaoAtual.x1 + missaoAtual.x2) / 2.0f;
            float destinoY = (missaoAtual.y1 + missaoAtual.y2) / 2.0f;

            float dx = destinoX - posicaoX;
            float dy = destinoY - posicaoY;
            float distancia = (float) Math.sqrt(dx * dx + dy * dy);

            lock.lock();
            try {
                if (distancia > 0.5f) {
                    float passo = 0.5f; // metros por tick (ajustável)
                    posicaoX += (dx / distancia) * passo;
                    posicaoY += (dy / distancia) * passo;
                    velocidade = VELOCIDADE_ROVER;
                } else {
                    velocidade = 0.0f;
                }

                bateria -= 0.1f;
                if (bateria < 0.0f)
                    bateria = 0.0f;

                if (bateria < 20.0f && ultimoEvento != EventoRelevante.EVENTO_BATERIA_BAIXA) {
                    eventoPendente = EventoRelevante.EVENTO_BATERIA_BAIXA;
                }

                long agora = Instant.now().getEpochSecond();
                double decorrido = (double) (agora - timestampInicioMissaoEpoch);
                if (missaoAtual instanceof Mensagens.PayloadMissao) {
                    // backward-compat: se existir um campo duracaoMissaoSecs, usá-lo
                    try {
                        java.lang.reflect.Field f = missaoAtual.getClass().getDeclaredField("duracaoMissaoSecs");
                        f.setAccessible(true);
                        long secs = f.getLong(missaoAtual);
                        if (secs > 0) {
                            progressoMissao = (float) ((decorrido / (double) secs) * 100.0);
                            if (progressoMissao > 100.0f)
                                progressoMissao = 100.0f;
                        }
                    } catch (NoSuchFieldException | IllegalAccessException ex) {
                        // campo não existe — ignora e não calcula progresso por tempo
                    }
                }

                // checkpoints a cada 25%
                int checkpoint = (int) (progressoMissao / 25.0f);
                if (checkpoint > 0) {
                    eventoPendente = EventoRelevante.EVENTO_CHECKPOINT_MISSAO;
                }
            } finally {
                lock.unlock();
            }
        }

        private boolean missaoConcluida() {
            lock.lock();
            try {
                return progressoMissao >= 100.0f;
            } finally {
                lock.unlock();
            }
        }

        public boolean deveEnviarHello() {
            lock.lock();
            try {
                long agora = Instant.now().getEpochSecond();
                if ((agora - ultimoHelloEpoch) >= INTERVALO_KEEPALIVE) {
                    ultimoHelloEpoch = agora;
                    return true;
                }
                return false;
            } finally {
                lock.unlock();
            }
        }

        public boolean deveEnviarTelemetria() {
            lock.lock();
            try {
                long agora = Instant.now().getEpochSecond();
                boolean deve = false;
                if ((agora - ultimoEnvioTelemetriaEpoch) >= INTERVALO_TELEMETRIA_BASE)
                    deve = true;
                if (eventoPendente != EventoRelevante.EVENTO_NENHUM) {
                    deve = true;
                    ultimoEvento = eventoPendente;
                    eventoPendente = EventoRelevante.EVENTO_NENHUM;
                }
                return deve;
            } finally {
                lock.unlock();
            }
        }

        public void telemetriaEnviada() {
            lock.lock();
            try {
                ultimoEnvioTelemetriaEpoch = Instant.now().getEpochSecond();
            } finally {
                lock.unlock();
            }
        }

        public int getMissaoId() {
            lock.lock();
            try {
                return idMissaoAtual;
            } finally {
                lock.unlock();
            }
        }

        public float getProgresso() {
            lock.lock();
            try {
                return progressoMissao;
            } finally {
                lock.unlock();
            }
        }

        // Notifica missão recebida (similar a rover_missao_recebida)
        public void receberMissao(Mensagens.PayloadMissao missao, int idMissao) {
            lock.lock();
            try {
                this.missaoAtual = missao;
                this.idMissaoAtual = idMissao;
                this.temMissao = true;
                this.progressoMissao = 0.0f;
                this.timestampInicioMissaoEpoch = Instant.now().getEpochSecond();
                this.eventoPendente = EventoRelevante.EVENTO_INICIO_MISSAO;
            } finally {
                lock.unlock();
            }
            atualizarEstadoOperacional("IN_MISSION");
            transicionarEstado(EstadoRover.ESTADO_EM_MISSAO);
        }

        // Preenche payload de telemetria atual
        public Mensagens.PayloadTelemetria getTelemetria() {
            Mensagens.PayloadTelemetria p = new Mensagens.PayloadTelemetria();
            lock.lock();
            try {
                p.posicaoX = posicaoX;
                p.posicaoY = posicaoY;
                p.estadoOperacional = estadoOperacional;
                p.bateria = bateria;
                p.velocidade = velocidade;
            } finally {
                lock.unlock();
            }
            return p;
        }

    }


    private ContextoRover contexto;
    
    public MaquinaEstados(int idRover, float posX, float posY) {
        this.contexto = new ContextoRover(idRover, posX, posY);
    }
    
    /**
     * Retorna o estado atual do rover.
     */
    public EstadoRover getEstadoAtual() {
        return contexto.estadoAtual;
    }
    
    /**
     * Processa recepção de missão via UDP.
     */
    public void receberMissao(PayloadMissao missao) {
        if (missao == null) {
            System.err.println("[MaquinaEstados] Missão nula recebida");
            return;
        }
        
        System.out.println("[MaquinaEstados] Rover " + contexto.idRover + 
                         " recebeu missão: " + missao);
        
        contexto.receberMissao(missao, missao.idMissao);
    }
    
    /**
     * Retorna o contexto do rover.
     */
    public ContextoRover getContexto() {
        return contexto;
    }
}


