package br.com.concorrencia.tradutor.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Dicionario {

    private final Map<String, String> mapaTraducoes;

    public Dicionario() {
        this.mapaTraducoes = new ConcurrentHashMap<>();
    }

    public void adicionar(String original, String traducao) {
        if (original != null && traducao != null) {
            mapaTraducoes.put(original.toLowerCase(), traducao.toLowerCase());
        }
    }

    public String traduzir(String palavra) {
        return mapaTraducoes.getOrDefault(palavra.toLowerCase(), palavra);
    }

    public int getTamanho() {
        return mapaTraducoes.size();
    }

    public String buscar(String chave) {
        return mapaTraducoes.get(chave);   // retorna o valor ou null se não existir
    }

    public void carregarArquivo(String caminho) throws IOException {
    File arquivo = new File(caminho);

    if (!arquivo.exists()) {
        throw new FileNotFoundException("Arquivo não encontrado: " + caminho);
    }

    try (BufferedReader br = new BufferedReader(new FileReader(arquivo))) {
        String linha;

        while ((linha = br.readLine()) != null) {
            linha = linha.trim();

            // ignora linhas em branco ou comentários
            if (linha.isEmpty() || linha.startsWith("#")) continue;

            // formato esperado: palavra;tradução
            String[] partes = linha.split(";");

            if (partes.length != 2) {
                System.err.println("Linha inválida no arquivo " + arquivo.getName() + ": " + linha);
                continue;
            }

            String original = partes[0].trim();
            String traducao = partes[1].trim();

            if (!original.isEmpty() && !traducao.isEmpty()) {
                adicionar(original, traducao); // usa seu método existente
            }
        }
    }
}

}