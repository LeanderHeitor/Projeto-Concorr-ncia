package br.com.concorrencia.tradutor.task;

import br.com.concorrencia.tradutor.model.Dicionario;
import br.com.concorrencia.tradutor.util.PosProcessador;
import br.com.concorrencia.tradutor.util.ProcessadorExpressoes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class EngineConcorrencia {

    private ExecutorService poolTraducao;
    private final Dicionario dicionario;
    private int numThreads;
    private final PosProcessador posProcessador;
    private final ProcessadorExpressoes processadorExpressoes;

    public EngineConcorrencia(int numThreads) {
        this.numThreads = numThreads;
        this.dicionario = new Dicionario();
        this.poolTraducao = Executors.newFixedThreadPool(numThreads);
        this.posProcessador = new PosProcessador();
        this.processadorExpressoes = new ProcessadorExpressoes();
    }

    public String aplicarExpressoes(String texto) {
        return processadorExpressoes.processar(texto);
    }

    public String aplicarGramatica(String texto) {
        return posProcessador.processar(texto);
    }

    public void inicializar(Runnable onConcluido) {
        new Thread(() -> {
            carregarRecursos();
            if (onConcluido != null) {
                onConcluido.run();
            }
        }).start();
    }

    private void carregarRecursos() {
        File pasta = new File("recursos");

        if (pasta.exists() && pasta.isDirectory()) {
            File[] arquivos = pasta.listFiles((dir, name) -> name.endsWith(".txt"));
            if (arquivos != null) {
                for (File f : arquivos) {
                    if (f.getName().contains("generos") || f.getName().contains("expressoes")) continue;
                    try {
                        System.out.println("Carregando dicionário: " + f.getName());
                        dicionario.carregarArquivo(f);
                    } catch (Exception e) {
                        System.err.println("Erro ao ler " + f.getName() + ": " + e.getMessage());
                    }
                }
                try {
                    processadorExpressoes.carregarArquivo("recursos/expressoes.txt");
                    posProcessador.carregarArquivo("recursos/generos.txt");
                } catch (Exception e) {
                    System.err.println("Erro ao carregar utilitários: " + e.getMessage());
                }
            }
        } else {
            System.err.println("AVISO: Pasta 'recursos' não encontrada.");
            try {
                File padrao = new File("ingles_portugues.txt");
                if (padrao.exists()) dicionario.carregarArquivo(padrao);
                else carregarDadosTeste();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("Total de palavras carregadas: " + dicionario.getTamanho());
    }

    private void carregarDadosTeste() {
        dicionario.adicionar("hello", "olá");
        dicionario.adicionar("world", "mundo");
        dicionario.adicionar("concurrency", "concorrência");
        dicionario.adicionar("java", "java");
        dicionario.adicionar("speedup", "aceleração");
        dicionario.adicionar("is", "é");
        dicionario.adicionar("the", "o");
        dicionario.adicionar("best", "melhor");
    }

    public String traduzirParalelo(List<String> palavras) {
        List<Future<String>> futures = new ArrayList<>();
        StringBuilder resultado = new StringBuilder();

        for (String p : palavras) {
            futures.add(poolTraducao.submit(new TradutorTask(p, dicionario)));
        }

        for (Future<String> f : futures) {
            try {
                resultado.append(f.get()).append(" ");
            } catch (InterruptedException | ExecutionException e) {
                resultado.append("ERRO ");
            }
        }

        return resultado.toString().trim();
    }

    public String traduzirSerial(List<String> palavras) {
        StringBuilder resultado = new StringBuilder();
        TradutorTask task;
        //executa tudo na mesma thread
        for (String p : palavras) {
            task = new TradutorTask(p, dicionario);
            resultado.append(task.call()).append(" ");
        }
        return resultado.toString().trim();
    }

    public String gerarRelatorioDesempenho(List<String> amostraTexto) {
        //TESTE SERIAL
        long inicioSerial = System.nanoTime();
        traduzirSerial(amostraTexto);
        long fimSerial = System.nanoTime();
        double tempoSerial = (fimSerial - inicioSerial) / 1_000_000.0; //converte para ms

        //TESTE PARALELO
        long inicioParalelo = System.nanoTime();
        traduzirParalelo(amostraTexto);
        long fimParalelo = System.nanoTime();
        double tempoParalelo = (fimParalelo - inicioParalelo) / 1_000_000.0;

        //CÁLCULO SPEEDUP
        double speedup = (tempoParalelo > 0) ? (tempoSerial / tempoParalelo) : 0.0;

        return String.format(
                "Serial: %.2f ms\n" +
                        "Paralelo: %.2f ms\n" +
                        "Speedup: %.2fx",
                tempoSerial, tempoParalelo, speedup
        );
    }

    public void encerrar() {
        poolTraducao.shutdown();
        try {
            if (!poolTraducao.awaitTermination(3, TimeUnit.SECONDS)) {
                poolTraducao.shutdownNow();
            }
        } catch (InterruptedException e) {
            poolTraducao.shutdownNow();
        }
    }
}