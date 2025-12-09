package lib.mensagens.payloads;

import java.util.ArrayList;
import java.util.List;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import lib.mensagens.CampoSerializado;

/**
 * Payload da missão UDP.
 */
public class PayloadMissao extends PayloadUDP {

    public int idMissao;
    public float x1, y1, x2, y2; // coordenadas da area da missao
    public String tarefa = "";
    // tempos em segundos
    public long duracaoMissao;          // duração da missão em segundos
    public long intervaloAtualizacao;   // intervalo de atualização em segundos
    public long inicioMissao;           // instante de início em segundos (epoch) 
    public int prioridade; // 1-5.  //implementar isto no gestaoestado, escolher primeiro missoes prioritarias ou apagar isto

    @Override
    public List<CampoSerializado> serializarCampos() {
        List<CampoSerializado> campos = new ArrayList<>();
        
        campos.add(new CampoSerializado("idMissao", ByteBuffer.allocate(4).putInt(idMissao).array()));
        campos.add(new CampoSerializado("x1", ByteBuffer.allocate(4).putFloat(x1).array()));
        campos.add(new CampoSerializado("y1", ByteBuffer.allocate(4).putFloat(y1).array()));
        campos.add(new CampoSerializado("x2", ByteBuffer.allocate(4).putFloat(x2).array()));
        campos.add(new CampoSerializado("y2", ByteBuffer.allocate(4).putFloat(y2).array()));
        
        byte[] tarefaBytes = (tarefa == null) ? new byte[0] : tarefa.getBytes(StandardCharsets.UTF_8);
        ByteBuffer tarefaBuf = ByteBuffer.allocate(4 + tarefaBytes.length);
        tarefaBuf.putInt(tarefaBytes.length);
        tarefaBuf.put(tarefaBytes);
        campos.add(new CampoSerializado("tarefa", tarefaBuf.array()));
        
        campos.add(new CampoSerializado("duracaoMissao", ByteBuffer.allocate(8).putLong(duracaoMissao).array()));
        campos.add(new CampoSerializado("intervaloAtualizacao", ByteBuffer.allocate(8).putLong(intervaloAtualizacao).array()));
        campos.add(new CampoSerializado("inicioMissao", ByteBuffer.allocate(8).putLong(inicioMissao).array()));
        campos.add(new CampoSerializado("prioridade", ByteBuffer.allocate(4).putInt(prioridade).array()));
        
        return campos;
    }

    @Override
    public String toString() {
        return String.format("Missao{id=%d, area=(%.2f,%.2f)-(%.2f,%.2f), tarefa=%s, dur=%ds, int=%ds, prio=%d}",
            idMissao, x1, y1, x2, y2, tarefa, duracaoMissao,
            intervaloAtualizacao, prioridade);
    }
}