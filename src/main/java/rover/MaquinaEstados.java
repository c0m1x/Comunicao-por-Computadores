package rover;

import java.time.Instant;

import lib.mensagens.payloads.*;
import lib.Rover.EstadoRover;

/**
 * Contém ContextoRover e a lógica da máquina de estados.
 */
public class MaquinaEstados {

    private final ContextoRover contexto;

    public MaquinaEstados(int idRover, float posX, float posY) {
        this.contexto = new ContextoRover(idRover, posX, posY);
    }

    // Consulta de estado atual
    public EstadoRover getEstadoAtual() {
        return contexto.getEstado();
    }

    // Passo da máquina de estados (chamada periódica)
    public void atualizar() {
        EstadoRover e = getEstadoAtual();
        switch (e) {
            case ESTADO_INICIAL:
                contexto.transicionarEstado(EstadoRover.ESTADO_DISPONIVEL);
                break;
            case ESTADO_DISPONIVEL:
                break;
            case ESTADO_RECEBENDO_MISSAO:
                break;
            case ESTADO_EM_MISSAO:
                executarPassoMissao();
                if (missaoConcluida()) {
                    contexto.transicionarEstado(EstadoRover.ESTADO_CONCLUIDO);
                    contexto.eventoPendente = EventoRelevante.EVENTO_FIM_MISSAO;
                }
                break;
            case ESTADO_CONCLUIDO:
                contexto.temMissao = false;
                contexto.idMissaoAtual = -1;
                contexto.progressoMissao = 0.0f;
                contexto.transicionarEstado(EstadoRover.ESTADO_DISPONIVEL);
                break;
            case ESTADO_FALHA:
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                contexto.transicionarEstado(EstadoRover.ESTADO_DISPONIVEL);
                break;
        }
    }

    // Processa recepção de missão
    public void receberMissao(PayloadMissao missao) {
        if (missao == null) {
            System.err.println("[MaquinaEstados] Missão nula recebida");
            return;
        }

        System.out.println("[MaquinaEstados] Rover " + contexto.idRover +
                " recebeu missão: " + missao);

        contexto.receberMissao(missao, missao.idMissao);
        contexto.transicionarEstado(EstadoRover.ESTADO_EM_MISSAO);
    }

    // Exposição do contexto
    public ContextoRover getContexto() {
        return contexto;
    }

    // ===== Implementação da lógica da missão =====
    private void executarPassoMissao() {
        if (!contexto.temMissao || contexto.missaoAtual == null)
            return;

        float destinoX = (contexto.missaoAtual.x1 + contexto.missaoAtual.x2) / 2.0f;
        float destinoY = (contexto.missaoAtual.y1 + contexto.missaoAtual.y2) / 2.0f;

        float dx = destinoX - contexto.posicaoX;
        float dy = destinoY - contexto.posicaoY;
        float distancia = (float) Math.sqrt(dx * dx + dy * dy);

        try {
            if (distancia > 0.5f) {
                float passo = 0.5f;
                contexto.posicaoX += (dx / distancia) * passo;
                contexto.posicaoY += (dy / distancia) * passo;
                contexto.velocidade = ContextoRover.VELOCIDADE_ROVER;
            } else {
                contexto.velocidade = 0.0f;
            }

            contexto.bateria -= 0.1f;
            if (contexto.bateria < 0.0f)
                contexto.bateria = 0.0f;

            if (contexto.bateria < 20.0f && contexto.ultimoEvento != EventoRelevante.EVENTO_BATERIA_BAIXA) {
                contexto.eventoPendente = EventoRelevante.EVENTO_BATERIA_BAIXA;
            }

            long agora = Instant.now().getEpochSecond();
            double decorrido = (double) (agora - contexto.timestampInicioMissaoEpoch);
            if (contexto.missaoAtual instanceof PayloadMissao) {
                try {
                    java.lang.reflect.Field f = contexto.missaoAtual.getClass().getDeclaredField("duracaoMissaoSecs");
                    f.setAccessible(true);
                    long secs = f.getLong(contexto.missaoAtual);
                    if (secs > 0) {
                        contexto.progressoMissao = (float) ((decorrido / (double) secs) * 100.0);
                        if (contexto.progressoMissao > 100.0f)
                            contexto.progressoMissao = 100.0f;
                    }
                } catch (NoSuchFieldException | IllegalAccessException ex) {
                    // Sem campo de duração — ignora cálculo temporal
                }
            }

            int checkpoint = (int) (contexto.progressoMissao / 25.0f);
            if (checkpoint > 0) {
                contexto.eventoPendente = EventoRelevante.EVENTO_CHECKPOINT_MISSAO;
            }
        } catch (Exception ex) {
            System.err.println("[MaquinaEstados] Erro ao executar passo de missão: " + ex.getMessage());
            contexto.transicionarEstado(EstadoRover.ESTADO_FALHA);
        }
    }

    private boolean missaoConcluida() {
        return contexto.progressoMissao >= 100.0f;
    }
}


