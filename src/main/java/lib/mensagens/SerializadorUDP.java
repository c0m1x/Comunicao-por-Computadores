package lib.mensagens;

import lib.mensagens.payloads.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Módulo unificado de serialização/desserialização para mensagens UDP.
 * 
 * Combina:
 * - Serialização de payloads em campos identificados
 * - Fragmentação de campos grandes em múltiplas partes
 * - Empacotamento de campos em FragmentoPayloads otimizados
 * - Agregação e reconstrução de campos fragmentados
 * - Serialização/deserialização de MensagemUDP para bytes
 * 
 * Instanciar para manter estado de agregação de fragmentos.
 */
public class SerializadorUDP {
    
    /** Tamanho máximo padrão por fragmento (bytes) */
    public static final int TAMANHO_FRAGMENTO_PADRAO = 512;
    
    // ==================== ESTADO DE INSTÂNCIA (para agregação) ====================
    
    /** Mapa de campos por nome (suporta campos fragmentados) */
    private final Map<String, List<CampoSerializado>> camposPorNome;
    
    /** Lista de nomes de campos esperados para PayloadMissao */
    private static final String[] CAMPOS_MISSAO = {
        "idMissao", "x1", "y1", "x2", "y2", "tarefa",
        "duracaoMissao", "intervaloAtualizacao", "inicioMissao", "prioridade"
    };
    
    /** Lista de nomes de campos esperados para PayloadProgresso */
    private static final String[] CAMPOS_PROGRESSO = {
        "idMissao", "tempoDecorrido", "progressoPercentagem"
    };
    
    /** Lista de nomes de campos esperados para PayloadAck */
    private static final String[] CAMPOS_ACK = {
        "missingCount", "missing"
    };
    
    /** Lista de nomes de campos esperados para PayloadErro */
    private static final String[] CAMPOS_ERRO = {
        "idMissao", "codigoErro", "descricao", "progressoAtual",
        "bateria", "posicaoX", "posicaoY", "timestampErro"
    };
    
    // Funções auxiliares de conversão (estáticas)
    private static final Function<Integer, byte[]> intToBytes = 
        val -> ByteBuffer.allocate(4).putInt(val).array();
    private static final Function<Float, byte[]> floatToBytes = 
        val -> ByteBuffer.allocate(4).putFloat(val).array();
    private static final Function<Long, byte[]> longToBytes = 
        val -> ByteBuffer.allocate(8).putLong(val).array();
    
    // ==================== CONSTRUTORES ====================
    
    public SerializadorUDP() {
        this.camposPorNome = new HashMap<>();
    }
    
    /**
     * Cria instância inicializada com campos existentes.
     */
    public SerializadorUDP(Map<String, List<CampoSerializado>> camposIniciais) {
        this.camposPorNome = camposIniciais != null ? camposIniciais : new HashMap<>();
    }
    
    // ==================== SERIALIZAÇÃO DE PAYLOADS (ESTÁTICO) ====================
    
    /**
     * Serializa um Payload em lista de campos identificados.
     * Suporta todos os tipos de payload do protocolo.
     * 
     * @param payload Payload a serializar
     * @return Lista de campos serializados com nomes
     */
    public static List<CampoSerializado> serializarPayload(Payload payload) {
        if (payload instanceof PayloadMissao) {
            return serializarMissao((PayloadMissao) payload);
        } else if (payload instanceof PayloadProgresso) {
            return serializarProgresso((PayloadProgresso) payload);
        } else if (payload instanceof PayloadAck) {
            return serializarAck((PayloadAck) payload);
        } else if (payload instanceof PayloadErro) {
            return serializarErro((PayloadErro) payload);
        } else if (payload instanceof FragmentoPayload) {
            FragmentoPayload frag = (FragmentoPayload) payload;
            return frag.campos != null ? frag.campos : new ArrayList<>();
        }
        return new ArrayList<>();
    }
    
    private static List<CampoSerializado> serializarMissao(PayloadMissao p) {
        List<CampoSerializado> campos = new ArrayList<>();
        
        campos.add(new CampoSerializado("idMissao", intToBytes.apply(p.idMissao)));
        campos.add(new CampoSerializado("x1", floatToBytes.apply(p.x1)));
        campos.add(new CampoSerializado("y1", floatToBytes.apply(p.y1)));
        campos.add(new CampoSerializado("x2", floatToBytes.apply(p.x2)));
        campos.add(new CampoSerializado("y2", floatToBytes.apply(p.y2)));
        
        byte[] tarefaBytes = (p.tarefa == null) ? new byte[0] : p.tarefa.getBytes(StandardCharsets.UTF_8);
        ByteBuffer tarefaBuf = ByteBuffer.allocate(4 + tarefaBytes.length);
        tarefaBuf.putInt(tarefaBytes.length);
        tarefaBuf.put(tarefaBytes);
        campos.add(new CampoSerializado("tarefa", tarefaBuf.array()));
        
        campos.add(new CampoSerializado("duracaoMissao", longToBytes.apply(p.duracaoMissao)));
        campos.add(new CampoSerializado("intervaloAtualizacao", longToBytes.apply(p.intervaloAtualizacao)));
        campos.add(new CampoSerializado("inicioMissao", longToBytes.apply(p.inicioMissao)));
        campos.add(new CampoSerializado("prioridade", intToBytes.apply(p.prioridade)));
        
        return campos;
    }
    
