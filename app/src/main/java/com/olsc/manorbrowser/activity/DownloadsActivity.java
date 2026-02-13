package com.olsc.manorbrowser.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.Config;
import com.olsc.manorbrowser.adapter.DownloadAdapter;
import com.olsc.manorbrowser.data.DownloadInfo;
import com.olsc.manorbrowser.utils.DownloadHelper;

import java.util.ArrayList;
import java.util.List;

public class DownloadsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private DownloadAdapter adapter;
    private List<DownloadInfo> downloadList = new ArrayList<>();
    private Handler handler;
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.content.SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME_THEME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(Config.PREF_KEY_DARK_MODE, false);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(isDarkMode ? 
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        recyclerView = findViewById(R.id.rv_downloads);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new DownloadAdapter(this, downloadList);
        recyclerView.setAdapter(adapter);

        handler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                loadDownloads();
                handler.postDelayed(this, 1000); // 1s refresh
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDownloads();
        handler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshRunnable);
    }

    private void loadDownloads() {
        new Thread(() -> {
            List<DownloadInfo> info = DownloadHelper.getDownloads(this);
            if (info != null) {
                handler.post(() -> {
                    downloadList.clear();
                    downloadList.addAll(info);
                    adapter.notifyDataSetChanged();
                    
                    findViewById(R.id.rv_downloads).setVisibility(downloadList.isEmpty() ? View.GONE : View.VISIBLE);
                });
            }
        }).start();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
