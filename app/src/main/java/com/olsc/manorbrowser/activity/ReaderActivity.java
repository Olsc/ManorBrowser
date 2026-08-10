/**
 * 阅读模式界面，提供沉浸式阅读体验。
 */
package com.olsc.manorbrowser.activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.SeekBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.graphics.Insets;
import com.olsc.manorbrowser.R;
public class ReaderActivity extends AppCompatActivity {
    private TextView contentTextView;
    private View readerContainer;
    private SeekBar fontSizeSeekBar;
    private View controlContent;
    private ImageView expandIcon;
    private boolean isExpanded = true;
    
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
        android.content.SharedPreferences prefs = getSharedPreferences(com.olsc.manorbrowser.Config.PREF_NAME_THEME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(com.olsc.manorbrowser.Config.PREF_KEY_DARK_MODE, false);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(isDarkMode ? 
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(!isDarkMode);
            controller.setAppearanceLightNavigationBars(!isDarkMode);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        View mainView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            toolbar.setPadding(toolbar.getPaddingLeft(), insets.top, toolbar.getPaddingRight(), 0);
            v.setPadding(v.getPaddingLeft(), 0, v.getPaddingRight(), insets.bottom);
            return windowInsets;
        });
        contentTextView = findViewById(R.id.reader_content);
        readerContainer = findViewById(R.id.reader_container);
        fontSizeSeekBar = findViewById(R.id.font_size_seekbar);
        controlContent = findViewById(R.id.control_content);
        expandIcon = findViewById(R.id.expand_icon);
        
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
        
        setupControlPanel();
        setupFontSizeControl();
        setupThemeButtons();
        applyTheme(0); // 默认护眼绿
    }
    
    private void setupControlPanel() {
        View controlHeader = findViewById(R.id.control_header);
        controlHeader.setOnClickListener(v -> toggleControlPanel());
    }
    
    private void toggleControlPanel() {
        isExpanded = !isExpanded;

        // 旋转箭头动画（属性动画，无 fillAfter 残留状态；reduce 下直接切换）
        boolean reduce = com.olsc.manorbrowser.utils.Motion.isReduceMotion(this);
        if (reduce) {
            expandIcon.setRotation(isExpanded ? 90f : -90f);
        } else {
            expandIcon.animate()
                .rotation(isExpanded ? 90f : -90f)
                .setDuration(com.olsc.manorbrowser.utils.Motion.DURATION_SWITCH)
                .setInterpolator(com.olsc.manorbrowser.utils.Motion.EASE_OUT)
                .start();
        }

        // 展开/收起内容：淡入 + 轻微下移，而非硬切
        if (isExpanded) {
            controlContent.setVisibility(View.VISIBLE);
            controlContent.setAlpha(0f);
            controlContent.setTranslationY(reduce ? 0f : -dp8r());
            controlContent.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(com.olsc.manorbrowser.utils.Motion.scaledDuration(
                    this, com.olsc.manorbrowser.utils.Motion.DURATION_SWITCH))
                .setInterpolator(com.olsc.manorbrowser.utils.Motion.EASE_OUT)
                .start();
        } else {
            controlContent.animate()
                .alpha(0f)
                .translationY(reduce ? 0f : dp8r())
                .setDuration(com.olsc.manorbrowser.utils.Motion.scaledDuration(
                    this, com.olsc.manorbrowser.utils.Motion.DURATION_SWITCH))
                .setInterpolator(com.olsc.manorbrowser.utils.Motion.EASE_OUT)
                .withEndAction(() -> {
                    // 若动画期间被新的展开操作打断（isExpanded 已变 true），不隐藏
                    if (isExpanded) return;
                    controlContent.setVisibility(View.GONE);
                    controlContent.setAlpha(1f);
                    controlContent.setTranslationY(0f);
                })
                .start();
        }
    }

    private float dp8r() {
        return 8 * getResources().getDisplayMetrics().density;
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
