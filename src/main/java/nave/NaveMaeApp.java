package nave;

public class NaveMaeApp {
    public static void main(String[] args) {
        System.out.println("=== Nave-Mãe - Sistema de Controlo ===");
        
        try {
            // Criar gestão de estado partilhada
            GestaoEstado estado = new GestaoEstado();
            
            // Iniciar os 3 servidores
            ServidorUDP servidorUDP = new ServidorUDP(estado);
            ServidorTCP servidorTCP = new ServidorTCP(5001, estado);
            //ServidorHTTP servidorHTTP = new ServidorHTTP(estado);
            
            // Arrancar servidores em threads separadas
            new Thread(() -> {
                try {
                    servidorUDP.run();
                } catch (Exception e) {
                    System.err.println("Erro no servidor UDP: " + e.getMessage());
                }
            }).start();
            
             
            new Thread(() -> {
                try {
                    servidorTCP.start();
                } catch (Exception e) {
                    System.err.println("Erro no servidor TCP: " + e.getMessage());
                }
            }).start();
            
            
            //servidorHTTP.start();
            
            System.out.println("Todos os servidores iniciados. Pressione CTRL+C para parar.");
            
        } catch (Exception e) {
            System.err.println("Erro ao iniciar Nave-Mãe: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

