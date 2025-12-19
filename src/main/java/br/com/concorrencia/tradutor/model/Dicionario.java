package br.com.concorrencia.tradutor.model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Dicionario {

    private final Map<String, String> mapaTraducoes;

    public Dicionario() {
        this.mapaTraducoes = new ConcurrentHashMap<>();
    }

    public void adicionar(String original, String traducao) {
        if (original != null && traducao != null) {
            mapaTraducoes.put(original.toLowerCase().trim(), traducao.toLowerCase().trim());
        }
    }

    public String traduzir(String palavra) {
        if (palavra == null) return "";
        String traducao = mapaTraducoes.get(palavra.toLowerCase());

        return traducao != null ? traducao : palavra;
    }

    public int getTamanho() {
        return mapaTraducoes.size();
    }

    public void carregarArquivo(File arquivo) throws IOException {
        if (!arquivo.exists()) {
            throw new FileNotFoundException("Arquivo não encontrado: " + arquivo.getAbsolutePath());
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(arquivo), StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = br.readLine()) != null) {
                linha = linha.trim();
                if (linha.isEmpty() || linha.startsWith("#") || linha.startsWith("//")) continue;

                String[] partes = linha.split(";");
                if (partes.length >= 2) {
                    adicionar(partes[0], partes[1]);
                }
            }
        }
    }
}