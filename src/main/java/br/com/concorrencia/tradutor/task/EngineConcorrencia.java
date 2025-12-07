package br.com.concorrencia.tradutor.task;

import br.com.concorrencia.tradutor.model.Dicionario;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class EngineConcorrencia {

    private final ExecutorService poolLeitura;
    private final ExecutorService poolAnalise;
    private final ExecutorService poolTraducao;
    private final Dicionario dicionario;
    private CyclicBarrier barreiraInicializacao;

    public EngineConcorrencia() {
        this.dicionario = new Dicionario();
        this.poolLeitura = Executors.newFixedThreadPool(2);
        this.poolAnalise = Executors.newFixedThreadPool(2);
        this.poolTraducao = Executors.newCachedThreadPool();
    }

    public void inicializar(Runnable onConcluido) {
        this.barreiraInicializacao = new CyclicBarrier(2, onConcluido);

        poolLeitura.submit(() -> {
            try {
                carregarDicionarios();
                barreiraInicializacao.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                Thread.currentThread().interrupt();
            }
        });

        poolAnalise.submit(() -> {
            try {
                carregarDicionarios();
                barreiraInicializacao.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Carrega automaticamente todos os arquivos .txt da pasta /dicionarios/
     */
    private void carregarDicionarios() {
        try {
            File pasta = new File("recursos");

            if (pasta.exists() && pasta.isDirectory()) {
                File[] arquivos = pasta.listFiles((dir, nome) -> nome.endsWith(".txt"));

                if (arquivos != null) {
                    for (File arq : arquivos) {
                        try {
                            dicionario.carregarArquivo(arq.getAbsolutePath());
                            System.out.println("Carregado: " + arq.getAbsolutePath());
                        } catch (IOException e) {
                            System.err.println("Erro ao carregar " + arq.getName() + ": " + e.getMessage());
                        }
                    }
                }
            } else {
                System.err.println("Pasta 'recursos' não encontrada. Crie a pasta no diretório raiz do projeto.");
            }

        } catch (Exception e) {
            System.err.println("Erro inesperado ao carregar dicionários: " + e.getMessage());
        }
    }

    public String traduzirParalelo(List<String> palavras) {
        List<Future<String>> futures = new ArrayList<>();
        StringBuilder resultado = new StringBuilder();

        for (String palavra : palavras) {
            futures.add(poolTraducao.submit(new TradutorTask(palavra, dicionario)));
        }

        for (Future<String> future : futures) {
            try {
                resultado.append(future.get()).append(" ");
            } catch (InterruptedException | ExecutionException e) {
                resultado.append("ERROR ");
            }
        }
        return resultado.toString().trim();
    }

    public String traduzirSerial(List<String> palavras) {
        StringBuilder resultado = new StringBuilder();
        for (String palavra : palavras) {
            resultado.append(dicionario.traduzir(palavra)).append(" ");
        }
        return resultado.toString().trim();
    }

    public String gerarRelatorioDesempenho(List<String> amostraTexto) {
        long inicioParalelo = System.nanoTime();
        traduzirParalelo(amostraTexto);
        long fimParalelo = System.nanoTime();
        double tempoParalelo = (fimParalelo - inicioParalelo) / 1_000_000.0;

        long inicioSerial = System.nanoTime();
        traduzirSerial(amostraTexto);
        long fimSerial = System.nanoTime();
        double tempoSerial = (fimSerial - inicioSerial) / 1_000_000.0;

        return String.format(
                "=== RELATÓRIO DE PERFORMANCE ===\n" +
                        "Threads Ativas: %d\n" +
                        "Tempo Serial: %.4f ms\n" +
                        "Tempo Paralelo: %.4f ms\n" +
                        "Ganho/Perda: %.4f ms\n",
                Thread.activeCount(), tempoSerial, tempoParalelo, (tempoSerial - tempoParalelo)
        );
    }

    public void encerrar() {
        poolLeitura.shutdown();
        poolAnalise.shutdown();
        poolTraducao.shutdown();
    }

    public String traduzirTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }

        String[] palavras = texto.split("\\s+");
        List<String> lista = List.of(palavras);
        return traduzirParalelo(lista);
    }
}
