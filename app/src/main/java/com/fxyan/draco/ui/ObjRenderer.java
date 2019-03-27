package com.fxyan.draco.ui;

import android.util.Log;

import com.fxyan.draco.entity.IModel;
import com.fxyan.draco.entity.ObjModel;
import com.fxyan.draco.utils.StorageUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import io.reactivex.SingleEmitter;

/**
 * @author fxYan
 */
public final class ObjRenderer
        extends BaseRenderer {

    private ThreeDActivity context;

    public ObjRenderer(ThreeDActivity context) {
        this.context = context;
    }

    @Override
    protected boolean isModelFileExist(String key) {
        File file = StorageUtils.objFile(key);
        if (file.exists()) {
            Log.d("fxYan", String.format("文件%s.obj已存在，直接加载", key));
            readModelFile(key, file.getAbsolutePath(), true);
            return true;
        }
        return false;
    }

    @Override
    protected File genDecodeFile(String key) {
        return StorageUtils.objFile(key);
    }

    @Override
    protected boolean decodeModel(String dracoFile, String decodeFile) {
        return context.decodeDraco(dracoFile, decodeFile, false);
    }

    @Override
    protected void parseFile(String path, SingleEmitter<IModel> emitter) {
        boolean result = false;

        BufferedReader bufr = null;
        ArrayList<Float> vertexList = new ArrayList<>();
        ArrayList<Float> normalList = new ArrayList<>();
        ArrayList<Integer> indexList = new ArrayList<>();

        long startTime = System.currentTimeMillis();
        Log.d("fxYan", String.format("文件%s开始解析", path));

        try {
            bufr = new BufferedReader(new FileReader(path));
            String line;
            while ((line = bufr.readLine()) != null) {
                String[] split = line.split(" ");
                if (split.length == 0) continue;
                if ("v".equals(split[0])) {
                    vertexList.add(Float.valueOf(split[1]));
                    vertexList.add(Float.valueOf(split[2]));
                    vertexList.add(Float.valueOf(split[3]));
                } else if ("vn".equals(split[0])) {
                    normalList.add(Float.valueOf(split[1]));
                    normalList.add(Float.valueOf(split[2]));
                    normalList.add(Float.valueOf(split[3]));
                } else if ("f".equals(split[0])) {
                    for (int i = 1; i < 4; i++) {
                        String[] array = split[i].split("/");
                        if (array.length > 0) {
                            indexList.add(Integer.valueOf(array[0]) - 1);
                        }
                        if (array.length > 2) {
                            indexList.add(Integer.valueOf(array[2]) - 1);
                        }
                    }
                }
            }

            result = true;
        } catch (Exception e) {
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

        if (result) {
            float[] vertex = new float[indexList.size() * 3];
            for (int i = 0; i < indexList.size(); i += 2) {
                int start = IModel.PER_VERTEX_SIZE * i / 2;
                int vIndex = indexList.get(i);
                vIndex *= IModel.PER_VERTEX_COORD_SIZE;
                vertex[start] = vertexList.get(vIndex);
                vertex[start + 1] = vertexList.get(vIndex + 1);
                vertex[start + 2] = vertexList.get(vIndex + 2);

                int nIndex = indexList.get(i + 1);
                nIndex *= IModel.PER_VERTEX_NORMAL_SIZE;
                vertex[start + 3] = normalList.get(nIndex);
                vertex[start + 4] = normalList.get(nIndex + 1);
                vertex[start + 5] = normalList.get(nIndex + 2);
            }

            long endTime = System.currentTimeMillis();
            Log.d("fxYan", String.format("%s文件解析完成，耗时%s", path, (endTime - startTime)));

            emitter.onSuccess(new ObjModel(vertex));
        } else {
            emitter.onError(new RuntimeException());
        }
    }
}
