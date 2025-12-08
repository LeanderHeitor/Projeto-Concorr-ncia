package br.com.concorrencia.tradutor.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcessadorExpressoes {

    private final Map<String, String> expressoes;
    private final Map<String, Integer> tamanhos; // quantas palavras tem cada expressão

    public ProcessadorExpressoes() {
        this.expressoes = new ConcurrentHashMap<>();
        this.tamanhos = new ConcurrentHashMap<>();
    }

    /**
     * Carrega expressões de um arquivo.
     * Formato: expressao_original;traducao
     */
    public void carregarArquivo(String caminho) throws IOException {
        File arquivo = new File(caminho);

        if (!arquivo.exists()) {
            System.err.println("Arquivo de expressões não encontrado: " + caminho);
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(arquivo))) {
            String linha;

            while ((linha = br.readLine()) != null) {
                linha = linha.trim();

                // Ignora linhas vazias ou comentários
                if (linha.isEmpty() || linha.startsWith("#"))
                    continue;

                String[] partes = linha.split(";");
                if (partes.length != 2)
                    continue;

                String original = partes[0].trim().toLowerCase();
                String traducao = partes[1].trim();

                // Conta quantas palavras tem a expressão
                int numPalavras = original.split("\\s+").length;

                expressoes.put(original, traducao);
                tamanhos.put(original, numPalavras);
            }
        }

        System.out.println("Carregadas " + expressoes.size() + " expressões multi-palavra");
    }

    /**
     * Processa o texto substituindo expressões conhecidas.
     */
    public String processar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }

        String resultado = texto;

        // Processa expressões de 3 palavras primeiro
        resultado = substituirExpressoes(resultado, 3);

        // Depois expressões de 2 palavras
        resultado = substituirExpressoes(resultado, 2);

        return resultado;
    }

    /**
     * Substitui expressões de um tamanho específico.
     * Preserva pontuação e capitalização.
     */
    private String substituirExpressoes(String texto, int tamanho) {
        String resultado = texto;

        for (Map.Entry<String, String> entrada : expressoes.entrySet()) {
            String expressaoOriginal = entrada.getKey();
            String traducao = entrada.getValue();

            // Só processa expressões do tamanho especificado
            if (tamanhos.get(expressaoOriginal) != tamanho) {
                continue;
            }

            // Cria padrão regex case-insensitive que captura a pontuação após
            // Exemplo: "the day" -> "(?i)\bthe\s+day\b"
            String regex = "(?i)\\b" + expressaoOriginal.replace(" ", "\\s+") + "\\b";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultado);

            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String match = matcher.group();

                // Preserva capitalização (se a expressão original começava com maiúscula)
                String traducaoFinal = traducao;
                if (Character.isUpperCase(match.charAt(0))) {
                    traducaoFinal = capitalizarPrimeira(traducao);
                }

                matcher.appendReplacement(sb, Matcher.quoteReplacement(traducaoFinal));
            }
            matcher.appendTail(sb);
            resultado = sb.toString();
        }

        return resultado;
    }

    /**
     * Capitaliza a primeira letra de uma string.
     */
    private String capitalizarPrimeira(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /**
     * Retorna o número de expressões carregadas.
     */
    public int getTamanho() {
        return expressoes.size();
    }
}
