package com.fxyan.draco.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.fxyan.draco.R;

/**
 * @author fxYan
 */
public final class RenderMenuActivity
        extends AppCompatActivity {

    public static final String KEY = "key";

    public static void open(Context context, String key) {
        Intent intent = new Intent(context, RenderMenuActivity.class);
        Bundle bundle = new Bundle();
        bundle.putString(KEY, key);
        intent.putExtras(bundle);
        context.startActivity(intent);
    }

    private String key;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_render_menu);

        key = getIntent().getExtras().getString(KEY);
    }

    public void objRender(View view) {
        ThreeDActivity.open(this, key, false);
    }

    public void plyRender(View view) {
        ThreeDActivity.open(this, key, true);
    }
}
