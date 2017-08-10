package emuos.ui;

import emuos.os.CentralProcessingUnit;
import emuos.os.shell.Shell;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.ResourceBundle;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * @author Link
 */
public class TerminalController implements Initializable {

    private final CommandHistory commandHistory = new CommandHistory();
    private final EmuInputStream eis = new EmuInputStream();
    private final EmuOutputStream eos = new EmuOutputStream();
    @FXML
    private TextArea inputArea;
    private Stage stage;
    private int lastPromptPosition = 0;
    private Shell shell;

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
    }

    private void clearInput() {
        if (lastPromptPosition != inputArea.getLength()) {
            inputArea.deleteText(lastPromptPosition, inputArea.getLength());
        }
    }

    private void print(String text) {
        inputArea.appendText(text);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void handleKeyPressed(KeyEvent keyEvent) {
        switch (keyEvent.getCode()) {
            case TAB:
                keyEvent.consume();
                return;
            case UP:
            case DOWN: {
                clearInput();
                print(keyEvent.getCode() == KeyCode.UP ? commandHistory.prev() : commandHistory.next());
                keyEvent.consume();
                return;
            }
            case LEFT:
            case BACK_SPACE:
                if (inputArea.getCaretPosition() <= lastPromptPosition) {
                    keyEvent.consume();
                }
                return;
            case HOME:
                inputArea.positionCaret(lastPromptPosition);
                keyEvent.consume();
                return;
            case ENTER: {
                if (shell.isWaiting()) {
                    break;
                }
                final String promptString = shell.getPromptString();
                String content = inputArea.getText();
                String inputLine = content.substring(content.lastIndexOf('\n') + 1);
                inputLine = inputLine.substring(inputLine.indexOf(promptString) + promptString.length());
                if (!inputLine.isEmpty()) {
                    commandHistory.add(inputLine);
                }
                eis.queue.add(new StringReader(inputLine + "\n"));
            }
        }
        if (inputArea.getCaretPosition() < lastPromptPosition) {
            inputArea.positionCaret(inputArea.getLength());
        }
    }

    public void initShell(CentralProcessingUnit kernel) {
        shell = new Shell(kernel, eis, eos);
        shell.setWaitInputHandler(() -> Platform.runLater(() ->
                lastPromptPosition = inputArea.getLength()
        ));
        shell.setWaitProcessHandler(() -> Platform.runLater(() ->
                inputArea.setEditable(false)
        ));
        shell.setWakeProcessHandler(() -> Platform.runLater(() ->
                inputArea.setEditable(true)
        ));
        shell.setClearHandler(() -> Platform.runLater(() ->
                inputArea.clear()
        ));
        shell.setExitHandler(() -> {
            shell.stop();
            Platform.runLater(stage::close);
        });
        Thread shellThread = new Thread(shell::run);
        shellThread.start();
    }

    private static class CommandHistory {
        private static final int MAX_HISTORY_COUNT = 50;
        private final LinkedList<String> historyList = new LinkedList<>();
        private ListIterator<String> current = historyList.listIterator();

        void add(String command) {
            if (!historyList.isEmpty() && historyList.getFirst().equals(command)) {
                return;
            }
            historyList.addFirst(command);
            if (historyList.size() > MAX_HISTORY_COUNT) {
                historyList.removeLast();
            }
            reset();
        }

        void reset() {
            current = historyList.listIterator();
        }

        String prev() {
            if (current.hasNext()) {
                return current.next();
            }
            if (current.hasPrevious()) {
                current.previous();
                return current.next();
            }
            return "";

        }

        String next() {
            if (current.hasPrevious()) {
                return current.previous();
            } else {
                return "";
            }
        }
    }

    private class EmuInputStream extends java.io.InputStream {
        final BlockingQueue<StringReader> queue = new LinkedBlockingDeque<>();
        private StringReader stringReader;

        @Override
        public int read() throws IOException {
            try {
                if (stringReader == null) {
                    stringReader = queue.take();
                }
                int value = stringReader.read();
                if (value == -1) {
                    stringReader = null;
                }
                return value;
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            return -1;
        }
    }

    private class EmuOutputStream extends java.io.OutputStream {
        @Override
        public void write(int b) throws IOException {
            Platform.runLater(() -> inputArea.appendText(Character.toString((char) b)));
        }
    }

}