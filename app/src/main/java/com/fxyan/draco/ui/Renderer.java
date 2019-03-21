package com.fxyan.draco.ui;

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
import com.fxyan.draco.net.ApiCreator;
import com.fxyan.draco.utils.GLESUtils;
import com.fxyan.draco.utils.StorageUtils;

import org.smurn.jply.Element;
import org.smurn.jply.ElementReader;
import org.smurn.jply.PlyReaderFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
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
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

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

    private ThreeDActivity context;
    private Bitmap t950;
    private Bitmap zuanshi;

    public Renderer(ThreeDActivity context) {
        this.context = context;
    }

    public void removeModel(String key) {
        PlyModel plyModel = map.remove(key);
        if (plyModel != null) {
            models.remove(plyModel);
        }
    }

    public void addModel(String key) {

    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.8f, 0.8f, 0.8f, 1f);
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

        t950 = BitmapFactory.decodeResource(context.getResources(), R.mipmap.t950);
        zuanshi = BitmapFactory.decodeResource(context.getResources(), R.mipmap.zuanshi);

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
            if (model.type == 0) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, t950, 0);
            } else {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, zuanshi, 0);
            }
            model.onDrawFrame(mvpMatrix, programHandle);
        }
    }

    public void render(List<String> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            final int type;
            if (i < 2) {
                type = 0;
            } else {
                type = 1;
            }
            String task = tasks.get(i);

            File file = StorageUtils.plyFile(task);
            if (file.exists()) {
                Log.d("fxYan", String.format("文件%s.ply已存在，直接加载", task));
                readPlyFile(file.getAbsolutePath(), type);
                continue;
            }

            Single.create((SingleOnSubscribe<String>) emitter -> {
                boolean result = false;
                File ply = StorageUtils.plyFile(task);

                Call<ResponseBody> download = ApiCreator.api().download(task);
                InputStream is = null;
                FileOutputStream fos = null;
                try {
                    Response<ResponseBody> execute = download.execute();
                    ResponseBody body = execute.body();
                    if (execute.isSuccessful() && body != null) {
                        long start = System.currentTimeMillis();
                        Log.d("fxYan", String.format("文件%s开始下载", task));

                        is = body.byteStream();
                        File draco = StorageUtils.dracoFile(task);
                        fos = new FileOutputStream(draco);
                        byte[] buf = new byte[1024 * 4 * 4];
                        int len;
                        while ((len = is.read(buf)) != -1) {
                            fos.write(buf, 0, len);
                            fos.flush();
                        }

                        long end = System.currentTimeMillis();
                        Log.d("fxYan", String.format("文件%s下载完成，耗时%s", task, (end - start)));

                        start = System.currentTimeMillis();
                        Log.d("fxYan", String.format("文件%s开始解压", task));

                        if (context.decodeDraco(draco.getAbsolutePath(), ply.getAbsolutePath())) {
                            end = System.currentTimeMillis();
                            Log.d("fxYan", String.format("文件%s解压完成，耗时%s", task, (end - start)));

                            result = true;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                if (result) {
                    emitter.onSuccess(ply.getAbsolutePath());
                } else {
                    emitter.onError(new RuntimeException());
                }
            }).observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe(new SingleObserver<String>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onSuccess(String ply) {
                            readPlyFile(ply, type);
                        }

                        @Override
                        public void onError(Throwable e) {
                        }
                    });
        }
    }

    public void readPlyFile(String path, int type) {
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
                emitter.onSuccess(new PlyModel(vertex, index, type));
            } else {
                emitter.onError(new RuntimeException());
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
