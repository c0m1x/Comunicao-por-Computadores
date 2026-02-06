package lib.mensagens;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * Representa um campo serializado com metadados para fragmentação.
 * 
 * Permite:
 * - Identificar o campo pelo nome
 * - Fragmentar campos grandes em múltiplas partes
 * - Reconstruir campos independentemente da ordem de chegada
 * 
 */

public class CampoSerializado implements Serializable {
    
    public final String nome; //nome do campo 
    public final byte[] dados; //dados serializados do campo (ou parte dele se fragmentado) 
    public final short indiceParte; //indice deste fragmento dentro do campo (0 se não fragmentado)
    public final short totalPartes; //total de partes do campo (1 se não fragmentado)
    
    /**
     * Construtor para campo completo (não fragmentado).
     */
    public CampoSerializado(String nome, byte[] dados) {
        this(nome, dados, 0, 1);
    }
    
    /**
     * Construtor para fragmento de campo.
     */
    public CampoSerializado(String nome, byte[] dados, int indiceParte, int totalPartes) {
        this.nome = nome;
        this.dados = dados;
        this.indiceParte = (short) indiceParte;
        this.totalPartes = (short) totalPartes;
    }
    
    /** @return true se este campo está fragmentado em múltiplas partes */
    public boolean isFragmentado() {
        return totalPartes > 1;
    }
    
    /** @return tamanho dos dados em bytes */
    public int tamanho() {
        return dados != null ? dados.length : 0;
    }
    
    /**
     * Tamanho total serializado.
     * Inclui overhead do nome e metadados.
     */
    public int tamanhoSerializado() {
        // nome (2 bytes length + chars) + dados (4 bytes length + bytes) + indiceParte (2) + totalPartes (2)
        int nomeBytes = nome != null ? nome.getBytes(StandardCharsets.UTF_8).length : 0;
        return 2 + nomeBytes + 4 + tamanho() + 2 + 2;
    }
    
    @Override
    public String toString() {
        if (isFragmentado()) {
            return String.format("Campo{%s[%d/%d], %d bytes}", nome, indiceParte + 1, totalPartes, tamanho());
        }
        return String.format("Campo{%s, %d bytes}", nome, tamanho());
    }
}

