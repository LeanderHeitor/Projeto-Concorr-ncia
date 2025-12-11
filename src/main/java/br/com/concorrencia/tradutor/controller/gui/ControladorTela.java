package br.com.concorrencia.tradutor.controller.gui;

import br.com.concorrencia.tradutor.task.EngineConcorrencia;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
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
    @FXML private Label lblTempoSerial;
    @FXML private Label lblTempoParalelo;
    @FXML private Label lblGanho;

    private EngineConcorrencia engine;
    private Timeline monitorTimeline;

    private final Map<Long, ThreadCard> threadCards = new HashMap<>();

    @FXML
    public void initialize() {
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 64, 4);
        spinnerThreads.setValueFactory(valueFactory);

        btnTraduzir.setDisable(true);
        btnCarregarArquivo.setDisable(true);

        iniciarMonitoramentoVisual();
    }

    @FXML
    protected void onInicializarClick() {
        int numThreads = spinnerThreads.getValue();

        // Feedback visual imediato
        lblStatusMotor.setText("CARREGANDO...");
        lblStatusMotor.setStyle("-fx-text-fill: #f39c12;");

        if (engine != null) engine.encerrar();
        panelThreads.getChildren().clear();
        threadCards.clear();

        Task<Void> initTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                engine = new EngineConcorrencia(numThreads);
                engine.inicializar(() -> {});
                Thread.sleep(500);
                return null;
            }
        };

        initTask.setOnSucceeded(e -> {
            lblStatusMotor.setText("ONLINE (" + numThreads + " Threads)");
            lblStatusMotor.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;");
            btnInicializar.setText("Reiniciar Engine");
            btnTraduzir.setDisable(false);
            btnCarregarArquivo.setDisable(false);
        });

        new Thread(initTask).start();
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
                lblArquivoInfo.setText(arquivo.getName() + " (" + (conteudo.length()/1024) + " KB)");
            } catch (Exception e) {
                lblArquivoInfo.setText("Erro ao ler arquivo");
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
                updateMessage("Processando " + palavras.size() + " palavras...");
                return engine.gerarRelatorioDesempenho(palavras);
            }
        };

        Timeline particleTimeline = criarAnimacaoParticulas();
        particleTimeline.play();

        tarefaTraducao.setOnSucceeded(e -> {
            particleTimeline.stop();
            limparParticulas();

            String relatorioBruto = tarefaTraducao.getValue();
            parseAndUpdateStats(relatorioBruto);

            String traducaoFinal = engine.traduzirParalelo(palavras);
            txtSaida.setText(traducaoFinal);

            progressoTraducao.setProgress(1.0);
            lblProgresso.setText("Concluído!");
            habilitarBotoes(true);
        });

        tarefaTraducao.setOnFailed(e -> {
            particleTimeline.stop();
            txtSaida.setText("Erro: " + tarefaTraducao.getException().getMessage());
            habilitarBotoes(true);
        });

        new Thread(tarefaTraducao).start();
    }

    private void iniciarMonitoramentoVisual() {
        monitorTimeline = new Timeline(new KeyFrame(Duration.millis(100), event -> {
            Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();

            List<Thread> workers = allThreads.keySet().stream()
                    .filter(t -> t.getName().contains("pool") || t.getName().contains("Worker") || t.getName().contains("ForkJoin"))
                    .sorted(Comparator.comparing(Thread::getName))
                    .collect(Collectors.toList());

            // 3. Atualiza ou Cria Cards
            for (Thread t : workers) {
                if (!threadCards.containsKey(t.getId())) {
                    ThreadCard card = new ThreadCard(t.getName());
                    threadCards.put(t.getId(), card);
                    panelThreads.getChildren().add(card);
                }
                threadCards.get(t.getId()).atualizarEstado(t.getState());
            }

            List<Long> idsAtivos = workers.stream().map(Thread::getId).toList();
            List<Long> idsParaRemover = new ArrayList<>();
            for(Long id : threadCards.keySet()) {
                if(!idsAtivos.contains(id)) idsParaRemover.add(id);
            }
            idsParaRemover.forEach(id -> {
                panelThreads.getChildren().remove(threadCards.get(id));
                threadCards.remove(id);
            });

        }));
        monitorTimeline.setCycleCount(Timeline.INDEFINITE);
        monitorTimeline.play();
    }

    private static class ThreadCard extends VBox {
        private final Label lblName;
        private final Label lblState;
        private final Rectangle statusIndicator;

        public ThreadCard(String threadName) {
            this.setPrefSize(140, 80);
            this.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #ecf0f1; -fx-border-radius: 8; -fx-padding: 10;");
            this.setEffect(new DropShadow(5, Color.rgb(0,0,0,0.1)));
            this.setAlignment(Pos.CENTER_LEFT);
            this.setSpacing(5);

            lblName = new Label(threadName);
            lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 10;");

            HBox statusBox = new HBox(5);
            statusBox.setAlignment(Pos.CENTER_LEFT);

            statusIndicator = new Rectangle(10, 10);
            statusIndicator.setArcWidth(10);
            statusIndicator.setArcHeight(10);

            lblState = new Label("INIT");
            lblState.setStyle("-fx-font-size: 9;");

            statusBox.getChildren().addAll(statusIndicator, lblState);
            this.getChildren().addAll(lblName, statusBox);
        }

        public void atualizarEstado(Thread.State state) {
            lblState.setText(state.toString());

            Color cor;
            boolean animar = false;

            switch (state) {
                case RUNNABLE:
                    cor = Color.web("#2ecc71"); //verde
                    animar = true;
                    break;
                case WAITING:
                case TIMED_WAITING:
                    cor = Color.web("#f1c40f"); //amarelo
                    break;
                case BLOCKED:
                    cor = Color.web("#e74c3c"); //vermelho
                    break;
                default:
                    cor = Color.GRAY;
            }

            statusIndicator.setFill(cor);

            if (animar) {
                this.setStyle("-fx-background-color: #f0fdf4; -fx-background-radius: 8; -fx-border-color: #2ecc71; -fx-border-width: 2;");
                if (this.getEffect() instanceof DropShadow) {
                    Glow glow = new Glow(0.8);
                    this.setEffect(glow);
                }
            } else {
                this.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #bdc3c7; -fx-border-width: 1;");
                this.setEffect(new DropShadow(5, Color.rgb(0,0,0,0.1)));
            }
        }
    }

    private void resetarUI() {
        txtSaida.clear();
        lblTempoSerial.setText("-");
        lblTempoParalelo.setText("-");
        lblGanho.setText("-");
        progressoTraducao.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        habilitarBotoes(false);
    }

    private void habilitarBotoes(boolean enable) {
        btnTraduzir.setDisable(!enable);
        btnCarregarArquivo.setDisable(!enable);
        btnInicializar.setDisable(!enable);
    }

    private void parseAndUpdateStats(String relatorio) {
        try {
            String[] linhas = relatorio.split("\n");
            for (String linha : linhas) {
                if (linha.contains("Serial:")) lblTempoSerial.setText(linha.split(":")[1].trim());
                if (linha.contains("Paralelo:")) lblTempoParalelo.setText(linha.split(":")[1].trim());
                if (linha.contains("Ganho")) lblGanho.setText(linha.split(":")[1].trim());
            }
        } catch (Exception ignore) {}
    }

    private final List<Node> activeParticles = new ArrayList<>();

    private Timeline criarAnimacaoParticulas() {
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(150), e -> {
            if (threadCards.isEmpty()) return;

            List<ThreadCard> activeCards = threadCards.values().stream()
                    .filter(c -> c.lblState.getText().equals("RUNNABLE"))
                    .toList();

            if (activeCards.isEmpty()) activeCards = new ArrayList<>(threadCards.values());
            if (activeCards.isEmpty()) return;

            ThreadCard target = activeCards.get(new Random().nextInt(activeCards.size()));

            Circle particle = new Circle(4, Color.web("#3498db"));
            rootPane.getChildren().add(particle);
            activeParticles.add(particle);

            double startX = txtEntrada.localToScene(txtEntrada.getWidth()/2, txtEntrada.getHeight()/2).getX();
            double startY = txtEntrada.localToScene(txtEntrada.getWidth()/2, txtEntrada.getHeight()/2).getY();

            double targetX = target.localToScene(target.getWidth()/2, target.getHeight()/2).getX();
            double targetY = target.localToScene(target.getWidth()/2, target.getHeight()/2).getY();

            particle.setTranslateX(startX);
            particle.setTranslateY(startY);

            TranslateTransition tt = new TranslateTransition(Duration.millis(600), particle);
            tt.setToX(targetX);
            tt.setToY(targetY);
            tt.setOnFinished(evt -> {
                rootPane.getChildren().remove(particle);
                activeParticles.remove(particle);
            });
            tt.play();
        }));
        tl.setCycleCount(Timeline.INDEFINITE);
        return tl;
    }

    private void limparParticulas() {
        rootPane.getChildren().removeAll(activeParticles);
        activeParticles.clear();
    }

    public void stop() {
        if(engine != null) engine.encerrar();
        if(monitorTimeline != null) monitorTimeline.stop();
    }
}