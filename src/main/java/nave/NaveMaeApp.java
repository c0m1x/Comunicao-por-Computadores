package nave;

public class NaveMaeApp {
    public static void main(String[] args) {
        System.out.println("=== Nave-Mãe - Sistema de Controlo ===");
        
        try {
            // Criar gestão de estado partilhada
            gestaoEstado estado = new gestaoEstado();
            
            // Iniciar os 3 servidores
            ServidorUDP servidorUDP = new ServidorUDP(estado);

            //TODO: iniciar os outros servidores aqui 
            //ServidorTCP servidorTCP = new ServidorTCP(estado);
            //ServidorHTTP servidorHTTP = new ServidorHTTP(estado);
            
            // Arrancar servidores em threads separadas
            new Thread(() -> {
                try {
                    servidorUDP.run();
                } catch (Exception e) {
                    System.err.println("Erro no servidor UDP: " + e.getMessage());
                }
            }).start();
            
            /* 
            new Thread(() -> {
                try {
                    servidorTCP.start();
                } catch (Exception e) {
                    System.err.println("Erro no servidor TCP: " + e.getMessage());
                }
            }).start();
            */
            
            //servidorHTTP.start();
            
            System.out.println("Todos os servidores iniciados. Pressione CTRL+C para parar.");
            
        } catch (Exception e) {
            System.err.println("Erro ao iniciar Nave-Mãe: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

