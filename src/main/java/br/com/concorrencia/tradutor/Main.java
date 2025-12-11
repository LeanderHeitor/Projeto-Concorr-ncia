package br.com.concorrencia.tradutor;

import br.com.concorrencia.tradutor.controller.gui.TradutorConcorrencia;
import br.com.concorrencia.tradutor.task.EngineConcorrencia;
import javafx.application.Application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        Application.launch(TradutorConcorrencia.class, args);

        try {
            System.out.println("=".repeat(50));
            System.out.println("TRADUTOR CONCORRENTE");
            System.out.println("=".repeat(50));

            Scanner scanner = new Scanner(System.in);
            System.out.println("\nQuantas threads deseja usar para traducao?");
            System.out.println("(Recomendado: 1, 2, 4, 8, 16 para testes de benchmark)");
            System.out.print("Threads: ");
            int numThreads = scanner.nextInt();

            EngineConcorrencia engine = new EngineConcorrencia(numThreads);

            System.out.println("\nInicializando engine...");
            engine.inicializar(() -> System.out.println("Dicionarios carregados!"));
            Thread.sleep(2000);

            System.out.println("\nLendo livro_entrada.txt...");
            String textoOriginal = Files.readString(Path.of("recursos/livro_entrada.txt"));
            System.out.println("Palavras: " + textoOriginal.split("\\s+").length);

            System.out.println("\nTraduzindo...");
            long inicio = System.currentTimeMillis();
            String traducao = engine.traduzirTexto(textoOriginal);
            long fim = System.currentTimeMillis();

            Path arquivoSaida = Path.of("recursos/livro_traduzido.txt");
            Files.writeString(arquivoSaida, traducao);

            double tempoSegundos = (fim - inicio) / 1000.0;
            int totalPalavras = textoOriginal.split("\\s+").length;
            double palavrasPorSegundo = totalPalavras / tempoSegundos;

            System.out.println("\n" + "=".repeat(50));
            System.out.println("RELATORIO DE DESEMPENHO");
            System.out.println("=".repeat(50));
            System.out.println("Threads usadas: " + engine.getNumThreadsTraducao());
            System.out.println("Palavras traduzidas: " + totalPalavras);
            System.out.println("Tempo total: " + String.format("%.3f", tempoSegundos) + " segundos");
            System.out.println("Palavras por segundo: " + String.format("%.2f", palavrasPorSegundo));
            System.out.println("Arquivo salvo: " + arquivoSaida);
            System.out.println("=".repeat(50));

            System.out.println("\n--- PREVIEW DA TRADUCAO ---");
            String[] linhas = traducao.split("\n");
            int linhasParaMostrar = Math.min(15, linhas.length);
            for (int i = 0; i < linhasParaMostrar; i++) {
                System.out.println(linhas[i]);
            }
            if (linhas.length > linhasParaMostrar) {
                System.out.println("...");
            }

            engine.encerrar();

        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
