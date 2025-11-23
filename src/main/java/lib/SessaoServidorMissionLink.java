package lib;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

 /**
     * Classe que representa uma sessão de envio de missão no servidor.
     */
    public class SessaoServidorMissionLink {

        // Endpoint do rover 
        public InetAddress enderecoRover;
        public int portaRover;

        public Rover rover;
        public Missao missao;
        
        // Estado da comunicação
        public boolean responseRecebido = false;
        public boolean responseSucesso = false;
        public boolean ackRecebido = false;
        public boolean recebendoProgresso = false;
        public int ultimoSeq = 0;
        public boolean completedRecebido = false;
        public boolean completedSucesso = false;

        
        // Fragmentação
        public int totalFragmentos = 0;
        public byte[][] fragmentos;
        public Set<Integer> fragmentosPerdidos = new HashSet<>();

        // Progresso perdido (seqs de PROGRESS não recebidos)
        public List<Integer> progressoPerdido = null;
        
        public SessaoServidorMissionLink(Rover rover, Missao missao) {
            this.rover = rover;
            this.missao = missao;
            try {
                this.enderecoRover = InetAddress.getByName(rover.enderecoHost);
            } catch (Exception e) {
                this.enderecoRover = null;
            }
            this.portaRover = rover.portaUdp != null ? rover.portaUdp : -1;
        }
    }