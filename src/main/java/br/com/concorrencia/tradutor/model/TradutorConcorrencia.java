package br.com.concorrencia.tradutor.model;

import java.util.*;
import java.util.concurrent.*;

public class TradutorConcorrencia {

    private final Dicionario dicionario;
    private final ExecutorService executor;

    public TradutorConcorrencia(Dicionario dicionario, int numThreads) {
        this.dicionario = dicionario;
        this.executor = Executors.newFixedThreadPool(numThreads);
    }

    public String traduzirTexto(String texto) throws InterruptedException, ExecutionException {

        // 1. Separar palavras
        String[] palavras = texto.split("\\s+");

        // 2. Criar lista de tarefas
        List<Callable<String>> tarefas = new ArrayList<>();

        for (String p : palavras) {
            tarefas.add(() -> traduzPalavra(p));
        }

        // 3. Executar tarefas concorrentes
        List<Future<String>> resultados = executor.invokeAll(tarefas);

        // 4. Juntar tradução final
        StringBuilder traducao = new StringBuilder();
        for (Future<String> f : resultados) {
            traducao.append(f.get()).append(" ");
        }

        return traducao.toString().trim();
    }

    private String traduzPalavra(String palavra) {
        // procura no dicionário
        String traducao = dicionario.buscar(palavra.toLowerCase());
        return (traducao != null) ? traducao : palavra; // fallback
    }

    public void fechar() {
        executor.shutdown();
    }
}
