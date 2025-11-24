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
            
            // Registar shutdown hook para parar os servidores corretamente
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[NaveMaeApp] Encerrando servidores...");
                servidorUDP.parar();
                servidorTCP.parar();
                servidorHTTP.parar();
                System.out.println("[NaveMaeApp] Todos os servidores encerrados.");
            }));
            
            // Arrancar servidores em threads separadas
            Thread threadUDP = new Thread(() -> {
                try {
                    servidorUDP.run();
                } catch (Exception e) {
                    System.err.println("Erro no servidor UDP: " + e.getMessage());
                }
            });
            threadUDP.setName("ServidorUDP");
            threadUDP.start();
        
            Thread threadTCP = new Thread(() -> {
                try {
                    servidorTCP.run();
                } catch (Exception e) {
                    System.err.println("Erro no servidor TCP: " + e.getMessage());
                }
            });
            threadTCP.setName("ServidorTCP");
            threadTCP.start();
            
            Thread threadHTTP = new Thread(() -> {
                try {
                    servidorHTTP.run();
                } catch (Exception e) {
                    System.err.println("Erro no servidor HTTP: " + e.getMessage());
                }
            });
            threadHTTP.setName("ServidorHTTP");
            threadHTTP.start();

            //este println pode ser removido depois de testado
            System.out.println("Todos os servidores iniciados. Pressione CTRL+C para parar.");
            
            // Aguardar threads (mantém o programa rodando)
            threadUDP.join();
            threadTCP.join();
            threadHTTP.join();
            
        } catch (Exception e) {
            System.err.println("Erro ao iniciar Nave-Mãe: " + e.getMessage());
            e.printStackTrace();
        }
    }
}