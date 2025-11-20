package lib.mensagens.payloads;
import java.util.ArrayList;
import java.util.List;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

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
    public int prioridade; // 1-5



    Function<Integer, byte[]> intBlock = (val) -> ByteBuffer.allocate(4).putInt(val).array();
    Function<Float, byte[]> floatBlock = (val) -> ByteBuffer.allocate(4).putFloat(val).array();
    Function<Long, byte[]> longBlock = (val) -> ByteBuffer.allocate(8).putLong(val).array();

    /**
     * Serializa os campos deste Payload em blocos independentes, por campo,
     * para facilitar fragmentação sem quebrar campos.
     * Formato por ordem
     */


    public List<byte[]> serializarPorCampos() {
        List<byte[]> blocos = new ArrayList<>();

        // idMissao
        blocos.add(intBlock.apply(idMissao));

        // coordenadas
        blocos.add(floatBlock.apply(x1));
        blocos.add(floatBlock.apply(y1));
        blocos.add(floatBlock.apply(x2));
        blocos.add(floatBlock.apply(y2));

        // tarefa (String como length + bytes UTF-8)
        byte[] tarefaBytes = (tarefa == null) ? new byte[0] : tarefa.getBytes(StandardCharsets.UTF_8);
        blocos.add(intBlock.apply(tarefaBytes.length));
        if (tarefaBytes.length > 0)
            blocos.add(tarefaBytes);

        // tempos em segundos (já armazenados assim)
        blocos.add(longBlock.apply(duracaoMissao));
        blocos.add(longBlock.apply(intervaloAtualizacao));
        blocos.add(longBlock.apply(inicioMissao));

        // prioridade
        blocos.add(intBlock.apply(prioridade));

        return blocos;
    }

    @Override
    public String toString() {
        return String.format("Missao{id=%d, area=(%.2f,%.2f)-(%.2f,%.2f), tarefa=%s, dur=%ds, int=%ds, prio=%d}",
            idMissao, x1, y1, x2, y2, tarefa, duracaoMissao,
            intervaloAtualizacao, prioridade);
    }

}