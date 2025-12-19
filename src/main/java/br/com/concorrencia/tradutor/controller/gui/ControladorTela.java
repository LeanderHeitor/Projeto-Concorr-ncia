package br.com.concorrencia.tradutor.controller.gui;

import br.com.concorrencia.tradutor.task.EngineConcorrencia;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class ControladorTela {

    @FXML private BorderPane rootPane;
    @FXML private Label lblStatusMotor;
    @FXML private Label lblArquivoInfo;
    @FXML private TextArea txtEntrada;
    @FXML private TextArea txtSaida;
    @FXML private TilePane panelThreads;
    @FXML private ProgressBar progressoTraducao;
    @FXML private Label lblProgresso;
    @FXML private Button btnTraduzir;
    @FXML private Button btnCarregarArquivo;
    @FXML private Button btnInicializar;
    @FXML private Spinner<Integer> spinnerThreads;

    // Resultados
    @FXML private Label lblTempoSerial;
    @FXML private Label lblTempoParalelo;
    @FXML private Label lblSpeedup; // Mudou nome de lblGanho para lblSpeedup no FXML? Se não, ajuste aqui.
    // Se no FXML ainda estiver lblGanho, use:
    @FXML private Label lblGanho; // Vou assumir que no FXML pode estar como lblGanho ou lblSpeedup

    private EngineConcorrencia engine;
    private Timeline monitorTimeline;
    private final Map<Long, ThreadCard> threadCards = new HashMap<>();
    private final List<Node> activeParticles = new ArrayList<>();

    @FXML
    public void initialize() {
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 32, 4);
        spinnerThreads.setValueFactory(valueFactory);

        btnTraduzir.setDisable(true);
        btnCarregarArquivo.setDisable(true);

        // Garante que a barra de progresso comece zerada
        progressoTraducao.setProgress(0);

        iniciarMonitoramentoVisual();
    }

    @FXML
    protected void onInicializarClick() {
        int numThreads = spinnerThreads.getValue();
        lblStatusMotor.setText("CARREGANDO DICIONÁRIOS...");
        lblStatusMotor.setStyle("-fx-text-fill: #f39c12;");

        if (engine != null) engine.encerrar();
        panelThreads.getChildren().clear();
        threadCards.clear();

        engine = new EngineConcorrencia(numThreads);

        // Inicializa em thread separada para carregar arquivos
        engine.inicializar(() -> Platform.runLater(() -> {
            lblStatusMotor.setText("ONLINE (" + numThreads + " Threads)");
            lblStatusMotor.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;");
            btnInicializar.setText("Reiniciar Engine");
            btnTraduzir.setDisable(false);
            btnCarregarArquivo.setDisable(false);
        }));
    }

    @FXML
    protected void onCarregarArquivoClick() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texto", "*.txt"));
        File arquivo = fileChooser.showOpenDialog(rootPane.getScene().getWindow());

        if (arquivo != null) {
            try {
                String conteudo = Files.readString(arquivo.toPath());
                txtEntrada.setText(conteudo);
                lblArquivoInfo.setText(arquivo.getName());
            } catch (Exception e) {
                lblArquivoInfo.setText("Erro na leitura");
            }
        }
    }

    @FXML
    protected void onTraduzirClick() {
        String texto = txtEntrada.getText();
        if (texto.isEmpty() || engine == null) return;

        resetarUI();
        List<String> palavras = Arrays.asList(texto.split("\\s+"));

        Task<String> tarefaTraducao = new Task<>() {
            @Override
            protected String call() throws Exception {
                // 1. Gera Relatório (Roda Serial vs Paralelo para comparar)
                updateMessage("Calculando Speedup...");
                String relatorio = engine.gerarRelatorioDesempenho(palavras);

                // 2. Traduz de verdade para exibir
                updateMessage("Finalizando tradução...");
                String traducaoFinal = engine.traduzirParalelo(palavras);

                // Gambiarra para passar dois resultados: concatenamos com um separador único
                return relatorio + "###SEP###" + traducaoFinal;
            }
        };

        Timeline particles = criarAnimacaoParticulas();
        particles.play();
        lblProgresso.textProperty().bind(tarefaTraducao.messageProperty());

        tarefaTraducao.setOnSucceeded(e -> {
            particles.stop();
            limparParticulas();
            lblProgresso.textProperty().unbind();

            String[] resultados = tarefaTraducao.getValue().split("###SEP###");
            if (resultados.length >= 2) {
                parseAndUpdateStats(resultados[0]);
                txtSaida.setText(resultados[1]);
            }

            progressoTraducao.setProgress(1.0);
            lblProgresso.setText("Concluído!");
            habilitarBotoes(true);
        });

        tarefaTraducao.setOnFailed(e -> {
            particles.stop();
            limparParticulas();
            txtSaida.setText("Erro: " + tarefaTraducao.getException().getMessage());
            habilitarBotoes(true);
        });

        new Thread(tarefaTraducao).start();
    }

    private void parseAndUpdateStats(String relatorio) {
        // Se no FXML o ID for lblGanho, mapeamos aqui para facilitar
        Label targetSpeedup = (lblSpeedup != null) ? lblSpeedup : lblGanho;

        String[] linhas = relatorio.split("\n");
        for (String linha : linhas) {
            if (linha.contains("Serial:")) lblTempoSerial.setText(linha.split(":")[1].trim());
            if (linha.contains("Paralelo:")) lblTempoParalelo.setText(linha.split(":")[1].trim());
            if (linha.contains("Speedup:")) {
                if(targetSpeedup != null) targetSpeedup.setText(linha.split(":")[1].trim());
            }
        }
    }

    private void resetarUI() {
        txtSaida.clear();
        progressoTraducao.setProgress(-1);
        habilitarBotoes(false);
    }

    private void habilitarBotoes(boolean enable) {
        btnTraduzir.setDisable(!enable);
        btnCarregarArquivo.setDisable(!enable);
        btnInicializar.setDisable(!enable);
    }

    // --- Monitoramento Visual das Threads ---

    private void iniciarMonitoramentoVisual() {
        monitorTimeline = new Timeline(new KeyFrame(Duration.millis(200), event -> {
            // Filtra threads criadas pelo nosso pool
            List<Thread> workers = Thread.getAllStackTraces().keySet().stream()
                    .filter(t -> t.getName().contains("pool") || t.getName().contains("thread"))
                    .filter(t -> t.getThreadGroup() != null && !t.getThreadGroup().getName().equals("system"))
                    .sorted(Comparator.comparing(Thread::getName))
                    .collect(Collectors.toList());

            // Só mostra se tivermos inicializado a engine e tiver threads de pool ativas
            if (engine == null) return;

            for (Thread t : workers) {
                // Filtro para pegar apenas as threads do ExecutorService (geralmente pool-X-thread-Y)
                if (!t.getName().contains("pool-")) continue;

                if (!threadCards.containsKey(t.getId())) {
                    ThreadCard card = new ThreadCard(t.getName());
                    threadCards.put(t.getId(), card);
                    panelThreads.getChildren().add(card);
                }
                threadCards.get(t.getId()).atualizarEstado(t.getState());
            }

            // Cleanup
            threadCards.keySet().removeIf(id -> workers.stream().noneMatch(t -> t.getId() == id));
            panelThreads.getChildren().removeIf(node -> {
                // Remove visualmente se a thread morreu (simplificado)
                return false;
            });
        }));
        monitorTimeline.setCycleCount(Timeline.INDEFINITE);
        monitorTimeline.play();
    }

    public void stop() {
        if(engine != null) engine.encerrar();
        if(monitorTimeline != null) monitorTimeline.stop();
    }

    // --- Componente UI Interno (Card) ---
    private static class ThreadCard extends VBox {
        private final Label lblState;
        private final Rectangle statusIndicator;

        public ThreadCard(String name) {
            this.setPrefSize(120, 50);
            this.setStyle("-fx-background-color: white; -fx-border-color: #bdc3c7; -fx-border-radius: 4; -fx-padding: 5;");
            this.setAlignment(Pos.CENTER_LEFT);
            Label lblName = new Label(name.replace("pool-", "T-"));
            lblName.setStyle("-fx-font-size: 9px; -fx-font-weight: bold;");

            HBox box = new HBox(5);
            statusIndicator = new Rectangle(8, 8, Color.GRAY);
            lblState = new Label("WAIT");
            lblState.setStyle("-fx-font-size: 8px;");
            box.getChildren().addAll(statusIndicator, lblState);

            this.getChildren().addAll(lblName, box);
        }

        public void atualizarEstado(Thread.State state) {
            lblState.setText(state.toString());
            if (state == Thread.State.RUNNABLE) statusIndicator.setFill(Color.web("#2ecc71"));
            else if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) statusIndicator.setFill(Color.web("#f1c40f"));
            else statusIndicator.setFill(Color.web("#e74c3c"));
        }
    }

    private Timeline criarAnimacaoParticulas() {
        // Retorna timeline vazia para não quebrar se não quiser animação complexa agora
        return new Timeline();
    }

    private void limparParticulas() {
        rootPane.getChildren().removeAll(activeParticles);
        activeParticles.clear();
    }
}