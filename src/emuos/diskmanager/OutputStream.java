package emuos.diskmanager;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Created by Link on 2017/7/15.
 */
public class OutputStream extends java.io.OutputStream {

    private static final FileSystem fs = FileSystem.getFileSystem();
    private MetaInfo metaInfo;
    private int currentBlockIndex;
    private int currentOffset;

    public OutputStream(FilePath file) throws IOException {
        metaInfo = fs.readMetaInfo(file.getPath());
        if (metaInfo == null) {
            throw new FileNotFoundException(file.getPath());
        }
        if (!metaInfo.isFile()) {
            throw new IOException("Opening file '" + file.getPath() + "' failed.");
        }
        fs.freeBlock(metaInfo.startBlockIndex);
        metaInfo.length = 0;
        currentBlockIndex
                = metaInfo.startBlockIndex
                = (byte) fs.allocFreeBlock();
        fs.writeMetaInfo(metaInfo);
        currentOffset = 0;
    }

    @Override
    public void write(int b) throws IOException {
        if (currentOffset >= FileSystem.BLOCK_SIZE) {
            int nextBlockIndex = fs.read(currentBlockIndex);
            if (nextBlockIndex == -1) {
                nextBlockIndex = fs.allocFreeBlock();
                if (nextBlockIndex == -1) {
                    throw new IOException("Not enough space.");
                }
                fs.write(currentBlockIndex, (byte) nextBlockIndex);
            }
            currentBlockIndex = nextBlockIndex;
            currentOffset = 0;
        }
        metaInfo.length++;
        fs.writeMetaInfo(metaInfo);
        fs.write(currentBlockIndex, currentOffset++, (byte) b);
    }

    @Override
    public void close() throws IOException {
        fs.flush();
        super.close();
    }

    @Override
    public void flush() throws IOException {
        fs.flush();
        super.flush();
    }
}