    private static List<CampoSerializado> serializarProgresso(PayloadProgresso p) {
        List<CampoSerializado> campos = new ArrayList<>();
        
        campos.add(new CampoSerializado("idMissao", intToBytes.apply(p.idMissao)));
        campos.add(new CampoSerializado("tempoDecorrido", longToBytes.apply(p.tempoDecorrido)));
        campos.add(new CampoSerializado("progressoPercentagem", floatToBytes.apply(p.progressoPercentagem)));
        
        return campos;
    }
    
    private static List<CampoSerializado> serializarAck(PayloadAck p) {
        List<CampoSerializado> campos = new ArrayList<>();
        
        campos.add(new CampoSerializado("missingCount", intToBytes.apply(p.missingCount)));
        
        int[] missing = p.missing != null ? p.missing : new int[0];
        ByteBuffer buf = ByteBuffer.allocate(4 + missing.length * 4);
        buf.putInt(missing.length);
        for (int seq : missing) {
            buf.putInt(seq);
        }
        campos.add(new CampoSerializado("missing", buf.array()));
        
        return campos;
    }
    
    private static List<CampoSerializado> serializarErro(PayloadErro p) {
        List<CampoSerializado> campos = new ArrayList<>();
        
        campos.add(new CampoSerializado("idMissao", intToBytes.apply(p.idMissao)));
        campos.add(new CampoSerializado("codigoErro", intToBytes.apply(p.codigoErro)));
        
        byte[] descBytes = p.descricao != null ? p.descricao.getBytes(StandardCharsets.UTF_8) : new byte[0];
        ByteBuffer descBuf = ByteBuffer.allocate(4 + descBytes.length);
        descBuf.putInt(descBytes.length);
        descBuf.put(descBytes);
        campos.add(new CampoSerializado("descricao", descBuf.array()));
        
        campos.add(new CampoSerializado("progressoAtual", floatToBytes.apply(p.progressoAtual)));
        campos.add(new CampoSerializado("bateria", floatToBytes.apply(p.bateria)));
        campos.add(new CampoSerializado("posicaoX", floatToBytes.apply(p.posicaoX)));
        campos.add(new CampoSerializado("posicaoY", floatToBytes.apply(p.posicaoY)));
        campos.add(new CampoSerializado("timestampErro", longToBytes.apply(p.timestampErro)));
        
        return campos;
    }
    
    // ==================== FRAGMENTAÇÃO (ESTÁTICO) ====================
    
    /**
     * Fragmenta um payload em múltiplos FragmentoPayload se necessário.
     */
    public static List<FragmentoPayload> fragmentarPayload(Payload payload, int tamanhoMaximo) {
        List<CampoSerializado> campos = serializarPayload(payload);
        return empacotarCampos(campos, tamanhoMaximo);
    }
    
    /**
     * Fragmenta um payload usando tamanho padrão.
     */
    public static List<FragmentoPayload> fragmentarPayload(Payload payload) {
        return fragmentarPayload(payload, TAMANHO_FRAGMENTO_PADRAO);
    }
    
    /**
     * Verifica se um payload precisa de fragmentação.
     */
    public static boolean precisaFragmentacao(Payload payload, int tamanhoMaximo) {
        List<CampoSerializado> campos = serializarPayload(payload);
        int tamanhoTotal = 0;
        for (CampoSerializado c : campos) {
            tamanhoTotal += c.tamanhoSerializado();
        }
        return tamanhoTotal > tamanhoMaximo;
    }
    
