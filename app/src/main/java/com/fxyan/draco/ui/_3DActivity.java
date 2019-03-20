package com.fxyan.draco.ui;

import android.content.Context;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.fxyan.draco.R;
import com.fxyan.draco.net.ApiCreator;
import com.fxyan.draco.pojo.Item;
import com.fxyan.draco.pojo._3DDetail;
import com.fxyan.draco.pojo._3DItem;
import com.fxyan.draco.utils.AssetsUtils;
import com.fxyan.draco.utils.StorageUtils;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
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
public final class _3DActivity
        extends AppCompatActivity {

    private static final String KEY = "key";

    public static void open(Context context, String name) {
        Intent intent = new Intent(context, _3DActivity.class);
        Bundle bundle = new Bundle();
        bundle.putString(KEY, name);
        intent.putExtras(bundle);
        context.startActivity(intent);
    }

    private Context context;

    private List<_3DItem> data = new ArrayList<>();
    private Adapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_3d);

        context = this;

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String key = extras.getString(KEY);

            fetchData(String.format("%s.json", key));
        }

        GLSurfaceView surfaceView = findViewById(R.id.surfaceView);
        surfaceView.setEGLContextClientVersion(2);// use opengl es 2.0
        surfaceView.setRenderer(new Renderer());

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        recyclerView.setAdapter(adapter);
    }

    private void fetchData(String name) {
        Single.create((SingleOnSubscribe<_3DDetail>) emitter -> {
            String json = AssetsUtils.read(context, name);
            _3DDetail data = new Gson().fromJson(json, _3DDetail.class);
            emitter.onSuccess(data);
        }).observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new SingleObserver<_3DDetail>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onSuccess(_3DDetail item) {
                        data.add(item.huatuo);
                        data.add(item.jiebi);
                        data.add(item.zhushi);
                        data.add(item.fushi);
                        adapter.notifyDataSetChanged();

                        List<String> tasks = new ArrayList<>();
                        tasks.add(item.huatuo.style.get(0).key);
                        tasks.add(item.jiebi.style.get(0).key);
                        tasks.add(item.zhushi.style.get(0).key);
                        tasks.add(item.fushi.style.get(0).key);

                        downloadDraco(tasks);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d("fxYan", "error");
                    }
                });
    }

    private void downloadDraco(List<String> tasks) {
        for (String task : tasks) {
            Single.create(new SingleOnSubscribe<Object>() {
                @Override
                public void subscribe(SingleEmitter<Object> emitter) throws Exception {
                    Call<ResponseBody> download = ApiCreator.api().download(task);
                    InputStream is = null;
                    FileOutputStream fos = null;
                    try {
                        Response<ResponseBody> execute = download.execute();
                        ResponseBody body = execute.body();
                        if (execute.isSuccessful() && body != null) {
                            is = body.byteStream();
                            File draco = StorageUtils.dracoFile(task);
                            fos = new FileOutputStream(draco);
                            byte[] buf = new byte[1024 * 4 * 4];
                            int len;
                            while ((len = is.read(buf)) != -1) {
                                fos.write(buf, 0, len);
                                fos.flush();
                            }
                            Log.d("fxYan", "download success");
                        }
                    } catch (IOException e) {
                        Log.d("fxYan", "download failed");
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
                }
            }).observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe(new SingleObserver<Object>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onSuccess(Object o) {

                        }

                        @Override
                        public void onError(Throwable e) {
                            Log.d("fxYan", "downlaod failed");
                        }
                    });
        }
    }

    class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(context).inflate(R.layout.listitem_3d, viewGroup, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
            View root = viewHolder.itemView;

            _3DItem item = data.get(i);

            TextView label = root.findViewById(R.id.label);
            label.setText(item.name);

            RecyclerView styleRv = root.findViewById(R.id.styleRv);
            styleRv.setLayoutManager(new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false));
            styleRv.setAdapter(new ChildAdapter(item.style));

            RecyclerView materialRv = root.findViewById(R.id.materialRv);
            materialRv.setLayoutManager(new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false));
            materialRv.setAdapter(new ChildAdapter(item.material));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    class ChildAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private List<Item> data;

        public ChildAdapter(List<Item> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(context).inflate(R.layout.listitem_3d_child, viewGroup, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
            View root = viewHolder.itemView;
            Item item = data.get(i);
            TextView view = root.findViewById(R.id.name);
            view.setText(item.name);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }
}
