package com.fxyan.draco.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.fxyan.draco.R;
import com.fxyan.draco.entity.Item;
import com.fxyan.draco.utils.AssetsUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class MainActivity
        extends AppCompatActivity {

    private Context context;

    private Adapter adapter;
    private List<Item> data = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        recyclerView.setAdapter(adapter);

        fetchData();

        boolean isGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
        if (!isGranted) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }
    }

    private void fetchData() {
        Single.create((SingleOnSubscribe<List<Item>>) emitter -> {
            String json = AssetsUtils.read("main.json");
            List<Item> data = new Gson().fromJson(json, new TypeToken<List<Item>>() {
            }.getType());
            emitter.onSuccess(data);
        }).observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new SingleObserver<List<Item>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onSuccess(List<Item> items) {
                        data.addAll(items);
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d("fxYan", "error");
                    }
                });
    }

    class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(context).inflate(R.layout.listitem_main, viewGroup, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
            View root = viewHolder.itemView;

            ImageView image = root.findViewById(R.id.image);
            TextView name = root.findViewById(R.id.name);

            Item item = data.get(i);

            Glide.with(context).asBitmap().load(String.format("https://api.app.jpark.vip/api/file/get/%s", item.key)).into(image);
            name.setText(item.name);

            root.setTag(item.key);
            root.setOnClickListener(v -> {
                String key = (String) v.getTag();
                ThreeDActivity.open(context, key);
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

}
