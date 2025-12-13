package rover;

import java.time.Instant;

import lib.Rover.EstadoRover;
import lib.mensagens.payloads.PayloadMissao;
import lib.mensagens.payloads.PayloadTelemetria;

    /**
     * Contexto interno do Rover — contém lógica da máquina de estados e
     * campos detalhados usados internamente pelo rover.
     */
    public class ContextoRover {

        // Identificadores
        public int idRover;
        public int idNave;

        // Estado
        private EstadoRover estadoAtual;
        private EstadoRover estadoAnterior;
        public volatile boolean ativo;
        public volatile int idMissaoAtual = -1;
        public PayloadMissao missaoAtual;
        public volatile boolean temMissao;
        public volatile float progressoMissao;
        public volatile long timestampInicioMissao;

        // Telemetria / posição
        public volatile float posicaoX;
        public volatile float posicaoY;
        public volatile float bateria;
        public volatile float velocidade;
        
        // timing / eventos
        public volatile long ultimoEnvioTelemetria = 0;
        public volatile EventoRelevante eventoPendente = EventoRelevante.EVENTO_NENHUM; //serve para mandar telemetria quando algum evento acontece além dos intervalos normais
        public volatile EventoRelevante ultimoEvento = EventoRelevante.EVENTO_NENHUM;
        public volatile int ultimoCheckpoint = 0;

        public volatile long timestampEntradaFalha = 0;
        public volatile long timestampInicioRecepcao = 0;

        // constantes (ajustar conforme necessário)
        public static final int INTERVALO_KEEPALIVE = 10;
        public static final int INTERVALO_TELEMETRIA_BASE = 10;
        public static final float VELOCIDADE_ROVER = 2.0f;

        public ContextoRover(int id, float posX, float posY) {
            this.idRover = id;
            this.idNave = 1; // assumindo nave com ID 1
            this.posicaoX = posX;
            this.posicaoY = posY;
            this.bateria = 100.0f;
            this.velocidade = 0.0f;
            this.estadoAtual = EstadoRover.ESTADO_DISPONIVEL;
            this.estadoAnterior = EstadoRover.ESTADO_DISPONIVEL;
            this.ativo = true;
            this.missaoAtual = new PayloadMissao();
        }

        public EstadoRover getEstado() {
            return estadoAtual;
        }

        public boolean isDisponivel() {
            return estadoAtual == EstadoRover.ESTADO_DISPONIVEL;
        }

        public void transicionarEstado(EstadoRover novo) {
            if (estadoAtual != novo) {
                estadoAnterior = estadoAtual;
                estadoAtual = novo;
                eventoPendente = EventoRelevante.EVENTO_MUDANCA_ESTADO;
            }
        }

        public boolean deveEnviarTelemetria() {
                long agora = Instant.now().getEpochSecond();
                boolean deve = false;
                if ((agora - ultimoEnvioTelemetria) >= INTERVALO_TELEMETRIA_BASE)
                    deve = true;
                if (eventoPendente != EventoRelevante.EVENTO_NENHUM) {
                    deve = true;
                    ultimoEvento = eventoPendente;
                    eventoPendente = EventoRelevante.EVENTO_NENHUM;
                }
                return deve;
        }

        public void telemetriaEnviada() {
                ultimoEnvioTelemetria = Instant.now().getEpochSecond();
        }

        public int getMissaoId() {
                return idMissaoAtual;
        }

        public float getProgresso() {
                return progressoMissao;
        }

        // Notifica missão recebida (dados); a transição fica a cargo da máquina de estados
        public void iniciarMissao(PayloadMissao missao) {
                if (missao != null) {
                    if (missao.duracaoMissao <= 0) missao.duracaoMissao = 60;
                    if (missao.intervaloAtualizacao <= 0) missao.intervaloAtualizacao = 2;
                }
                this.missaoAtual = missao;
                this.idMissaoAtual = (missao != null ? missao.idMissao : -1);
                this.temMissao = (missao != null);
                this.progressoMissao = 0.0f;
                this.timestampInicioMissao = Instant.now().getEpochSecond();
                this.ultimoCheckpoint = 0;
                this.eventoPendente = EventoRelevante.EVENTO_INICIO_MISSAO;
        }

        // Atualiza dinâmica (movimento, bateria, eventos) e progresso temporal
        public void atualizarDuranteMissao() {
                if (!temMissao || missaoAtual == null) return;

                float destinoX = (missaoAtual.x1 + missaoAtual.x2) / 2.0f;
                float destinoY = (missaoAtual.y1 + missaoAtual.y2) / 2.0f;

                float dx = destinoX - posicaoX;
                float dy = destinoY - posicaoY;
                float distancia = (float) Math.sqrt(dx * dx + dy * dy);
                
                if (distancia > 0.1f) {
                    // Normalizar vetor direção
                    float dirX = dx / distancia;
                    float dirY = dy / distancia;
                    
                    // Calcular passo baseado na velocidade e deltaTime
                    float passo = Math.min(VELOCIDADE_ROVER, distancia); // Não ultrapassar o destino
                    
                    // Atualizar posição
                    posicaoX += dirX * passo;
                    posicaoY += dirY * passo;
                    velocidade = VELOCIDADE_ROVER;
                } else {
                    // Chegou ao destino: parar
                    posicaoX = destinoX;
                    posicaoY = destinoY;
                    velocidade = 0.0f;
                }

                // Descarrega mais quando em movimento, menos quando parado
                bateria = Math.max(0.0f, bateria - (velocidade > 0.0f ? 0.05f : 0.01f));

                long agora = Instant.now().getEpochSecond();
                long decorrido = agora - timestampInicioMissao;
                if (missaoAtual.duracaoMissao > 0) {
                    progressoMissao = (float) (((double) decorrido / (double) missaoAtual.duracaoMissao) * 100.0);
                    if (progressoMissao > 100.0f) progressoMissao = 100.0f;
                }

                int checkpointAtual = (int) (progressoMissao / 25.0f);
                if (checkpointAtual > ultimoCheckpoint && checkpointAtual <= 3) {
                    ultimoCheckpoint = checkpointAtual;
                    eventoPendente = EventoRelevante.EVENTO_CHECKPOINT_MISSAO;
                    System.out.println("[ContextoRover]  Checkpoint: " + (checkpointAtual * 25) + "%");
                }
        }

        public void concluirMissao() {
            temMissao = false;
            idMissaoAtual = -1;
            progressoMissao = 0.0f;
            missaoAtual = null;
            velocidade = 0.0f;
            ultimoCheckpoint = 0;

            System.out.println("[ContextoRover] Rover #" + idRover + " agora disponível");
        }

        // Preenche payload de telemetria atual
        public PayloadTelemetria getTelemetria() {
            PayloadTelemetria p = new PayloadTelemetria();
                p.posicaoX = posicaoX;
                p.posicaoY = posicaoY;
                p.estadoOperacional = estadoAtual;
                p.bateria = bateria;
                p.velocidade = velocidade;

            return p;
        }

    }