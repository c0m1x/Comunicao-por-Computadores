package rover;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Calendar;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import lib.Mensagens.*;

/**
 * Cliente UDP para o Rover. Recebe HELLO/MISSION do servidor e responde conforme o protocolo.
 * Após receber uma missão, começa a enviar progressos periodicamente.
 */
public class ClienteUDP implements Runnable {

	private final MaquinaEstados.ContextoRover ctx;
	private final InetSocketAddress serverAddr;
	private final int localPort;
	private volatile boolean running = true;
	private DatagramSocket socket;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	public ClienteUDP(MaquinaEstados.ContextoRover ctx, String serverIp, int serverPort, int localPort) {
		this.ctx = ctx;
		this.serverAddr = new InetSocketAddress(serverIp, serverPort);
		this.localPort = localPort;
	}


    //Nota: isto está completamente na merda depois vejo

	@Override
	public void run() {
		try {
			socket = new DatagramSocket(localPort);
			byte[] buf = new byte[65507];
			System.out.println("Rover UDP escutando na porta " + socket.getLocalPort());

			while (running) {
				DatagramPacket packet = new DatagramPacket(buf, buf.length);
				socket.receive(packet);
				try (ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
					 ObjectInputStream ois = new ObjectInputStream(bais)) {
					Object obj = ois.readObject();
					if (obj instanceof MensagemUDP) {
						MensagemUDP msg = (MensagemUDP) obj;
						handleMensagem(msg, packet);
					} else {
						System.out.println("ClienteUDP: objeto desconhecido recebido: " + obj.getClass());
					}
				} catch (Exception e) {
					System.err.println("ClienteUDP: erro ao ler pacote: " + e.getMessage());
				}
			}
		} catch (Exception e) {
			System.err.println("ClienteUDP erro: " + e.getMessage());
		} finally {
			if (socket != null && !socket.isClosed()) socket.close();
			scheduler.shutdownNow();
		}
	}

	public void stop() {
		running = false;
		if (socket != null) socket.close();
		scheduler.shutdownNow();
	}

	private void handleMensagem(MensagemUDP msg, DatagramPacket packet) {
		TipoMensagem tipo = msg.header != null ? msg.header.tipo : null;
		if (tipo == TipoMensagem.MSG_HELLO) {
			System.out.println("[Rover] Recebido HELLO (missao=" + msg.header.idMissao + ")");
			// responder RESPONSE
			try {
				MensagemUDP resp = new MensagemUDP();
				resp.header.tipo = TipoMensagem.MSG_RESPONSE;
				resp.header.idEmissor = ctx.idRover;
				resp.header.idRecetor = msg.header.idEmissor; // a nave
				resp.header.idMissao = msg.header.idMissao;
				sendUdp(resp, new InetSocketAddress(packet.getAddress(), packet.getPort()));
				System.out.println("[Rover] RESPONSE enviado");
			} catch (Exception e) { System.err.println("Erro ao enviar RESPONSE: " + e.getMessage()); }
		} else if (tipo == TipoMensagem.MSG_MISSION) {
			System.out.println("[Rover] Recebido MISSION (id=" + msg.header.idMissao + ")");
			if (msg.payload instanceof PayloadMissao) {
				PayloadMissao m = (PayloadMissao) msg.payload;

				// aceitar missão
				ctx.receberMissao(m, msg.header.idMissao);

                //TODO: verificar se recebemos os fragmentos todos para saber como enviar o ack
				// enviar ACK (assume sem fragmentos em falta)
				try {
					MensagemUDP ack = new MensagemUDP();
					ack.header.tipo = TipoMensagem.MSG_ACK;
					ack.header.idEmissor = ctx.idRover;
					ack.header.idRecetor = msg.header.idEmissor;
					ack.header.idMissao = msg.header.idMissao;
					PayloadAck pa = new PayloadAck();
					pa.missingCount = 0;
					pa.missing = new int[0];
					ack.payload = pa;
					sendUdp(ack, new InetSocketAddress(packet.getAddress(), packet.getPort()));
					System.out.println("[Rover] ACK enviado para missão " + msg.header.idMissao);
				} catch (Exception e) { System.err.println("Erro ao enviar ACK: " + e.getMessage()); }

				// iniciar envio periódico de progresso com base no intervalo da missão
				long intervaloSegundos = 60; // default 60s
				try {
					if (m.intervaloAtualizacao != null) intervaloSegundos = m.intervaloAtualizacao.get(Calendar.MINUTE) * 60L;
				} catch (Exception ex) { /* ignore */ }
				final InetSocketAddress nmAddr = new InetSocketAddress(packet.getAddress(), packet.getPort());
				scheduler.scheduleAtFixedRate(() -> {
					try {
						PayloadProgresso prog = new PayloadProgresso();
						prog.idMissao = ctx.getMissaoId();
						prog.tempoDecorrido = java.util.Calendar.getInstance();
						prog.progressoPercentagem = ctx.getProgresso();
						MensagemUDP pm = new MensagemUDP();
						pm.header.tipo = TipoMensagem.MSG_MISSION; // reusar tipo
						pm.header.idEmissor = ctx.idRover;
						pm.header.idRecetor = 1; // nave
						pm.payload = prog;
						sendUdp(pm, nmAddr);
						System.out.println("[Rover] Enviado progresso missao=" + prog.idMissao + " prog=" + prog.progressoPercentagem);
					} catch (Exception e) {
						System.err.println("Erro ao enviar progresso: " + e.getMessage());
					}
				}, 1, Math.max(1, intervaloSegundos), TimeUnit.SECONDS);
			}
		} else if (tipo == TipoMensagem.MSG_ACK) {
			// possivelmente resposta a fragmentos (ignorar aqui)
			System.out.println("[Rover] Recebido ACK de NM");
		} else {
			System.out.println("[Rover] Mensagem UDP recebida: tipo=" + tipo);
		}
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
