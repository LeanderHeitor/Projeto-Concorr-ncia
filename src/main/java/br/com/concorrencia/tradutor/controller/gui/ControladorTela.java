package br.com.concorrencia.tradutor.controller.gui;

import br.com.concorrencia.tradutor.task.EngineConcorrencia;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ControladorTela {

    @FXML private Label lblStatusMotor;
    @FXML private Label lblArquivoInfo;
    @FXML private TextArea txtEntrada;
    @FXML private TextArea txtSaida;
    @FXML private ListView<String> listMonitorThreads;
    @FXML private ProgressBar progressoTraducao;
    @FXML private Button btnTraduzir;
    @FXML private Button btnCarregarArquivo;
    @FXML private Label lblTempoSerial;
    @FXML private Label lblTempoParalelo;
    @FXML private Label lblGanho;
    @FXML private Label lblThreadsAtivas;

    private EngineConcorrencia engine;
    private Timeline monitorTimeline;

    @FXML
    public void initialize() {
        this.engine = new EngineConcorrencia();
        listMonitorThreads.setItems(FXCollections.observableArrayList());

        lblStatusMotor.setText("Inicializando Pools e Carregando Corpus...");
        btnTraduzir.setDisable(true);
        btnCarregarArquivo.setDisable(true);

        engine.inicializar(() -> {
            Platform.runLater(() -> {
                lblStatusMotor.setText("Motor Concorrente Pronto");
                lblStatusMotor.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;");
                btnTraduzir.setDisable(false);
                btnCarregarArquivo.setDisable(false);
            });
        });

        iniciarMonitoramento();
    }

    @FXML
    protected void onCarregarArquivoClick() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Selecione o arquivo de texto");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivos de Texto", "*.txt"));

        Stage stage = (Stage) btnCarregarArquivo.getScene().getWindow();
        File arquivo = fileChooser.showOpenDialog(stage);

        if (arquivo != null) {
            try {
                String conteudo = Files.readString(arquivo.toPath());
                txtEntrada.setText(conteudo);
                lblArquivoInfo.setText("Arquivo: " + arquivo.getName() + " (" + conteudo.length() + " chars)");
            } catch (Exception e) {
                lblArquivoInfo.setText("Erro ao ler arquivo");
            }
        }
    }

    @FXML
    protected void onTraduzirClick() {
        String texto = txtEntrada.getText();
        if (texto.isEmpty()) return;

        txtSaida.clear();
        progressoTraducao.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        btnTraduzir.setDisable(true);
        btnCarregarArquivo.setDisable(true);
        lblTempoSerial.setText("-");
        lblTempoParalelo.setText("-");
        lblGanho.setText("-");

        List<String> palavras = Arrays.asList(texto.split("\\s+"));

        Task<String> tarefaTraducao = new Task<>() {
            @Override
            protected String call() throws Exception {
                return engine.gerarRelatorioDesempenho(palavras);
            }
        };

        tarefaTraducao.setOnSucceeded(e -> {
            String relatorioBruto = tarefaTraducao.getValue();
            parseAndUpdateStats(relatorioBruto);

            String traducaoFinal = engine.traduzirParalelo(palavras);
            txtSaida.setText(traducaoFinal);

            progressoTraducao.setProgress(1.0);
            btnTraduzir.setDisable(false);
            btnCarregarArquivo.setDisable(false);
        });

        tarefaTraducao.setOnFailed(e -> {
            txtSaida.setText("Erro Crítico na Thread de Tradução: " + tarefaTraducao.getException().getMessage());
            btnTraduzir.setDisable(false);
            btnCarregarArquivo.setDisable(false);
        });

        new Thread(tarefaTraducao).start();
    }

    private void parseAndUpdateStats(String relatorio) {
        try {
            String[] linhas = relatorio.split("\n");
            for (String linha : linhas) {
                if (linha.contains("Serial:")) lblTempoSerial.setText(linha.split(":")[1].trim());
                if (linha.contains("Paralelo:")) lblTempoParalelo.setText(linha.split(":")[1].trim());
                if (linha.contains("Ganho")) lblGanho.setText(linha.split(":")[1].trim());
            }
        } catch (Exception e) {
            lblTempoSerial.setText("Erro stats");
        }
    }

    private void iniciarMonitoramento() {
        monitorTimeline = new Timeline(new KeyFrame(Duration.millis(100), event -> {
            Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();

            List<String> threadsRelevantes = allThreads.keySet().stream()
                    .filter(t -> t.getName().toLowerCase().contains("pool") ||
                            t.getName().toLowerCase().contains("worker") ||
                            t.getName().toLowerCase().contains("executor"))
                    .map(t -> String.format("%s | %s", t.getName(), t.getState()))
                    .sorted()
                    .collect(Collectors.toList());

            listMonitorThreads.setItems(FXCollections.observableArrayList(threadsRelevantes));
            lblThreadsAtivas.setText(String.valueOf(threadsRelevantes.size()));
        }));
        monitorTimeline.setCycleCount(Timeline.INDEFINITE);
        monitorTimeline.play();
    }
}