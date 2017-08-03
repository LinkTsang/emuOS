package emuos.diskmanager;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author Link
 */
public class InputStream extends java.io.InputStream {

    private static final FileSystem fs = FileSystem.getFileSystem();
    private MetaInfo metaInfo;
    private int currentBlockIndex;
    private int currentOffset;
    private int available;

    @SuppressWarnings("DuplicateThrows")
    public InputStream(FilePath file) throws IOException, FileNotFoundException {
        metaInfo = fs.readMetaInfo(file.getPath());
        if (metaInfo == null) {
            throw new FileNotFoundException(file.getPath());
        }
        if (!metaInfo.isFile()) {
            throw new IOException("Opening file '" + file.getPath() + "' failed.");
        }
        currentBlockIndex = metaInfo.startBlockIndex;
        available = metaInfo.length;
        currentOffset = 0;
    }

    @Override
    public int available() throws IOException {
        return available;
    }

    @Override
    public int read() {
        if (currentOffset >= FileSystem.BLOCK_SIZE) {
            currentBlockIndex = fs.read(currentBlockIndex);
            currentOffset = 0;
        }
        if (available <= 0 || currentBlockIndex == -1) {
            return -1;
        } else {
            available--;
            return fs.read(currentBlockIndex, currentOffset++);
        }
    }
}
