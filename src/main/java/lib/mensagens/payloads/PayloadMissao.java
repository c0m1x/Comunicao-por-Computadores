package lib.mensagens.payloads;

import java.util.Calendar;
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
    public Calendar duracaoMissao; // NOTA: VER O TIPO DE DADOS DISTO - poria segundos (int)
    public Calendar intervaloAtualizacao; // em minutos - poria int
    public Calendar inicioMissao;
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

        // Calendars como millis
        long durMillis = (duracaoMissao != null) ? duracaoMissao.getTimeInMillis() : 0L;
        long intMillis = (intervaloAtualizacao != null) ? intervaloAtualizacao.getTimeInMillis() : 0L;
        long iniMillis = (inicioMissao != null) ? inicioMissao.getTimeInMillis() : 0L;
        blocos.add(longBlock.apply(durMillis));
        blocos.add(longBlock.apply(intMillis));
        blocos.add(longBlock.apply(iniMillis));

        // prioridade
        blocos.add(intBlock.apply(prioridade));

        return blocos;
    }

    @Override
    public String toString() {
        return String.format("Missao{id=%d, area=(%.2f,%.2f)-(%.2f,%.2f), tarefa=%s, dur=%dmin, int=%dmin, prio=%d}",
                idMissao, x1, y1, x2, y2, tarefa, duracaoMissao.get(Calendar.MINUTE),
                intervaloAtualizacao.get(Calendar.MINUTE), prioridade);
    }

}