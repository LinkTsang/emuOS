package emuos.ui;

import emuos.compiler.Generator;
import emuos.compiler.TinyLexer;
import emuos.compiler.TinyParser;
import emuos.diskmanager.FilePath;
import emuos.diskmanager.FileSystem;
import emuos.diskmanager.InputStream;
import emuos.diskmanager.OutputStream;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * @author Link
 */
public class EditorController implements Initializable {

    @FXML
    private Label messageLabel;
    @FXML
    private TextArea textArea;
    private FilePath filePath;
    private Handler exitHandler = Handler.NULL;

    public Handler getExitHandler() {
        return exitHandler;
    }

    public void setExitHandler(Handler exitHandler) {
        this.exitHandler = exitHandler;
    }

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
    }

    public void setFilePath(FilePath filePath) {
        this.filePath = filePath;
        try (InputStream is = new InputStream(filePath)) {
            textArea.setText(FileSystem.convertStreamToString(is));
        } catch (IOException e) {
            exitHandler.handle(e.getMessage());
        }
    }

    @FXML
    public void handleSaveAction(ActionEvent actionEvent) {
        try (PrintWriter writer = new PrintWriter(new OutputStream(filePath))) {
            writer.write(textArea.getText());
        } catch (IOException e) {
            messageLabel.setText(e.getMessage());
        }
        messageLabel.setText("Saved!");
    }

    @FXML
    public void handleExitAction(ActionEvent actionEvent) {
        exitHandler.handle(null);
    }

    @FXML
    public void handleCompileAction(ActionEvent actionEvent) {
        String filename = filePath.getName();
        int index = filename.lastIndexOf(".");
        String targetFileName = (index == -1
                ? filename
                : filename.substring(0, index)
        ) + ".e";
        FilePath target = new FilePath(filePath.getParent(), targetFileName);
        String content = textArea.getText();
        TinyLexer lexer = new TinyLexer(content);
        Generator gen = new Generator();
        TinyParser parser = new TinyParser(lexer, gen);
        try {
            parser.program();
        } catch (Error e) {
            messageLabel.setText(e.getMessage());
            return;
        }
        try {
            target.create();
        } catch (IOException e) {
            e.printStackTrace();
            messageLabel.setText("Error write file: " + e.getMessage());
            return;
        }
        try (BufferedOutputStream bos = new BufferedOutputStream(new OutputStream(target))) {
            bos.write(gen.getCode());
        } catch (IOException e) {
            e.printStackTrace();
            messageLabel.setText("Error write file: " + e.getMessage());
            return;
        }
        messageLabel.setText("Complied!");
    }

    public interface Handler {
        public static final Handler NULL = ignore -> {
        };

        void handle(String message);
    }
}