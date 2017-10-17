/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.diskmanager;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Link
 */
public class FileSystem {

    final static int ROOT_BLOCK_INDEX = 2;
    final static int FILEINFO_SIZE = 8;
    final static int BLOCK_SIZE = 64;
    final static int BLOCK_FILEINFO_COUNT = BLOCK_SIZE / FILEINFO_SIZE;
    private final static char SEPARATOR_CHAR = '/';
    private final static int BLOCK_COUNT = 256;
    private final static String DISK_FILENAME = "disk";
    private final static int DISK_SIZE = BLOCK_SIZE * BLOCK_COUNT;
    private final static int DATA_BLOCK_INDEX = ROOT_BLOCK_INDEX + 1;
    private static final FileSystem fs = new FileSystem();
    private MappedByteBuffer mappedByteBuffer;

    private FileSystem() {
        File diskFile = new File(DISK_FILENAME);
        boolean isNew = false;
        if (!diskFile.exists()) {
            try {
                isNew = diskFile.createNewFile();
            } catch (IOException ex) {
                Logger.getLogger(FileSystem.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        try (RandomAccessFile memoryMappedFile = new RandomAccessFile(DISK_FILENAME, "rw")) {
            mappedByteBuffer = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, DISK_SIZE);
        } catch (IOException ex) {
            Logger.getLogger(FileSystem.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (isNew) {
            init();
        }
    }

    public static FileSystem getFileSystem() {
        return fs;
    }

    public static String convertStreamToString(InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public byte read(int address) {
        return mappedByteBuffer.get(address);
    }

    public byte read(int blockIndex, int offset) {
        return read(blockIndex * BLOCK_SIZE + offset);
    }

    public short readShort(int address) {
        return mappedByteBuffer.getShort(address);
    }

    public short readShort(int blockIndex, int offset) {
        return readShort(blockIndex * BLOCK_SIZE + offset);
    }

    public void write(int blockIndex, int offset, byte value) {
        mappedByteBuffer.put(blockIndex * BLOCK_SIZE + offset, value);
    }

    public void write(int address, byte value) {
        mappedByteBuffer.put(address, value);
    }

    int allocFreeBlock() {
        for (int i = ROOT_BLOCK_INDEX + 1; i < BLOCK_SIZE * 2; ++i) {
            if (read(i) == 0) {
                write(i, (byte) -1);
                return i;
            }
        }
        return -1;
    }

    void freeBlock(int index) {
        int nextBlockIndex = read(index);
        if (nextBlockIndex == 0) {
            return;
        } else if (nextBlockIndex != -1) {
            freeBlock(nextBlockIndex);
        }
        write(index, (byte) 0);
    }

    public void init() {
        write(0, 0, (byte) 0xff);
        write(0, 1, (byte) 0xff);
        write(0, 2, (byte) 0xff);
        for (int i = ROOT_BLOCK_INDEX + 1; i < 2 * BLOCK_SIZE; ++i) {
            write(i, (byte) 0);
        }

        for (int i = 0; i < BLOCK_SIZE / FILEINFO_SIZE; ++i) {
            writeMetaInfo(MetaInfo.emptyDir(), ROOT_BLOCK_INDEX, i * FILEINFO_SIZE);
        }
        for (int i = DATA_BLOCK_INDEX * BLOCK_SIZE; i < BLOCK_COUNT * BLOCK_SIZE; ++i) {
            mappedByteBuffer.put(i, (byte) 0);
        }
        mappedByteBuffer.force();
    }

    private void writeMetaInfo(MetaInfo metaInfo, int address) {
        for (int i = 0; i < metaInfo.name_ext.length; ++i) {
            mappedByteBuffer.put(address++, metaInfo.name_ext[i]);
        }
        mappedByteBuffer.put(address++, metaInfo.attribute);
        mappedByteBuffer.put(address++, metaInfo.startBlockIndex);
        mappedByteBuffer.putShort(address, metaInfo.length);
    }

    private void writeMetaInfo(MetaInfo metaInfo, int blockIndex, int offset) {
        writeMetaInfo(metaInfo, blockIndex * BLOCK_SIZE + offset);
    }

    void writeMetaInfo(MetaInfo metaInfo) {
        writeMetaInfo(metaInfo, metaInfo.address);
    }

    private MetaInfo readMetaInfo(int address) {
        MetaInfo metaInfo = new MetaInfo();
        metaInfo.address = address;
        for (int i = 0; i < 4; ++i) {
            metaInfo.name_ext[i] = read(address++);
        }
        metaInfo.attribute = read(address++);
        metaInfo.startBlockIndex = read(address++);
        metaInfo.length = readShort(address);
        return metaInfo;
    }

    MetaInfo readMetaInfo(int blockIndex, int offset) {
        return readMetaInfo(blockIndex * BLOCK_SIZE + offset);
    }

    /**
     * path = path.replaceAll("\\\\", "/");
     *
     * @param path path
     * @return meta info
     */
    MetaInfo readMetaInfo(String path) {
        if (path.equals("/")) {
            return MetaInfo.rootDir();
        }
        String[] names = path.split("/");
        if (names.length < 2 || !names[0].isEmpty()) {
            return null;
        }
        MetaInfo current = findInRoot(names[1]);
        if (current == null || names.length == 2) {
            return current;
        }
        for (int i = 2; i < names.length - 1; ++i) {
            if (current.isFile() || (current = find(current, names[i])) == null) {
                return null;
            }
        }
        return find(current, names[names.length - 1]);
    }

    private MetaInfo findInRoot(String name) {
        for (int i = 0; i < BLOCK_FILEINFO_COUNT; ++i) {
            MetaInfo metaInfo = readMetaInfo(ROOT_BLOCK_INDEX, i * FILEINFO_SIZE);
            if (metaInfo.getName().equals(name)) {
                return metaInfo;
            }
        }
        return null;
    }

    private MetaInfo find(MetaInfo parent, String name) {
        if (parent.isFile()) {
            return null;
        }
        if (parent.isRootDir()) {
            return findInRoot(name);
        }
        for (MetaInfo metaInfo : parent) {
            if (metaInfo.getName().equals(name)) {
                return metaInfo;
            }
        }
        return null;
    }

    public FilePath[] listRoot() {
        FilePath[] filePaths = new FilePath[FILEINFO_SIZE];
        int pos = 0;
        for (int i = 0; i < BLOCK_FILEINFO_COUNT; ++i) {
            MetaInfo metaInfo = readMetaInfo(ROOT_BLOCK_INDEX, i * FILEINFO_SIZE);
            if (metaInfo.name_ext[0] != '$') {
                filePaths[pos++] = new FilePath("/" + metaInfo.getName());
            }
        }
        FilePath[] results = new FilePath[pos];
        System.arraycopy(filePaths, 0, results, 0, results.length);
        return results;
    }

    public FilePath[] list(FilePath path) {
        if (path.isRootDir()) {
            return listRoot();
        }
        MetaInfo parent = readMetaInfo(path.getPath());
        if (parent == null || parent.isFile()) {
            return null;
        }
        ArrayList<FilePath> filePathList = new ArrayList<>();
        for (MetaInfo metaInfo : parent) {
            filePathList.add(new FilePath(path, metaInfo.getName()));
        }
        return filePathList.toArray(new FilePath[0]);
    }

    private boolean addFile(MetaInfo parent, String filename, byte attribute) throws IOException {
        if (parent.isRootDir()) {
            if (findInRoot(filename) != null) {
                return false;
            }
            for (int i = 0; i < BLOCK_FILEINFO_COUNT; ++i) {
                int offset = i * FILEINFO_SIZE;
                MetaInfo metaInfo = readMetaInfo(ROOT_BLOCK_INDEX, offset);
                if (metaInfo.name_ext[0] == '$') {
                    int startBlockIndex = allocFreeBlock();
                    if (startBlockIndex == -1) {
                        throw new IOException("Not enough block space.");
                    }
                    metaInfo.setName(filename);
                    metaInfo.startBlockIndex = (byte) startBlockIndex;
                    metaInfo.attribute = attribute;
                    metaInfo.length = 0;
                    writeMetaInfo(metaInfo);
                    return true;
                }
            }
            throw new IOException("Not enough space in the root directory.");
        } else {
            // TODO: combine ?
            if (find(parent, filename) != null) {
                return false;
            }
            int currentCount = parent.length;
            int blockIndex = parent.startBlockIndex;
            int nextBlockIndex = read(blockIndex);
            while (nextBlockIndex != -1) {
                currentCount -= BLOCK_FILEINFO_COUNT;
                blockIndex = nextBlockIndex;
                nextBlockIndex = read(blockIndex);
            }
            if (BLOCK_FILEINFO_COUNT - currentCount == 0) {
                nextBlockIndex = allocFreeBlock();
                if (nextBlockIndex == -1) {
                    throw new IOException("Not enough block space.");
                }
                write(blockIndex, (byte) nextBlockIndex);
                blockIndex = nextBlockIndex;
                currentCount = 0;
            }
            int offset = currentCount * FILEINFO_SIZE;
            int startBlockIndex = allocFreeBlock();
            if (startBlockIndex == -1) {
                throw new IOException("Not enough block space.");
            }

            MetaInfo metaInfo = new MetaInfo();
            metaInfo.startBlockIndex = (byte) startBlockIndex;
            metaInfo.attribute = attribute;
            metaInfo.setName(filename);
            writeMetaInfo(metaInfo, blockIndex, offset);

            // Update parent directory info
            parent.length++;
            writeMetaInfo(parent);
        }
        return true;
    }

    /**
     * NOTE: throw new FileAlreadyExistsException("File '" + file.getPath() + "' already exists."); ?
     *
     * @param file      file
     * @param attribute attribute
     * @return <code>true</code> if the named file does not exist and was successfully created; <code>false</code> if the named file already exists
     * @throws IOException IOException
     */
    private boolean createNewFile(FilePath file, byte attribute) throws IOException {
        String[] filenames = file.getPath().split("/");
        FilePath parentPath = new FilePath("/");
        for (int i = 1; i < filenames.length - 1; ++i) {
            FilePath current = new FilePath(parentPath, filenames[i]);
            if (current.exists()) {
                if (!current.isDir()) {
                    throw new IOException("Error: " + current.getPath() + " is a file\n" +
                            "Invalid path: " + file.getPath());
                }
            } else {
                current.mkdir();
            }
            parentPath = current;
        }
        MetaInfo parent = readMetaInfo(parentPath.getPath());
        String filename = file.getName();
        if (parent == null) {
            throw new FileNotFoundException("Invalid path: " + file.getPath());
        }
        return addFile(parent, filename, attribute);
    }

    public boolean createNewFile(FilePath path) throws IOException {
        return createNewFile(path, (byte) MetaInfo.MASK_FILE);
    }

    public boolean mkdir(FilePath path) throws IOException {
        return createNewFile(path, (byte) 0);
    }

    /**
     * delete meta info of file or directory
     * if the meta info is the directory,
     * then it'll delete sub meta info recursively.
     *
     * @param metaInfo meta info
     */
    private void delete(MetaInfo metaInfo) {
        if (metaInfo.isRootDir()) return;
        if (metaInfo.isDir()) {
            for (MetaInfo subMetaInfo : metaInfo) {
                delete(subMetaInfo);
            }
        }
        freeBlock(metaInfo.startBlockIndex);
    }

    /**
     * delete file or directory
     *
     * @param file file
     * @throws IOException IOException
     */
    public void delete(FilePath file) throws IOException {
        MetaInfo parent = readMetaInfo(file.getParent());
        String filename = file.getName();
        if (file.isRootDir() || parent == null || parent.isFile()) {
            throw new IOException("cannot remove `" + file.getPath() + "': Invalid path");
        }
        if (parent.isRootDir()) {
            MetaInfo metaInfo = findInRoot(filename);
            if (metaInfo == null) {
                throw new FileNotFoundException("cannot remove '" + file.getPath() + "': No such file or directory");
            }
            delete(metaInfo);
            metaInfo.name_ext[0] = '$';
            freeBlock(metaInfo.startBlockIndex);
            writeMetaInfo(metaInfo);
            return;
        }
        Iterator<MetaInfo> iterator = parent.iterator();
        while (iterator.hasNext()) {
            MetaInfo metaInfo = iterator.next();
            if (metaInfo.getName().equals(filename)) {
                delete(metaInfo);
                parent.length--;
                writeMetaInfo(parent, parent.address);
                if (iterator.hasNext()) {
                    MetaInfo lastMetaInfo;
                    do {
                        lastMetaInfo = iterator.next();
                    } while (iterator.hasNext());
                    writeMetaInfo(lastMetaInfo, metaInfo.address);
                }
                return;
            }
        }
        throw new FileNotFoundException("cannot remove '" + file.getPath() + "': No such file or directory");
    }

    public void copy(FilePath src, FilePath dest) throws IOException {
        if (!src.exists()) {
            throw new FileNotFoundException(src.getPath() + " is not found.");
        }
        if (exists(dest)) {
            throw new FileAlreadyExistsException(dest.getPath() + " already exists.");
        }
        if (src.isDir()) {
            dest.mkdir();
            for (FilePath subSource : src.list()) {
                copy(subSource, new FilePath(dest, subSource.getName()));
            }
        } else if (src.isFile()) {
            dest.create();
            BufferedInputStream bis = new BufferedInputStream(new InputStream(src), BLOCK_SIZE);
            BufferedOutputStream bos = new BufferedOutputStream(new OutputStream(dest), BLOCK_SIZE);
            int data;
            while ((data = bis.read()) != -1) {
                bos.write(data);
            }
            bis.close();
            bos.close();
        }
    }

    public void move(FilePath src, FilePath dest) throws IOException {
        MetaInfo sourceMetaInfo = readMetaInfo(src.getPath());
        if (sourceMetaInfo == null) {
            throw new FileNotFoundException(src.getPath() + " is not found.");
        }
        if (dest.exists()) {
            throw new FileAlreadyExistsException(dest.getPath() + " already exists.");
        }
        if (sourceMetaInfo.isFile()) {
            dest.create();
        } else if (sourceMetaInfo.isDir()) {
            dest.mkdir();
        } else {
            throw new IOException(src.getPath() + " is neither a file nor a directory.");
        }
        MetaInfo targetMetaInfo = readMetaInfo(dest.getPath());
        int oldBlockIndex = targetMetaInfo.startBlockIndex;
        targetMetaInfo.attribute = sourceMetaInfo.attribute;
        targetMetaInfo.length = sourceMetaInfo.length;
        targetMetaInfo.startBlockIndex = sourceMetaInfo.startBlockIndex;
        sourceMetaInfo.attribute = MetaInfo.MASK_FILE;
        sourceMetaInfo.length = 0;
        sourceMetaInfo.startBlockIndex = (byte) oldBlockIndex;
        writeMetaInfo(targetMetaInfo);
        writeMetaInfo(sourceMetaInfo);
        src.delete();
    }

    public void rename(FilePath file, String name) throws IOException {
        if (file.getName().equals(name)) return;
        FilePath target = new FilePath(file.getParent(), name);
        if (exists(target)) {
            throw new FileAlreadyExistsException("File '" + target.getPath() + "' already exists.");
        }
        MetaInfo metaInfo = readMetaInfo(file.getPath());
        if (metaInfo == null) throw new FileNotFoundException("File `" + file.getPath() + "` is not found.");
        metaInfo.setName(name);
        writeMetaInfo(metaInfo);
    }

    public int getFreeSize() {
        int count = 0;
        for (int i = ROOT_BLOCK_INDEX + 1; i < BLOCK_SIZE * 2; ++i) {
            if (read(i) == 0)
                count++;
        }
        return count * BLOCK_SIZE;
    }

    public int getSize(FilePath filePath) throws FileNotFoundException {
        MetaInfo metaInfo = readMetaInfo(filePath.getPath());
        if (metaInfo == null) throw new FileNotFoundException("File '" + filePath.getPath() + "' doesn't exist.");
        return metaInfo.length;
    }

    public int getAttribute(FilePath filePath) throws FileNotFoundException {
        MetaInfo metaInfo = readMetaInfo(filePath.getPath());
        if (metaInfo == null) throw new FileNotFoundException("File '" + filePath.getPath() + "' doesn't exist.");
        return metaInfo.attribute;
    }

    public boolean exists(FilePath filePath) {
        MetaInfo metaInfo = readMetaInfo(filePath.getPath());
        return metaInfo != null;
    }

    public void flush() {
        mappedByteBuffer.force();
    }
}
