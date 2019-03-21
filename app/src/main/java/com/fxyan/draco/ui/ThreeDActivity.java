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
import com.fxyan.draco.entity.Item;
import com.fxyan.draco.entity._3DDetail;
import com.fxyan.draco.entity._3DItem;
import com.fxyan.draco.utils.AssetsUtils;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * @author fxYan
 */
public final class ThreeDActivity
        extends AppCompatActivity {

    static {
        System.loadLibrary("native-lib");
    }

    private static final String KEY = "key";

    public static void open(Context context, String name) {
        Intent intent = new Intent(context, ThreeDActivity.class);
        Bundle bundle = new Bundle();
        bundle.putString(KEY, name);
        intent.putExtras(bundle);
        context.startActivity(intent);
    }

    private Context context;

    private List<_3DItem> data = new ArrayList<>();
    private Adapter adapter;

    private Renderer renderer;

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
        renderer = new Renderer(this);
        surfaceView.setRenderer(renderer);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        recyclerView.setAdapter(adapter);
    }

    public native boolean decodeDraco(String draco, String ply);

    private void fetchData(String name) {
        Single.create((SingleOnSubscribe<_3DDetail>) emitter -> {
            String json = AssetsUtils.read(name);
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

                        renderer.addModel(item.huatuo.style.get(0).key);
                        renderer.addModel(item.jiebi.style.get(0).key);
                        renderer.addModel(item.zhushi.style.get(0).key);
                        renderer.addModel(item.fushi.style.get(0).key);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d("fxYan", "error");
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        renderer.destroy();
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
