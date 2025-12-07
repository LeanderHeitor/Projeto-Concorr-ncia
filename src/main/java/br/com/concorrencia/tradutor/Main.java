package br.com.concorrencia.tradutor;

import br.com.concorrencia.tradutor.task.EngineConcorrencia;

public class Main {
    public static void main(String[] args) {

        EngineConcorrencia engine = new EngineConcorrencia();

        // inicializa o dicionário carregando os arquivos da pasta "recursos"
        engine.inicializar(() -> System.out.println("Inicialização concluída! Dicionários carregados."));

        // dá um pequeno delay para garantir leitura dos arquivos (pelo pool)
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // texto de teste
        String texto = "You can do it if you try";

        System.out.println("\nTexto original:");
        System.out.println(texto);

        String traducao = engine.traduzirTexto(texto);

        System.out.println("\nTexto traduzido:");
        System.out.println(traducao);

        engine.encerrar();
    }
}
