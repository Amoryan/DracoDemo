package com.fxyan.draco.ui;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import com.fxyan.draco.entity.IModel;
import com.fxyan.draco.entity.PlyModel;
import com.fxyan.draco.utils.StorageUtils;

import org.smurn.jply.Element;
import org.smurn.jply.ElementReader;
import org.smurn.jply.PlyReaderFile;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.microedition.khronos.opengles.GL10;

import io.reactivex.SingleEmitter;

/**
 * @author fxYan
 * ply 渲染 正交投影
 */
public final class OrthogonalityPlyRenderer
        extends BaseRenderer {

    private ThreeDActivity context;

    public OrthogonalityPlyRenderer(ThreeDActivity context) {
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
        return context.decodeDraco(dracoFile, decodeFile, true);
    }

    @Override
    protected void parseFile(String path, SingleEmitter<IModel> emitter) {
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
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        float ratio = (float) width / height;

        Matrix.orthoM(projectionMatrix, 0, -ratio, ratio, -1, 1, -1, 1);
        for (IModel model : modelMap.values()) {
            model.onSurfaceChanged(width, height);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(programHandle);

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.scaleM(modelMatrix, 0, scale, scale, scale);
        Matrix.rotateM(modelMatrix, 0, rotateX, 1f, 0f, 0f);
        Matrix.rotateM(modelMatrix, 0, rotateY, 0f, 1f, 0f);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0);

        for (Map.Entry<String, IModel> entry : modelMap.entrySet()) {
            String style = entry.getKey();
            String material = materialMap.get(style);
            Bitmap bitmap = bitmapCache.get(material);
            if (bitmap != null && !bitmap.isRecycled()) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            }
            entry.getValue().onDrawFrame(mvpMatrix, programHandle);
        }
    }

    private float[] readVertex(PlyReaderFile reader) throws IOException {
        float[] vertex;
        ElementReader elementReader = reader.nextElementReader();
        vertex = new float[elementReader.getCount() * IModel.PER_VERTEX_SIZE];
        for (int i = 0; i < elementReader.getCount(); i++) {
            Element element = elementReader.readElement();
            // todo这里最好是取最大的最标点，然后将正交投影的立方体放大然后看模型，这里为了简单，直接除以了 30
            vertex[i * IModel.PER_VERTEX_SIZE] = (float) element.getDouble("x") / 30;
            vertex[i * IModel.PER_VERTEX_SIZE + 1] = (float) element.getDouble("y") / 30;
            vertex[i * IModel.PER_VERTEX_SIZE + 2] = (float) element.getDouble("z") / 30;

            vertex[i * IModel.PER_VERTEX_SIZE + 3] = (float) element.getDouble("nx");
            vertex[i * IModel.PER_VERTEX_SIZE + 4] = (float) element.getDouble("ny");
            vertex[i * IModel.PER_VERTEX_SIZE + 5] = (float) element.getDouble("nz");
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
