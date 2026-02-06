package lib.mensagens.payloads;

import java.util.ArrayList;
import java.util.List;

import lib.mensagens.CampoSerializado;

/**
 * Container para fragmentos de dados com campos identificados.
 * 
 * Cada fragmento transporta uma lista de CampoSerializado, permitindo:
 * - Identificar campos pelo nome
 * - Fragmentar campos grandes em múltiplas partes
 * - Reconstruir independentemente da ordem de chegada
 * 
 * Usada no protocolo MissionLink para transmissão fiável de missões.
 */
public class FragmentoPayload implements Payload {
  
    /** Lista de campos serializados neste fragmento */
    public List<CampoSerializado> campos;
    
    /** Construtor vazio para serialização */
    public FragmentoPayload() {
        this.campos = new ArrayList<>();
    }
    
    /** Construtor com lista de campos */
    public FragmentoPayload(List<CampoSerializado> campos) {
        this.campos = campos != null ? campos : new ArrayList<>();
    }
    
    /** Adiciona um campo ao fragmento */
    public void adicionarCampo(CampoSerializado campo) {
        if (campos == null) {
            campos = new ArrayList<>();
        }
        campos.add(campo);
    }
    
    /** Adiciona um campo completo (não fragmentado) */
    public void adicionarCampo(String nome, byte[] dados) {
        adicionarCampo(new CampoSerializado(nome, dados));
    }
    
    /** @return true se o fragmento contém campos */
    public boolean temDados() {
        return campos != null && !campos.isEmpty();
    }
    
    /** @return número de campos neste fragmento */
    public int numeroCampos() {
        return campos != null ? campos.size() : 0;
    }
    
    /** 
     * @return tamanho total estimado dos dados (soma dos campos)
     */
    public int tamanhoEstimado() {
        if (campos == null) return 0;
        int total = 0;
        for (CampoSerializado c : campos) {
            total += c.tamanhoSerializado();
        }
        return total;
    }
    
    @Override
    public String toString() {
        return String.format("FragmentoPayload{%d campos, ~%d bytes}", 
                            numeroCampos(), tamanhoEstimado());
    }
}
    