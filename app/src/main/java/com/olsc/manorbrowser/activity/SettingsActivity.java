package com.olsc.manorbrowser.activity;

import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.Config;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private android.widget.TextView tvCurrentEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.content.SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME_THEME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(Config.PREF_KEY_DARK_MODE, false);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(isDarkMode ? 
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        View containerSearchEngine = findViewById(R.id.container_search_engine_select);
        tvCurrentEngine = findViewById(R.id.tv_current_engine);

        updateCurrentEngineText();

        containerSearchEngine.setOnClickListener(v -> showSearchEngineDialog());

        View containerBgEffect = findViewById(R.id.container_background_effect_select);
        if (containerBgEffect != null) {
            containerBgEffect.setOnClickListener(v -> showBackgroundEffectDialog());
            updateCurrentBackgroundEffectText();
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

        new androidx.appcompat.app.AlertDialog.Builder(this)
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

        new androidx.appcompat.app.AlertDialog.Builder(this)
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
            default: checkedItem = 0; break;
        }

        String[] effects = {
            getString(R.string.bg_effect_meteor),
            getString(R.string.bg_effect_rain),
            getString(R.string.bg_effect_snow),
            getString(R.string.bg_effect_aurora),
            getString(R.string.bg_effect_sakura),
            getString(R.string.bg_effect_solid)
        };
        String[] values = {Config.BG_EFFECT_METEOR, Config.BG_EFFECT_RAIN, Config.BG_EFFECT_SNOW, Config.BG_EFFECT_AURORA, Config.BG_EFFECT_SAKURA, Config.BG_EFFECT_SOLID};

        new androidx.appcompat.app.AlertDialog.Builder(this)
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
            default: effectName = getString(R.string.bg_effect_meteor); break;
        }
        tvCurrentEffect.setText(effectName);
        
        View containerSolid = findViewById(R.id.container_solid_settings);
        View btnPickColor = findViewById(R.id.btn_bg_pick_color);

        if (containerSolid != null) {
            boolean isSolidEffect = Config.BG_EFFECT_SOLID.equals(currentEffect);
            if (isSolidEffect) {
                containerSolid.setVisibility(View.VISIBLE);
                if (btnPickColor != null) {
                    btnPickColor.setOnClickListener(v -> showColorPickerDialog());
                }
            } else {
                containerSolid.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
