package gc;

import lib.mensagens.MensagemUDP;
import lib.mensagens.SerializadorUDP;
import lib.TipoMensagem;
import lib.mensagens.payloads.PayloadMissao;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import gc.models.MissaoModel;

/**
 * Constroi missao e envia-la via UDP
 */
public class GestaoEnvioMissoes {

    private final int portaNaveMae;
    private final SerializadorUDP serializador;

    public GestaoEnvioMissoes(int porta) {
        this.portaNaveMae = porta;
        this.serializador = new SerializadorUDP();
    }

    public void enviarMissao(MissaoModel missao) throws Exception {

        PayloadMissao payload = new PayloadMissao();
        payload.idMissao = missao.idMissao;
        payload.tarefa = missao.tarefa;

        MensagemUDP mensagem = new MensagemUDP(TipoMensagem.MSG_MISSION, missao.idMissao, payload);
        byte[] data = serializador.serializarObjeto(mensagem);

        DatagramSocket socket = new DatagramSocket();
        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName("localhost"),
                portaNaveMae
        );

        socket.send(packet);
        socket.close();

        System.out.println("[GroundControl] Missão enviada: " + missao.idMissao);
    }

}
