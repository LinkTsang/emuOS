package emuos.diskmanager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * @author Link
 *         <code>
 *         attribute:
 *         ---------------------------------------------------------------------
 *         | x | x | x | x | isHidden | isFile(/isDir) | SystemFile | ReadOnly |
 *         ---------------------------------------------------------------------
 *         </code>
 */
class MetaInfo implements Iterable<MetaInfo> {
    static final int EXT_LENGTH = 1;
    static final int NAME_LENGTH = 3;
    static final int FULL_NAME_LENGTH = EXT_LENGTH + NAME_LENGTH;
    static final int MASK_HIDDEN = 0b1000;
    static final int MASK_FILE = 0b0100;
    static final int MASK_SYSTEM = 0b0010;
    static final int MASK_READONLY = 0b0001;

    // info on disk
    byte[] name_ext = new byte[FULL_NAME_LENGTH];
    byte attribute;
    byte startBlockIndex;
    short length;           // the length of file or the count of sub directories and files

    // extra info
    int address;

    static MetaInfo newDir(int address, String name, byte startBlockIndex) throws IOException {
        MetaInfo metaInfo = new MetaInfo();
        metaInfo.address = address;
        metaInfo.setName(name);
        metaInfo.startBlockIndex = startBlockIndex;
        return metaInfo;
    }

    static MetaInfo newFile(int address, String name, byte startBlockIndex) throws IOException {
        MetaInfo metaInfo = new MetaInfo();
        metaInfo.address = address;
        metaInfo.setName(name);
        metaInfo.startBlockIndex = startBlockIndex;
        metaInfo.attribute |= MASK_FILE;
        return metaInfo;
    }

    static MetaInfo emptyDir() {
        MetaInfo metaInfo = new MetaInfo();
        metaInfo.name_ext[0] = '$';
        return metaInfo;
    }

    static MetaInfo rootDir() {
        MetaInfo metaInfo = new MetaInfo();
        metaInfo.name_ext[0] = '/';
        metaInfo.attribute = MetaInfo.MASK_SYSTEM;
        metaInfo.startBlockIndex = FileSystem.ROOT_BLOCK_INDEX;
        metaInfo.length = -1;
        metaInfo.address = -1;
        return metaInfo;
    }

    private static boolean isValidFileName(String name) {
        for (int i = 0; i < name.length(); ++i) {
            char c = name.charAt(i);
            if (c == '$' || c == '/') {
                return false;
            }
        }
        return true;
    }

    boolean isRootDir() {
        return address == -1;
    }

    boolean isFile() {
        return (attribute & MASK_FILE) != 0;
    }

    boolean isSystem() {
        return (attribute & MASK_SYSTEM) != 0;
    }

    boolean isReadOnly() {
        return (attribute & MASK_READONLY) != 0;
    }

    boolean isHidden() {
        return (attribute & MASK_HIDDEN) != 0;
    }

    String getName() {
        if (isFile()) {
            StringBuilder stringBuilder = new StringBuilder(name_ext.length + 1);
            for (int i = 0; i < NAME_LENGTH && name_ext[i] != 0; ++i) {
                stringBuilder.append((char) name_ext[i]);
            }
            if (name_ext[NAME_LENGTH] != 0) {
                stringBuilder.append('.');
                for (int i = 0; i < EXT_LENGTH && name_ext[NAME_LENGTH + i] != 0; ++i) {
                    stringBuilder.append((char) name_ext[NAME_LENGTH + i]);
                }
            }
            return stringBuilder.toString();
        } else {
            StringBuilder stringBuilder = new StringBuilder(name_ext.length);
            for (int i = 0; i < FULL_NAME_LENGTH && name_ext[i] != 0; ++i) {
                stringBuilder.append((char) name_ext[i]);
            }
            return stringBuilder.toString();
        }
    }

    void setName(String name) throws IOException {
        if (isFile()) {
            String[] names = name.split("\\.");
            if (names.length == 1 && names[0].length() < NAME_LENGTH && !names[0].matches("[$/]")) {
                System.arraycopy(names[0].getBytes(StandardCharsets.US_ASCII), 0,
                        name_ext, 0, names[0].length());
            } else if (names.length == 2
                    && names[0].length() <= NAME_LENGTH && !names[0].matches("[$/]")
                    && names[1].length() <= EXT_LENGTH && !names[1].matches("[$/]")) {
                System.arraycopy(names[0].getBytes(StandardCharsets.US_ASCII), 0,
                        name_ext, 0, names[0].length());
                System.arraycopy(names[1].getBytes(StandardCharsets.US_ASCII), 0,
                        name_ext, NAME_LENGTH, names[1].length());
            } else {
                throw new IOException("Illegal file name.");
            }
        } else {
            if (name.length() > NAME_LENGTH + EXT_LENGTH || !isValidFileName(name)) {
                throw new IOException("Illegal file name.");
            }
            System.arraycopy(name.getBytes(StandardCharsets.US_ASCII), 0,
                    name_ext, 0, name.length());
        }
    }

    @Override
    public Iterator<MetaInfo> iterator() {
        if (isFile()) return new MetaInfoIterator();
        return new MetaInfoIterator(this);
    }

    @Override
    public String toString() {
        return String.format("{Name: %s; File: %s; Length: %d }", getName(), isFile(), length);
    }

    boolean isDir() {
        return (attribute & MASK_FILE) == 0;
    }
}
