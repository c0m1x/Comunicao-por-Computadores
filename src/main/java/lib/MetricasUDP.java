package lib;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Módulo de métricas para contabilizar estatísticas de comunicação UDP.
 * Thread-safe usando AtomicLong.
 */
public class MetricasUDP {
    
    private final String nomeEntidade; // "ServidorUDP" ou "ClienteUDP-{id}"
    
    // Contadores de mensagens
    private final AtomicLong mensagensEnviadas = new AtomicLong(0);
    private final AtomicLong mensagensRecebidas = new AtomicLong(0);
    private final AtomicLong mensagensRetransmitidas = new AtomicLong(0);
    private final AtomicLong mensagensDuplicadas = new AtomicLong(0);
    private final AtomicLong mensagensEmAtraso = new AtomicLong(0);
    private final AtomicLong mensagensPerdidas = new AtomicLong(0);
    
    // Contadores específicos por tipo
    private final AtomicLong helloEnviados = new AtomicLong(0);
    private final AtomicLong responseRecebidos = new AtomicLong(0);
    private final AtomicLong missionEnviados = new AtomicLong(0);
    private final AtomicLong acksEnviados = new AtomicLong(0);
    private final AtomicLong acksRecebidos = new AtomicLong(0);
    private final AtomicLong progressEnviados = new AtomicLong(0);
    private final AtomicLong progressRecebidos = new AtomicLong(0);
    private final AtomicLong completedEnviados = new AtomicLong(0);
    private final AtomicLong completedRecebidos = new AtomicLong(0);
    private final AtomicLong errorEnviados = new AtomicLong(0);
    private final AtomicLong errorRecebidos = new AtomicLong(0);
    
    // Timestamp de início
    private final long timestampInicio;
    
    public MetricasUDP(String nomeEntidade) {
        this.nomeEntidade = nomeEntidade;
        this.timestampInicio = System.currentTimeMillis();
    }
    
    // ==================== MÉTODOS DE INCREMENTO ====================
    
    public void incrementarMensagensEnviadas() {
        mensagensEnviadas.incrementAndGet();
    }
    
    public void incrementarMensagensRecebidas() {
        mensagensRecebidas.incrementAndGet();
    }
    
    public void incrementarMensagensRetransmitidas() {
        mensagensRetransmitidas.incrementAndGet();
    }
    
    public void incrementarMensagensDuplicadas() {
        mensagensDuplicadas.incrementAndGet();
    }
    
    public void incrementarMensagensEmAtraso() {
        mensagensEmAtraso.incrementAndGet();
    }
    
    public void incrementarMensagensPerdidas(int quantidade) {
        mensagensPerdidas.addAndGet(quantidade);
    }
    
    // Contadores por tipo de mensagem
    public void incrementarHelloEnviados() {
        helloEnviados.incrementAndGet();
    }
    
    public void incrementarResponseRecebidos() {
        responseRecebidos.incrementAndGet();
    }
    
    public void incrementarMissionEnviados() {
        missionEnviados.incrementAndGet();
    }
    
    public void incrementarAcksEnviados() {
        acksEnviados.incrementAndGet();
    }
    
    public void incrementarAcksRecebidos() {
        acksRecebidos.incrementAndGet();
    }
    
    public void incrementarProgressEnviados() {
        progressEnviados.incrementAndGet();
    }
    
    public void incrementarProgressRecebidos() {
        progressRecebidos.incrementAndGet();
    }
    
    public void incrementarCompletedEnviados() {
        completedEnviados.incrementAndGet();
    }
    
    public void incrementarCompletedRecebidos() {
        completedRecebidos.incrementAndGet();
    }
    
    public void incrementarErrorEnviados() {
        errorEnviados.incrementAndGet();
    }
    
    public void incrementarErrorRecebidos() {
        errorRecebidos.incrementAndGet();
    }
    
    // ==================== MÉTODOS DE CONSULTA ====================
    
    public long getMensagensEnviadas() {
        return mensagensEnviadas.get();
    }
    
    public long getMensagensRecebidas() {
        return mensagensRecebidas.get();
    }
    
