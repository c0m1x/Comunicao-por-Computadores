package lib;

import java.util.HashSet;
import java.util.Set;

 /**
     * Classe que representa uma sessão de envio de missão no servidor.
     */
    public class SessaoServidorMissionLink {
        public Rover rover;
        public Missao missao;
        
        // Estado da comunicação
        public boolean responseRecebido = false;
        public boolean responseSucesso = false;
        public boolean ackRecebido = false;

        //adicionar aqui o resto das variaveis que precisamos para controlar a sessao (progresso, completed)
        
        // Fragmentação
        public int totalFragmentos = 0;
        public byte[][] fragmentos;
        public Set<Integer> fragmentosPerdidos = new HashSet<>();
        
        public SessaoServidorMissionLink(Rover rover, Missao missao) {
            this.rover = rover;
            this.missao = missao;
        }
    }