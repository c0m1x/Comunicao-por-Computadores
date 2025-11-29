package nave;

public class NaveMaeApp {
    public static void main(String[] args) {
        System.out.println("=== Nave-Mãe - Sistema de Controlo ===");
        
        try {
            GestaoEstado estado = new GestaoEstado();

            // Iniciar os 3 servidores
            ServidorUDP servidorUDP = new ServidorUDP(estado);
            ServidorTCP servidorTCP = new ServidorTCP(estado);
            ServidorHTTP servidorHTTP = new ServidorHTTP(estado);
            
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
                    servidorTCP.run();
                } catch (Exception e) {
                    System.err.println("Erro no servidor TCP: " + e.getMessage());
                }
            }).start();
            
                         
            new Thread(() -> {
                try {
                    servidorHTTP.run();
                } catch (Exception e) {
                    System.err.println("Erro no servidor HTTP: " + e.getMessage());
                }
            }).start();

            System.out.println("Todos os servidores iniciados. Pressione CTRL+C para parar.");
            
        } catch (Exception e) {
            System.err.println("Erro ao iniciar Nave-Mãe: " + e.getMessage());
            e.printStackTrace();
        }
    }
    //TODO: confirmar se os serviços estão a parar corretamente
}

