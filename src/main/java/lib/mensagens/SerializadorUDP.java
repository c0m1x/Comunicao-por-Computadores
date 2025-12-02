package lib.mensagens;

import lib.mensagens.payloads.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    
    // ==================== CONSTRUTORES ====================
    
    public SerializadorUDP() {
        this.camposPorNome = new HashMap<>();
    }
    
    // ==================== SERIALIZAÇÃO DE PAYLOADS (ESTÁTICO) ====================
    
    /**
     * Serializa um Payload em lista de campos identificados.
     * Delega a serialização para cada tipo de payload.
     * 
     * @param payload Payload a serializar
     * @return Lista de campos serializados com nomes
     */
    public static List<CampoSerializado> serializarPayload(Payload payload) {
        if (payload instanceof PayloadUDP) {
            return ((PayloadUDP) payload).serializarCampos();
        } else if (payload instanceof FragmentoPayload) {
            FragmentoPayload frag = (FragmentoPayload) payload;
            return frag.campos != null ? frag.campos : new ArrayList<>();
        }
        return new ArrayList<>();
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
        List<CampoSerializado> camposPendentes = new ArrayList<>(camposProcessados);
        FragmentoPayload fragmentoAtual = new FragmentoPayload();
        int tamanhoAtual = 0;
        
        while (!camposPendentes.isEmpty()) {
            boolean encontrouCampo = false;
            CampoSerializado melhorCampo = null;
            int melhorIndice = -1;
            int melhorDesperdicio = Integer.MAX_VALUE;
            
            // Procurar o campo que melhor aproveita o espaço restante (best-fit)
            int espacoRestante = tamanhoMaximo - tamanhoAtual;
            for (int i = 0; i < camposPendentes.size(); i++) {
                CampoSerializado campo = camposPendentes.get(i);
                int tamanhoCampo = campo.tamanhoSerializado();
                if (tamanhoCampo <= espacoRestante) {
                    int desperdicio = espacoRestante - tamanhoCampo;
                    if (desperdicio < melhorDesperdicio) {
                        melhorDesperdicio = desperdicio;
                        melhorCampo = campo;
                        melhorIndice = i;
                        encontrouCampo = true;
                    }
                }
            }
            if (encontrouCampo) {
                // Adicionar o melhor campo encontrado ao fragmento atual
                fragmentoAtual.adicionarCampo(melhorCampo);
                tamanhoAtual += melhorCampo.tamanhoSerializado();
                camposPendentes.remove(melhorIndice);
            } else {
                // Nenhum campo cabe - fechar fragmento atual e começar novo
                if (fragmentoAtual.temDados()) {
                    fragmentos.add(fragmentoAtual);
                }
                fragmentoAtual = new FragmentoPayload();
                tamanhoAtual = 0;
                // Adicionar o primeiro campo pendente ao novo fragmento
                if (!camposPendentes.isEmpty()) {
                    CampoSerializado campo = camposPendentes.remove(0);
                    fragmentoAtual.adicionarCampo(campo);
                    tamanhoAtual += campo.tamanhoSerializado();
                }
            }
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
        
        int overheadPorParte = 2 + campo.nome.length() + 4 + 2 + 2;
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
            total += c.tamanhoSerializado();
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
     * Limpa todos os campos agregados.
     */
    public void limpar() {
        camposPorNome.clear();
    }
    
    // ==================== VERIFICAÇÃO DE COMPLETUDE (INSTÂNCIA) ====================
    
    public boolean missaoCompleta() {
        return camposCompletos(CAMPOS_MISSAO);
    }
    
    public boolean progressoCompleto() {
        return camposCompletos(CAMPOS_PROGRESSO);
    }
    
    private boolean camposCompletos(String[] nomesCampos) {
        for (String nome : nomesCampos) {
            if (!campoCompleto(nome)) {
                return false;
            }
        }
        return true;
    }
    
    private boolean campoCompleto(String nomeCampo) {
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
    private byte[] reconstruirBytes(String nomeCampo) {
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
    
    private int lerInt(String nome) {
        byte[] dados = reconstruirBytes(nome);
        return ByteBuffer.wrap(dados).getInt();
    }
    
    private float lerFloat(String nome) {
        byte[] dados = reconstruirBytes(nome);
        return ByteBuffer.wrap(dados).getFloat();
    }
    
    private long lerLong(String nome) {
        byte[] dados = reconstruirBytes(nome);
        return ByteBuffer.wrap(dados).getLong();
    }
    
    private String lerString(String nome) {
        byte[] dados = reconstruirBytes(nome);
        ByteBuffer buf = ByteBuffer.wrap(dados);
        int len = buf.getInt();
        if (len <= 0) return "";
        byte[] strBytes = new byte[len];
        buf.get(strBytes);
        return new String(strBytes, StandardCharsets.UTF_8);
    }
}
