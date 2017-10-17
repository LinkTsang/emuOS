package emuos.os.shell;

import emuos.diskmanager.FilePath;
import emuos.diskmanager.FileSystem;
import emuos.os.Kernel;
import emuos.os.ProcessControlBlock;
import emuos.os.ProcessManager;

import java.io.*;
import java.util.*;

/**
 * @author Link
 */
public class Shell implements Closeable {
    public final SPrintStream out;
    public final InputStream in;
    private final Set<ProcessControlBlock> waitingQueue = new HashSet<>();
    private final Object processWaiter = new Object();
    private final String promptString = "$ ";
    private final CommandHistory commandHistory = new CommandHistory();
    private Map<String, Command> commandMap = new HashMap<>();
    private FilePath workingDirectory = new FilePath("/");
    private Kernel kernel;
    private Handler exitHandler = Handler.NULL;
    private Handler clearHandler = Handler.NULL;
    private Handler waitInputHandler = Handler.NULL;
    private Handler waitProcessHandler = Handler.NULL;
    private Handler wakeProcessHandler = Handler.NULL;
    private State state = State.STOPPED;
    private Timer timer = new Timer(true);
    private TimerTask spawnTimerTask;
    private Kernel.Listener intExitListener = c -> {
        final ProcessControlBlock pcb = c.getProcessManager().getRunningProcess();
        synchronized (waitingQueue) {
            if (waitingQueue.remove(pcb)) {
                synchronized (processWaiter) {
                    processWaiter.notify();
                }
            }
        }
    };

    public Shell(Kernel kernel, InputStream in, OutputStream out) {
        this.kernel = kernel;
        kernel.addIntExitListener(intExitListener);
        this.in = in;
        this.out = new SPrintStream(out);
        loadCommandMap(this);
    }

    public static void main(String[] args) {
        System.out.println(Command.class.getCanonicalName());
        Kernel kernel = new Kernel();
        kernel.run();
        Shell shell = new Shell(kernel, System.in, System.out);
        shell.setExitHandler(shell::stop);
        shell.run();
    }

    public CommandHistory getCommandHistory() {
        return commandHistory;
    }

    public Handler getWaitInputHandler() {
        return waitInputHandler;
    }

    public void setWaitInputHandler(Handler waitInputHandler) {
        this.waitInputHandler = waitInputHandler;
    }

    public String getPromptString() {
        return promptString;
    }

    public Handler getWakeProcessHandler() {
        return wakeProcessHandler;
    }

    public void setWakeProcessHandler(Handler wakeProcessHandler) {
        this.wakeProcessHandler = wakeProcessHandler;
    }

    public Handler getWaitProcessHandler() {
        return waitProcessHandler;
    }

