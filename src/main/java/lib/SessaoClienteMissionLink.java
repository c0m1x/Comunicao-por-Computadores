package lib;

import lib.mensagens.payloads.PayloadProgresso;
import lib.mensagens.payloads.FragmentoPayload;
import lib.mensagens.SerializadorUDP;

import java.net.InetAddress;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

    /**
     * Classe que representa uma sessão completa de missão no cliente.
     * Gerencia tanto a recepção quanto a reportagem do progresso da missão.
     */
    public class SessaoClienteMissionLink {
        // Comum a todas as fases
        public int idMissao;
        public InetAddress enderecoNave;
        public int portaNave;
        
    // Recepção de missão (nova versão com campos identificados)
    public int totalFragmentos;
    public Map<Integer, FragmentoPayload> fragmentosRecebidos;
    public Set<Integer> fragmentosPerdidos;
    public long ultimoFragmentoRecebido; // timestamp do último fragmento recebido
    
    // Serializador para serialização/desserialização
    public SerializadorUDP serializador;        // Reportagem do progresso da missão
        public boolean emExecucao = false;
        public int seqAtual;
        public long intervaloAtualizacao; // em ms
        public long duracaoMissao; // em ms
        public long inicioMissao; // timestamp de início
        public boolean aguardandoAck = false;
        
        // Controlo de ACK com validação de sequência
        public int seqAckEsperado = 0;      // seq que esperamos confirmar
        public int ultimoSeqConfirmado = 0; // último seq confirmado com sucesso
        
        // Armazena os progressos enviados por sequência
        public Map<Integer, PayloadProgresso> progressosEnviados;


    public SessaoClienteMissionLink(int idMissao, InetAddress enderecoNave, int portaNave) {
        this.idMissao = idMissao;
        this.enderecoNave = enderecoNave;
        this.portaNave = portaNave;
        this.seqAtual = 0;
        this.totalFragmentos = 0;
        this.fragmentosRecebidos = new HashMap<>();
        this.serializador = new SerializadorUDP();
        this.progressosEnviados = new HashMap<>();
        this.fragmentosPerdidos = new HashSet<>();
        this.ultimoFragmentoRecebido = 0;
    }    }