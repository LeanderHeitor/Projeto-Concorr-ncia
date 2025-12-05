package br.com.concorrencia.tradutor.task;

import br.com.concorrencia.tradutor.model.Dicionario;
import java.util.concurrent.Callable;

public class TradutorTask implements Callable<String> {

    private final String palavra;
    private final Dicionario dicionario;

    public TradutorTask(String palavra, Dicionario dicionario) {
        this.palavra = palavra;
        this.dicionario = dicionario;
    }

    @Override
    public String call() {
        return dicionario.traduzir(palavra);
    }
}