package api.gc;

import lib.mensagens.payloads.PayloadMissao;
import api.gc.models.MissaoModel;

import java.util.Scanner;

/**
 * interface interativa
 */
public class GroundControlApp {

    public static void main(String[] args) {

        GroundControlAPI api = new GroundControlAPI("http://localhost:8080");
        GestaoEnvioMissoes missionSender = new GestaoEnvioMissoes(9001); // porta MissionLink

        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.println("\n=== Ground Control ===");
            System.out.println("1. Listar Rovers");
            System.out.println("2. Ver Rover");
            System.out.println("3. Listar Missões");
            System.out.println("4. Ver Progresso Missão");
            System.out.println("5. Enviar Nova Missão");
            System.out.println("0. Sair");
            System.out.print("Escolha: ");

            int op = sc.nextInt();
            sc.nextLine();

            try {
                switch (op) {

                    case 1 -> System.out.println(api.listarRovers());
                    case 2 -> {
                        System.out.print("ID Rover: ");
                        int r = sc.nextInt();
                        System.out.println(api.obterRover(r));
                    }
                    case 3 -> System.out.println(api.listarMissoes());
                    case 4 -> {
                        System.out.print("ID Missão: ");
                        int m = sc.nextInt();
                        System.out.println(api.obterProgresso(m));
                    }
                    case 5 -> {
                        System.out.print("ID Missão: ");
                        int id = sc.nextInt(); sc.nextLine();
                        System.out.print("Tarefa: ");
                        String tarefa = sc.nextLine();

                        MissaoModel m = new MissaoModel();
                        m.idMissao = id;
                        m.tarefa = tarefa;
                        m.estado = "PENDENTE";
                        m.x1 = 0;
                        m.y1 = 0;
                        m.x2 = 0;
                        m.y2 = 0;
                        m.prioridade = 1;

                        missionSender.enviarMissao(m);
                    }
                    case 0 -> {
                        System.exit(0);
                    }
                }
            } catch (Exception e) {
                System.err.println("Erro: " + e.getMessage());
            }
        }
    }
}
