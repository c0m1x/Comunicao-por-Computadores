package rover;

import lib.mensagens.payloads.*;

import java.time.Instant;

import lib.Rover.EstadoRover;
import rover.EventoRelevante;


    /**
     * Contexto interno do Rover — contém lógica da máquina de estados e
     * campos detalhados usados internamente pelo rover.
     */
    public class ContextoRover {

        // Identificadores
        public int idRover;
        public int idNave = 1; // assumindo nave com ID 0

        // Estado
        private EstadoRover estadoAtual;
        private EstadoRover estadoAnterior;
        public volatile boolean ativo;
        public volatile int idMissaoAtual = -1;
        public PayloadMissao missaoAtual;
        public volatile boolean temMissao;
        public volatile float progressoMissao;
        public volatile long timestampInicioMissaoEpoch;

        // Telemetria / posição
        public volatile float posicaoX;
        public volatile float posicaoY;
        public volatile float bateria;
        public volatile float velocidade;
        
        // timing / eventos
        public volatile long ultimoHelloEpoch = 0;
        public volatile long ultimoEnvioTelemetriaEpoch = 0;
        public volatile EventoRelevante eventoPendente = EventoRelevante.EVENTO_NENHUM;
        public volatile EventoRelevante ultimoEvento = EventoRelevante.EVENTO_NENHUM;

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
            this.estadoAtual = EstadoRover.ESTADO_DISPONIVEL;
            this.estadoAnterior = EstadoRover.ESTADO_DISPONIVEL;
            this.ativo = true;
            this.missaoAtual = new PayloadMissao();
        }

        public EstadoRover getEstado() {
            return estadoAtual;
        }
  

        public void transicionarEstado(EstadoRover novo) {
            if (estadoAtual != novo) {
                estadoAnterior = estadoAtual;
                estadoAtual = novo;
                eventoPendente = EventoRelevante.EVENTO_MUDANCA_ESTADO;
            }
        }

        public boolean deveEnviarHello() {
                long agora = Instant.now().getEpochSecond();
                if ((agora - ultimoHelloEpoch) >= INTERVALO_KEEPALIVE) {
                    ultimoHelloEpoch = agora;
                    return true;
                }
                return false;
        }

        public boolean deveEnviarTelemetria() {
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
        }

        public void telemetriaEnviada() {
                ultimoEnvioTelemetriaEpoch = Instant.now().getEpochSecond();
        }

        public int getMissaoId() {
                return idMissaoAtual;
        }

        public float getProgresso() {
                return progressoMissao;
        }

        // Notifica missão recebida (dados); a transição fica a cargo da máquina de estados
        public void receberMissao(PayloadMissao missao, int idMissao) {
                // Garantir valores mínimos razoáveis todo:: tirar daqui e meter em missao
                if (missao != null) {
                    if (missao.duracaoMissao <= 0) {
                        missao.duracaoMissao = 60; // 60s por omissão
                    }
                    if (missao.intervaloAtualizacao <= 0) {
                        missao.intervaloAtualizacao = 2; // 2s por omissão
                    }
                }
                this.missaoAtual = missao;
                this.idMissaoAtual = idMissao;
                this.temMissao = true;
                this.progressoMissao = 0.0f;
                this.timestampInicioMissaoEpoch = Instant.now().getEpochSecond();
                this.eventoPendente = EventoRelevante.EVENTO_INICIO_MISSAO;
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