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
public final class Renderer
        implements GLSurfaceView.Renderer {

    private ThreeDActivity context;

    private CompositeDisposable disposables;
    private ConcurrentHashMap<String, PlyModel> modelMap;
    private ConcurrentHashMap<String, String> materialMap;
    private LruCache<String, Bitmap> bitmapCache;

    private float[] mvpMatrix = new float[16];
    private float[] mvMatrix = new float[16];
    private float[] modelMatrix = new float[16];
    private float[] viewMatrix = new float[16];
    private float[] projectionMatrix = new float[16];

    private int programHandle;

    public Renderer(ThreeDActivity context) {
        this.context = context;
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

        for (PlyModel model : modelMap.values()) {
            model.onSurfaceCreated(gl, config);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        Matrix.setLookAtM(viewMatrix, 0, 0, 0, 30f, 0f, 0f, -5f, 1f, 1f, 1f);

        float ratio = (float) width / height;

        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 1f, 100f);
        for (PlyModel model : modelMap.values()) {
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

        for (Map.Entry<String, PlyModel> entry : modelMap.entrySet()) {
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
        String cache = materialMap.get(style);
        if (cache == null) {
            throw new RuntimeException("please invoke addModel(String, String) first, not find this style in map");
        }
        Bitmap bitmap = bitmapCache.get(material);
        if (bitmap == null) {
            downloadImage(material);
        }
    }

    private void renderStyle(String key) {
        if (!isPlyFileExist(key)) {
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

    private boolean isPlyFileExist(String key) {
        File file = StorageUtils.plyFile(key);
        if (file.exists()) {
            Log.d("fxYan", String.format("文件%s.ply已存在，直接加载", key));
            readPlyFile(key, file.getAbsolutePath(), true);
            return true;
        }
        return false;
    }

    private void downloadAndDecodeDracoFile(String key) {
        Single.create((SingleOnSubscribe<String>) emitter -> {
            boolean result = false;
            File ply = StorageUtils.plyFile(key);

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

                    if (context.decodeDraco(draco.getAbsolutePath(), ply.getAbsolutePath())) {
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
                emitter.onSuccess(ply.getAbsolutePath());
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
                    public void onSuccess(String ply) {
                        readPlyFile(key, ply, false);
                    }

                    @Override
                    public void onError(Throwable e) {
                    }
                });
    }

    public void readPlyFile(String key, String path, boolean isExistRead) {
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
        context = null;
    }

}
