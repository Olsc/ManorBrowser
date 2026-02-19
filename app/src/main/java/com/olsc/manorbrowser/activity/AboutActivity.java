/**
 * 关于界面，显示应用版本和信息。
 */
package com.olsc.manorbrowser.activity;
import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.Config;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.graphics.Insets;
import org.mozilla.geckoview.BuildConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class AboutActivity extends AppCompatActivity {
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
        setContentView(R.layout.activity_about);
        View mainView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), insets.top, v.getPaddingRight(), insets.bottom);
            return windowInsets;
        });
        View llSourceCode = findViewById(R.id.ll_source_code);
        if (llSourceCode != null) {
            llSourceCode.setOnClickListener(v -> {
                String url = getString(R.string.source_code_url);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            });
        }
        
        // 设置版本信息
        setupVersionInfo();
    }
    
    private void setupVersionInfo() {
        try {
            // 获取应用版本
            String appVersion = getAppVersion();
            TextView appVersionText = findViewById(R.id.tv_app_version);
            if (appVersionText != null) {
                appVersionText.setText(getString(R.string.label_app_version_value, appVersion));
            }
            
            // 获取 GeckoView 版本
            String geckoVersion = getGeckoViewVersion();
            TextView geckoVersionText = findViewById(R.id.tv_gecko_version);
            if (geckoVersionText != null) {
                geckoVersionText.setText(getString(R.string.label_gecko_version_value, geckoVersion));
            }
            
            // 获取 Android SDK 版本
            String sdkVersion = android.os.Build.VERSION.RELEASE;
            TextView sdkVersionText = findViewById(R.id.tv_sdk_version);
            if (sdkVersionText != null) {
                sdkVersionText.setText(getString(R.string.label_sdk_version_value, sdkVersion));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private String getAppVersion() {
        try {
            String packageName = getPackageName();
            return getPackageManager().getPackageInfo(packageName, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "Unknown";
        }
    }
    
    private String getGeckoViewVersion() {
        try {
            // 从 GeckoView BuildConfig 获取版本信息
            String fullVersion = BuildConfig.MOZ_APP_VERSION;
            if (fullVersion != null && !fullVersion.isEmpty()) {
                return fullVersion;
            } else {
                // 如果无法获取完整版本，尝试从 GeckoView 类获取一些信息
                return "GeckoView";
            }
        } catch (Exception e) {
            // 如果无法获取 GeckoView 版本，则返回编译时的版本号
            return getString(R.string.default_gecko_version); // fallback
        }
    }
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(com.olsc.manorbrowser.utils.LocaleHelper.onAttach(newBase));
    }
}