    /**
     * Empacota campos identificados em FragmentoPayloads.
     */
    public static List<FragmentoPayload> empacotarCampos(List<CampoSerializado> campos, int tamanhoMaximo) {
        List<FragmentoPayload> fragmentos = new ArrayList<>();
        List<CampoSerializado> camposProcessados = new ArrayList<>();
        
        // 1) Fragmentar campos grandes
        for (CampoSerializado campo : campos) {
            if (campo.tamanho() > tamanhoMaximo) {
                List<CampoSerializado> partes = fragmentarCampoGrande(campo, tamanhoMaximo);
                camposProcessados.addAll(partes);
            } else {
                camposProcessados.add(campo);
            }
        }
        
        // 2) Empacotar campos em fragmentos (otimizando espaço)
        FragmentoPayload fragmentoAtual = new FragmentoPayload();
        int tamanhoAtual = 0;
        
        for (CampoSerializado campo : camposProcessados) {
            int tamanhoCampo = campo.tamanhoSerializado();
            
            if (tamanhoAtual + tamanhoCampo > tamanhoMaximo && fragmentoAtual.temDados()) {
                fragmentos.add(fragmentoAtual);
                fragmentoAtual = new FragmentoPayload();
                tamanhoAtual = 0;
            }
            
            fragmentoAtual.adicionarCampo(campo);
            tamanhoAtual += tamanhoCampo;
        }
        
        if (fragmentoAtual.temDados()) {
            fragmentos.add(fragmentoAtual);
        }
        
        return fragmentos;
    }
    
    /**
     * Fragmenta um campo grande em múltiplas partes.
     */
    public static List<CampoSerializado> fragmentarCampoGrande(CampoSerializado campo, int tamanhoMaximo) {
        List<CampoSerializado> partes = new ArrayList<>();
        byte[] dados = campo.dados;
        
        int overheadPorParte = 2 + campo.nome.length() + 4 + 4 + 4;
        int tamanhoUtilPorParte = tamanhoMaximo - overheadPorParte;
        
        if (tamanhoUtilPorParte <= 0) {
            tamanhoUtilPorParte = tamanhoMaximo / 2;
        }
        
        int totalPartes = (int) Math.ceil((double) dados.length / tamanhoUtilPorParte);
        
        for (int i = 0; i < totalPartes; i++) {
            int inicio = i * tamanhoUtilPorParte;
            int fim = Math.min(inicio + tamanhoUtilPorParte, dados.length);
            int tamanho = fim - inicio;
            
            byte[] parteDados = new byte[tamanho];
            System.arraycopy(dados, inicio, parteDados, 0, tamanho);
            
            partes.add(new CampoSerializado(campo.nome, parteDados, i, totalPartes));
        }
        
        return partes;
    }
    
    // ==================== SERIALIZAÇÃO DE OBJETOS (ESTÁTICO) ====================
    
    /**
     * Serializa objeto para bytes (usando ObjectOutputStream).
     */
    // ==================== SERIALIZAÇÃO DE OBJETOS ====================
    
    /**
     * Serializa objeto para bytes (usando ObjectOutputStream).
     */
    public byte[] serializarObjeto(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.flush();
        return baos.toByteArray();
    }
    
    /**
     * Deserializa bytes para MensagemUDP.
     */
    public static MensagemUDP deserializarMensagem(byte[] dados, int length) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(dados, 0, length);
            ObjectInputStream ois = new ObjectInputStream(bais);
            return (MensagemUDP) ois.readObject();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Calcula o tamanho total dos campos serializados.
     */
    public static int calcularTamanhoTotal(List<CampoSerializado> campos) {
        int total = 0;
        for (CampoSerializado c : campos) {
            total += c.tamanho();
        }
        return total;
    }
    
    // ==================== AGREGAÇÃO DE CAMPOS (INSTÂNCIA) ====================
    
    /**
     * Agrega campos de um FragmentoPayload.
     */
    public void agregarCampos(FragmentoPayload fragmento) {
        if (fragmento == null || fragmento.campos == null) return;
        
        for (CampoSerializado campo : fragmento.campos) {
            camposPorNome
                .computeIfAbsent(campo.nome, k -> new ArrayList<>())
                .add(campo);
        }
    }
    
    /**
     * Adiciona um campo diretamente.
     */
    public void adicionarCampo(CampoSerializado campo) {
        if (campo == null) return;
        camposPorNome
            .computeIfAbsent(campo.nome, k -> new ArrayList<>())
            .add(campo);
    }
    
    /**
     * Limpa todos os campos agregados.
     */
    public void limpar() {
        camposPorNome.clear();
    }
    
    /**
     * @return Mapa de campos por nome (para inspeção)
     */
    public Map<String, List<CampoSerializado>> getCamposPorNome() {
        return camposPorNome;
    }
    
    // ==================== VERIFICAÇÃO DE COMPLETUDE (INSTÂNCIA) ====================
    
    public boolean missaoCompleta() {
        return camposCompletos(CAMPOS_MISSAO);
    }
    
    public boolean progressoCompleto() {
        return camposCompletos(CAMPOS_PROGRESSO);
    }
    
    public boolean ackCompleto() {
        return camposCompletos(CAMPOS_ACK);
    }
    
    public boolean erroCompleto() {
        return camposCompletos(CAMPOS_ERRO);
    }
    
