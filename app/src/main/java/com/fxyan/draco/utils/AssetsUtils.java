package com.fxyan.draco.utils;

import com.fxyan.draco.BaseApp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author fxYan
 */
public final class AssetsUtils {

    public static String read(String path) {
        StringBuilder builder = new StringBuilder();
        BufferedReader bufr = null;
        try {
            bufr = new BufferedReader(new InputStreamReader(BaseApp.getContext().getAssets().open(path)));
            String line;
            while ((line = bufr.readLine()) != null) {
                builder.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bufr != null) {
                try {
                    bufr.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return builder.toString();
    }

}
