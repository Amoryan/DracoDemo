package com.fxyan.draco.ui;

import android.util.Log;

import com.fxyan.draco.entity.PlyModel;
import com.fxyan.draco.utils.StorageUtils;

import org.smurn.jply.Element;
import org.smurn.jply.ElementReader;
import org.smurn.jply.PlyReaderFile;

import java.io.File;
import java.io.IOException;

import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * @author fxYan
 */
public final class PlyRenderer
        extends BaseRenderer {

    private ThreeDActivity context;

    public PlyRenderer(ThreeDActivity context) {
        this.context = context;
    }

    protected boolean isModelFileExist(String key) {
        File file = StorageUtils.plyFile(key);
        if (file.exists()) {
            Log.d("fxYan", String.format("文件%s.ply已存在，直接加载", key));
            readModelFile(key, file.getAbsolutePath(), true);
            return true;
        }
        return false;
    }

    @Override
    protected File genDecodeFile(String key) {
        return StorageUtils.plyFile(key);
    }

    @Override
    protected boolean decodeModel(String dracoFile, String decodeFile) {
        return context.decodeDraco(dracoFile, decodeFile);
    }

    protected void readModelFile(String key, String path, boolean isExistRead) {
        Single.create((SingleOnSubscribe<PlyModel>) emitter -> {
            boolean result = false;

            PlyReaderFile reader = null;
            float[] vertex = null;
            int[] index = null;

            try {
                long start = System.currentTimeMillis();
                Log.d("fxYan", String.format("文件%s开始解析", path));

                reader = new PlyReaderFile(path);
                vertex = readVertex(reader);
                index = readFace(reader);
                result = true;

                long end = System.currentTimeMillis();
                Log.d("fxYan", String.format("%s文件解析完成，耗时%s", path, (end - start)));
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (result) {
                emitter.onSuccess(new PlyModel(vertex, index));
            } else {
                emitter.onError(new RuntimeException());
            }
        }).observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new SingleObserver<PlyModel>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        disposables.add(d);
                    }

                    @Override
                    public void onSuccess(PlyModel plyModel) {
                        modelMap.put(key, plyModel);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d("fxYan", String.format("路径为 %s 的文件解析失败", path));

                        if (isExistRead) {
                            downloadAndDecodeDracoFile(key);
                        }
                    }
                });
    }

    private float[] readVertex(PlyReaderFile reader) throws IOException {
        float[] vertex;
        ElementReader elementReader = reader.nextElementReader();
        vertex = new float[elementReader.getCount() * PlyModel.PER_VERTEX_SIZE];
        for (int i = 0; i < elementReader.getCount(); i++) {
            Element element = elementReader.readElement();
            vertex[i * PlyModel.PER_VERTEX_SIZE] = (float) element.getDouble("x");
            vertex[i * PlyModel.PER_VERTEX_SIZE + 1] = (float) element.getDouble("y");
            vertex[i * PlyModel.PER_VERTEX_SIZE + 2] = (float) element.getDouble("z");

            vertex[i * PlyModel.PER_VERTEX_SIZE + 3] = (float) element.getDouble("nx");
            vertex[i * PlyModel.PER_VERTEX_SIZE + 4] = (float) element.getDouble("ny");
            vertex[i * PlyModel.PER_VERTEX_SIZE + 5] = (float) element.getDouble("nz");
        }
        elementReader.close();
        return vertex;
    }

    private int[] readFace(PlyReaderFile reader) throws IOException {
        int[] index;
        ElementReader elementReader = reader.nextElementReader();
        int PER_FACE_VERTEX_COUNT = 3;
        index = new int[elementReader.getCount() * PER_FACE_VERTEX_COUNT];
        for (int i = 0; i < elementReader.getCount(); i++) {
            Element element = elementReader.readElement();
            int[] vertex_indices = element.getIntList("vertex_indices");
            index[i * PER_FACE_VERTEX_COUNT] = vertex_indices[0];
            index[i * PER_FACE_VERTEX_COUNT + 1] = vertex_indices[1];
            index[i * PER_FACE_VERTEX_COUNT + 2] = vertex_indices[2];
        }
        elementReader.close();
        return index;
    }

    @Override
    public void destroy() {
        super.destroy();
        context = null;
    }
}
