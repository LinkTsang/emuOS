package emuos.os.shell;

import emuos.diskmanager.FilePath;
import emuos.diskmanager.FileSystem;
import emuos.os.CentralProcessingUnit;
import emuos.os.ProcessControlBlock;

import java.io.*;
import java.util.*;

/**
 * @author Link
 */
public class Shell {
    public final SPrintStream out;
    public final InputStream in;
    private final Set<ProcessControlBlock> waitingQueue = new HashSet<>();
    private final Object processWaiter = new Object();
    private final String promptString = "$ ";
    private Map<String, Command> commandMap = new HashMap<>();
    private FilePath workingDirectory = new FilePath("/");
    private CentralProcessingUnit cpu;
    private Handler exitHandler = Handler.NULL;
    private Handler clearHandler = Handler.NULL;
    private Handler waitInputHandler = Handler.NULL;
    private Handler waitProcessHandler = Handler.NULL;
    private Handler wakeProcessHandler = Handler.NULL;
    private State state = State.STOPPED;

    public Shell(CentralProcessingUnit cpu, InputStream in, OutputStream out) {
        this.cpu = cpu;
        cpu.setIntEndListener(c -> {
            final ProcessControlBlock pcb = c.getProcessManager().getRunningProcess();
            synchronized (waitingQueue) {
                if (waitingQueue.remove(pcb)) {
                    synchronized (processWaiter) {
                        processWaiter.notify();
                    }
                }
            }
        });
        this.in = in;
        this.out = new SPrintStream(out);
        loadCommandMap(this);
    }

    public static void main(String[] args) {
        System.out.println(Command.class.getCanonicalName());
        CentralProcessingUnit cpu = new CentralProcessingUnit();
        cpu.run();
        Shell shell = new Shell(cpu, System.in, System.out);
        shell.setExitHandler(shell::stop);
        shell.run();
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
                    filePath.create();
                } catch (IOException e) {
                    print(e.getMessage());
                }
            }
        });

        registerCommandHandler(new Command("delete", "del") {
            @Override
            public void execute(String args) {
                FilePath filePath = getFilePath(args);
                try {
                    filePath.delete();
                } catch (IOException e) {
                    print(e.getMessage());
                }
            }
        });

        registerCommandHandler(new Command("type") {
            @Override
            public void execute(String args) {
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
                FilePath filePath = getFilePath(args);
                try {
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
                    ProcessControlBlock pcb = cpu.getProcessManager().create(file);
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
                    int exitCode = pcb.getCPUState().getAX();
                    if (exitCode != 0) {
                        print("Process (PID: "
                                + pcb.getPID()
                                + ") exited with code: "
                                + exitCode
                                + "\n");
                    }
                } catch (IOException e) {
                    print(e.getMessage());
                    e.printStackTrace();
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

    private enum State {
        RUNNING,
        WAITING,
        STOPPED
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
