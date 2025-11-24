package rover;

import lib.Rover;

/**
 * Aplicação minimalista do Rover.
 * 
 * Arquitetura:
 * - Cliente UDP: recebe HELLO/MISSION da Nave-Mãe
 * - Cliente TCP: envia telemetria periódica
 * 
 * Uso: java RoverApp [id] [posXinicial] [posYinicial] [portaLocal]
 */
public class RoverApp {

    public static void main(String[] args) {
        System.out.println("=== Rover - Sistema de Exploração ===");
        
        // Parâmetros do rover (valores padrão)
        int roverId = 1;
        float posX = 0.0f;
        float posY = 0.0f;
        int portaLocal = 5001;

        // Parse argumentos da linha de comando
        if (args.length >= 3) {
            try {
                roverId = Integer.parseInt(args[0]);
                posX = Float.parseFloat(args[1]);
                posY = Float.parseFloat(args[2]);
                if (args.length >= 4) {
                    portaLocal = Integer.parseInt(args[3]);
                }
            } catch (NumberFormatException e) {
                System.err.println("Erro: formato inválido");
                System.err.println("Uso: RoverApp <id> <posX> <posY> [portaLocal]");
                System.err.println("Usando valores padrão: id=1, pos=(0,0), porta=5001");
            }
        }

        System.out.printf("Rover ID: %d | Posição: (%.2f, %.2f) | Porta: %d%n", 
                         roverId, posX, posY, portaLocal);

        try {
            MaquinaEstados maquina = new MaquinaEstados(roverId, posX, posY);
            
            // Criar clientes
            ClienteUDP clienteUDP = new ClienteUDP(roverId, maquina);

            //NOTA: DUVIDA PARA STOR, para testar no core temos que usar os ips de lá e nao este localhost, 
            // para isso vi duas opções (nao sei se há mais), ou passar por argumentos o ip e porta da nave, ou meter aqui hardcoded
            // ou então fazer um sistema de descoberta automática (mais complexo) isto é (segue-se a explicação do copilot): 
            /*
            Passo 1 – Descoberta (broadcast/multicast):

    Rover envia uma mensagem TCP/UDP de tipo HELLO_ROVER para:
    ou um IP de broadcast (255.255.255.255:porta ou 10.0.X.255:porta, se permitido),
    ou um endereço multicast bem conhecido,
    ou ainda um IP fixo de “bootstrap” do segmento local.
    Essa mensagem só precisa conter o roverId e, opcionalmente, a porta em que o rover aceita algo.


        Passo 2 – Resposta da Nave‑Mãe:
    A Nave‑Mãe está a escutar nessa porta em todas as interfaces.
    Quando recebe HELLO_ROVER, responde com uma mensagem HELLO_ACK diretamente para sourceIP:sourcePort do rover.
    Nessa resposta, a nave envia:
    IP e porta onde espera receber telemetria (ipNave, portaTelemetria).
    Eventualmente outros parâmetros (intervalo de envio, etc.).

        Passo 3 – Fixar endpoints:

    Rover guarda ipNave e portaTelemetria vindos do HELLO_ACK e daí em diante:
    abre/usa o ClienteTCP ou ClienteUDP apontando para esse IP/porta;
    não precisa mais de broadcast.
    Nave‑Mãe também passa a associar aquele roverId ao IP/porta de origem para futuras mensagens.

    SE: o stor disser que é melhor fazer isto em vez de hardcoded
    PERGUNTAR AO STOR: será melhor em tcp ou udp? 
      
    Do ponto de vista de implementação, cabem duas escolhas:

    HELLO em UDP, telemetria depois em TCP:

    Já tens ClienteUDP e ServidorUDP; basta:
    Definir um tipo de payload HELLO_ROVER/HELLO_ACK.
    Na nave, ServidorUDP responde com o IP/porta de telemetria.
    No rover, ClienteUDP recebe o HELLO_ACK e cria o ClienteTCP com o IP/porta recebidos.


    Tudo em TCP (um pequeno “servidor descoberta”):

    Um socket TCP na nave, a escutar numa porta fixa.
    Rovers conectam‑se lá, trocam HELLO/ACK, fecham a ligação e, depois, abrem outra ligação na porta “oficial” de telemetria.

    No ambiente CORE, isto resolve o teu problema porque:
    O rover não precisa saber o IP da nave à partida, basta conhecer a porta e o mecanismo (broadcast/multicast/local).
    A nave responde a partir do IP certo da interface pela qual chegou o pacote, logo o rover aprende automaticamente o endpoint correto.
*/

            ClienteTCP clienteTCP = new ClienteTCP(maquina.getContexto(), "127.0.0.1", 5001);
            
            // Arrancar clientes em threads separadas
            new Thread(() -> {
                try {
                    clienteUDP.run();
                } catch (Exception e) {
                    System.err.println("Erro no cliente UDP: " + e.getMessage());
                }
            }, "RoverUDP").start();

            
            new Thread(() -> {
                try {
                    clienteTCP.run();
                } catch (Exception e) {
                    System.err.println("Erro no cliente TCP: " + e.getMessage());
                }
            }, "RoverTCP").start(); 
            
             

            // Shutdown hook para encerramento 
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[SHUTDOWN] Encerrando rover...");
                maquina.getContexto().ativo = false;
            }));
            
            System.out.println("Rover iniciado. Pressione CTRL+C para parar.");
            
        } catch (Exception e) {
            System.err.println("Erro ao iniciar Rover: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

