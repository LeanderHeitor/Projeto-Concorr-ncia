package br.com.concorrencia.tradutor.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Dicionario {

    private final Map<String, String> mapaTraducoes;

    public Dicionario() {
        this.mapaTraducoes = new ConcurrentHashMap<>();
    }

    public void adicionar(String original, String traducao) {
        if (original != null && traducao != null) {
            mapaTraducoes.put(original.toLowerCase(), traducao);
        }
    }

    public String traduzir(String palavra) {
        return mapaTraducoes.getOrDefault(palavra.toLowerCase(), palavra);
    }

    public int getTamanho() {
        return mapaTraducoes.size();
    }
}