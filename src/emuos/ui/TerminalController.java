package emuos.ui;

import emuos.diskmanager.FilePath;
import emuos.diskmanager.FileSystem;
import emuos.diskmanager.InputStream;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

import static emuos.ui.MainWindow.WINDOW_TITLE;

/**
 * @author Link
 */
public class TerminalController implements Initializable {

    @FXML
    private TextArea inputArea;

    private String promptString = "$ ";
    private FilePath workingDirectory = new FilePath("/");
    private CommandHistory commandHistory = new CommandHistory();
    private int lastPromptPosition = 0;
    private Map<String, Method> commandMap = new HashMap<>();
    private Stage stage;
    private Parent self;

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initializeCommandMap();
        showPrompt();
    }

    private void initializeCommandMap() {
        for (Method method : this.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(CommandAnnotation.class)) {
                CommandAnnotation commandAnnotation = method.getAnnotation(CommandAnnotation.class);
                for (String name : commandAnnotation.name()) {
                    commandMap.put(name, method);
                }
            }
        }
    }

    private void clearInput() {
        if (lastPromptPosition != inputArea.getLength()) {
            inputArea.deleteText(lastPromptPosition, inputArea.getLength());
        }
    }

    public void print(String text) {
        inputArea.appendText(text);
    }

    private void showPrompt() {
        print("[" + workingDirectory.getPath() + "] " + promptString);
        lastPromptPosition = inputArea.getLength();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        self = stage.getScene().getRoot();
    }

    public void handleShellExit() {
        stage.getScene().setRoot(self);
        stage.setTitle(WINDOW_TITLE);
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
                String content = inputArea.getText();
                String inputLine = content.substring(content.lastIndexOf('\n') + 1);
                inputLine = inputLine.substring(inputLine.indexOf(promptString) + promptString.length());
                if (!inputLine.isEmpty()) {
                    commandHistory.add(inputLine);
                    print("\n");
                    int index = inputLine.indexOf(' ');
                    String command;
                    String args;
                    if (index == -1) {
                        command = inputLine;
                        args = "";
                    } else {
                        command = inputLine.substring(0, index);
                        while (index < inputLine.length() && Character.isSpaceChar(inputLine.charAt(index))) {
                            ++index;
                        }
                        args = inputLine.substring(index);
                    }
                    Method method = commandMap.get(command);
                    if (method == null) {
                        print(command + ": command not found\n");
                    } else {
                        try {
                            method.invoke(this, args);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (inputArea.getCaretPosition() > inputArea.getText().lastIndexOf('\n') + promptString.length()) {
                    print("\n");
                }
                showPrompt();
                keyEvent.consume();
            }
        }
        if (inputArea.getCaretPosition() < lastPromptPosition) {
            inputArea.positionCaret(inputArea.getLength());
        }
    }

    @FXML
    private void handleKeyReleased(KeyEvent keyEvent) {

    }

    @FXML
    private void handleKeyTyped(KeyEvent keyEvent) {

    }

    private FilePath getFilePath(String path) {
        if (path.startsWith("/")) {
            return new FilePath(path);
        } else if (path.startsWith("./")) {
            return path.length() > 2 ? new FilePath(workingDirectory, path.substring(2)) : workingDirectory;
        } else {
            return new FilePath(workingDirectory, path);
        }
    }

    @CommandAnnotation(name = {"dir"})
    private void dir(String args) {
        FilePath filePath = args.isEmpty() ? workingDirectory : getFilePath(args);
        try {
            FilePath[] files = filePath.list();
            if (files == null) {
                print(filePath.getPath() + ": Not a directory");
                return;
            }
            int maxNameLength = 0;
            for (FilePath file : files) {
                int length = file.getName().length();
                if (length > maxNameLength) {
                    maxNameLength = length;
                }
            }
            String fmt = "%-" + (maxNameLength + 4) + "s";
            for (FilePath file : files) {
                if (file.isDir()) {
                    print("<DIR>   ");
                    print(file.getName());
                } else {
                    print("        ");
                    print(String.format(fmt, file.getName()));
                    print(String.valueOf(file.size()));
                }
                print("\n");
            }
        } catch (IOException e) {
            print(e.getMessage());
        }
    }

    @CommandAnnotation(name = {"create"})
    private void create(String args) {
        FilePath filePath = getFilePath(args);
        try {
            filePath.create();
        } catch (IOException e) {
            print(e.getMessage());
        }
    }

    @CommandAnnotation(name = {"delete", "del"})
    private void delete(String args) {
        FilePath filePath = getFilePath(args);
        try {
            filePath.delete();
        } catch (IOException e) {
            print(e.getMessage());
        }
    }

    @CommandAnnotation(name = {"type"})
    private void type(String args) {
        FilePath filePath = getFilePath(args);
        if (filePath.exists()) {
            try (InputStream is = new InputStream(filePath)) {
                print(FileSystem.convertStreamToString(is));
            } catch (IOException e) {
                print(e.getMessage());
            }
        } else {
            print("No such file or directory");
        }
        print("\n");
    }

    @CommandAnnotation(name = {"copy", "cp"})
    private void copy(String args) {
        print("command not implemented");
    }

    @CommandAnnotation(name = {"mkdir", "md"})
    private void mkdir(String args) {
        FilePath filePath = getFilePath(args);
        try {
            filePath.mkdir();
        } catch (IOException e) {
            print(e.getMessage());
        }
    }

    @CommandAnnotation(name = {"rmdir", "rm"})
    private void rmdir(String args) {
        FilePath filePath = getFilePath(args);
        try {
            filePath.delete();
        } catch (IOException e) {
            print(e.getMessage());
        }
    }

    @CommandAnnotation(name = {"chdir", "cd"})
    private void chdir(String args) {
        switch (args) {
            case "":
                break;
            case ".":
                break;
            case "..":
                workingDirectory = workingDirectory.getParentFile();
                break;
            default:
                FilePath file = getFilePath(args);
                try {
                    if (!file.isDir()) {
                        print("Not a directory");
                    } else {
                        workingDirectory = file;
                    }
                } catch (FileNotFoundException e) {
                    print("No such file or directory");
                }
                break;
        }
    }

    @CommandAnnotation(name = {"move", "mv"})
    private void move(String args) {
        print("command not implemented");
    }

    @CommandAnnotation(name = {"edit"})
    private void showEditor(String args) {
        FilePath filePath = getFilePath(args);
        if (filePath.exists()) {
            try {
                FXMLLoader editorLoader = new FXMLLoader(getClass().getResource("Editor.fxml"));
                Parent root = editorLoader.load();
                EditorController editorController = editorLoader.getController();
                editorController.setTerminal(this);
                editorController.setFilePath(filePath);
                stage.getScene().setRoot(root);
            } catch (IOException e) {
                print(e.getMessage());
                e.printStackTrace();
            }
        } else {
            print(filePath.getPath() + ": No such file");
        }
    }

    @CommandAnnotation(name = {"hex"})
    private void showHex(String args) {
        if (args.isEmpty()) {
            print("Usage: hex [file]");
            return;
        }
        FilePath filePath = getFilePath(args);
        if (filePath.exists()) {
            try (InputStream is = new InputStream(filePath)) {
                StringBuilder stringBuilder = new StringBuilder(filePath.size());
                stringBuilder.append("   ");
                for (int i = 0; i < 16; ++i) {
                    stringBuilder.append(String.format("%02X", i));
                    stringBuilder.append(' ');
                }
                stringBuilder.append('\n');
                int b;
                int count = 0;
                int lineCount = 0;
                while ((b = is.read()) != -1) {
                    if (count == 0) {
                        stringBuilder.append(String.format("%02X ", lineCount++));
                    }
                    stringBuilder.append(String.format("%02X", b));
                    stringBuilder.append(' ');
                    if (++count == 16) {
                        count = 0;
                        stringBuilder.append('\n');
                    }
                }
                print(stringBuilder.toString());
            } catch (IOException e) {
                print(e.getMessage());
            }
        } else {
            print(filePath.getPath() + ": No such file");
        }
    }

    @CommandAnnotation(name = {"format"})
    private void format(String args) {
        FileSystem.getFileSystem().init();
        print("Finished!");
    }

    @CommandAnnotation(name = {"exit"})
    private void exit(String args) {
        stage.close();
    }

    @CommandAnnotation(name = {"help"})
    private void showHelp(String args) {
        print("Support commands: \n");
        for (String key : commandMap.keySet()) {
            print(" " + key + "\n");
        }
    }

    @CommandAnnotation(name = {"clear"})
    private void clearScreen(String args) {
        inputArea.clear();
    }

    @CommandAnnotation(name = {"diskstat"})
    private void showDiskStat(String args) {
        StringBuilder stringBuilder = new StringBuilder();
        FileSystem fs = FileSystem.getFileSystem();
        for (int i = 0; i < 64 * 2; ++i) {
            stringBuilder.append(String.format("%4d ", fs.read(i)));
            if (i % 8 == 7) {
                stringBuilder.append('\n');
            }
        }
        stringBuilder.append('\n');
        print(stringBuilder.toString());
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface CommandAnnotation {
        String[] name();
    }

    private static class CommandHistory {
        private static final int MAX_HISTORY_COUNT = 50;
        private LinkedList<String> historyList = new LinkedList<>();
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

}