package lib;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

    /**
     * Classe que representa uma sessão completa de missão no cliente.
     * Gerencia tanto a recepção quanto a reportagem do progresso da missão.
     */
    public class SessaoClienteMissionLink {
        // Comum a todas as fases
        public int idMissao;
        public InetAddress enderecoNave;
        public int portaNave;
        
        // Recepção de missão
        public int totalFragmentos;
        public Map<Integer, byte[]> fragmentosRecebidos;
        public List<Integer> fragmentosPerdidos;
        
        // Reportagem do progresso da missão
        public boolean emExecucao = false;
        public int seqAtual;
        public long intervaloAtualizacao; // em ms
        public long duracaoMissao; // em ms
        public long inicioMissao; // timestamp de início
        public boolean aguardandoAck = false;

    }