    public long getMensagensRetransmitidas() {
        return mensagensRetransmitidas.get();
    }
    
    public long getMensagensDuplicadas() {
        return mensagensDuplicadas.get();
    }
    
    public long getMensagensEmAtraso() {
        return mensagensEmAtraso.get();
    }
    
    public long getMensagensPerdidas() {
        return mensagensPerdidas.get();
    }
    
    // ==================== EXPORTAÇÃO ====================
    
    /**
     * Exporta as métricas para um ficheiro de texto.
     * @param caminhoFicheiro Caminho do ficheiro de destino
     */
    public void exportarParaFicheiro(String caminhoFicheiro) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(caminhoFicheiro, true))) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            long duracaoMs = System.currentTimeMillis() - timestampInicio;
            
            writer.println("========================================");
            writer.println("MÉTRICAS UDP - " + nomeEntidade);
            writer.println("Data: " + sdf.format(new Date()));
            writer.println("Duração: " + (duracaoMs / 1000) + "s");
            writer.println("========================================");
            writer.println();
            
            writer.println("CONTADORES GERAIS:");
            writer.println("  Mensagens Enviadas:       " + mensagensEnviadas.get());
            writer.println("  Mensagens Recebidas:      " + mensagensRecebidas.get());
            writer.println("  Mensagens Retransmitidas: " + mensagensRetransmitidas.get());
            writer.println("  Mensagens Duplicadas:     " + mensagensDuplicadas.get());
            writer.println("  Mensagens Em Atraso:      " + mensagensEmAtraso.get());
            writer.println("  Mensagens Perdidas:       " + mensagensPerdidas.get());
            writer.println();
            
            writer.println("CONTADORES POR TIPO:");
            writer.println("  HELLO enviados:           " + helloEnviados.get());
            writer.println("  RESPONSE recebidos:       " + responseRecebidos.get());
            writer.println("  MISSION enviados:         " + missionEnviados.get());
            writer.println("  ACK enviados:             " + acksEnviados.get());
            writer.println("  ACK recebidos:            " + acksRecebidos.get());
            writer.println("  PROGRESS enviados:        " + progressEnviados.get());
            writer.println("  PROGRESS recebidos:       " + progressRecebidos.get());
            writer.println("  COMPLETED enviados:       " + completedEnviados.get());
            writer.println("  COMPLETED recebidos:      " + completedRecebidos.get());
            writer.println("  ERROR enviados:           " + errorEnviados.get());
            writer.println("  ERROR recebidos:          " + errorRecebidos.get());
            writer.println();
            
            // Calcular taxa de perda
            long totalEsperadas = mensagensEnviadas.get();
            if (totalEsperadas > 0) {
                double taxaPerda = (mensagensPerdidas.get() * 100.0) / totalEsperadas;
                double taxaRetransmissao = (mensagensRetransmitidas.get() * 100.0) / totalEsperadas;
                writer.println("ESTATÍSTICAS:");
                writer.printf("  Taxa de Perda:            %.2f%%\n", taxaPerda);
                writer.printf("  Taxa de Retransmissão:    %.2f%%\n", taxaRetransmissao);
            }
            
            writer.println();
            
            System.out.println("[MetricasUDP] Métricas de " + nomeEntidade + " exportadas para " + caminhoFicheiro);
            
        } catch (IOException e) {
            System.err.println("[MetricasUDP] Erro ao exportar métricas: " + e.getMessage());
        }
    }
    
    /**
     * Imprime um resumo das métricas na consola.
     */
    public void imprimirResumo() {
        System.out.println("\n========== MÉTRICAS UDP - " + nomeEntidade + " ==========");
        System.out.println("Enviadas: " + mensagensEnviadas.get() + 
                         " | Recebidas: " + mensagensRecebidas.get());
        System.out.println("Retransmitidas: " + mensagensRetransmitidas.get() + 
                         " | Duplicadas: " + mensagensDuplicadas.get());
        System.out.println("Em Atraso: " + mensagensEmAtraso.get() + 
                         " | Perdidas: " + mensagensPerdidas.get());
        System.out.println("===============================================\n");
    }
}
