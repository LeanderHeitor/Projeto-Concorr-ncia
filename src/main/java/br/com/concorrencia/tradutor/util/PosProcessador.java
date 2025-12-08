package br.com.concorrencia.tradutor.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ajustar concordância de gênero.
 * Corrige artigos e adjetivos baseado no gênero dos substantivos.
 * 
 */
public class PosProcessador {

    private static class InfoPalavra {
        String genero; // "m" (masculino), "f" (feminino), "n" (neutro)
        String tipo; // "subst", "adj", "art", "verb", etc

        InfoPalavra(String genero, String tipo) {
            this.genero = genero;
            this.tipo = tipo;
        }
    }

    private final Map<String, InfoPalavra> palavras;

    public PosProcessador() {
        this.palavras = new ConcurrentHashMap<>();
    }

    /**
     * Carrega informações de gênero de um arquivo.
     * Formato: palavra;gênero;tipo
     */
    public void carregarArquivo(String caminho) throws IOException {
        File arquivo = new File(caminho);

        if (!arquivo.exists()) {
            System.err.println("Arquivo de gêneros não encontrado: " + caminho);
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
                if (partes.length != 3)
                    continue;

                String palavra = partes[0].trim().toLowerCase();
                String genero = partes[1].trim();
                String tipo = partes[2].trim();

                palavras.put(palavra, new InfoPalavra(genero, tipo));
            }
        }

        System.out.println("Carregadas " + palavras.size() + " informações de gênero");
    }

    /**
     * Processa o texto ajustando concordância de gênero.
     * Ajusta artigos e adjetivos baseado em substantivos.
     */
    public String processar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }

        String[] palavrasTexto = texto.split("\\s+");
        StringBuilder resultado = new StringBuilder();

        for (int i = 0; i < palavrasTexto.length; i++) {
            String palavra = palavrasTexto[i];
            String palavraLimpa = limparPontuacao(palavra);
            String pontuacao = extrairPontuacao(palavra);

            // Ajustar artigo antes de substantivo
            if (i < palavrasTexto.length - 1) {
                String proximaPalavra = limparPontuacao(palavrasTexto[i + 1]);
                String palavraAjustada = ajustarArtigo(palavraLimpa, proximaPalavra);

                if (!palavraAjustada.equals(palavraLimpa)) {
                    resultado.append(palavraAjustada).append(pontuacao).append(" ");
                    continue;
                }
            }

            // Ajustar adjetivo após substantivo
            if (i > 0) {
                String palavraAnterior = limparPontuacao(palavrasTexto[i - 1]);
                String palavraAjustada = ajustarAdjetivo(palavraLimpa, palavraAnterior);

                if (!palavraAjustada.equals(palavraLimpa)) {
                    resultado.append(palavraAjustada).append(pontuacao).append(" ");
                    continue;
                }
            }

            // Sem ajuste necessário
            resultado.append(palavra).append(" ");
        }

        return resultado.toString().trim();
    }

    /**
     * Ajusta artigo baseado no gênero do substantivo seguinte.
     * Exemplo: "o noite" -> "a noite"
     */
    private String ajustarArtigo(String artigo, String substantivo) {
        InfoPalavra infoArtigo = palavras.get(artigo.toLowerCase());
        InfoPalavra infoSubst = palavras.get(substantivo.toLowerCase());

        // Se não for artigo ou não temos info do substantivo, não ajusta
        if (infoArtigo == null || !infoArtigo.tipo.equals("art") || infoSubst == null) {
            return artigo;
        }

        // Se substantivo não é substantivo, não ajusta
        if (!infoSubst.tipo.equals("subst")) {
            return artigo;
        }

        // Ajusta artigo baseado no gênero do substantivo
        String generoSubst = infoSubst.genero;

        if (artigo.equalsIgnoreCase("o") && generoSubst.equals("f")) {
            return preservarCapitalizacao(artigo, "a");
        }
        if (artigo.equalsIgnoreCase("a") && generoSubst.equals("m")) {
            return preservarCapitalizacao(artigo, "o");
        }
        if (artigo.equalsIgnoreCase("um") && generoSubst.equals("f")) {
            return preservarCapitalizacao(artigo, "uma");
        }
        if (artigo.equalsIgnoreCase("uma") && generoSubst.equals("m")) {
            return preservarCapitalizacao(artigo, "um");
        }

        return artigo;
    }

    /**
     * Ajusta adjetivo baseado no gênero do substantivo anterior.
     * Exemplo: "noite quieto" -> "noite quieta"
     */
    private String ajustarAdjetivo(String adjetivo, String substantivo) {
        InfoPalavra infoAdj = palavras.get(adjetivo.toLowerCase());
        InfoPalavra infoSubst = palavras.get(substantivo.toLowerCase());

        // Se não for adjetivo ou não temos info do substantivo, não ajusta
        if (infoAdj == null || !infoAdj.tipo.equals("adj") || infoSubst == null) {
            return adjetivo;
        }

        // Se substantivo não é substantivo, não ajusta
        if (!infoSubst.tipo.equals("subst")) {
            return adjetivo;
        }

        String generoSubst = infoSubst.genero;
        String generoAdj = infoAdj.genero;

        // Se adjetivo já está no gênero correto, não ajusta
        if (generoAdj.equals(generoSubst) || generoAdj.equals("n")) {
            return adjetivo;
        }

        // Tenta ajustar adjetivos conhecidos
        String adjLower = adjetivo.toLowerCase();

        // Masculino para feminino
        if (generoAdj.equals("m") && generoSubst.equals("f")) {
            String forma = converterParaFeminino(adjLower);
            return preservarCapitalizacao(adjetivo, forma);
        }

        // Feminino para masculino
        if (generoAdj.equals("f") && generoSubst.equals("m")) {
            String forma = converterParaMasculino(adjLower);
            return preservarCapitalizacao(adjetivo, forma);
        }

        return adjetivo;
    }

    /**
     * Converte adjetivo masculino para feminino.
     */
    private String converterParaFeminino(String adjetivo) {
        // Regras básicas de português
        if (adjetivo.endsWith("o")) {
            return adjetivo.substring(0, adjetivo.length() - 1) + "a";
        }
        // Adjetivos que terminam em consoante geralmente adicionam "a"
        // Mas existem muitas exceções, então só fazemos ajustes conhecidos
        return adjetivo;
    }

    /**
     * Converte adjetivo feminino para masculino.
     */
    private String converterParaMasculino(String adjetivo) {
        // Regras básicas de português
        if (adjetivo.endsWith("a")) {
            return adjetivo.substring(0, adjetivo.length() - 1) + "o";
        }
        return adjetivo;
    }

    /**
     * Preserva capitalização original ao substituir palavra.
     */
    private String preservarCapitalizacao(String original, String nova) {
        if (Character.isUpperCase(original.charAt(0))) {
            return Character.toUpperCase(nova.charAt(0)) + nova.substring(1);
        }
        return nova;
    }

    /**
     * Remove pontuação de uma palavra.
     */
    private String limparPontuacao(String palavra) {
        return palavra.replaceAll("[^a-zA-Zá-úÁ-Ú]", "");
    }

    /**
     * Extrai pontuação de uma palavra.
     */
    private String extrairPontuacao(String palavra) {
        String pontuacao = palavra.replaceAll("[a-zA-Zá-úÁ-Ú]", "");
        return pontuacao.isEmpty() ? "" : pontuacao;
    }

    /**
     * Retorna o número de palavras carregadas.
     */
    public int getTamanho() {
        return palavras.size();
    }
}
