package lib;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

import lib.mensagens.payloads.FragmentoPayload;
import lib.mensagens.SerializadorUDP;

 /**
     * Classe que representa uma sessão de envio de missão no servidor.
     */
    public class SessaoServidorMissionLink {

        // Endpoint do rover 
        public InetAddress enderecoRover;
        public int portaRover;

        public Rover rover;
        public Missao missao;

        public long ultimaAtividade;
        
        // Limite de inatividade para considerar sessão órfã (ms)
        // Calculado a partir do intervalo de atualização da missão vezes um multiplicador.
        public long limiteInatividade;
        
        // Estado da comunicação
        public boolean responseRecebido = false;
        public boolean responseSucesso = false;
        public boolean ackRecebido = false;
        public boolean recebendoProgresso = false;
        public int ultimoSeq = 0;
        public boolean completedRecebido = false;
        public boolean completedSucesso = false;
        public boolean erroRecebido = false;  // Indica se recebeu MSG_ERROR do rover
        
        // Fragmentação (nova versão com campos identificados)
        public int totalFragmentos = 0;
        public List<FragmentoPayload> fragmentosPayload;
        public Set<Integer> fragmentosPerdidos = new HashSet<>();

        // Progresso perdido (seqs de PROGRESS não recebidos)
        public Set<Integer> progressoPerdido = null;
        
        // Serializador para serialização/desserialização
        public SerializadorUDP serializador;
        
        public SessaoServidorMissionLink(Rover rover, Missao missao) {
            this.rover = rover;
            this.missao = missao;
            this.serializador = new SerializadorUDP();
            this.ultimaAtividade = System.currentTimeMillis();
            // Inicializar limite de inatividade com base no intervalo de atualização
            // da missão (em segundos). Usamos um multiplicador conservador para
            // tolerar perdas e jitter de rede. Se não houver intervalo definido,
            // usa-se 30s como default.
            int multiplicador = 8;
            if (this.missao != null && this.missao.intervaloAtualizacao > 0) {
                this.limiteInatividade = this.missao.intervaloAtualizacao * 1000 * multiplicador;
            } else {
                this.limiteInatividade = 30000;
            }
            try {
                this.enderecoRover = InetAddress.getByName(rover.enderecoHost);
            } catch (Exception e) {
                this.enderecoRover = null;
            }
            this.portaRover = rover.portaUdp != null ? rover.portaUdp : -1;
        }

        /**
         * Atualiza timestamp de atividade
         */
        public void atualizarAtividade() {
            this.ultimaAtividade = System.currentTimeMillis();
        }
    }