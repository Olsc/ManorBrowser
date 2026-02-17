package com.olsc.manorbrowser.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.SeekBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.olsc.manorbrowser.R;

public class ReaderActivity extends AppCompatActivity {

    private TextView contentTextView;
    private View readerContainer;
    private SeekBar fontSizeSeekBar;
    private int currentFontSize = 16;
    private int currentTheme = 0; // 0: 护眼绿, 1: 米黄, 2: 夜间
    
    // 主题颜色配置
    private static final int[][] THEME_COLORS = {
        {0xFFCCE8CC, 0xFF333333}, // 护眼绿：背景，文字
        {0xFFF5E6D3, 0xFF333333}, // 米黄：背景，文字
        {0xFF1E1E1E, 0xFFCCCCCC}  // 夜间：背景，文字
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        contentTextView = findViewById(R.id.reader_content);
        readerContainer = findViewById(R.id.reader_container);
        fontSizeSeekBar = findViewById(R.id.font_size_seekbar);
        
        Intent intent = getIntent();
        String content = intent.getStringExtra("content");
        String title = intent.getStringExtra("title");
        
        if (title != null && !title.isEmpty()) {
            toolbar.setTitle(title);
        }
        
        if (content != null) {
            // 清理和格式化内容
            String formattedContent = formatContent(content);
            contentTextView.setText(formattedContent);
        }

        setupFontSizeControl();
        setupThemeButtons();
        applyTheme(0); // 默认护眼绿
    }

    private String formatContent(String content) {
        // 移除多余的空行
        content = content.replaceAll("\n{3,}", "\n\n");
        // 移除首尾空白
        content = content.trim();
        return content;
    }

    private void setupFontSizeControl() {
        fontSizeSeekBar.setMax(20);
        fontSizeSeekBar.setProgress(6); // 默认16sp (10 + 6)
        
        fontSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentFontSize = 10 + progress;
                contentTextView.setTextSize(currentFontSize);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupThemeButtons() {
        findViewById(R.id.theme_green).setOnClickListener(v -> applyTheme(0));
        findViewById(R.id.theme_beige).setOnClickListener(v -> applyTheme(1));
        findViewById(R.id.theme_night).setOnClickListener(v -> applyTheme(2));
    }

    private void applyTheme(int theme) {
        currentTheme = theme;
        int bgColor = THEME_COLORS[theme][0];
        int textColor = THEME_COLORS[theme][1];
        
        readerContainer.setBackgroundColor(bgColor);
        contentTextView.setTextColor(textColor);
    }
}
