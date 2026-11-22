package com.macos.launcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileNotFoundException;

/** Minimal FileProvider replacement (no androidx): serves files by path with URI grants. */
public class FileProv extends ContentProvider {
    public static Uri uri(File f) {
        return new Uri.Builder().scheme("content").authority("com.macos.launcher.files")
                .encodedPath(Uri.encode(f.getAbsolutePath(), "/")).build();
    }
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri u, String[] proj, String sel, String[] args, String sort) {
        File f = new File(u.getPath());
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }
    @Override public String getType(Uri u) {
        String n = new File(u.getPath()).getName();
        int i = n.lastIndexOf('.');
        String m = i >= 0 ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(n.substring(i + 1).toLowerCase()) : null;
        return m != null ? m : "application/octet-stream";
    }
    @Override public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(new File(u.getPath()), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