    public void setWaitProcessHandler(Handler waitProcessHandler) {
        this.waitProcessHandler = waitProcessHandler;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public boolean isWaiting() {
        return state == State.WAITING;
    }

    public boolean isStopped() {
        return state == State.STOPPED;
    }

    public Handler getClearHandler() {
        return clearHandler;
    }

    public void setClearHandler(Handler clearHandler) {
        this.clearHandler = clearHandler;
    }

    public Handler getExitHandler() {
        return exitHandler;
    }

    public void setExitHandler(Handler exitHandler) {
        this.exitHandler = exitHandler;
    }

    public void run() {
        state = State.RUNNING;
        Scanner scanner = new Scanner(in);
        while (isRunning()) {
            showPrompt();
            out.setDirty(false);
            waitInputHandler.handle();

            String commandLine = scanner.nextLine();
            if (commandLine.isEmpty()) continue;
            commandHistory.add(commandLine);
            String[] args = commandLine.split("\\s+", 2);
            String command = args[0];
            Command handler = getCommandHandler(command);
            if (handler == null) {
                out.println(command + ": command not found");
            } else {
                handler.execute(args.length == 2 ? args[1] : "");
            }

            if (out.isDirty()) {
                out.println();
            }
        }
    }

    public void stop() {
        state = State.STOPPED;
    }

    public void print(String text) {
        out.print(text);
    }

    // Helper methods
    public FilePath getFilePath(String path) {
        if (path.startsWith("/")) {
            return new FilePath(path);
        } else if (path.startsWith("./")) {
            return path.length() > 2 ? new FilePath(workingDirectory, path.substring(2)) : workingDirectory;
        } else {
            return new FilePath(workingDirectory, path);
        }
    }

    private void showPrompt() {
        print("[" + workingDirectory.getPath() + "] " + promptString);
    }

    private void loadCommandMap(Object object) {
        registerCommandHandler(new Command("dir") {
            @Override
            public void execute(String args) {
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
        });

        registerCommandHandler(new Command("create") {
            @Override
            public void execute(String args) {
                FilePath filePath = getFilePath(args);
                try {
                    if (args.isEmpty()) {
                        print("Usage: create [file]");
                        return;
                    }
                    filePath.create();
                } catch (IOException e) {
                    print(e.getMessage());
                }
            }
        });

        registerCommandHandler(new Command("delete", "del") {
            @Override
            public void execute(String args) {
                if (args.isEmpty()) {
                    print("Usage: delete [file]");
                    return;
                }
                FilePath filePath = getFilePath(args);
                try {
                    if (!filePath.isFile()) {
                        print(filePath.getPath() + " is not a file.");
                        return;
                    }
                    filePath.delete();
                } catch (IOException e) {
                    print(e.getMessage());
                }
            }
        });

        registerCommandHandler(new Command("type") {
            @Override
            public void execute(String args) {
                if (args.isEmpty()) {
                    print("Usage: type [file]");
                    return;
                }
                FilePath filePath = getFilePath(args);
                if (filePath.exists()) {
                    try (emuos.diskmanager.InputStream is = new emuos.diskmanager.InputStream(filePath)) {
                        print(FileSystem.convertStreamToString(is));
                    } catch (IOException e) {
                        print(e.getMessage());
                    }
                } else {
                    print("No such file or directory");
                }
                print("\n");
            }
        });

        registerCommandHandler(new Command("copy", "cp") {
            @Override
            public void execute(String args) {
                print("command not implemented");
            }
        });

        registerCommandHandler(new Command("mkdir", "md") {
            @Override
            public void execute(String args) {
                if (args.isEmpty()) {
                    print("Usage: mkdir [file]");
                    return;
                }
                FilePath filePath = getFilePath(args);
                try {
                    filePath.mkdir();
                } catch (IOException e) {
                    print(e.getMessage());
                }
            }
        });

        registerCommandHandler(new Command("rmdir", "rm") {
            @Override
            public void execute(String args) {
                if (args.isEmpty()) {
                    print("Usage: rmdir [file]");
                    return;
                }
                FilePath filePath = getFilePath(args);
                try {
                    if (!filePath.isDir()) {
                        print(filePath.getPath() + " is not a directory.");
                        return;
                    }
                    if (filePath.list().length != 0) {
                        print("Failed! " + filePath.getPath() + " is not empty.");
                        return;
                    }
                    filePath.delete();
                } catch (IOException e) {
                    print(e.getMessage());
                }
            }
        });

        registerCommandHandler(new Command("deldir") {
            @Override
            public void execute(String args) {
                if (args.isEmpty()) {
                    print("Usage: deldir [file]");
                    return;
                }
                FilePath filePath = getFilePath(args);
                try {
                    if (!filePath.isDir()) {
                        print(filePath.getPath() + " is not a directory.");
                        return;
                    }
                    filePath.delete();
                } catch (IOException e) {
                    print(e.getMessage());
                }
            }
        });

        registerCommandHandler(new Command("chdir", "cd") {
            @Override
            public void execute(String args) {
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
        });

        registerCommandHandler(new Command("move", "mv") {
            @Override
            public void execute(String args) {
                print("command not implemented");
            }
        });

        registerCommandHandler(new Command("hex") {
            @Override
            public void execute(String args) {
                if (args.isEmpty()) {
                    print("Usage: hex [file]");
                    return;
                }
                FilePath filePath = getFilePath(args);
                if (filePath.exists()) {
                    try (emuos.diskmanager.InputStream is = new emuos.diskmanager.InputStream(filePath)) {
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
        });

        registerCommandHandler(new Command("format") {
            @Override
            public void execute(String args) {
                FileSystem.getFileSystem().init();
                print("Finished!");
            }
        });

        registerCommandHandler(new Command("exec") {
            @Override
            public void execute(String args) {
                if (args.isEmpty()) {
                    print("Usage: exec [file]");
                    return;
                }
                FilePath file = getFilePath(args);
                try {
                    ProcessControlBlock pcb = kernel.getProcessManager().create(file);
                    synchronized (waitingQueue) {
                        waitingQueue.add(pcb);
                    }
                    state = State.WAITING;
                    waitProcessHandler.handle();
                    synchronized (processWaiter) {
                        try {
                            processWaiter.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    wakeProcessHandler.handle();
                    state = State.RUNNING;
                    int exitCode = pcb.getContext().getAX();
                    if (exitCode != 0) {
                        print("Process ("
                                + "PID: "
                                + pcb.getPID()
                                + ", Path: "
                                + pcb.getImageFile().getPath()
                                + ") exited with code: "
                                + exitCode);
                    }
                } catch (IOException | ProcessManager.ProcessException e) {
                    print(e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        registerCommandHandler(new Command("bg") {
            @Override
            public void execute(String args) {
                if (args.isEmpty()) {
                    print("Usage: bg [file]");
                    return;
                }
                FilePath file = getFilePath(args);
                try {
                    kernel.getProcessManager().create(file);
                } catch (IOException | ProcessManager.ProcessException e) {
                    print(e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        registerCommandHandler(new Command("spawn") {
            @Override
            public void execute(String args) {
                if (args.isEmpty()) {
                    print("Usage: spawn { start [directory] | stop | status }");
                    return;
                }
                String[] arguments = args.split("\\s+");
                if (arguments.length == 1) {
                    if (arguments[0].equals("stop")) {
                        if (spawnTimerTask == null) {
                            print("No spawning.");
                            return;
                        }
                        if (spawnTimerTask.cancel()) {
                            print("Stopped spawning.");
                            spawnTimerTask = null;
                        } else {
                            print("Failed to stop spawning.");
                        }
                    } else if (arguments[0].equals("status")) {
                        print(spawnTimerTask == null ? "Stopped" : "Started");
                    } else {
                        print("Invalid argument: " + arguments[0] + "\n"
                                + "Usage: spawn { start [directory] | stop | status }");
                    }
                } else if (arguments.length == 2 && arguments[0].equals("start")) {
                    if (spawnTimerTask != null) {
                        print("Already spawning...");
                        return;
                    }
                    String path = arguments[1];
                    FilePath directory = getFilePath(path);
                    try {
                        if (!directory.isDir()) {
                            print(directory + " is not a directory.");
                            return;
                        }
                    } catch (FileNotFoundException e) {
                        print(e.getMessage());
                        e.printStackTrace();
                        return;
                    }
                    spawnTimerTask = new ProcessProducer(kernel, directory);
                    timer.schedule(spawnTimerTask, 0, 1000);
                    print("Started spawning.");
                } else {
                    print("Invalid argument\n"
                            + "Usage: spawn { start [directory] | stop | status }");
                }
            }
        });

        registerCommandHandler(new Command("help") {
            @Override
            public void execute(String args) {
                print("Support commands: \n");
                for (String key : commandMap.keySet()) {
                    print(" " + key + "\n");
                }
            }
        });

        registerCommandHandler(new Command("exit") {
            @Override
            public void execute(String args) {
                exitHandler.handle();
            }
        });

        registerCommandHandler(new Command("diskstat") {
            @Override
            public void execute(String args) {
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
        });

        registerCommandHandler(new Command("clear") {
            @Override
            public void execute(String args) {
                clearHandler.handle();
            }
        });
    }

    public void registerCommandHandler(Command command) {
        for (String name : command.names()) {
            commandMap.put(name, command);
        }
    }

    public Command getCommandHandler(String name) {
        return commandMap.get(name);
    }

    @Override
    public void close() {
        kernel.removeIntExitListener(intExitListener);
    }

    private enum State {
        RUNNING,
        WAITING,
        STOPPED
    }

    static class ProcessProducer extends TimerTask {

        private final FilePath[] executable;
        private final Kernel kernel;
        private final Random random = new Random();

        ProcessProducer(Kernel kernel, FilePath dir) {
            this.kernel = kernel;
            ArrayList<FilePath> list = new ArrayList<>();
            for (FilePath file : dir.list()) {
                try {
                    if (file.isFile() && file.getName().endsWith(".e")) {
                        list.add(file);
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
            executable = list.toArray(new FilePath[0]);
        }

        @Override
        public void run() {
            if (executable.length == 0) {
                this.cancel();
            }
            if (random.nextBoolean()) return;
            try {
                kernel.getProcessManager().create(executable[random.nextInt(executable.length)]);
            } catch (IOException | ProcessManager.ProcessException e) {
                e.printStackTrace();
            }
        }
    }

    private static class SPrintStream extends PrintStream {
        private boolean dirty;

        public SPrintStream(OutputStream out) {
            super(out);
        }

        public SPrintStream(OutputStream out, boolean autoFlush) {
            super(out, autoFlush);
        }

        public SPrintStream(OutputStream out, boolean autoFlush, String encoding) throws UnsupportedEncodingException {
            super(out, autoFlush, encoding);
        }

        public SPrintStream(String fileName) throws FileNotFoundException {
            super(fileName);
        }

        public SPrintStream(String fileName, String csn) throws FileNotFoundException, UnsupportedEncodingException {
            super(fileName, csn);
        }

        public SPrintStream(File file) throws FileNotFoundException {
            super(file);
        }

        public SPrintStream(File file, String csn) throws FileNotFoundException, UnsupportedEncodingException {
            super(file, csn);
        }

        public boolean isDirty() {
            return dirty;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        @Override
        public void write(int b) {
            dirty = true;
            super.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            dirty = true;
            super.write(b);
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            dirty = true;
            super.write(buf, off, len);
        }
    }
}
