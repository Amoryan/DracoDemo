package com.fxyan.draco.utils;

import android.os.Environment;

import java.io.File;

/**
 * @author fxYan
 */
public final class StorageUtils {

    public static File dracoFile(String key) {
        return new File(rootFile(), String.format("%s.drc", key));
    }

    public static File imageFile(String key) {
        return new File(rootFile(), String.format("%s.png", key));
    }

    public static File plyFile(String key) {
        return new File(rootFile(), String.format("%s.ply", key));
    }

    private static File rootFile() {
        File root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "JPARK");
        if (!root.exists()) {
            root.mkdirs();
        }
        return root;
    }

}
