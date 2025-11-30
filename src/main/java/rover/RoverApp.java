package rover;

/**
 * Aplicação minimalista do Rover.
 * 
 * Arquitetura:
 * - Cliente UDP: recebe HELLO/MISSION da Nave-Mãe
 * - Cliente TCP: envia telemetria periódica
 * 
 * Uso: java RoverApp [id] [posX] [posY] [ipNave] [portaTcpNave] [portaUdp]
 * 
 * Valores por defeito: id=1, pos=(0,0), ipNave=127.0.0.1, portaTcpNave=5001, portaUdp=9010+id
 */
public class RoverApp {

    public static void main(String[] args) {
        System.out.println("=== Rover - Sistema de Exploração ===");
        
        // Parâmetros do rover (valores padrão)
        int roverId = 1;
        float posX = 0.0f;
        float posY = 0.0f;
        String ipNave = "127.0.0.1";
        int portaTcpNave = 5001; // Porta do servidor TCP da nave
        int portaUdp = -1; // -1 significa calcular automaticamente (9010 + id)

        // Parse argumentos da linha de comando
        if (args.length >= 1) {
            try {
                roverId = Integer.parseInt(args[0]);
                if (args.length >= 2) {
                    posX = Float.parseFloat(args[1]);
                }
                if (args.length >= 3) {
                    posY = Float.parseFloat(args[2]);
                }
                if (args.length >= 4) {
                    ipNave = args[3];
                }
                if (args.length >= 5) {
                    portaTcpNave = Integer.parseInt(args[4]);
                }
                if (args.length >= 6) {
                    portaUdp = Integer.parseInt(args[5]);
                }

            } catch (NumberFormatException e) {
                System.err.println("Erro: formato inválido");
                System.err.println("Uso: RoverApp <id> [posX] [posY] [ipNave] [portaTcpNave] [portaUdp]");
                System.err.println("Usando valores padrão: id=1, pos=(0,0), ipNave=127.0.0.1, portaTcpNave=5001, portaUdp=9010+id");
            }
        }

        // Calcular porta UDP se não foi especificada
        if (portaUdp <= 0) {
            portaUdp = 9010 + roverId;
        }
        
        System.out.printf("Rover ID: %d | Posição: (%.2f, %.2f) | IP Nave: %s | Porta TCP Nave: %d | Porta UDP: %d%n", 
                         roverId, posX, posY, ipNave, portaTcpNave, portaUdp);

        try {
            MaquinaEstados maquina = new MaquinaEstados(roverId, posX, posY);
            
            // Criar clientes
            ClienteUDP clienteUDP = new ClienteUDP(roverId, portaUdp, maquina);
            ClienteTCP clienteTCP = new ClienteTCP(maquina.getContexto(), ipNave, portaTcpNave);
            
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

