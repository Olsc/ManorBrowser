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
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.graphics.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class HistoryActivity extends AppCompatActivity {

    private HistoryAdapter adapter;
    private List<HistoryStorage.HistoryItem> historyList;
    private Toolbar toolbar;
    private View btnClear;
    private View btnDeleteSelected;
    private View btnSelectAll;
    private View btnCancel;
    private TextView tvEmpty;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(com.olsc.manorbrowser.utils.LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.content.SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME_THEME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(Config.PREF_KEY_DARK_MODE, false);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(isDarkMode ?
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(!isDarkMode);
            controller.setAppearanceLightNavigationBars(!isDarkMode);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        toolbar = findViewById(R.id.toolbar_history);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        View mainView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            toolbar.setPadding(toolbar.getPaddingLeft(), insets.top, toolbar.getPaddingRight(), 0);
            v.setPadding(v.getPaddingLeft(), 0, v.getPaddingRight(), insets.bottom);
            return windowInsets;
        });

        androidx.recyclerview.widget.RecyclerView recyclerView = findViewById(R.id.recycler_view_history);
        historyList = HistoryStorage.loadHistory(this);

        btnClear = findViewById(R.id.btn_clear_history);
        btnDeleteSelected = findViewById(R.id.btn_delete_selected);
        btnSelectAll = findViewById(R.id.btn_select_all);
        btnCancel = findViewById(R.id.btn_cancel_selection);
        tvEmpty = findViewById(R.id.tv_empty_history);

        adapter = new HistoryAdapter(historyList, new HistoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(HistoryStorage.HistoryItem item) {
                Intent intent = new Intent(HistoryActivity.this, MainActivity.class);
                intent.putExtra("url", item.url);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }

            @Override
            public void onItemLongClick(HistoryStorage.HistoryItem item, int position) {
                showDeleteDialog(item, position);
            }

            @Override
            public void onSelectionChanged(int count) {
                updateSelectionUI(count);
            }
        });

        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        updateEmptyState();

        btnClear.setOnClickListener(v -> showClearAllDialog());
        
        btnDeleteSelected.setOnClickListener(v -> deleteSelectedItems());
        
        btnSelectAll.setOnClickListener(v -> adapter.selectAll());
        
        btnCancel.setOnClickListener(v -> exitSelectionMode());
    }

    private void showDeleteDialog(HistoryStorage.HistoryItem item, int position) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.action_delete)
            .setMessage(R.string.msg_confirm_delete_history)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                HistoryStorage.deleteHistoryItem(this, item);
                historyList.remove(position);
                adapter.notifyItemRemoved(position);
                updateEmptyState();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.action_select_multiple, (dialog, which) -> {
                enterSelectionMode();
                adapter.toggleSelection(position);
            })
            .show();
    }

    private void enterSelectionMode() {
        adapter.setSelectionMode(true);
        updateSelectionUI(0);
    }

    private void exitSelectionMode() {
        adapter.setSelectionMode(false);
        updateNormalUI();
    }

    private void updateSelectionUI(int count) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.title_selected_count, count));
        }
        btnClear.setVisibility(View.GONE);
        btnDeleteSelected.setVisibility(View.VISIBLE);
        btnSelectAll.setVisibility(View.VISIBLE);
        btnCancel.setVisibility(View.VISIBLE);
        
        btnDeleteSelected.setEnabled(count > 0);
    }

    private void updateNormalUI() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_history);
        }
        btnClear.setVisibility(historyList.isEmpty() ? View.GONE : View.VISIBLE);
        btnDeleteSelected.setVisibility(View.GONE);
        btnSelectAll.setVisibility(View.GONE);
        btnCancel.setVisibility(View.GONE);
    }

    private void deleteSelectedItems() {
        Set<Integer> selectedPositions = adapter.getSelectedPositions();
        if (selectedPositions.isEmpty()) return;

        new AlertDialog.Builder(this)
            .setTitle(R.string.action_delete)
            .setMessage(getString(R.string.msg_confirm_delete_selected, selectedPositions.size()))
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                List<Integer> sortedPositions = new ArrayList<>(selectedPositions);
                Collections.sort(sortedPositions, Collections.reverseOrder());

                for (int position : sortedPositions) {
                    if (position < historyList.size()) {
                        HistoryStorage.deleteHistoryItem(this, historyList.get(position));
                        historyList.remove(position);
                    }
                }

                exitSelectionMode();
                adapter.notifyDataSetChanged();
                updateEmptyState();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showClearAllDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.clear_history)
            .setMessage(R.string.history_empty_confirm)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                HistoryStorage.clearHistory(this);
                historyList.clear();
                adapter.notifyDataSetChanged();
                updateEmptyState();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void updateEmptyState() {
        if (historyList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            btnClear.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            if (!adapter.isSelectionMode()) {
                btnClear.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (adapter.isSelectionMode()) {
            exitSelectionMode();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (adapter.isSelectionMode()) {
                exitSelectionMode();
            } else {
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
