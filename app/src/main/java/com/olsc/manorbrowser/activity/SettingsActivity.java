/**
 * 设置界面，允许用户配置浏览器环境和个人偏好。
 */
package com.olsc.manorbrowser.activity;
import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.Config;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.graphics.Insets;
public class SettingsActivity extends AppCompatActivity {
    private android.widget.TextView tvCurrentEngine;
    private androidx.activity.result.ActivityResultLauncher<String> pickImageLauncher;
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
        setContentView(R.layout.activity_settings);
        
        pickImageLauncher = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        saveCustomImage(uri);
                    }
                });
        
        Toolbar toolbar = findViewById(R.id.toolbar_settings);
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
        View containerSearchEngine = findViewById(R.id.container_search_engine_select);
        tvCurrentEngine = findViewById(R.id.tv_current_engine);
        updateCurrentEngineText();
        containerSearchEngine.setOnClickListener(v -> showSearchEngineDialog());
        View containerBgEffect = findViewById(R.id.container_background_effect_select);
        if (containerBgEffect != null) {
            containerBgEffect.setOnClickListener(v -> showBackgroundEffectDialog());
            updateCurrentBackgroundEffectText();
        }
        View containerLanguage = findViewById(R.id.container_language_select);
        if (containerLanguage != null) {
            containerLanguage.setOnClickListener(v -> showLanguageDialog());
            updateCurrentLanguageText();
        }
        View containerAbout = findViewById(R.id.container_about);
        if (containerAbout != null) {
            containerAbout.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this, AboutActivity.class);
                startActivity(intent);
            });
        }
        
        View containerDefaultBrowser = findViewById(R.id.container_default_browser);
        if (containerDefaultBrowser != null) {
            containerDefaultBrowser.setOnClickListener(v -> requestDefaultBrowserRole());
        }

        // 获取SharedPreferences实例供后续使用
        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // 桌面模式开关
        androidx.appcompat.widget.SwitchCompat switchDesktopMode = findViewById(R.id.switch_desktop_mode);
        if (switchDesktopMode != null) {
            boolean isDesktopMode = defaultPrefs.getBoolean(Config.PREF_KEY_DESKTOP_MODE, false);
            switchDesktopMode.setChecked(isDesktopMode);
            
            switchDesktopMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                defaultPrefs.edit().putBoolean(Config.PREF_KEY_DESKTOP_MODE, isChecked).apply();
            });
        }

        // 系统下载器开关
        androidx.appcompat.widget.SwitchCompat switchSystemDownloader = findViewById(R.id.switch_system_downloader);
        if (switchSystemDownloader != null) {
            boolean useSystemDownloader = defaultPrefs.getBoolean(Config.PREF_KEY_USE_SYSTEM_DOWNLOADER, false);
            switchSystemDownloader.setChecked(useSystemDownloader);
            
            switchSystemDownloader.setOnCheckedChangeListener((buttonView, isChecked) -> {
                defaultPrefs.edit().putBoolean(Config.PREF_KEY_USE_SYSTEM_DOWNLOADER, isChecked).apply();
            });
        }

        // 主页按钮自定义
        View containerCustomHomeButton = findViewById(R.id.container_custom_home_button);
        if (containerCustomHomeButton != null) {
            containerCustomHomeButton.setOnClickListener(v -> showCustomHomeButtonDialog());
            updateCurrentHomeButtonText();
        }

        // 白天黑夜模式
        View containerTheme = findViewById(R.id.container_theme_switch);
        androidx.appcompat.widget.SwitchCompat switchDarkMode = findViewById(R.id.switch_dark_mode);
        if (containerTheme != null && switchDarkMode != null) {
            SharedPreferences themePrefs = getSharedPreferences(Config.PREF_NAME_THEME, MODE_PRIVATE);
            boolean isDark = themePrefs.getBoolean(Config.PREF_KEY_DARK_MODE, false);
            switchDarkMode.setChecked(isDark);
            
            containerTheme.setOnClickListener(v -> {
                boolean nextMode = !switchDarkMode.isChecked();
                switchDarkMode.setChecked(nextMode);
                toggleTheme(themePrefs, nextMode);
            });
        }

        // 密码管理
        View containerPasswords = findViewById(R.id.container_passwords);
        if (containerPasswords != null) {
            containerPasswords.setOnClickListener(v -> {
                startActivity(new android.content.Intent(this, PasswordManagerActivity.class));
            });
        }

        // Cookies管理
        View containerCookies = findViewById(R.id.container_cookies);
        if (containerCookies != null) {
            containerCookies.setOnClickListener(v -> {
                startActivity(new android.content.Intent(this, CookieManagerActivity.class));
            });
        }
    }

    private void toggleTheme(SharedPreferences themePrefs, boolean isDarkMode) {
        themePrefs.edit().putBoolean(Config.PREF_KEY_DARK_MODE, isDarkMode).apply();
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(isDarkMode ? 
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        recreate();
    }

    
    private void requestDefaultBrowserRole() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.app.role.RoleManager roleManager = getSystemService(android.app.role.RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
                if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
                    android.content.Intent intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER);
                    startActivityForResult(intent, 1003);
                    return;
                }
            }
        }
        
        android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
        try {
            startActivity(intent);
        } catch (Exception e) {
            try {
                android.content.Intent intentFallback = new android.content.Intent(android.provider.Settings.ACTION_SETTINGS);
                startActivity(intentFallback);
            } catch (Exception ex) {}
        }
    }

    private void showColorPickerDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String prefKey = Config.PREF_KEY_SOLID_BG_COLOR;
        int defaultColor = Config.DEFAULT_SOLID_BG_COLOR;
        int currentColor = prefs.getInt(prefKey, defaultColor);
        
        final com.olsc.manorbrowser.view.AuroraView auroraView = new com.olsc.manorbrowser.view.AuroraView(this);
        auroraView.setPureColorMode(true);
        auroraView.setSolidColor(currentColor);
        auroraView.setShowColorWheel(true);
        
        android.widget.FrameLayout layout = new android.widget.FrameLayout(this);
        int size = (int) (300 * getResources().getDisplayMetrics().density);
        layout.addView(auroraView, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                size)); 
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_pick_color)
                .setView(layout)
                .setPositiveButton(R.string.action_select, (dialog, which) -> {
                    int selected = auroraView.getSolidColor();
                    prefs.edit().putInt(prefKey, selected).apply();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    private void showSearchEngineDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String currentEngine = prefs.getString(Config.PREF_KEY_SEARCH_ENGINE, Config.ENGINE_BAIDU);
        int checkedItem = Config.ENGINE_GOOGLE.equals(currentEngine) ? 1 : 0;
        String[] engines = {getString(R.string.engine_baidu), getString(R.string.engine_google)};
        String[] values = {Config.ENGINE_BAIDU, Config.ENGINE_GOOGLE};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.label_default_search_engine)
                .setSingleChoiceItems(engines, checkedItem, (dialog, which) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(Config.PREF_KEY_SEARCH_ENGINE, values[which]);
                    editor.apply();
                    updateCurrentEngineText();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    private void updateCurrentEngineText() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String currentEngine = prefs.getString(Config.PREF_KEY_SEARCH_ENGINE, Config.ENGINE_BAIDU);
        String engineName = Config.ENGINE_GOOGLE.equals(currentEngine) ? getString(R.string.engine_google) : getString(R.string.engine_baidu);
        tvCurrentEngine.setText(engineName);
    }
    private void showBackgroundEffectDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String currentEffect = prefs.getString(Config.PREF_KEY_BG_EFFECT, Config.BG_EFFECT_METEOR);
        
        int checkedItem = 0;
        switch (currentEffect) {
            case Config.BG_EFFECT_RAIN: checkedItem = 1; break;
            case Config.BG_EFFECT_SNOW: checkedItem = 2; break;
            case Config.BG_EFFECT_AURORA: checkedItem = 3; break;
            case Config.BG_EFFECT_SAKURA: checkedItem = 4; break;
            case Config.BG_EFFECT_SOLID: checkedItem = 5; break;
            case Config.BG_EFFECT_IMAGE: checkedItem = 6; break;
            default: checkedItem = 0; break;
        }
        String[] effects = {
            getString(R.string.bg_effect_meteor),
            getString(R.string.bg_effect_rain),
            getString(R.string.bg_effect_snow),
            getString(R.string.bg_effect_aurora),
            getString(R.string.bg_effect_sakura),
            getString(R.string.bg_effect_solid),
            getString(R.string.bg_effect_image)
        };
        String[] values = {
            Config.BG_EFFECT_METEOR, 
            Config.BG_EFFECT_RAIN, 
            Config.BG_EFFECT_SNOW, 
            Config.BG_EFFECT_AURORA, 
            Config.BG_EFFECT_SAKURA, 
            Config.BG_EFFECT_SOLID,
            Config.BG_EFFECT_IMAGE
        };
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_background_effect)
                .setSingleChoiceItems(effects, checkedItem, (dialog, which) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(Config.PREF_KEY_BG_EFFECT, values[which]);
                    editor.apply();
                    updateCurrentBackgroundEffectText();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    private void updateCurrentBackgroundEffectText() {
        android.widget.TextView tvCurrentEffect = findViewById(R.id.tv_current_bg_effect);
        if (tvCurrentEffect == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String currentEffect = prefs.getString(Config.PREF_KEY_BG_EFFECT, Config.BG_EFFECT_METEOR);
        String effectName;
        switch (currentEffect) {
            case Config.BG_EFFECT_RAIN: effectName = getString(R.string.bg_effect_rain); break;
            case Config.BG_EFFECT_SNOW: effectName = getString(R.string.bg_effect_snow); break;
            case Config.BG_EFFECT_AURORA: effectName = getString(R.string.bg_effect_aurora); break;
            case Config.BG_EFFECT_SAKURA: effectName = getString(R.string.bg_effect_sakura); break;
            case Config.BG_EFFECT_SOLID: effectName = getString(R.string.bg_effect_solid); break;
            case Config.BG_EFFECT_IMAGE: effectName = getString(R.string.bg_effect_image); break;
            default: effectName = getString(R.string.bg_effect_meteor); break;
        }
        tvCurrentEffect.setText(effectName);
        
        View containerSolid = findViewById(R.id.container_solid_settings);
        View btnPickColor = findViewById(R.id.btn_bg_pick_color);
        if (containerSolid != null) {
            boolean isSolidEffect = Config.BG_EFFECT_SOLID.equals(currentEffect);
            containerSolid.setVisibility(isSolidEffect ? View.VISIBLE : View.GONE);
            if (isSolidEffect && btnPickColor != null) {
                btnPickColor.setOnClickListener(v -> showColorPickerDialog());
            }
        }

        View containerImage = findViewById(R.id.container_image_settings);
        View btnPickImage = findViewById(R.id.btn_bg_pick_image);
        if (containerImage != null) {
            boolean isImageEffect = Config.BG_EFFECT_IMAGE.equals(currentEffect);
            containerImage.setVisibility(isImageEffect ? View.VISIBLE : View.GONE);
            if (isImageEffect && btnPickImage != null) {
                btnPickImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
            }
        }
    }
    private void showLanguageDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String currentLang = prefs.getString(Config.PREF_KEY_LANGUAGE, "");
        
        int checkedItem = 0;
        if ("en".equals(currentLang)) checkedItem = 1;
        else if ("zh".equals(currentLang)) checkedItem = 2;
        else if ("zh-TW".equals(currentLang)) checkedItem = 3;
        else if ("ko".equals(currentLang)) checkedItem = 4;
        else if ("ru".equals(currentLang)) checkedItem = 5;
        else if ("ja".equals(currentLang)) checkedItem = 6;
        else if ("ko-KP".equals(currentLang)) checkedItem = 7;
        else if ("es".equals(currentLang)) checkedItem = 8;
        else if ("de".equals(currentLang)) checkedItem = 9;
        else if ("fr".equals(currentLang)) checkedItem = 10;
        else if ("it".equals(currentLang)) checkedItem = 11;
        else if ("pt".equals(currentLang)) checkedItem = 12;
        else if ("ar".equals(currentLang)) checkedItem = 13;
        else if ("hi".equals(currentLang)) checkedItem = 14;
        else if ("tr".equals(currentLang)) checkedItem = 15;
        else if ("vi".equals(currentLang)) checkedItem = 16;
        else if ("id".equals(currentLang)) checkedItem = 17;
        else if ("pl".equals(currentLang)) checkedItem = 18;
        else if ("nl".equals(currentLang)) checkedItem = 19;

        String[] languages = {
            getString(R.string.language_system),
            getString(R.string.language_en),
            getString(R.string.language_zh),
            getString(R.string.language_zh_tw),
            getString(R.string.language_ko),
            getString(R.string.language_ru),
            getString(R.string.language_ja),
            getString(R.string.language_ko_rkp),
            getString(R.string.language_es),
            getString(R.string.language_de),
            getString(R.string.language_fr),
            getString(R.string.language_it),
            getString(R.string.language_pt),
            getString(R.string.language_ar),
            getString(R.string.language_hi),
            getString(R.string.language_tr),
            getString(R.string.language_vi),
            getString(R.string.language_id),
            getString(R.string.language_pl),
            getString(R.string.language_nl)
        };
        String[] values = {"", "en", "zh", "zh-TW", "ko", "ru", "ja", "ko-KP", "es", "de", "fr", "it", "pt", "ar", "hi", "tr", "vi", "id", "pl", "nl"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_language)
                .setSingleChoiceItems(languages, checkedItem, (dialog, which) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(Config.PREF_KEY_LANGUAGE, values[which]);
                    editor.apply();
                    
                    com.olsc.manorbrowser.utils.LocaleHelper.setLocale(this, values[which]);
                    
                    dialog.dismiss();
                    
                    android.content.Intent intent = new android.content.Intent(this, MainActivity.class);
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    private void updateCurrentLanguageText() {
        android.widget.TextView tvLanguage = findViewById(R.id.tv_current_language);
        if (tvLanguage == null) return;
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String currentLang = prefs.getString(Config.PREF_KEY_LANGUAGE, "");
        
        String display;
        if ("en".equals(currentLang)) display = getString(R.string.language_en);
        else if ("zh".equals(currentLang)) display = getString(R.string.language_zh);
        else if ("zh-TW".equals(currentLang)) display = getString(R.string.language_zh_tw);
        else if ("ko".equals(currentLang)) display = getString(R.string.language_ko);
        else if ("ru".equals(currentLang)) display = getString(R.string.language_ru);
        else if ("ja".equals(currentLang)) display = getString(R.string.language_ja);
        else if ("ko-KP".equals(currentLang)) display = getString(R.string.language_ko_rkp);
        else if ("es".equals(currentLang)) display = getString(R.string.language_es);
        else if ("de".equals(currentLang)) display = getString(R.string.language_de);
        else if ("fr".equals(currentLang)) display = getString(R.string.language_fr);
        else if ("it".equals(currentLang)) display = getString(R.string.language_it);
        else if ("pt".equals(currentLang)) display = getString(R.string.language_pt);
        else if ("ar".equals(currentLang)) display = getString(R.string.language_ar);
        else if ("hi".equals(currentLang)) display = getString(R.string.language_hi);
        else if ("tr".equals(currentLang)) display = getString(R.string.language_tr);
        else if ("vi".equals(currentLang)) display = getString(R.string.language_vi);
        else if ("id".equals(currentLang)) display = getString(R.string.language_id);
        else if ("pl".equals(currentLang)) display = getString(R.string.language_pl);
        else if ("nl".equals(currentLang)) display = getString(R.string.language_nl);
        else display = getString(R.string.language_system);
        
        tvLanguage.setText(display);
    }

    private void showCustomHomeButtonDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String currentFunc = prefs.getString(Config.PREF_KEY_LEFT_BUTTON_FUNCTION, Config.FUNC_BOOKMARKS);
        
        String[] functions = {
            getString(R.string.func_home),
            getString(R.string.func_extensions),
            getString(R.string.func_bookmarks),
            getString(R.string.func_history),
            getString(R.string.func_downloads),
            getString(R.string.func_desktop_mode),
            getString(R.string.func_add_bookmark),
            getString(R.string.func_theme)
        };
        String[] values = {
            Config.FUNC_HOME,
            Config.FUNC_EXTENSIONS,
            Config.FUNC_BOOKMARKS,
            Config.FUNC_HISTORY,
            Config.FUNC_DOWNLOADS,
            Config.FUNC_DESKTOP_MODE,
            Config.FUNC_ADD_BOOKMARK,
            Config.FUNC_THEME
        };
        
        int checkedItem = 2;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentFunc)) {
                checkedItem = i;
                break;
            }
        }
        
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_custom_home_button)
                .setSingleChoiceItems(functions, checkedItem, (dialog, which) -> {
                    prefs.edit().putString(Config.PREF_KEY_LEFT_BUTTON_FUNCTION, values[which]).apply();
                    updateCurrentHomeButtonText();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateCurrentHomeButtonText() {
        android.widget.TextView tvFunc = findViewById(R.id.tv_current_home_function);
        if (tvFunc == null) return;
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String currentFunc = prefs.getString(Config.PREF_KEY_LEFT_BUTTON_FUNCTION, Config.FUNC_BOOKMARKS);
        
        String display;
        switch (currentFunc) {
            case Config.FUNC_HOME: display = getString(R.string.func_home); break;
            case Config.FUNC_EXTENSIONS: display = getString(R.string.func_extensions); break;
            case Config.FUNC_BOOKMARKS: display = getString(R.string.func_bookmarks); break;
            case Config.FUNC_HISTORY: display = getString(R.string.func_history); break;
            case Config.FUNC_DOWNLOADS: display = getString(R.string.func_downloads); break;
            case Config.FUNC_DESKTOP_MODE: display = getString(R.string.func_desktop_mode); break;
            case Config.FUNC_ADD_BOOKMARK: display = getString(R.string.func_add_bookmark); break;
            case Config.FUNC_THEME: display = getString(R.string.func_theme); break;
            default: display = getString(R.string.func_bookmarks); break;
        }
        tvFunc.setText(display);
    }
    
    private void saveCustomImage(android.net.Uri uri) {
        java.io.File destFile = new java.io.File(getFilesDir(), "custom_bg.jpg");
        boolean success = com.olsc.manorbrowser.utils.FileUtil.copyUriToFile(this, uri, destFile);
        if (success) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putString(Config.PREF_KEY_CUSTOM_BG_IMAGE, destFile.getAbsolutePath()).apply();
            updateCurrentBackgroundEffectText();
        } else {
            android.widget.Toast.makeText(this, R.string.msg_pick_image_failed, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(com.olsc.manorbrowser.utils.LocaleHelper.onAttach(newBase));
    }
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
