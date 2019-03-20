package com.fxyan.draco.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import com.fxyan.draco.R;
import com.fxyan.draco.entity.PlyModel;
import com.fxyan.draco.utils.GLESUtils;

import org.smurn.jply.Element;
import org.smurn.jply.ElementReader;
import org.smurn.jply.PlyReaderFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * @author fxYan
 */
public final class Renderer
        implements GLSurfaceView.Renderer {

    private CopyOnWriteArrayList<PlyModel> models = new CopyOnWriteArrayList<>();
    private Map<String, PlyModel> map = new HashMap<>();

    float[] mvpMatrix = new float[16];
    float[] mvMatrix = new float[16];
    float[] modelMatrix = new float[16];
    float[] viewMatrix = new float[16];
    float[] projectionMatrix = new float[16];

    private int programHandle;

    private Context context;

    public Renderer(Context context) {
        this.context = context;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        programHandle = GLESUtils.createAndLinkProgram("ply.vert", "ply.frag");

        int[] textureIds = new int[2];
        GLES20.glGenTextures(2, textureIds, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_MIRRORED_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_MIRRORED_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.mipmap.t950);
//        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.kh);
//        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.kb);
//        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.kr);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

        for (PlyModel model : models) {
            model.onSurfaceCreated(gl, config);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        Matrix.setLookAtM(viewMatrix, 0, 0, 0, 30f, 0f, 0f, -5f, 1f, 1f, 1f);

        float ratio = (float) width / height;

        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 1f, 100f);
        for (PlyModel model : models) {
            model.onSurfaceChanged(gl, width, height);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(programHandle);

        Matrix.setIdentityM(modelMatrix, 0);
        long time = SystemClock.uptimeMillis() % 10000L;
        float angleInDegrees = (360.0f / 10000.0f) * ((int) time);
        Matrix.rotateM(modelMatrix, 0, angleInDegrees, 1f, 1f, 1f);
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0);

        for (PlyModel model : models) {
            model.onDrawFrame(mvpMatrix, programHandle);
        }
    }

    public void readPlyFile(String path) {
        Single.create((SingleOnSubscribe<PlyModel>) emitter -> {
            PlyReaderFile reader = null;
            try {
                reader = new PlyReaderFile(path);

                float[] vertex = readVertex(reader);

                int[] index = readFace(reader);

                emitter.onSuccess(new PlyModel(vertex, index));
            } catch (IOException e) {
                emitter.onError(e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        emitter.onError(e);
                    }
                }
            }
        }).observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new SingleObserver<PlyModel>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onSuccess(PlyModel plyModel) {
                        map.put(path, plyModel);
                        models.add(plyModel);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d("fxYan", String.format("路径为 %s 的文件解析失败", path));
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

}
