package com.fxyan.draco.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;
import android.util.LruCache;

import com.fxyan.draco.entity.IModel;
import com.fxyan.draco.net.ApiCreator;
import com.fxyan.draco.utils.GLESUtils;
import com.fxyan.draco.utils.StorageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleObserver;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * @author fxYan
 */
public abstract class BaseRenderer
        implements GLSurfaceView.Renderer {

    protected CompositeDisposable disposables;
    protected ConcurrentHashMap<String, IModel> modelMap;
    private ConcurrentHashMap<String, String> materialMap;
    private LruCache<String, Bitmap> bitmapCache;

    private float[] mvpMatrix = new float[16];
    private float[] mvMatrix = new float[16];
    private float[] modelMatrix = new float[16];
    private float[] viewMatrix = new float[16];
    private float[] projectionMatrix = new float[16];

    private int programHandle;
    private float scale = 1;

    public BaseRenderer() {
        this.disposables = new CompositeDisposable();
        this.modelMap = new ConcurrentHashMap<>();
        this.materialMap = new ConcurrentHashMap<>();
        bitmapCache = new LruCache<String, Bitmap>(1024) {
            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                if (oldValue != newValue) {
                    oldValue.recycle();
                }
            }
        };
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.8f, 0.8f, 0.8f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        programHandle = GLESUtils.createAndLinkProgram("ply.vert", "ply.frag");

        int[] textureIds = new int[1];
        GLES20.glGenTextures(1, textureIds, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_MIRRORED_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_MIRRORED_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        for (IModel model : modelMap.values()) {
            model.onSurfaceCreated();
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        Matrix.setLookAtM(viewMatrix, 0, 0, 0, 30f, 0f, 0f, -5f, 0f, 1f, 0f);

        float ratio = (float) width / height;

        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 1f, 50f);
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
        long time = SystemClock.uptimeMillis() % 10000L;
        float angleInDegrees = (360.0f / 10000.0f) * ((int) time);
        Matrix.rotateM(modelMatrix, 0, angleInDegrees, 1f, 1f, 1f);
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0);

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

    public void removeModel(String key) {
        modelMap.remove(key);
    }

    public void addModel(String style, String material) {
        renderStyle(style);

        materialMap.put(style, material);

        renderMaterial(material);
    }

    public void updateMaterial(String style, String material) {
        materialMap.put(style, material);
        renderMaterial(material);
    }

    private void renderStyle(String key) {
        if (!isModelFileExist(key)) {
            downloadAndDecodeDracoFile(key);
        }
    }

    private void renderMaterial(String key) {
        Bitmap cache = bitmapCache.get(key);
        if (cache == null) {
            File file = StorageUtils.imageFile(key);
            if (file.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bitmap != null) {
                    bitmapCache.put(key, bitmap);
                } else {
                    downloadImage(key);
                }
            } else {
                downloadImage(key);
            }
        }
    }

    protected abstract boolean isModelFileExist(String key);

    protected void downloadAndDecodeDracoFile(String key) {
        Single.create((SingleOnSubscribe<String>) emitter -> {
            boolean result = false;
            File decodeFile = genDecodeFile(key);

            Call<ResponseBody> download = ApiCreator.api().download(key);
            InputStream is = null;
            FileOutputStream fos = null;
            try {
                Response<ResponseBody> execute = download.execute();
                ResponseBody body = execute.body();
                if (execute.isSuccessful() && body != null) {
                    long start = System.currentTimeMillis();
                    Log.d("fxYan", String.format("文件%s开始下载", key));

                    is = body.byteStream();
                    File draco = StorageUtils.dracoFile(key);
                    fos = new FileOutputStream(draco);
                    byte[] buf = new byte[1024 * 4 * 4];
                    int len;
                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                        fos.flush();
                    }

                    long end = System.currentTimeMillis();
                    Log.d("fxYan", String.format("文件%s下载完成，耗时%s", key, (end - start)));

                    start = System.currentTimeMillis();
                    Log.d("fxYan", String.format("文件%s开始解压", key));

                    if (decodeModel(draco.getAbsolutePath(), decodeFile.getAbsolutePath())) {
                        end = System.currentTimeMillis();
                        Log.d("fxYan", String.format("文件%s解压完成，耗时%s", key, (end - start)));

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
                emitter.onSuccess(decodeFile.getAbsolutePath());
            } else {
                emitter.onError(new RuntimeException());
            }
        }).observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new SingleObserver<String>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        disposables.add(d);
                    }

                    @Override
                    public void onSuccess(String file) {
                        readModelFile(key, file, false);
                    }

                    @Override
                    public void onError(Throwable e) {
                    }
                });
    }

    protected abstract File genDecodeFile(String key);

    protected abstract boolean decodeModel(String dracoFile, String decodeFile);

    protected void readModelFile(String key, String path, boolean isExistRead) {
        Single.create((SingleOnSubscribe<IModel>) emitter -> parseFile(path, emitter)).observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new SingleObserver<IModel>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        disposables.add(d);
                    }

                    @Override
                    public void onSuccess(IModel plyModel) {
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

    protected abstract void parseFile(String path, SingleEmitter<IModel> emitter);

    private void downloadImage(String material) {
        Single.create(new SingleOnSubscribe<Bitmap>() {
            @Override
            public void subscribe(SingleEmitter<Bitmap> emitter) throws Exception {
                boolean result = false;
                File image = StorageUtils.imageFile(material);

                Bitmap bitmap = null;
                InputStream is = null;
                FileOutputStream fos = null;

                try {
                    Response<ResponseBody> execute = ApiCreator.api().download(material).execute();
                    ResponseBody body = execute.body();
                    if (execute.isSuccessful() && body != null) {
                        is = body.byteStream();
                        fos = new FileOutputStream(image);
                        byte[] buf = new byte[1024 * 4 * 4];
                        int len;
                        while ((len = is.read(buf)) != -1) {
                            fos.write(buf, 0, len);
                            fos.flush();
                        }

                        bitmap = BitmapFactory.decodeFile(image.getAbsolutePath());
                        result = true;
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
                    emitter.onSuccess(bitmap);
                } else {
                    emitter.onError(new RuntimeException());
                }
            }
        }).observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new SingleObserver<Bitmap>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        disposables.add(d);
                    }

                    @Override
                    public void onSuccess(Bitmap bitmap) {
                        bitmapCache.put(material, bitmap);
                    }

                    @Override
                    public void onError(Throwable e) {
                    }
                });
    }

    public void destroy() {
        disposables.clear();
    }

}
