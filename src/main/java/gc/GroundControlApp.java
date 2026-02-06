package gc;

import lib.mensagens.payloads.PayloadMissao;

import java.util.Scanner;
import gc.models.MissaoModel;

/**
 * interface interativa
 * Agora usa HTTP para TODAS as operações (observação + criação de missões)
 */
public class GroundControlApp {

    public static void main(String[] args) {

        // Detectar endpoint da API:
        // - Se fornecido como argumento: usar esse
        // - Caso contrário: usar localhost:8080
        String apiEndpoint = "http://localhost:8080";
        
        if (args.length > 0) {
            apiEndpoint = args[0];
            // Adicionar http:// se não tiver protocolo
            if (!apiEndpoint.contains("://")) {
                apiEndpoint = "http://" + apiEndpoint;
            }
        }
        
        GroundControlAPI api = new GroundControlAPI(apiEndpoint);
        Scanner sc = new Scanner(System.in);

        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║      Ground Control - Interface        ║");
        System.out.println("║   Conectado a: " + String.format("%-23s", apiEndpoint) + "║");
        System.out.println("╚════════════════════════════════════════╝\n");

        while (true) {
            System.out.println("\n┌─── Ground Control Menu ───────────────┐");
            System.out.println("│ 1. Listar Rovers                      │");
            System.out.println("│ 2. Ver Rover (por ID)                 │");
            System.out.println("│ 3. Listar Missões                     │");
            System.out.println("│ 4. Ver Progresso de Missão            │");
            System.out.println("│ 5. Criar Nova Missão                  │");
            System.out.println("│ 0. Sair                               │");
            System.out.println("└───────────────────────────────────────┘");
            System.out.print("Escolha: ");

            int op = -1;
            try {
                op = sc.nextInt();
                sc.nextLine();
            } catch (Exception e) {
                sc.nextLine();
                System.out.println("⚠ Entrada inválida!");
                continue;
            }

            try {
                switch (op) {

                    case 1 -> System.out.println(api.listarRovers());
                    case 2 -> {
                        System.out.print("ID Rover: ");
                        int r = sc.nextInt();
                        sc.nextLine();

                        System.out.println(api.obterRover(r));
                    }
                    case 3 -> System.out.println(api.listarMissoes());
                    case 4 -> {
                        System.out.print("ID Missão: ");
                        int m = sc.nextInt();
                        sc.nextLine();

                        System.out.println(api.obterProgresso(m));
                    }
                    case 5 -> {
                        System.out.println("\n┌─── Criar Nova Missão ───────────┐");
                        
                        System.out.print("│ ID da Missão: ");
                        int id = sc.nextInt();
                        sc.nextLine();
                        
                        System.out.print("│ Tarefa: ");
                        String tarefa = sc.nextLine();
                        
                        System.out.print("│ Prioridade: ");
                        int prioridade = sc.nextInt();
                        
                        System.out.print("│ Coordenada X inicial: ");
                        float x1 = sc.nextFloat();
                        
                        System.out.print("│ Coordenada Y inicial: ");
                        float y1 = sc.nextFloat();
                        
                        System.out.print("│ Coordenada X final: ");
                        float x2 = sc.nextFloat();
                        
                        System.out.print("│ Coordenada Y final: ");
                        float y2 = sc.nextFloat();
                        sc.nextLine();
                        
                        System.out.println("└─────────────────────────────────┘");
                        
                        // Criar modelo
                        MissaoModel missao = new MissaoModel();
                        missao.idMissao = id;
                        missao.tarefa = tarefa;
                        missao.estado = "PENDENTE";
                        missao.x1 = x1;
                        missao.y1 = y1;
                        missao.x2 = x2;
                        missao.y2 = y2;
                        missao.prioridade = prioridade;
                                                
                        // Enviar via HTTP POST
                        String resposta = api.criarMissao(missao);
                        System.out.println("✓ " + resposta);
                    }
                    
                    case 0 -> {
                        System.out.println("\nA encerrar Ground Control...");
                        sc.close();
                        System.exit(0);
                    }
                    
                    default -> System.out.println("⚠ Opção inválida!");
                }
                
            } catch (Exception e) {
                System.err.println("✗ Erro: " + e.getMessage());
                // e.printStackTrace(); // Descomentar para debug
            }
        }
    }
}
