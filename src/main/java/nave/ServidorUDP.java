package nave;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import lib.Mensagens.*;

/**
 * Servidor UDP da Nave-Mãe que implementa o fluxo de missão descrito em protocolos.md.
 *
 * Fluxo (resumido):
 * - NM envia HELLO ao rover
 * - Rover responde com RESPONSE
 * - NM envia MISSION (pode fragmentar)
 * - Rover responde com ACK (missing fragments)
 * - Após atribuição, Rover envia PayloadProgresso periodicamente; NM recebe e atualiza estado.
 *
 * Implementação: escuta UDP num socket, desserializa MensagemUDP e coloca em filas por rover.
 * Para atribuição de missão a um rover é lançada uma thread dedicada que realiza a troca HELLO/RESPONSE/MISSION/ACK
 * e depois aguarda/processa mensagens de progresso em paralelo.
 */
public class ServidorUDP {

	private final int port;
	private final gestaoEstado estado;
	private boolean running = false;

	private DatagramSocket socket;
	private final ExecutorService listeners = Executors.newCachedThreadPool();
	// fila por roverId para entrega de mensagens recebidas desse rover
	private final Map<Integer, BlockingQueue<MensagemUDP>> filasPorRover = new ConcurrentHashMap<>();

	public ServidorUDP(int port, gestaoEstado estado) {
		this.port = port;
		this.estado = estado;
	}

	public void stop() {
		running = false;
		if (socket != null && !socket.isClosed()) socket.close();
		listeners.shutdownNow();
	}

	public void start() throws Exception {
		socket = new DatagramSocket(port);
		running = true;
		System.out.println("Servidor UDP escuta na porta " + port);

		// Iniciar o listener principal
		listeners.submit(this::runListener);
}

	private void runListener() {
		try {
			byte[] buf = new byte[65507];
        while (running) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            trataPacoteRecebido(packet);
        }
    } catch (Exception e) {
        if (running)
            System.err.println("Listener UDP terminou com erro: " + e.getMessage());
    }
}

