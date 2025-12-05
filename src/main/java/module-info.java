module br.com.concorrencia.tradutor {
    requires javafx.controls;
    requires javafx.fxml;


    opens br.com.concorrencia.tradutor to javafx.fxml;
    exports br.com.concorrencia.tradutor;
    exports br.com.concorrencia.tradutor.controller.gui;
    opens br.com.concorrencia.tradutor.controller.gui to javafx.fxml;
}