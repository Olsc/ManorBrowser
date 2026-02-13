package com.olsc.manorbrowser.activity;

import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.Config;
import com.olsc.manorbrowser.adapter.HistoryAdapter;
import com.olsc.manorbrowser.data.HistoryStorage;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.content.SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME_THEME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(Config.PREF_KEY_DARK_MODE, false);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(isDarkMode ? 
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        androidx.recyclerview.widget.RecyclerView recyclerView = findViewById(R.id.recycler_view_history);
        List<HistoryStorage.HistoryItem> historyList = HistoryStorage.loadHistory(this);

        View btnClear = findViewById(R.id.btn_clear_history);
        TextView tvEmpty = findViewById(R.id.tv_empty_history);

        Runnable updateEmptyState = () -> {
            if (historyList.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                btnClear.setVisibility(View.GONE);
            } else {
                tvEmpty.setVisibility(View.GONE);
                btnClear.setVisibility(View.VISIBLE);
            }
        };

        HistoryAdapter adapter = new HistoryAdapter(historyList, item -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("url", item.url);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        updateEmptyState.run();

        btnClear.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle(R.string.clear_history)
                .setMessage(R.string.history_empty_confirm)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    HistoryStorage.clearHistory(this);
                    historyList.clear();
                    adapter.notifyDataSetChanged();
                    updateEmptyState.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

