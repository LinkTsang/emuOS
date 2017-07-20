/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.diskmanager;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author Link
 */
public class FilePath {

    public static final String separatorChar = "/";
    private final String path;
    private FileSystem fs = FileSystem.getFileSystem();

    public FilePath(String path) {
        if (path.startsWith(separatorChar)) {
            this.path = path;
        } else {
            this.path = "/" + path;
        }
    }

    public FilePath(FilePath parent, String name) {
        if (parent.isRootDir()) {
            this.path = parent.getPath() + name;
        } else {
            this.path = parent.getPath() + separatorChar + name;
        }
    }

    public FilePath(String parentPath, String name) {
        this(new FilePath(parentPath), name);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FilePath && ((FilePath) obj).path.equals(path);
    }

    public String getParent() {
        int index = path.lastIndexOf(separatorChar);
        return index < 1 ? "/" : path.substring(0, index);
    }

    public String getName() {
        int index = path.lastIndexOf(separatorChar);
        return path.substring(index + 1, path.length());
    }

    public String getPath() {
        return path;
    }

    public int getAttrubite() throws FileNotFoundException {
        return fs.getAttribute(this);
    }

    public boolean isFile() throws FileNotFoundException {
        return (fs.getAttribute(this) & MetaInfo.MASK_FILE) != 0;
    }

    public boolean isDir() throws FileNotFoundException {
        return (fs.getAttribute(this) & MetaInfo.MASK_FILE) == 0;
    }

    public boolean exists() {
        return fs.exists(this);
    }

    public int size() throws FileNotFoundException {
        return fs.getSize(this);
    }

    public void mkdir() throws IOException {
        fs.mkdir(this);
    }

    public boolean create() throws IOException {
        return fs.createNewFile(this);
    }

    public void delete() throws IOException {
        fs.delete(this);
    }

    public void renameTo(String name) throws IOException {
        fs.rename(this, name);
    }

    public void moveTo(FilePath path) {
        throw new UnsupportedOperationException();
    }

    public FilePath[] list() {
        return fs.list(this);
    }

    @Override
    public String toString() {
        return "{"
                + "Path: '" + getPath() + "'; "
                + "Name: '" + getName() + "'; "
                + "Parent: '" + getParent() + "' "
                + "}";
    }

    public boolean isRootDir() {
        return path.equals(separatorChar);
    }

    public FilePath getParentFile() {
        return new FilePath(getParent());
    }
}
