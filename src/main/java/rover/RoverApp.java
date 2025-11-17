package rover;

import rover.MaquinaEstados.ContextoRover;

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

            //TODO: iniciar os outros clientes aqui 
            // ClienteTCP clienteTCP = new ClienteTCP(roverId, maquina, portaLocal);
            
            // Arrancar clientes em threads separadas
            new Thread(() -> {
                try {
                    clienteUDP.run();
                } catch (Exception e) {
                    System.err.println("Erro no cliente UDP: " + e.getMessage());
                }
            }, "RoverUDP").start();

          /*  
            new Thread(() -> {
                try {
                    clienteTCP.run();
                } catch (Exception e) {
                    System.err.println("Erro no cliente TCP: " + e.getMessage());
                }
            }, "RoverTCP").start(); 
            
            */ 

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

