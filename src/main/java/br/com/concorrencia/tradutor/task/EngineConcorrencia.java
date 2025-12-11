package br.com.concorrencia.tradutor.task;

import br.com.concorrencia.tradutor.model.Dicionario;
import br.com.concorrencia.tradutor.util.ProcessadorExpressoes;
import br.com.concorrencia.tradutor.util.PosProcessador;

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
    private final ProcessadorExpressoes processadorExpressoes;
    private final PosProcessador posProcessador;
    private CyclicBarrier barreiraInicializacao;
    private final int numThreadsTraducao;

    public EngineConcorrencia() {
        this(4);
    }

    public EngineConcorrencia(int numThreadsTraducao) {
        this.numThreadsTraducao = numThreadsTraducao;
        this.dicionario = new Dicionario();
        this.processadorExpressoes = new ProcessadorExpressoes();
        this.posProcessador = new PosProcessador();
        this.poolLeitura = Executors.newFixedThreadPool(2);
        this.poolAnalise = Executors.newFixedThreadPool(2);
        this.poolTraducao = Executors.newFixedThreadPool(numThreadsTraducao);
    }

    public int getNumThreadsTraducao() {
        return numThreadsTraducao;
    }

    public void inicializar(Runnable onConcluido) {
        this.barreiraInicializacao = new CyclicBarrier(2, () -> {
            // Encerra pools de inicialização após uso
            poolLeitura.shutdown();
            poolAnalise.shutdown();
            // Executa callback do usuário
            if (onConcluido != null) {
                onConcluido.run();
            }
        });

        poolLeitura.submit(() -> {
            try {
                carregarDicionarios();
            } catch (Exception e) {
                System.err.println("Erro ao carregar dicionários: " + e.getMessage());
            } finally {
                try {
                    barreiraInicializacao.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        poolAnalise.submit(() -> {
            try {
                carregarDicionarios();
            } catch (Exception e) {
                System.err.println("Erro ao carregar dicionários: " + e.getMessage());
            } finally {
                try {
                    barreiraInicializacao.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    /**
     * Carrega automaticamente todos os recursos:
     * - Dicionários (arquivos .txt)
     * - Expressões multi-palavra
     * - Informações de gênero
     */
    private void carregarDicionarios() {
        try {
            File pasta = new File("recursos");

            if (pasta.exists() && pasta.isDirectory()) {
                // Carrega expressões e gêneros primeiro
                try {
                    processadorExpressoes.carregarArquivo("recursos/expressoes.txt");
                } catch (IOException e) {
                    System.err.println("Aviso: expressoes.txt não encontrado - tradução sem expressões multi-palavra");
                }

                try {
                    posProcessador.carregarArquivo("recursos/generos.txt");
                } catch (IOException e) {
                    System.err.println("Aviso: generos.txt não encontrado - tradução sem ajuste de concordância");
                }

                // Carrega apenas dicionarios EN->PT (excluindo outros arquivos)
                File[] arquivos = pasta.listFiles((dir, nome) ->
                    nome.endsWith(".txt") &&
                    !nome.equals("expressoes.txt") &&
                    !nome.equals("generos.txt") &&
                    !nome.equals("portugues_ingles.txt") &&
                    !nome.equals("livro_entrada.txt") &&
                    !nome.equals("dicionario.txt") &&
                    !nome.contains("traduzido"));

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
            System.err.println("Erro inesperado ao carregar recursos: " + e.getMessage());
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
        // poolLeitura e poolAnalise já foram encerrados após inicialização
        poolTraducao.shutdown();

        try {
            poolTraducao.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Traduz texto usando sistema de 3 camadas para melhor qualidade.
     *
     * CAMADA 1: Processamento de expressoes multi-palavra
     * CAMADA 2: Traducao palavra-por-palavra (paralela)
     * CAMADA 3: Pos-processamento linguistico (concordancia)
     *
     * @param texto Texto a ser traduzido
     * @return Texto traduzido com melhor qualidade
     */
    public String traduzirTextoMelhorado(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }

        // CAMADA 1: Substituir expressoes multi-palavra
        String textoComExpressoes = processadorExpressoes.processar(texto);

        // CAMADA 2: Traduzir palavras restantes (paralelo)
        String[] palavras = textoComExpressoes.split("\\s+");
        List<String> lista = List.of(palavras);
        String traducaoParcial = traduzirParalelo(lista);

        // CAMADA 3: Ajustar concordancia de genero
        String traducaoFinal = posProcessador.processar(traducaoParcial);

        return traducaoFinal;
    }

    /**
     * Traduz texto usando a nova implementação melhorada (3 camadas).
     * Mantém assinatura original para compatibilidade.
     *
     * @param texto Texto a ser traduzido
     * @return Texto traduzido
     */
    public String traduzirTexto(String texto) {
        return traduzirTextoMelhorado(texto);
    }

    /**
     * Traduz texto usando apenas palavra-por-palavra (sem melhorias).
     * Útil para comparação de performance.
     *
     * @param texto Texto a ser traduzido
     * @return Texto traduzido (método antigo)
     */
    public String traduzirTextoSimples(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }

        String[] palavras = texto.split("\\s+");
        List<String> lista = List.of(palavras);
        return traduzirParalelo(lista);
    }
}
