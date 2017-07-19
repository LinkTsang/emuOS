package emuos.diskmanager;

import java.util.Iterator;

/**
 * Created by Link on 2017/7/15.
 */
public class MetaInfoIterator implements Iterator<MetaInfo> {
    private MetaInfo parent;
    private int currentBlockIndex;
    private int currentIndexInBlock;
    private int rest;

    MetaInfoIterator(MetaInfo parent) {
        this.parent = parent;
        currentBlockIndex = parent.startBlockIndex;
        currentIndexInBlock = 0;
        rest = parent.length;
    }

    MetaInfoIterator() {
        this.parent = null;
        currentBlockIndex = -1;
        currentIndexInBlock = 0;
        rest = 0;
    }

    @Override
    public boolean hasNext() {
        return rest > 0 && currentBlockIndex != -1;
    }

    @Override
    public MetaInfo next() {
        FileSystem fs = FileSystem.getFileSystem();
        if (rest <= 0 || currentBlockIndex == -1) return null;
        MetaInfo metaInfo = fs.readMetaInfo(currentBlockIndex, currentIndexInBlock * FileSystem.FILEINFO_SIZE);
        currentIndexInBlock++;
        rest--;
        if (currentIndexInBlock == FileSystem.BLOCK_FILEINFO_COUNT) {
            currentBlockIndex = fs.read(currentBlockIndex);
            currentIndexInBlock = 0;
        }
        return metaInfo;
    }
}