    private boolean camposCompletos(String[] nomesCampos) {
        for (String nome : nomesCampos) {
            if (!campoCompleto(nome)) {
                return false;
            }
        }
        return true;
    }
    
    public boolean campoCompleto(String nomeCampo) {
        List<CampoSerializado> partes = camposPorNome.get(nomeCampo);
        if (partes == null || partes.isEmpty()) {
            return false;
        }
        
        if (partes.size() == 1 && !partes.get(0).isFragmentado()) {
            return true;
        }
        
        int totalEsperado = partes.get(0).totalPartes;
        return partes.size() == totalEsperado;
    }
    
    public boolean temCampo(String nome) {
        return camposPorNome.containsKey(nome) && !camposPorNome.get(nome).isEmpty();
    }
    
    public int numeroCampos() {
        return camposPorNome.size();
    }
    
    // ==================== RECONSTRUÇÃO DE PAYLOADS (INSTÂNCIA) ====================
    
    /**
     * Reconstrói PayloadMissao a partir dos campos agregados.
     */
    public PayloadMissao reconstruirMissao() {
        PayloadMissao p = new PayloadMissao();
        
        p.idMissao = lerInt("idMissao");
        p.x1 = lerFloat("x1");
        p.y1 = lerFloat("y1");
        p.x2 = lerFloat("x2");
        p.y2 = lerFloat("y2");
        p.tarefa = lerString("tarefa");
        p.duracaoMissao = lerLong("duracaoMissao");
        p.intervaloAtualizacao = lerLong("intervaloAtualizacao");
        p.inicioMissao = lerLong("inicioMissao");
        p.prioridade = lerInt("prioridade");
        
        return p;
    }
    
    /**
     * Reconstrói PayloadProgresso a partir dos campos agregados.
     */
    public PayloadProgresso reconstruirProgresso() {
        PayloadProgresso p = new PayloadProgresso();
        
        p.idMissao = lerInt("idMissao");
        p.tempoDecorrido = lerLong("tempoDecorrido");
        p.progressoPercentagem = lerFloat("progressoPercentagem");
        
        return p;
    }
    
    // ==================== LEITURA DE CAMPOS (INSTÂNCIA) ====================
    
    /**
     * Reconstrói os bytes de um campo a partir das suas partes.
     */
    public byte[] reconstruirBytes(String nomeCampo) {
        List<CampoSerializado> partes = camposPorNome.get(nomeCampo);
        if (partes == null || partes.isEmpty()) {
            throw new IllegalStateException("Campo não encontrado: " + nomeCampo);
        }
        
        if (partes.size() == 1 && !partes.get(0).isFragmentado()) {
            return partes.get(0).dados;
        }
        
        partes.sort((a, b) -> Integer.compare(a.indiceParte, b.indiceParte));
        
        int totalEsperado = partes.get(0).totalPartes;
        if (partes.size() != totalEsperado) {
            throw new IllegalStateException("Campo " + nomeCampo + " incompleto: " + 
                                          partes.size() + "/" + totalEsperado + " partes");
        }
        
        int tamanhoTotal = 0;
        for (CampoSerializado p : partes) {
            tamanhoTotal += p.dados.length;
        }
        
        byte[] resultado = new byte[tamanhoTotal];
        int pos = 0;
        for (CampoSerializado p : partes) {
            System.arraycopy(p.dados, 0, resultado, pos, p.dados.length);
            pos += p.dados.length;
        }
        
        return resultado;
    }
    
    public int lerInt(String nome) {
        byte[] dados = reconstruirBytes(nome);
        return ByteBuffer.wrap(dados).getInt();
    }
    
    public float lerFloat(String nome) {
        byte[] dados = reconstruirBytes(nome);
        return ByteBuffer.wrap(dados).getFloat();
    }
    
    public long lerLong(String nome) {
        byte[] dados = reconstruirBytes(nome);
        return ByteBuffer.wrap(dados).getLong();
    }
    
    public String lerString(String nome) {
        byte[] dados = reconstruirBytes(nome);
        ByteBuffer buf = ByteBuffer.wrap(dados);
        int len = buf.getInt();
        if (len <= 0) return "";
        byte[] strBytes = new byte[len];
        buf.get(strBytes);
        return new String(strBytes, StandardCharsets.UTF_8);
    }
    
    public int[] lerIntArray(String nome) {
        byte[] dados = reconstruirBytes(nome);
        ByteBuffer buf = ByteBuffer.wrap(dados);
        int len = buf.getInt();
        if (len <= 0) return new int[0];
        int[] result = new int[len];
        for (int i = 0; i < len; i++) {
            result[i] = buf.getInt();
        }
        return result;
    }
}