private void trataPacoteRecebido(DatagramPacket packet) {
    try (ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
         ObjectInputStream ois = new ObjectInputStream(bais)) {
        Object obj = ois.readObject();

        if (obj instanceof MensagemUDP msg) {
            int roverId = msg.header.idEmissor;
            BlockingQueue<MensagemUDP> q =
                filasPorRover.computeIfAbsent(roverId, k -> new LinkedBlockingQueue<>());
            q.offer(msg);
        } else {
            System.out.println("Recebido objeto UDP desconhecido: " + obj.getClass());
        }
    } catch (Exception e) {
        System.err.println("Erro ao desserializar pacote UDP: " + e.getMessage());
    }
}

	/**
	 * Atribui uma missão a um rover (endereço do rover + id) — executa o protocolo HELLO/RESPONSE/MISSION/ACK
	 * em background e passa a processar mensagens de progresso desse rover.
	 */
	public void atribuirMissaoAoRover(InetSocketAddress roverAddr, int roverId, Missao missao) {
    BlockingQueue<MensagemUDP> q = filasPorRover.computeIfAbsent(roverId, k -> new LinkedBlockingQueue<>());
    listeners.submit(() -> executarAtribuicaoMissao(roverAddr, roverId, missao, q));
}


    private void executarAtribuicaoMissao(InetSocketAddress roverAddr, int roverId, Missao missao, BlockingQueue<MensagemUDP> q) {
    try {
        // 1) Enviar HELLO
        MensagemUDP hello = new MensagemUDP();
        hello.header.tipo = TipoMensagem.MSG_HELLO;
        hello.header.idEmissor = 1;
        hello.header.idRecetor = roverId;
        hello.header.idMissao = missao.idMissao;
        sendUdp(hello, roverAddr);
        System.out.println("[NM->R" + roverId + "] HELLO (missao=" + missao.idMissao + ")");

        // 2) Esperar RESPONSE

        MensagemUDP resp = pollPorTipo(q, TipoMensagem.MSG_RESPONSE, 5000);
        if (resp == null) {
            System.out.println("Nenhuma RESPONSE do rover " + roverId + " — abortando atribuição");
            return;
        }
        System.out.println("[R" + roverId + "->NM] RESPONSE recebido");

        // 3) Enviar MISSION
        MensagemUDP missionMsg = new MensagemUDP();
        missionMsg.header.tipo = TipoMensagem.MSG_MISSION;
        missionMsg.header.idEmissor = 1;
        missionMsg.header.idRecetor = roverId;
        missionMsg.header.idMissao = missao.idMissao;
        missionMsg.header.seq = 1;
        missionMsg.header.totalFragm = 1;
        missionMsg.payload = missao.toPayload();
        sendUdp(missionMsg, roverAddr);
        System.out.println("[NM->R" + roverId + "] MISSION enviada (id=" + missao.idMissao + ")");

        // 4) Esperar ACK
        MensagemUDP ackMsg = pollPorTipo(q, TipoMensagem.MSG_ACK, 5000);
        if (ackMsg == null) {
            System.out.println("Nenhum ACK do rover " + roverId + " — abortando");
            return;
        }
        if (ackMsg.payload instanceof PayloadAck ack) {
            if (ack.missingCount == 0) {
                System.out.println("ACK completo do rover " + roverId + " — missão atribuída com sucesso");
            } else {
                System.out.println("ACK do rover " + roverId + " indica missing=" + ack.missingCount + " — retransmitindo");
                sendUdp(missionMsg, roverAddr);
            }
        }

        // 5) Registar e iniciar processamento de progresso
        estado.adicionarMissao(missao.idMissao, missao);
        listeners.submit(() -> processarProgressos(roverId, q, missao));

    } catch (Exception e) {
        System.err.println("Erro atribuindo missão ao rover " + roverId + ": " + e.getMessage());
    }
}

	private void processarProgressos(int roverId, BlockingQueue<MensagemUDP> q, nave.Missao missao) {
		System.out.println("Iniciado processador de progresso para rover " + roverId + " (missao=" + missao.idMissao + ")");
		long intervaloMinutos = 0;
		try {
			if (missao.intervaloAtualizacao != null) intervaloMinutos = missao.intervaloAtualizacao.get(java.util.Calendar.MINUTE);
		} catch (Exception e) { /* ignore */ }

		while (running) {
			try {
				MensagemUDP m = q.poll( (intervaloMinutos > 0 ? intervaloMinutos : 5) , TimeUnit.SECONDS);
				if (m == null) {
					// timeout — pode verificar se a missão terminou ou continuar a aguardar
					continue;
				}
				if (m.payload instanceof PayloadProgresso) {
					PayloadProgresso p = (PayloadProgresso) m.payload;
					System.out.println("[PROGRESS] rover=" + roverId + " missao=" + p.idMissao + " progresso=" + p.progressoPercentagem);
					// atualizar estado do rover na gestaoEstado (se existir)
					Rover r = estado.obterRover(roverId);
					if (r == null) {
						// criar snapshot mínimo
						r = new Rover(roverId, 0.0f, 0.0f);
						estado.adicionarRover(roverId, r);
					}
					r.idMissaoAtual = p.idMissao;
					r.progressoMissao = p.progressoPercentagem;
					// poderias também guardar timestamp, etc.
				}
			} catch (Exception e) {
				System.err.println("Erro no processador de progressos do rover " + roverId + ": " + e.getMessage());
			}
		}
	}

	private MensagemUDP pollPorTipo(BlockingQueue<MensagemUDP> q, TipoMensagem tipo, long timeoutMs) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			MensagemUDP m = q.poll(200, TimeUnit.MILLISECONDS);
			if (m == null) continue;
			if (m.header != null && m.header.tipo == tipo) return m;
		}
		return null;
	}

	private void sendUdp(MensagemUDP msg, InetSocketAddress addr) throws Exception {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			oos.writeObject(msg);
			oos.flush();
			byte[] data = baos.toByteArray();
			DatagramPacket packet = new DatagramPacket(data, data.length, addr.getAddress(), addr.getPort());
			socket.send(packet);
		}
	}

}

