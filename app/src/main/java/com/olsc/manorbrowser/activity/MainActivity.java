/**
 * 浏览器主界面类
 * 核心功能：
 * 1. 管理多标签页 (Tabs) 的生命周期与状态切换。
 * 2. 调度 GeckoView 引擎进行网页渲染。
 * 3. 处理导航逻辑、历史记录、书签以及自动填充系统的接入。
 * 4. 提供动态背景切换、主题切换等 UI 特性。
 */
package com.olsc.manorbrowser.activity;

import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.data.HistoryStorage;
import com.olsc.manorbrowser.view.DynamicBackgroundView;
import com.olsc.manorbrowser.adapter.TabSwitcherAdapter;
import com.olsc.manorbrowser.adapter.TabSwipeCallback;
import com.olsc.manorbrowser.Config;
import com.olsc.manorbrowser.data.TabInfo;
import com.olsc.manorbrowser.data.TabStorage;
import com.olsc.manorbrowser.utils.SearchHelper;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.cardview.widget.CardView;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsControllerCompat;
import android.view.HapticFeedbackConstants;
import android.os.Handler;
import android.os.Looper;

import com.google.android.material.navigation.NavigationView;

import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebResponse;
import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.ContentBlocking.CookieBehavior;

import org.mozilla.geckoview.Autocomplete;
import org.mozilla.geckoview.Autocomplete.LoginEntry;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.view.ViewGroup;

import com.olsc.manorbrowser.data.PasswordItem;
import com.olsc.manorbrowser.data.PasswordStorage;

import androidx.preference.PreferenceManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import org.mozilla.geckoview.GeckoSession.PromptDelegate;

import com.olsc.manorbrowser.utils.QrCodeHelper;
import android.graphics.BitmapFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.olsc.manorbrowser.utils.BrowserCommandServer;
import java.util.Locale;
import android.text.Spanned;
import io.noties.markwon.Markwon;


public class MainActivity extends AppCompatActivity {
    // --- UI 基础控件 ---
    private DrawerLayout drawerLayout;
    private GeckoView geckoView;
    private androidx.recyclerview.widget.RecyclerView tabSwitcher;
    private EditText urlInput;
    private View topBar, bottomBar, contentContainer, fullscreenMenuContainer;
    private ImageButton btnBack, btnRefresh, btnStop, btnHome, btnTabs, btnMenu, btnFullscreenMenu;
    private NavigationView navigationView;
    private android.widget.ProgressBar progressBar;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;

    // --- 引擎与数据管理器 ---
    /** 全局共享的 GeckoRuntime，一个进程通常只需要一个 */
    public static GeckoRuntime sRuntime;
    /** 当前会话中维护的所有标签页列表 */
    private final List<TabInfo> tabs = new ArrayList<>();
    private TabSwitcherAdapter tabSwitcherAdapter;
    /** 当前处于前台显示的标签索引 */
    private int currentTabIndex = -1;
    
    // --- 状态标志位 ---
    /** 标签切换器界面是否可见 */
    private boolean isTabSwitcherVisible = false;
    private long lastPopupTime = 0;
    private long lastBackTime = 0;
    private int lastX, lastY; // 上次点击坐标，辅助 context menu 脚本定位
    private boolean urlInputFirstClick = true; // 跟踪URL输入框是否是第一次点击
    private boolean isSwitchingTab = false; // 防止switchToTab重入导致的崩溃和逻辑混乱
    private boolean isProcessingAction = false; // 全局锁，防止多重点击导致的 PixelCopy 并发或状态异常
    private boolean isFullScreenMode = false;
    private String currentTabGroup = null;
    private GeckoResult<PromptDelegate.PromptResponse> mFilePromptResult = null;
    private PromptDelegate.FilePrompt mCurrentFilePrompt = null;
    private ActivityResultLauncher<android.content.Intent> mFilePickerLauncher;
    
    /** 应用初始化权限申请处理器 (如存储权限) */
    private ActivityResultLauncher<String> mPermissionLauncher;
    private final java.util.Queue<String> mPendingPermissions = new java.util.LinkedList<>();
    
    /** 网页请求权限处理器 (如摄像头、地理位置) */
    private GeckoSession.PermissionDelegate.Callback mGeckoPermissionCallback;
    private ActivityResultLauncher<String[]> mGeckoPermissionLauncher;
    private final java.util.Set<String> mPromptedAutofillUrls = new java.util.HashSet<>();
    
    // --- 远程控制 ---
    private BrowserCommandServer commandServer;
    private volatile BrowserCommandServer.EvalCallback pendingJsCallback = null;
    private volatile String pendingJsOriginalTitle = null;
    private boolean isLordMode = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // --- 历史推荐与闲时整理 ---
    private androidx.cardview.widget.CardView recommendationsContainer;
    private RecyclerView rvRecommendations;
    private com.olsc.manorbrowser.adapter.RecommendationAdapter recommendationAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 加载主题色调（深色模式或亮色模式）
        android.content.SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME_THEME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(Config.PREF_KEY_DARK_MODE, false);
        AppCompatDelegate.setDefaultNightMode(isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        
        // 沉浸式状态栏配置
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!isDarkMode);
        controller.setAppearanceLightNavigationBars(!isDarkMode);

        super.onCreate(savedInstanceState);
        
        // 1. 注册文件选择器的 Activity 回调，用于处理 <input type="file">
        mFilePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (mFilePromptResult != null && mCurrentFilePrompt != null) {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        try {
                            android.net.Uri uri = result.getData().getData();
                            if (uri != null) {
                                mFilePromptResult.complete(mCurrentFilePrompt.confirm(MainActivity.this, uri));
                            } else if (result.getData().getClipData() != null) {
                                int count = result.getData().getClipData().getItemCount();
                                android.net.Uri[] uris = new android.net.Uri[count];
                                for (int i = 0; i < count; i++) {
                                    uris[i] = result.getData().getClipData().getItemAt(i).getUri();
                                }
                                mFilePromptResult.complete(mCurrentFilePrompt.confirm(MainActivity.this, uris));
                            } else {
                                mFilePromptResult.complete(mCurrentFilePrompt.dismiss());
                            }
                        } catch(Exception e) {
                            e.printStackTrace();
                            mFilePromptResult.complete(mCurrentFilePrompt.dismiss());
                        }
                    } else {
                        mFilePromptResult.complete(mCurrentFilePrompt.dismiss());
                    }
                    mFilePromptResult = null;
                    mCurrentFilePrompt = null;
                }
            }
        );

        // 2. 注册通用权限申请回调
        mPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                requestNextPendingPermission();
            }
        );

        // 3. 注册网页特权申请回调 (如相机、位置)
        mGeckoPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                if (mGeckoPermissionCallback != null) {
                    boolean anyGranted = false;
                    for (Boolean granted : result.values()) {
                        if (granted) {
                            anyGranted = true;
                            break;
                        }
                    }
                    if (anyGranted) mGeckoPermissionCallback.grant();
                    else mGeckoPermissionCallback.reject();
                    mGeckoPermissionCallback = null;
                }
            }
        );

        setContentView(R.layout.activity_main);
        
        // --- 初始化 UI 树 ---
        drawerLayout = findViewById(R.id.drawer_layout);
        geckoView = findViewById(R.id.geckoview);
        tabSwitcher = findViewById(R.id.tab_switcher);
        urlInput = findViewById(R.id.et_url);
        topBar = findViewById(R.id.top_bar);
        progressBar = findViewById(R.id.progress_bar);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        navigationView = findViewById(R.id.nav_view);
        btnBack = findViewById(R.id.btn_back);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnStop = findViewById(R.id.btn_stop);
        btnHome = findViewById(R.id.btn_home);
        btnTabs = findViewById(R.id.btn_tabs);
        btnMenu = findViewById(R.id.btn_menu);
        bottomBar = findViewById(R.id.bottom_bar);
        contentContainer = findViewById(R.id.content_container);
        fullscreenMenuContainer = findViewById(R.id.fullscreen_menu_container);
        btnFullscreenMenu = findViewById(R.id.btn_fullscreen_menu);
        
        // 初始化历史推荐列表
        recommendationsContainer = findViewById(R.id.recommendations_container);
        rvRecommendations = findViewById(R.id.rv_recommendations);
        if (rvRecommendations != null) {
            rvRecommendations.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
            recommendationAdapter = new com.olsc.manorbrowser.adapter.RecommendationAdapter(item -> {
                loadUrlInCurrentTab(item.url);
                recommendationsContainer.setVisibility(View.GONE);
                urlInput.clearFocus();
                hideKeyboard();
            });
            rvRecommendations.setAdapter(recommendationAdapter);
        }
        
        // 4. 应用窗口 Insets 监听 (解决刘海屏、导航栏遮挡)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawer_layout), (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            int topPadding = systemBars.top;
            int bottomPadding = systemBars.bottom;
            
            topBar.setPadding(topBar.getPaddingLeft(), topPadding, topBar.getPaddingRight(), 0);
            bottomBar.setPadding(bottomBar.getPaddingLeft(), bottomBar.getPaddingTop(), bottomBar.getPaddingRight(), bottomPadding);
            navigationView.setPadding(0, 0, 0, bottomPadding);
            
            if (contentContainer != null) {
                android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) contentContainer.getLayoutParams();
                if (topBar.getVisibility() == View.GONE) {
                    // 全屏显示时，顶栏消失，内容容器直接给顶部 padding 以避开系统状态栏
                    contentContainer.setPadding(0, topPadding, 0, 0);
                } else {
                    contentContainer.setPadding(0, 0, 0, 0);
                }
                contentContainer.setLayoutParams(lp);
            }
            
            // 为全屏模式下的进度条保存顶部高度
            if (progressBar != null) {
                android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) progressBar.getLayoutParams();
                if (topBar.getVisibility() == View.GONE) {
                    lp.topMargin = topPadding;
                } else {
                    lp.topMargin = 0;
                }
                progressBar.setLayoutParams(lp);
            }
            return windowInsets;
        });
        if (isPrivacyAgreed()) {
            initializeApp();
        } else {
            showPrivacyDialog();
        }
    }

    /**
     * 检测隐私协议同意状态
     */
    private boolean isPrivacyAgreed() {
        android.content.SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME_THEME, MODE_PRIVATE);
        return prefs.getBoolean(Config.PREF_KEY_PRIVACY_AGREED, false);
    }
    
    private void setPrivacyAgreed() {
        android.content.SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME_THEME, MODE_PRIVATE);
        prefs.edit().putBoolean(Config.PREF_KEY_PRIVACY_AGREED, true).apply();
    }

    /**
     * 强制弹窗显示隐私政策，仅在首次运行或未同意时调用
     */
    private void showPrivacyDialog() {
        String fileName;
        String lang = Locale.getDefault().getLanguage();
        if ("zh".equals(lang)) {
            fileName = "legal/UserPrivacyAgreement_zh.txt";
        } else {
            fileName = "legal/UserPrivacyAgreement_en.txt";
        }

        String markdown = readAssetFile(fileName);
        final Markwon markwon = Markwon.create(this);
        final Spanned spannedMarkdown = markwon.toMarkdown(markdown);

        // 创建自定义布局以监听滚动
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.TextView textView = new android.widget.TextView(this);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        textView.setPadding(padding, padding, padding, padding);
        textView.setText(spannedMarkdown);
        textView.setTextSize(14);
        scrollView.addView(textView);

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_privacy_policy)
            .setView(scrollView)
            .setCancelable(false)
            .setPositiveButton(R.string.action_agree, (d, which) -> {
                setPrivacyAgreed();
                initializeApp();
            })
            .setNegativeButton(R.string.action_disagree, (d, which) -> {
                finish();
            })
            .create();

        dialog.setOnShowListener(d -> {
            android.widget.Button btnAgree = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            btnAgree.setEnabled(false); // 初始禁用

            scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
                if (scrollView.getChildAt(0).getBottom() <= (scrollView.getHeight() + scrollView.getScrollY())) {
                    btnAgree.setEnabled(true);
                }
            });
            
            // 如果内容很短不需要滚动，直接启用
            textView.post(() -> {
                if (scrollView.getChildAt(0).getBottom() <= scrollView.getHeight()) {
                    btnAgree.setEnabled(true);
                }
            });
        });

        dialog.show();
    }

    private String readAssetFile(String fileName) {
        try (InputStream is = getAssets().open(fileName)) {
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            return new String(buffer, "UTF-8");
        } catch (java.io.IOException e) {
            e.printStackTrace();
            return "";
        }
    }


    /**
     * 核心初始化流程：包括引擎启动、标签页恢复、监听器挂载
     */
    private void initializeApp() {
        initDefaultSearchEngine();

        // 初始化添加标签按钮
        ImageButton btnAddTab = findViewById(R.id.btn_add_tab);
        if (btnAddTab != null) {
            btnAddTab.setOnClickListener(v -> {
                if (isProcessingAction) return;
                isProcessingAction = true;
                captureScreenshot(() -> {
                    createNewTab(Config.URL_BLANK);
                    // 直接执行动画展示切换器，不走 toggleTabSwitcher 的逻辑锁
                    performToggleAnimation(true);
                    isProcessingAction = false;
                });
            });
        }
        navigationView = findViewById(R.id.nav_view);
        if (sRuntime == null) {
            // 首次创建 Runtime
            GeckoRuntimeSettings.Builder builder = new GeckoRuntimeSettings.Builder()
                .loginAutofillEnabled(true)
                .enterpriseRootsEnabled(true);
            
            // 配置全域内容屏蔽与防追踪 (ETP)
            // 默认启用增强型追踪保护 (Strict)，有效屏蔽 90% 以上的广告脚本与弹窗脚本
            builder.contentBlocking(new ContentBlocking.Settings.Builder()
                .antiTracking(ContentBlocking.AntiTracking.STRICT)
                .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                .build());
            
            sRuntime = GeckoRuntime.create(getApplicationContext(), builder.build());
            sRuntime.setAutocompleteStorageDelegate(mAutocompleteStorageDelegate);
            sRuntime.getWebExtensionController().setPromptDelegate(new com.olsc.manorbrowser.utils.ExtensionPromptDelegate(this));
        } else {
            // Runtime 已存在
        }

        // 2. 初始化 RecyclerView 标签列表
        setupTabSwitcher();

        // 3. 处理 Intent 跳转或会话恢复
        if (getIntent() != null) {
            if (getIntent().hasExtra("url")) {
                restoreTabsOrInit();
                String url = getIntent().getStringExtra("url");
                if (url != null && !url.isEmpty()) {
                    loadUrlInCurrentTab(url);
                }
            } else if (android.content.Intent.ACTION_VIEW.equals(getIntent().getAction()) && getIntent().getData() != null) {
                restoreTabsOrInit();
                handleViewIntent(getIntent());
            } else {
                restoreTabsOrInit();
            }
        } else {
            restoreTabsOrInit();
        }

        // 4. 初始化各种子组件
        setupListeners();
        setupSwipeRefresh();
        
        // 初始化内置 HTTP 命令服务器
        initAiRemoteAssistant();
    }

    /**
     * 初始化默认搜索引擎：
     * 初次启动时，由于偏好设置尚未建立，根据系统语言分配搜索引擎。
     * 中国 (zh) 和朝鲜 (ko) 语言时默认使用百度，其他语言默认使用谷歌。
     */
    private void initDefaultSearchEngine() {
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.contains(Config.PREF_KEY_SEARCH_ENGINE)) {
            String lang = Locale.getDefault().getLanguage();
            String defaultEngine;
            if ("zh".equals(lang) || "ko".equals(lang)) {
                defaultEngine = Config.ENGINE_BAIDU;
            } else {
                defaultEngine = Config.ENGINE_GOOGLE;
            }
            prefs.edit().putString(Config.PREF_KEY_SEARCH_ENGINE, defaultEngine).apply();
        }
    }

    
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(com.olsc.manorbrowser.utils.LocaleHelper.onAttach(newBase));
    }
    private void requestInitialPermissions() {
        if (!isPrivacyAgreed()) return;
        
        mPendingPermissions.clear();
        
        // 1. 通知权限 (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            mPendingPermissions.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        
        // 2. 存储权限 (Android 12 及以下)
        // 注意：录音与相机权限将在网页实际请求时，通过 PermissionDelegate 动态申请
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S_V2) {
            mPendingPermissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            mPendingPermissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        
        requestNextPendingPermission();
    }

    private void requestNextPendingPermission() {
        String permission = mPendingPermissions.poll();
        // 跳过已授权的权限
        while (permission != null && 
               androidx.core.content.ContextCompat.checkSelfPermission(this, permission) 
               == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permission = mPendingPermissions.poll();
        }
        
        if (permission != null) {
            mPermissionLauncher.launch(permission);
        }
    }
    private void setupSwipeRefresh() {
        if (swipeRefresh == null) return;
        swipeRefresh.setColorSchemeResources(R.color.purple_500);
        swipeRefresh.setOnRefreshListener(() -> {
            if (isFullScreenMode && topBar.getVisibility() == View.GONE) {
                setBarsVisible(true);
            } else {
                performSmartRefresh();
            }
            swipeRefresh.setRefreshing(false);
        });
        // 增加触发刷新的拉动距离，防止在全屏模式下误触
        swipeRefresh.setDistanceToTriggerSync(400); 
    }
    @SuppressLint("NotifyDataSetChanged")
    private void restoreTabsOrInit() {
        List<TabInfo> savedTabs = TabStorage.loadTabs(this, currentTabGroup);
        if (savedTabs != null && !savedTabs.isEmpty()) {
            tabs.clear();
            tabs.addAll(savedTabs);
            for (TabInfo tab : tabs) {
                initializeSessionForTab(tab);
            }
            if (tabSwitcherAdapter != null) {
                tabSwitcherAdapter.notifyDataSetChanged();
            }
            switchToTab(tabs.size() - 1);
            updateBottomTabCounter();
        } else {
            createNewTab(Config.URL_BLANK);
        }
    }
    private void initializeSessionForTab(TabInfo tab) {
        if (tab.session != null) return;
        GeckoSession session = new GeckoSession();
        session.open(sRuntime);
        tab.session = session; 
        applyThemeToSession(session);
        
        if (Config.URL_BLANK.equals(tab.url)) {
            tab.title = getString(R.string.title_new_tab);
        }
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(@NonNull GeckoSession session, @NonNull String url) {
               // 导航开始时，清除该 session 所有的待处理 JS 摘要提取任务，防止延迟注入到新页面或导致引擎状态冲突
               mainHandler.removeCallbacksAndMessages(session);
               updateTabInfo(session, url, null);
               // 页面重新加载时，清除该 session 的自动填充弹出记录，使刷新后可再次弹出
               mPromptedAutofillUrls.removeIf(key -> key.startsWith(session.hashCode() + "_"));
               
               TabInfo currentTab = null;
               for (TabInfo t : tabs) {
                   if (t.session == session) {
                       t.scrollY = 0;
                       currentTab = t;
                       break;
                   }
               }
               
               if (currentTab != null) {
                   currentTab.lastProgress = 0;
                   resetPageLoadTimeout(currentTab, url);
               }

               runOnUiThread(() -> {
                   updateStopButtonVisibility(true);
                   if (progressBar != null) {
                       progressBar.setProgress(0);
                       progressBar.setAlpha(1f);
                       progressBar.setVisibility(View.VISIBLE);
                       progressBar.setProgressDrawable(androidx.core.content.ContextCompat.getDrawable(MainActivity.this, R.drawable.progress_gradient_red));
                   }
                   if (swipeRefresh != null) {
                       swipeRefresh.setRefreshing(true);
                   }
               });
            }
            @Override
            public void onProgressChange(@NonNull GeckoSession session, int progress) {
                TabInfo currentTab = null;
                for (TabInfo t : tabs) {
                    if (t.session == session) {
                        currentTab = t;
                        break;
                    }
                }
                if (currentTab != null && progress > currentTab.lastProgress) {
                    currentTab.lastProgress = progress;
                    // 70% 以上不再计算超时
                    if (progress < 70) {
                        resetPageLoadTimeout(currentTab, currentTab.url);
                    } else {
                        cancelPageLoadTimeout(currentTab);
                    }
                }

                runOnUiThread(() -> {
                    if (progressBar == null) return;
                    progressBar.setProgress(progress);
                    if (progress < 20) {
                        progressBar.setProgressDrawable(androidx.core.content.ContextCompat.getDrawable(MainActivity.this, R.drawable.progress_gradient_red));
                    } else if (progress < 50) {
                        progressBar.setProgressDrawable(androidx.core.content.ContextCompat.getDrawable(MainActivity.this, R.drawable.progress_gradient_orange));
                    } else if (progress < 90) {
                        progressBar.setProgressDrawable(androidx.core.content.ContextCompat.getDrawable(MainActivity.this, R.drawable.progress_gradient_green));
                    } else {
                        progressBar.setProgressDrawable(androidx.core.content.ContextCompat.getDrawable(MainActivity.this, R.drawable.progress_gradient_green));
                    }
                    if (progress >= 100) {
                        progressBar.setVisibility(View.INVISIBLE);
                        progressBar.setAlpha(1f);
                        updateStopButtonVisibility(false);
                    } else {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setAlpha(1f);
                        updateStopButtonVisibility(true);
                    }
                });
            }
            @Override
            public void onPageStop(@NonNull GeckoSession session, boolean success) {
               updateTabInfo(session, null, null);
               
               // 页面停止加载后，提取摘要 (比在 onTitleChange 中提取更稳定，避开渲染高峰期)
               TabInfo tabInfo = null;
               for (TabInfo t : tabs) {
                   if (t.session == session) {
                       tabInfo = t;
                        cancelPageLoadTimeout(t);
                       break;
                   }
               }
               
               if (tabInfo != null && tabInfo.url != null && !tabInfo.url.equals("about:blank") && !tabInfo.url.startsWith("moz-extension://") && !isLordMode) {
                   final TabInfo finalTab = tabInfo;
                   mainHandler.removeCallbacksAndMessages(session);
                   mainHandler.postAtTime(() -> {
                       if (finalTab.session != null && finalTab.session == session) {
                           // 注入更轻量的脚本。使用 textContent 替代 innerText 可避免触发昂贵的 native 布局计算，提升稳定性
                           finalTab.session.loadUri("javascript:(function(){" +
                               "try {" +
                               "  if(document.body && document.body.textContent){" +
                               "    alert('HISTORY_SNIPPET:' + document.body.textContent.substring(0, 500).replace(/\\n/g, ' '));" +
                               "  }" +
                               "} catch(e) {}" +
                               "})()");
                       }
                   }, session, android.os.SystemClock.uptimeMillis() + 500);
               }
               
               runOnUiThread(() -> {
                   updateStopButtonVisibility(false);
                   if (progressBar != null) progressBar.setVisibility(View.INVISIBLE);
                   if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
               });
            }
            
            @Override public void onSessionStateChange(@NonNull GeckoSession session, @NonNull GeckoSession.SessionState sessionState) {}
        });
        
        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onTitleChange(@NonNull GeckoSession session, String title) {
                updateTabInfo(session, null, title);
                if (title != null && !title.isEmpty() && !title.equals(Config.URL_BLANK)) {
                   TabInfo tabInfo = null;
                   for(TabInfo t: tabs){
                       if(t.session == session){
                           tabInfo = t;
                           break;
                       }
                   }
                   if(tabInfo != null && tabInfo.url != null && !tabInfo.url.equals("about:blank")){
                        if (!isLordMode) {
                            // 1. 记录基础历史并增加权重
                            HistoryStorage.addHistory(MainActivity.this, title, tabInfo.url);
                        }
                   }
                }
            }
            
            @Override
            public void onExternalResponse(@NonNull GeckoSession session, @NonNull WebResponse response) {
                final String url = response.uri;

                String lowerUrl = url.toLowerCase();
                String contentType = null;
                contentType = response.headers.get("Content-Type");
                if (contentType == null) contentType = response.headers.get("content-type");

                // APK 检查，防止自动下载
                if (lowerUrl.endsWith(".apk") || (contentType != null && contentType.contains("vnd.android.package-archive"))) {
                    String tempMime = contentType;
                    String tempDisp = null;
                    tempDisp = response.headers.get("Content-Disposition");
                    if (tempDisp == null) tempDisp = response.headers.get("content-disposition");
                    final String apkMime = tempMime;
                    final String apkDisp = tempDisp;
                    
                    runOnUiThread(() -> {
                        String filename = com.olsc.manorbrowser.utils.DownloadHelper.guessFileName(url, apkDisp, apkMime);
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle(R.string.title_download_apk)
                            .setMessage(getString(R.string.msg_download_apk, filename))
                            .setPositiveButton(R.string.action_download, (dialog, which) -> {
                                triggerDownload(session, url, apkDisp, apkMime);
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    });
                    return;
                }

                // 扩展程序检查
                if (lowerUrl.endsWith(".xpi") || "application/x-xpinstall".equals(contentType)) {
                    final String extensionUrl = url;
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, R.string.msg_installing_extension, Toast.LENGTH_SHORT).show();
                        if (sRuntime != null) {
                            sRuntime.getWebExtensionController().install(extensionUrl).accept(
                                ext -> runOnUiThread(() -> Toast.makeText(MainActivity.this, getString(R.string.msg_extension_installed, ext.metaData.name), Toast.LENGTH_SHORT).show()),
                                e -> runOnUiThread(() -> Toast.makeText(MainActivity.this, R.string.msg_extension_install_failed, Toast.LENGTH_SHORT).show())
                            );
                        }
                    });
                    return;
                }

                // 在尝试下载之前，先检查是否可以由外部应用处理（处理某些Gecko未拦截的Scheme）
                if (com.olsc.manorbrowser.utils.IntentHelper.shouldInterceptUrl(url)) {
                    boolean handled = com.olsc.manorbrowser.utils.IntentHelper.tryOpenExternalApp(MainActivity.this, url);
                    if (handled) {
                        return;
                    }
                }

                String tempMime = contentType;
                String tempDisp = null;
                tempDisp = response.headers.get("Content-Disposition");
                if (tempDisp == null) tempDisp = response.headers.get("content-disposition");
                final String finalMime = tempMime;
                final String finalDisp = tempDisp;
                // 把完整的 WebResponse 传给下载触发方法，让它能读取 GeckoView 的真实 Cookie
                final org.mozilla.geckoview.WebResponse finalResponse = response;

                runOnUiThread(() -> {
                    String filename = com.olsc.manorbrowser.utils.DownloadHelper.guessFileName(url, finalDisp, finalMime);
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(R.string.title_download_confirm)
                        .setMessage(getString(R.string.msg_download_confirm, filename))
                        .setPositiveButton(R.string.action_download, (dialog, which) -> {
                            triggerDownloadWithHeaders(session, url, finalDisp, finalMime, finalResponse);
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                });
            }

            private void triggerDownload(GeckoSession session, String url, String contentDisposition, String mimeType) {
                triggerDownloadWithHeaders(session, url, contentDisposition, mimeType, null);
            }

            private void triggerDownloadWithHeaders(GeckoSession session, String url, String contentDisposition, String mimeType, org.mozilla.geckoview.WebResponse geckoResponse) {
                String filename = com.olsc.manorbrowser.utils.DownloadHelper.guessFileName(url, contentDisposition, mimeType);

                if (geckoResponse != null && geckoResponse.body != null) {
                    long contentLength = -1;
                    String cl = geckoResponse.headers.get("Content-Length");
                    if (cl == null) cl = geckoResponse.headers.get("content-length");
                    if (cl != null) {
                        try { contentLength = Long.parseLong(cl.trim()); } catch (NumberFormatException ignored) {}
                    }
                    com.olsc.manorbrowser.utils.BrowserDownloader.downloadFromStream(
                        MainActivity.this, url, mimeType, filename, contentLength, geckoResponse.body
                    );
                    return;
                }

                String ua = session.getSettings().getUserAgentOverride();
                if (ua == null || ua.isEmpty()) ua = Config.DEFAULT_UA;

                // 尝试从 GeckoView 响应头中获取 Cookie（可能没有，因为是请求头不是响应头）
                String cookies = null;
                if (geckoResponse != null) {
                    String setCookie = geckoResponse.headers.get("Set-Cookie");
                    if (setCookie == null) setCookie = geckoResponse.headers.get("set-cookie");
                    if (setCookie != null) cookies = setCookie.split(";")[0].trim();
                }
                if (cookies == null || cookies.isEmpty()) {
                    cookies = android.webkit.CookieManager.getInstance().getCookie(url);
                }

                String referer = null;
                for (TabInfo t : tabs) {
                    if (t.session == session) { referer = t.url; break; }
                }

                com.olsc.manorbrowser.utils.DownloadHelper.startDownload(
                    MainActivity.this, url, ua, contentDisposition, mimeType, cookies, referer
                );
            }

             @Override public void onFocusRequest(@NonNull GeckoSession session) {}
             @Override public void onFullScreen(@NonNull GeckoSession session, boolean fullScreen) {}
             @Override public void onCloseRequest(@NonNull GeckoSession session) {}
             @Override
             public void onContextMenu(@NonNull GeckoSession session, int screenX, int screenY, @NonNull ContextElement element) {
                 lastX = screenX;
                 lastY = screenY;
                 runOnUiThread(() -> showWebContextMenu(element));
             }
        });
        session.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override
            public GeckoResult<Integer> onContentPermissionRequest(@NonNull GeckoSession session, @NonNull GeckoSession.PermissionDelegate.ContentPermission perm) {
                if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION) {
                    // 检查网站是否始终被拒绝访问位置
                    if (isLocationRestricted(perm.uri)) {
                        return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
                    }

                    // 首先检查系统权限
                    if (androidx.core.content.ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, R.string.msg_no_location_permission, Toast.LENGTH_LONG).show());
                        return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
                    }
                    
                    final GeckoResult<Integer> result = new GeckoResult<>();
                    runOnUiThread(() -> {
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle(R.string.title_location_warning)
                            .setMessage(getString(R.string.msg_location_warning, perm.uri))
                            .setPositiveButton(R.string.action_allow, (d, w) -> result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW))
                            .setNegativeButton(R.string.action_deny, (d, w) -> result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY))
                            .setNeutralButton(R.string.action_always_deny, (d, w) -> {
                                addLocationRestriction(perm.uri);
                                result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
                            })
                            .setCancelable(false)
                            .show();
                    });
                    return result;
                } else if (isLordMode && perm.permission == GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE) {
                    final GeckoResult<Integer> result = new GeckoResult<>();
                    runOnUiThread(() -> {
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle(R.string.title_cookie_warning)
                            .setMessage(getString(R.string.msg_cookie_warning, perm.uri))
                            .setPositiveButton(R.string.action_allow, (d, w) -> result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW))
                            .setNegativeButton(R.string.action_deny, (d, w) -> result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY))
                            .setCancelable(false)
                            .show();
                    });
                    return result;
                }
                return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW);
            }

            private boolean isLocationRestricted(String uri) {
                android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                java.util.Set<String> restricted = prefs.getStringSet(Config.PREF_KEY_LOCATION_RESTRICTED_SITES, new java.util.HashSet<>());
                String host = android.net.Uri.parse(uri).getHost();
                return host != null && restricted.contains(host);
            }

            private void addLocationRestriction(String uri) {
                android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                java.util.Set<String> restricted = new java.util.HashSet<>(prefs.getStringSet(Config.PREF_KEY_LOCATION_RESTRICTED_SITES, new java.util.HashSet<>()));
                String host = android.net.Uri.parse(uri).getHost();
                if (host != null) {
                    restricted.add(host);
                    prefs.edit().putStringSet(Config.PREF_KEY_LOCATION_RESTRICTED_SITES, restricted).apply();
                }
            }

            @Override
            public void onAndroidPermissionsRequest(@NonNull GeckoSession session, @NonNull String[] permissions, @NonNull GeckoSession.PermissionDelegate.Callback callback) {
                if (!isPrivacyAgreed()) {
                    callback.reject();
                    return;
                }
                // 这里处理使用时的动态申请（如相机）
                mGeckoPermissionCallback = callback;
                mGeckoPermissionLauncher.launch(permissions);
            }
        });
        session.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override
            public GeckoResult<PromptResponse> onFilePrompt(@NonNull GeckoSession session, @NonNull PromptDelegate.FilePrompt prompt) {
                mCurrentFilePrompt = prompt;
                mFilePromptResult = new GeckoResult<>();
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                String[] mimeTypes = prompt.mimeTypes;
                if (mimeTypes != null && mimeTypes.length > 0) {
                    intent.setType(mimeTypes[0]);
                    intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES, mimeTypes);
                } else {
                    intent.setType("*/*");
                }
                intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                if (prompt.type == 2) {
                    intent.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                mFilePickerLauncher.launch(intent);
                return mFilePromptResult;
            }

            @Override
            public GeckoResult<PromptResponse> onAlertPrompt(@NonNull GeckoSession session, @NonNull AlertPrompt prompt) {
                String message = prompt.message;
                if (message != null && message.startsWith("MANOR_SAVE_PASS|")) {
                    // 兼容旧版本注入逻辑，防止遗漏
                    handleSavePasswordRequest(message);
                    return GeckoResult.fromValue(prompt.dismiss());
                } else if (message != null && message.startsWith("COPY_TEXT:")) {
                    String textToCopy = message.substring("COPY_TEXT:".length());
                    runOnUiThread(() -> copyToClipboard(textToCopy));
                    return GeckoResult.fromValue(prompt.dismiss());
                } else if (message != null && message.startsWith("READER_MODE:")) {
                    String data = message.substring("READER_MODE:".length());
                    String[] parts = data.split("\\|\\|\\|", 2);
                    String title = parts.length > 0 ? parts[0] : "";
                    String content = parts.length > 1 ? parts[1] : "";
                    if (content.trim().isEmpty()) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, R.string.msg_reader_mode_failed, Toast.LENGTH_SHORT).show());
                    } else {
                        runOnUiThread(() -> openReaderActivity(title, content));
                    }
                    return GeckoResult.fromValue(prompt.dismiss());
                } else if (message != null && message.startsWith("__JSRESULT__:")) {
                    // JS 桥接调用拦截
                    String resultData = message.substring("__JSRESULT__:".length());
                    BrowserCommandServer.EvalCallback cb = pendingJsCallback;
                    pendingJsCallback = null;
                    if (cb != null) {
                        cb.onResult(resultData);
                    }
                    return GeckoResult.fromValue(prompt.dismiss());
                } else if (message != null && message.startsWith("HISTORY_SNIPPET:")) {
                    // 历史记录摘要回传
                    String snippet = message.substring("HISTORY_SNIPPET:".length());
                    TabInfo tab = null;
                    for (TabInfo t : tabs) {
                        if (t.session == session) {
                            tab = t;
                            break;
                        }
                    }
                    if (tab != null && tab.url != null) {
                        HistoryStorage.updateHistoryContent(MainActivity.this, tab.url, snippet);
                    }
                    return GeckoResult.fromValue(prompt.dismiss());
                }
                // 显示真实的网页 alert 对话框
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                final java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);
                runOnUiThread(() -> {
                    String dlgTitle = (prompt.title != null && !prompt.title.isEmpty()) ? prompt.title : getString(R.string.dialog_title_alert);
                    String msg = prompt.message != null ? prompt.message : "";
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(dlgTitle)
                        .setMessage(msg)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            if (completed.compareAndSet(false, true)) {
                                result.complete(prompt.dismiss());
                            }
                        })
                        .setOnDismissListener(d -> {
                            if (completed.compareAndSet(false, true)) {
                                result.complete(prompt.dismiss());
                            }
                        })
                        .show();
                });
                return result;
            }

            @Override
            public GeckoResult<PromptResponse> onTextPrompt(@NonNull GeckoSession session, @NonNull PromptDelegate.TextPrompt prompt) {
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                final java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);
                runOnUiThread(() -> {
                    String dlgTitle = (prompt.title != null && !prompt.title.isEmpty()) ? prompt.title : getString(R.string.dialog_title_prompt);
                    String msg = prompt.message != null ? prompt.message : "";
                    android.widget.EditText editText = new android.widget.EditText(MainActivity.this);
                    if (!msg.isEmpty()) editText.setHint(msg);
                    if (prompt.defaultValue != null) {
                        editText.setText(prompt.defaultValue);
                        editText.setSelection(prompt.defaultValue.length());
                    }
                    int paddingPx = (int)(24 * getResources().getDisplayMetrics().density);
                    android.widget.LinearLayout container = new android.widget.LinearLayout(MainActivity.this);
                    container.setPadding(paddingPx, paddingPx / 2, paddingPx, 0);
                    container.addView(editText);
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(dlgTitle)
                        .setMessage(msg.isEmpty() ? null : msg)
                        .setView(container)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            if (completed.compareAndSet(false, true)) {
                                result.complete(prompt.confirm(editText.getText().toString()));
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                            if (completed.compareAndSet(false, true)) {
                                result.complete(prompt.dismiss());
                            }
                        })
                        .setOnDismissListener(d -> {
                            if (completed.compareAndSet(false, true)) {
                                result.complete(prompt.dismiss());
                            }
                        })
                        .show();
                });
                return result;
            }

            @Override
            public GeckoResult<PromptResponse> onLoginSelect(@NonNull GeckoSession session, @NonNull AutocompleteRequest<Autocomplete.LoginSelectOption> request) {
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                String currentUrl = null;
                for (TabInfo t : tabs) {
                    if (t.session == session) {
                        currentUrl = t.url;
                        break;
                    }
                }
                final String urlKey = session.hashCode() + "_" + (currentUrl != null ? currentUrl : "");
                if (mPromptedAutofillUrls.contains(urlKey)) {
                    result.complete(request.dismiss());
                    return result;
                }
                mPromptedAutofillUrls.add(urlKey);
                
                runOnUiThread(() -> showPasswordSelectionDialogForNative(request, result));
                return result;
            }

            @Override
            public GeckoResult<PromptResponse> onLoginSave(@NonNull GeckoSession session, @NonNull AutocompleteRequest<Autocomplete.LoginSaveOption> request) {
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                runOnUiThread(() -> handleNativeSavePassword(request, result));
                return result;
            }

            @Override
            public GeckoResult<PromptResponse> onButtonPrompt(@NonNull GeckoSession session, @NonNull PromptDelegate.ButtonPrompt prompt) {
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                final java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);
                runOnUiThread(() -> {
                    String dlgTitle = (prompt.title != null && !prompt.title.isEmpty()) ? prompt.title : getString(R.string.dialog_title_confirm);
                    String msg = prompt.message != null ? prompt.message : "";
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(dlgTitle)
                        .setMessage(msg)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            if (completed.compareAndSet(false, true)) {
                                result.complete(prompt.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE));
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                            if (completed.compareAndSet(false, true)) {
                                result.complete(prompt.confirm(PromptDelegate.ButtonPrompt.Type.NEGATIVE));
                            }
                        })
                        .setOnDismissListener(d -> {
                            if (completed.compareAndSet(false, true)) {
                                result.complete(prompt.dismiss());
                            }
                        })
                        .show();
                });
                return result;
            }

            @Override
            public GeckoResult<PromptResponse> onChoicePrompt(@NonNull GeckoSession session, @NonNull PromptDelegate.ChoicePrompt prompt) {
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                final java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);
                runOnUiThread(() -> {
                    String dlgTitle = (prompt.title != null && !prompt.title.isEmpty()) ? prompt.title : getString(R.string.dialog_title_select);
                    PromptDelegate.ChoicePrompt.Choice[] choices = prompt.choices;
                    if (choices.length == 0) {
                        result.complete(prompt.dismiss());
                        return;
                    }
                    String[] labels = new String[choices.length];
                    for (int i = 0; i < choices.length; i++) {
                        labels[i] = choices[i].label;
                    }
                    if (prompt.type == PromptDelegate.ChoicePrompt.Type.SINGLE ||
                        prompt.type == PromptDelegate.ChoicePrompt.Type.MENU) {
                        // 单选列表
                        final int[] selectedIdx = {-1};
                        for (int i = 0; i < choices.length; i++) {
                            if (choices[i].selected) { selectedIdx[0] = i; break; }
                        }
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle(dlgTitle)
                            .setSingleChoiceItems(labels, selectedIdx[0], (dialog, which) -> selectedIdx[0] = which)
                            .setCancelable(false)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                if (completed.compareAndSet(false, true)) {
                                    if (selectedIdx[0] >= 0) {
                                        result.complete(prompt.confirm(choices[selectedIdx[0]]));
                                    } else {
                                        result.complete(prompt.dismiss());
                                    }
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                                if (completed.compareAndSet(false, true)) {
                                    result.complete(prompt.dismiss());
                                }
                            })
                            .setOnDismissListener(d -> {
                                if (completed.compareAndSet(false, true)) {
                                    result.complete(prompt.dismiss());
                                }
                            })
                            .show();
                    } else if (prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
                        // 多选列表
                        boolean[] checkedItems = new boolean[choices.length];
                        for (int i = 0; i < choices.length; i++) {
                            checkedItems[i] = choices[i].selected;
                        }
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle(dlgTitle)
                            .setMultiChoiceItems(labels, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
                            .setCancelable(false)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                if (completed.compareAndSet(false, true)) {
                                    List<PromptDelegate.ChoicePrompt.Choice> selected = new ArrayList<>();
                                    for (int i = 0; i < choices.length; i++) {
                                        if (checkedItems[i]) selected.add(choices[i]);
                                    }
                                    result.complete(prompt.confirm(selected.toArray(new PromptDelegate.ChoicePrompt.Choice[0])));
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                                if (completed.compareAndSet(false, true)) {
                                    result.complete(prompt.dismiss());
                                }
                            })
                            .setOnDismissListener(d -> {
                                if (completed.compareAndSet(false, true)) {
                                    result.complete(prompt.dismiss());
                                }
                            })
                            .show();
                    } else {
                        if (completed.compareAndSet(false, true)) {
                            result.complete(prompt.dismiss());
                        }
                    }
                });
                return result;
            }
        });
        session.setScrollDelegate(new GeckoSession.ScrollDelegate() {
            @Override
            public void onScrollChanged(@NonNull GeckoSession session, int scrollX, int scrollY) {
                for (TabInfo t : tabs) {
                    if (t.session == session) {
                        t.scrollY = scrollY;
                        break;
                    }
                }
                if (session == getCurrentSession() && swipeRefresh != null) {
                    swipeRefresh.setEnabled(scrollY <= 0);
                }
            }
        });
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public GeckoResult<String> onLoadError(@NonNull GeckoSession session, @Nullable String uri, @NonNull org.mozilla.geckoview.WebRequestError error) {
                // 1. 允许内部资源和关于页通过（防止重定向到 404 或 offline 导致循环或拦截失效）
                if (uri != null && (uri.startsWith("resource://") || uri.startsWith("about:") || uri.startsWith("view-source:") || uri.startsWith("moz-extension://"))) {
                    return GeckoResult.fromValue(null);
                }

                // 2. 证书与安全错误拦截 (针对 HTTPS 不安全连接)
                if (error.category == org.mozilla.geckoview.WebRequestError.ERROR_CATEGORY_SECURITY) {
                    String tempHost = "unknown";
                    try {
                        if (uri != null) {
                            android.net.Uri parsed = android.net.Uri.parse(uri);
                            tempHost = parsed.getHost();
                            if (tempHost == null) tempHost = parsed.getAuthority();
                        }
                    } catch (Exception ignored) {}
                    final String host = tempHost != null ? tempHost : "unknown";

                    // 如果已经加入了白名单，返回 null 显示引擎默认的证书错误页
                    // 用户可以在内置错误页点“高级”->“继续”来进入网页
                    if (isSslOverridden(host)) {
                        return GeckoResult.fromValue(null);
                    }

                    // 弹出原生 BottomSheet 告知安全问题
                    final GeckoResult<String> result = new GeckoResult<>();
                    runOnUiThread(() -> {
                        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(MainActivity.this);
                        android.view.View view = getLayoutInflater().inflate(R.layout.bottom_sheet_security_warning, null);
                        dialog.setContentView(view);
                        
                        android.widget.TextView tvHost = view.findViewById(R.id.tv_ssl_host);
                        if (tvHost != null) tvHost.setText(host);
                        
                        view.findViewById(R.id.btn_stop_visit).setOnClickListener(v -> {
                            dialog.dismiss();
                            result.complete("resource://android/assets/html/404.html");
                        });
                        
                        // 隐藏“继续访问”按钮，只保留停止访问选型
                        android.view.View btnAccept = view.findViewById(R.id.btn_accept_risk);
                        if (btnAccept != null) {
                            btnAccept.setVisibility(android.view.View.GONE);
                        }
                        
                        dialog.show();
                    });
                    return result;
                }
                
                // 3. 其他网络与 URI 错误分发
                switch (error.category) {
                    case org.mozilla.geckoview.WebRequestError.ERROR_CATEGORY_NETWORK:
                        return GeckoResult.fromValue("resource://android/assets/html/offline.html");
                    case org.mozilla.geckoview.WebRequestError.ERROR_CATEGORY_URI:
                        return GeckoResult.fromValue("resource://android/assets/html/404.html");
                    default:
                        if (error.code == org.mozilla.geckoview.WebRequestError.ERROR_CONNECTION_REFUSED || 
                            error.code == org.mozilla.geckoview.WebRequestError.ERROR_NET_TIMEOUT) {
                            return GeckoResult.fromValue("resource://android/assets/html/timeout.html");
                        }
                        return GeckoResult.fromValue("resource://android/assets/html/404.html");
                }
            }

            @Override
            public void onLocationChange(@NonNull GeckoSession session, @Nullable String url, @NonNull java.util.List<org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission> permissions, @NonNull Boolean hasUserGesture) {
                if (url != null) {
                    // 同步 URL 状态到内存中的标签模型
                    tab.url = url;
                    // 如果是当前活动标签，更新地址栏
                    runOnUiThread(() -> {
                        if (session == getCurrentSession()) {
                            updateUrlBar();
                        }
                    });
                }
            }

            @Override
            public GeckoResult<GeckoSession> onNewSession(@NonNull GeckoSession session, @NonNull String uri) {
                // 1. 无效或基础跳转拦截
                if (uri == null || uri.isEmpty() || "about:blank".equals(uri) || uri.startsWith("data:")) {
                    return GeckoResult.fromValue(null);
                }
                
                // 2. 黑名单过滤
                if (isLordMode && isAdUri(uri)) {
                    return GeckoResult.fromValue(null);
                }
                
                // 3. 频控保护
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastPopupTime < 1000) { 
                    lastPopupTime = currentTime;
                    return GeckoResult.fromValue(null);
                }
                lastPopupTime = currentTime;
                
                // 允许新标签页
                runOnUiThread(() -> {
                    try {
                        createNewTab(uri);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                return GeckoResult.fromValue(null);
            }

            private boolean isAdUri(String uri) {
                if (uri == null || uri.isEmpty()) return true;
                String lower = uri.toLowerCase();
                return lower.contains("popads") || lower.contains("onclickads") || 
                       lower.contains("onclickperformance") || lower.contains("clksite") || 
                       lower.contains("cpm") || lower.contains("adsterra") || 
                       lower.contains("doubleclick") || lower.contains("googleadservices") ||
                       lower.contains("affiliate") || lower.contains("/ad/") || 
                       lower.contains("clk") || lower.contains("redirect") ||
                       lower.contains("track") || lower.contains("analytic") ||
                       lower.contains("popcash") || lower.contains("exoclick");
            }

            @Override
            public void onCanGoBack(@NonNull GeckoSession session, boolean canGoBack) {
                tab.canGoBack = canGoBack;
                if (session == getCurrentSession()) {
                    runOnUiThread(() -> {
                        btnBack.setEnabled(canGoBack);
                        btnBack.setAlpha(canGoBack ? 1.0f : 0.3f);
                    });
                }
            }
            
            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(
                    @NonNull GeckoSession session,
                    @NonNull LoadRequest request) {
                String uri = request.uri;

                // 检查是否是 .onion 域名
                try {
                    android.net.Uri parsedUri = android.net.Uri.parse(uri);
                    String host = parsedUri.getHost();
                    if (host != null && host.toLowerCase().endsWith(".onion")) {
                        if (!uri.contains("allow_onion=1")) {
                            String encodedUrl = android.net.Uri.encode(uri);
                            String warningUrl = "resource://android/assets/html/warning_onion.html?url=" + encodedUrl;
                            runOnUiThread(() -> session.loadUri(warningUrl));
                            return GeckoResult.fromValue(AllowOrDeny.DENY);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                if (isLordMode && isAdUri(uri)) {
                    return GeckoResult.fromValue(AllowOrDeny.DENY);
                }
                
                if (com.olsc.manorbrowser.utils.IntentHelper.shouldInterceptUrl(uri)) {
                    boolean handled = com.olsc.manorbrowser.utils.IntentHelper.tryOpenExternalApp(MainActivity.this, uri);
                    if (handled) {
                        return GeckoResult.fromValue(AllowOrDeny.DENY);
                    } else if (uri.startsWith("intent://")) {
                        try {
                            android.content.Intent intent = android.content.Intent.parseUri(uri, android.content.Intent.URI_INTENT_SCHEME);
                            String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                            if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                                runOnUiThread(() -> session.loadUri(fallbackUrl));
                                return GeckoResult.fromValue(AllowOrDeny.DENY);
                            }
                        } catch (Exception ignored) {}
                    }
                }
                
                return GeckoResult.fromValue(AllowOrDeny.ALLOW);
            }
        });
        session.setHistoryDelegate(new GeckoSession.HistoryDelegate() {
            @Override
            public void onHistoryStateChange(@NonNull GeckoSession session, @NonNull HistoryList historyList) {
                tab.canGoBack = historyList.getCurrentIndex() > 0;
                if (session == getCurrentSession()) {
                    runOnUiThread(() -> {
                        btnBack.setEnabled(tab.canGoBack);
                        btnBack.setAlpha(tab.canGoBack ? 1.0f : 0.3f);
                    });
                }
            }
        });
        if (tab.url != null && !tab.url.isEmpty() && !Config.URL_BLANK.equals(tab.url)) {
            session.loadUri(tab.url);
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        refreshBackgroundEffect();
        
        // 重新应用桌面模式设置到所有session
        for (TabInfo tab : tabs) {
            if (tab.session != null) {
                applyThemeToSession(tab.session);
            }
        }
        updateHomeButton();
    }
    
    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!isPrivacyAgreed()) return;
        if (android.content.Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            handleViewIntent(intent);
        } else if (intent.hasExtra("url")) {
            String url = intent.getStringExtra("url");
            if (url != null && !url.isEmpty()) {
                loadUrlInCurrentTab(url);
            }
        }
    }
    
    private void handleViewIntent(android.content.Intent intent) {
        android.net.Uri data = intent.getData();
        if (data == null) return;
        String url = data.toString();
        String type = intent.getType();
        
        String filename = url;
        if ("content".equals(data.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(data, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        filename = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        
        final String finalFilename = filename;

        boolean isXpi = false;
        String mimeType = type;
        if (mimeType == null && "content".equals(data.getScheme())) {
            mimeType = getContentResolver().getType(data);
        }
        
        if ("application/x-xpinstall".equalsIgnoreCase(mimeType) || "application/xpi".equalsIgnoreCase(mimeType)) {
            isXpi = true;
        } else if (filename != null && (filename.toLowerCase().endsWith(".xpi") || filename.toLowerCase().contains(".xpi"))) {
            isXpi = true;
        } else if (url.toLowerCase().endsWith(".xpi") || url.toLowerCase().contains(".xpi")) {
            isXpi = true;
        }

        if (isXpi) {
            Toast.makeText(this, R.string.msg_installing_extension, Toast.LENGTH_SHORT).show();
            Runnable installRunnable = () -> {
                String tempUrl = url;
                if ("content".equals(data.getScheme())) {
                    java.io.File tempFile = com.olsc.manorbrowser.utils.FileUtil.copyContentUriToTempFile(this, data, finalFilename != null ? finalFilename : "temp.xpi");
                    if (tempFile != null) {
                        tempUrl = "file://" + tempFile.getAbsolutePath();
                    }
                } else if ("file".equals(data.getScheme()) && !url.startsWith("file://")) {
                    tempUrl = "file://" + data.getPath();
                }
                final String finalUrl = tempUrl;
                runOnUiThread(() -> {
                    if (sRuntime != null) {
                        sRuntime.getWebExtensionController().install(finalUrl).accept(
                            ext -> runOnUiThread(() -> Toast.makeText(MainActivity.this, getString(R.string.msg_extension_installed, ext.metaData.name), Toast.LENGTH_SHORT).show()),
                            e -> runOnUiThread(() -> Toast.makeText(MainActivity.this, R.string.msg_extension_install_failed, Toast.LENGTH_SHORT).show())
                        );
                    }
                });
            };
            new Thread(installRunnable).start();
        } else {
            loadUrlInCurrentTab(url);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 传递副本以确保后台线程保存时的线程安全
        TabStorage.saveTabs(this, currentTabGroup, new ArrayList<>(tabs));
        
        // 触发闲时历史整理
        com.olsc.manorbrowser.data.HistoryStorage.runIdleOrganization(this);
    }
    @Override
    protected void onDestroy() {
        for (TabInfo tab : tabs) {
            if (tab.session != null) {
                cancelPageLoadTimeout(tab);
                tab.session.setProgressDelegate(null);
                tab.session.setContentDelegate(null);
                tab.session.close();
                tab.session = null;
            }
        }
        if (commandServer != null) {
            commandServer = null;
        }
        super.onDestroy();
    }
    private void setupTabSwitcher() {
        tabSwitcherAdapter = new TabSwitcherAdapter(tabs, new TabSwitcherAdapter.OnTabActionListener() {
            @Override
            public void onTabClick(int position) {
                switchToTab(position);
            }
            @Override
            public void onTabClose(int position) {
                closeTab(position);
                tabSwitcher.postDelayed(this::updateCardScales, 300);
            }
            @Override
            public void onTabLongPress(int position) {
                showTabEditDialog(position);
            }
            private void updateCardScales() {
                 if (tabSwitcher.getLayoutManager() != null) {
                     tabSwitcher.scrollBy(0,0);
                 }
            }
        });
        final androidx.recyclerview.widget.LinearLayoutManager layoutManager =
            new androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false);
        tabSwitcher.setLayoutManager(layoutManager);
        tabSwitcher.setAdapter(tabSwitcherAdapter);
        tabSwitcher.setClipToPadding(false);
        tabSwitcher.setClipChildren(false);
        tabSwitcher.setOverScrollMode(View.OVER_SCROLL_NEVER);
        android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        float screenRatio = (float) screenWidth / displayMetrics.heightPixels;
        float density = displayMetrics.density;
        int cardHeight = displayMetrics.heightPixels - (int) (64 * density);
        int cardWidthPx = (int) (cardHeight * screenRatio);
        int maxWidth = (int) (screenWidth * 0.85f);
        if (cardWidthPx > maxWidth) cardWidthPx = maxWidth;
        int padding = (screenWidth - cardWidthPx) / 2;
        tabSwitcher.setPadding(padding, 0, padding, 0);
        final int overlapPx = (int) (110 * displayMetrics.density);
        tabSwitcher.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                if (position != state.getItemCount() - 1) {
                     outRect.right = -overlapPx;
                } else {
                     outRect.right = 0;
                }
            }
        });
        androidx.recyclerview.widget.LinearSnapHelper snapHelper = new androidx.recyclerview.widget.LinearSnapHelper();
        snapHelper.attachToRecyclerView(tabSwitcher);
        TabSwipeCallback swipeCallback = new TabSwipeCallback(this::closeTab);
        androidx.recyclerview.widget.ItemTouchHelper itemTouchHelper =
            new androidx.recyclerview.widget.ItemTouchHelper(swipeCallback);
        itemTouchHelper.attachToRecyclerView(tabSwitcher);
        tabSwitcher.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updateTabAnimations();
                updateTabCount();
            }
        });
        tabSwitcher.post(this::updateTabAnimations);
    }
    private void setupListeners() {
        urlInput.setSelectAllOnFocus(false); // 禁用自动全选
        
        urlInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // 获得焦点时，重置为第一次点击状态并展示候选列表
                urlInputFirstClick = true;
                updateRecommendations(urlInput.getText().toString());
            } else {
                // 失去焦点时，延迟隐藏（给列表点击留出响应时间）
                urlInputFirstClick = true;
                mainHandler.postDelayed(() -> recommendationsContainer.setVisibility(View.GONE), 200);
            }
        });
        
        urlInput.setOnClickListener(v -> {
            if (urlInput.isFocused() && urlInputFirstClick) {
                // 第一次点击时全选
                urlInput.post(() -> urlInput.selectAll());
                urlInputFirstClick = false;
            }
            // 第二次及之后的点击不做处理，使用默认行为（定位光标）
        });
        
        // 长按时全选
        urlInput.setOnLongClickListener(v -> {
            urlInput.selectAll();
            return true; // 返回true表示已处理，不再执行默认行为
        });

        // 核心：处理输入建议
        urlInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (urlInput.hasFocus()) {
                    updateRecommendations(s.toString());
                }
            }
        });

        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                recommendationsContainer.setVisibility(View.GONE);
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
                }
                urlInput.clearFocus();
                String input = urlInput.getText().toString();
                String url = SearchHelper.getSearchUrl(this, input);
                // 直接调用 loadUri 以绕过主界面的 UI 锁校验（或者是统一在 loadUrlInCurrentTab 中校验）
                loadUrlInCurrentTab(url);
                return true;
            }
            return false;
        });
        EditText homeSearch = findViewById(R.id.et_home_search);
        if (homeSearch != null) {
            homeSearch.setSelectAllOnFocus(true);
            homeSearch.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    homeSearch.post(homeSearch::selectAll);
                }
            });
            homeSearch.setOnClickListener(v -> {
                if (homeSearch.isFocused()) {
                    homeSearch.selectAll();
                }
            });
            homeSearch.setOnLongClickListener(v -> {
                homeSearch.selectAll();
                return false;
            });
        }
        assert homeSearch != null;
        homeSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(homeSearch.getWindowToken(), 0);
                }
                String input = homeSearch.getText().toString();
                String url = SearchHelper.getSearchUrl(this, input);
                loadUrlInCurrentTab(url);
                return true;
            }
            return false;
        });
        btnBack.setOnClickListener(v -> {
            if (isProcessingAction) return;
            GeckoSession session = getCurrentSession();
            if (session != null) {
                // 扩展页(moz-extension://)的 goBack 会触发引擎跨边界历史切换,导致 libxul 原生崩溃
                // 此处改为关闭扩展标签页,回到之前的普通页面
                if (isExtensionPageUrl(getCurrentTabUrl())) {
                    closeExtensionTabAndSwitch();
                    return;
                }
                mainHandler.removeCallbacksAndMessages(session);
                session.goBack();
            }
        });
        btnRefresh.setOnClickListener(v -> {
            if (isProcessingAction) return;
            performSmartRefresh();
        });
        btnStop.setOnClickListener(v -> {
            if (isProcessingAction) return;
            GeckoSession session = getCurrentSession();
            if (session != null) {
                session.stop();
            }
        });
        updateHomeButton();
        btnTabs.setOnClickListener(v -> toggleTabSwitcher());
        btnTabs.setOnLongClickListener(v -> {
            showTabManagerOptionsDialog();
            return true;
        });
        btnMenu.setOnClickListener(v -> {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.openDrawer(GravityCompat.START);
            } else {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
        });
        if (btnFullscreenMenu != null && fullscreenMenuContainer != null) {
            fullscreenMenuContainer.setOnTouchListener(new View.OnTouchListener() {
                private float dX, dY;
                private float startX, startY;
                private boolean isDragging = false;
                private boolean longPressed = false;
                private static final int DRAG_THRESHOLD = 20;
                private final Handler handler = new Handler(Looper.getMainLooper());
                private final Runnable longPressRunnable = () -> {
                    longPressed = true;
                    fullscreenMenuContainer.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                };

                @Override
                public boolean onTouch(View view, android.view.MotionEvent event) {
                    switch (event.getAction()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            startX = event.getRawX();
                            startY = event.getRawY();
                            dX = view.getX() - event.getRawX();
                            dY = view.getY() - event.getRawY();
                            isDragging = false;
                            longPressed = false;
                            handler.postDelayed(longPressRunnable, 500);
                            return true;

                        case android.view.MotionEvent.ACTION_MOVE:
                            float currentX = event.getRawX();
                            float currentY = event.getRawY();
                            if (!isDragging && (Math.abs(currentX - startX) > DRAG_THRESHOLD || Math.abs(currentY - startY) > DRAG_THRESHOLD)) {
                                if (longPressed) {
                                    isDragging = true;
                                } else {
                                    handler.removeCallbacks(longPressRunnable);
                                }
                            }
                            if (isDragging) {
                                view.setX(currentX + dX);
                                view.setY(currentY + dY);
                            }
                            return true;

                        case android.view.MotionEvent.ACTION_UP:
                            handler.removeCallbacks(longPressRunnable);
                            if (!isDragging) {
                                float endX = event.getRawX();
                                float endY = event.getRawY();
                                if (Math.abs(endX - startX) < DRAG_THRESHOLD && Math.abs(endY - startY) < DRAG_THRESHOLD) {
                                    // 点击球，正式退出全屏模式
                                    toggleFullScreenMode();
                                }
                            }
                            return true;
                        case android.view.MotionEvent.ACTION_CANCEL:
                            handler.removeCallbacks(longPressRunnable);
                            return true;
                    }
                    return false;
                }
            });
        }
        
        // 下拉恢复顶栏和底栏
        if (swipeRefresh != null) {
            swipeRefresh.setOnChildScrollUpCallback((parent, child) -> {
                if (isFullScreenMode) {
                    topBar.getVisibility();
                }
                return child != null && child.canScrollVertically(-1);
            });
        }
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            drawerLayout.closeDrawer(GravityCompat.START);
            if (id == R.id.nav_home) {
                loadUrlInCurrentTab(Config.URL_BLANK);
            } else if (id == R.id.nav_history) {
                if (isProcessingAction) return false;
                android.content.Intent intent = new android.content.Intent(this, HistoryActivity.class);
                startActivity(intent);
            } else if (id == R.id.nav_settings) {
                if (isProcessingAction) return false;
                android.content.Intent intent = new android.content.Intent(this, SettingsActivity.class);
                startActivity(intent);
            } else if (id == R.id.nav_downloads) {
                if (isProcessingAction) return false;
                android.content.Intent intent = new android.content.Intent(this, DownloadsActivity.class);
                startActivity(intent);
            } else if (id == R.id.nav_add_bookmark) {
                showAddBookmarkDialog();
            } else if (id == R.id.nav_bookmarks) {
                if (isProcessingAction) return false;
                android.content.Intent intent = new android.content.Intent(this, BookmarkActivity.class);
                startActivity(intent);
            } else if (id == R.id.nav_desktop_mode) {
                toggleDesktopMode();
            } else if (id == R.id.nav_lord_mode) {
                toggleLordMode();
            } else if (id == R.id.nav_fullscreen) {
                toggleFullScreenMode();
            } else if (id == R.id.nav_extensions_action) {
                navigationView.postDelayed(this::showExtensionActionDialog, 250);
            } else if (id == R.id.nav_extensions_browse) {
                createNewTab("https://addons.mozilla.org/");
            } else if (id == R.id.nav_extensions_manager) {
                if (isProcessingAction) return false;
                android.content.Intent intent = new android.content.Intent(this, ExtensionManagerActivity.class);
                startActivity(intent);
            }
            if (isTabSwitcherVisible) toggleTabSwitcher();
            return true;
        });
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 1. 如果正在执行核心操作（如截图），忽略返回键以防止 Native 状态冲突导致崩溃
                if (isProcessingAction) return;

                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }
                if (isTabSwitcherVisible) {
                    toggleTabSwitcher();
                    return;
                }
                TabInfo currentTab = null;
                if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
                    currentTab = tabs.get(currentTabIndex);
                }

                // 2. 扩展页(moz-extension://)返回：直接关闭标签，避免引擎跨边界历史切换导致 libxul 崩溃
                if (currentTab != null && isExtensionPageUrl(currentTab.url)) {
                    closeExtensionTabAndSwitch();
                    return;
                }
                // 3. 页面内后退逻辑（后退前清除待执行的 JS 注入，防止与 goBack 并发操作导致 libxul 崩溃）
                if (currentTab != null && currentTab.session != null && currentTab.canGoBack) {
                    mainHandler.removeCallbacksAndMessages(currentTab.session);
                    currentTab.session.goBack();
                } 
                // 4. 页面无法后退时，如果不在主页，则先返回主页（响应用户要求返回主页的诉求）
                else if (currentTab != null && !Config.URL_BLANK.equals(currentTab.url)) {
                    loadUrlInCurrentTab(Config.URL_BLANK);
                } 
                // 5. 已在主页且无法后退，执行退出确认逻辑
                else {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastBackTime < 2000) {
                        finish();
                    } else {
                        lastBackTime = currentTime;
                        Toast.makeText(MainActivity.this, R.string.msg_exit_confirm, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        
        // 初始化桌面模式菜单项的状态
        if (navigationView != null) {
            android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean isDesktopMode = prefs.getBoolean(Config.PREF_KEY_DESKTOP_MODE, false);
            android.view.Menu menu = navigationView.getMenu();
            android.view.MenuItem desktopItem = menu.findItem(R.id.nav_desktop_mode);
            if (desktopItem != null) {
                desktopItem.setChecked(isDesktopMode);
                desktopItem.setTitle(isDesktopMode ? R.string.title_mobile_mode : R.string.title_desktop_mode);
            }
            android.view.MenuItem fsItem = menu.findItem(R.id.nav_fullscreen);
            if (fsItem != null) {
                fsItem.setChecked(isFullScreenMode);
            }
            updateLordModeMenuItem();
        }
    }
    private GeckoSession createNewTab(String url) {
        TabInfo info = new TabInfo(null);
        info.url = url;
        if (Config.URL_BLANK.equals(url)) {
            info.title = getString(R.string.title_new_tab);
        }
        tabs.add(info);
        initializeSessionForTab(info);
        if (tabSwitcherAdapter != null) {
            tabSwitcherAdapter.notifyItemInserted(tabs.size() - 1);
        }
        switchToTab(tabs.size() - 1);
        updateBottomTabCounter();
        return info.session;
    }
    private void updateTabInfo(GeckoSession session, String url, String title) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).session == session) {
                if (url != null) tabs.get(i).url = url;
                if (title != null) tabs.get(i).title = title;
                final int index = i;
                runOnUiThread(() -> {
                    if (tabSwitcherAdapter != null) {
                        tabSwitcherAdapter.notifyItemChanged(index);
                    }
                    if (index == currentTabIndex) {
                        updateUrlBar();
                        updateViewVisibility();
                    }
                });
                break;
            }
        }
    }

    /**
     * 重置标签页加载超时计时器
     *
     * @param tab 目标标签页
     * @param url 当前加载的 URL，用于排除本地资源
     */
    private void resetPageLoadTimeout(TabInfo tab, String url) {
        if (tab == null || tab.session == null) return;
        
        // 如果是本地资源页或扩展页，不设置超时
        if (url != null && (url.startsWith("resource://android/assets/") || url.startsWith("moz-extension://"))) {
            cancelPageLoadTimeout(tab);
            return;
        }

        synchronized (tab) {
            cancelPageLoadTimeout(tab);

            tab.timeoutRunnable = new Runnable() {
                @Override
                public void run() {
                    synchronized (tab) {
                        // 确保此任务仍然是该标签页的最新的任务
                        if (this != tab.timeoutRunnable) return;
                        
                        // 检查标签页是否还在列表中（防止已关闭的标签页弹出提示）
                        if (!tabs.contains(tab)) return;

                        // 如果超时后还在加载 (进度未达 100) 且 UI 指示正在加载
                        if (progressBar != null && progressBar.getVisibility() == View.VISIBLE && tab.lastProgress < 100) {
                            tab.session.stop();
                            String timeoutUrl = "resource://android/assets/html/timeout.html";
                            if (url != null && !url.isEmpty()) {
                                timeoutUrl += "?url=" + android.net.Uri.encode(url);
                            }
                            tab.session.loadUri(timeoutUrl);
                            Toast.makeText(MainActivity.this, R.string.msg_load_timeout, Toast.LENGTH_SHORT).show();
                        }
                        tab.timeoutRunnable = null;
                    }
                }
            };
            
            mainHandler.postDelayed(tab.timeoutRunnable, 20000); // 20秒后检查
        }
    }

    /**
     * 取消标签页的加载超时计时器
     * @param tab 目标标签页
     */
    private void cancelPageLoadTimeout(TabInfo tab) {
        if (tab != null) {
            synchronized (tab) {
                if (tab.timeoutRunnable != null) {
                    mainHandler.removeCallbacks(tab.timeoutRunnable);
                    tab.timeoutRunnable = null;
                }
            }
        }
    }

    /**
     * 执行智能刷新：如果当前是超时页面，尝试刷新原始 URL
     */
    private void performSmartRefresh() {
        if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) return;
        TabInfo currentTab = tabs.get(currentTabIndex);
        if (currentTab == null || currentTab.session == null) return;

        String currentUrl = currentTab.url;
        if (currentUrl != null && currentUrl.startsWith("resource://android/assets/html/timeout.html")) {
            android.net.Uri uri = android.net.Uri.parse(currentUrl);
            String originalUrl = uri.getQueryParameter("url");
            if (originalUrl != null && !originalUrl.isEmpty()) {
                currentTab.session.loadUri(originalUrl);
                return;
            }
        }
        currentTab.session.reload();
    }

    /**
     * 更新停止按钮的可见性及动画效果
     */
    private void updateStopButtonVisibility(boolean show) {
        if (btnStop == null) return;
        
        runOnUiThread(() -> {
            if (show) {
                if (btnStop.getVisibility() != View.VISIBLE) {
                    btnStop.setVisibility(View.VISIBLE);
                    btnStop.setAlpha(0f);
                    btnStop.setTranslationX(-20f);
                    btnStop.animate()
                           .alpha(1f)
                           .translationX(0f)
                           .setDuration(300)
                           .setInterpolator(new android.view.animation.OvershootInterpolator())
                           .start();
                }
            } else {
                if (btnStop.getVisibility() == View.VISIBLE) {
                    btnStop.animate()
                           .alpha(0f)
                           .translationX(50f)
                           .setDuration(300)
                           .withEndAction(() -> btnStop.setVisibility(View.GONE))
                           .start();
                }
            }
        });
    }
    private void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        if (isSwitchingTab) return;
        isSwitchingTab = true;
        
        try {
            if (index != tabs.size() - 1) {
                TabInfo t = tabs.remove(index);
                tabs.add(t);
                if (tabSwitcherAdapter != null) {
                    tabSwitcherAdapter.notifyItemMoved(index, tabs.size() - 1);
                    tabSwitcherAdapter.notifyItemRangeChanged(0, tabs.size());
                }
                index = tabs.size() - 1;
            }
            
            final int targetIndex = index;
            currentTabIndex = targetIndex;
            
            if (isTabSwitcherVisible) {
                toggleTabSwitcher();
            }
            
            TabInfo tab = tabs.get(targetIndex);
            if (tab.session != null) {
                if (geckoView.getSession() != tab.session) {
                    geckoView.setSession(tab.session);
                }
            }
            
            if (swipeRefresh != null) {
                swipeRefresh.setEnabled(tab.scrollY <= 0);
            }
            updateUrlBar();
            updateViewVisibility();
        } finally {
            isSwitchingTab = false;
        }
    }
    private void updateViewVisibility() {
        if (isTabSwitcherVisible) return;
        View layoutHome = findViewById(R.id.layout_home);
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
            TabInfo tab = tabs.get(currentTabIndex);
            boolean isHome = Config.URL_BLANK.equals(tab.url);
            if (isHome) {
                geckoView.setVisibility(View.GONE);
                layoutHome.setVisibility(View.VISIBLE);
                urlInput.setText("");
                // 主页强制显示 Bar，但不应该退出全屏模式的全域标志
                setBarsVisible(true);
            } else {
                geckoView.setVisibility(View.VISIBLE);
                layoutHome.setVisibility(View.GONE);
                // 仅在非主页时，根据全屏标志来决定。
                // 即使正在加载下一页，如果 mode 为 true，就该保持。
                if (isFullScreenMode) {
                    setBarsVisible(false);
                } else {
                    setBarsVisible(true);
                }
            }
        }
    }
    @SuppressLint("NotifyDataSetChanged")
    private void closeTab(int position) {
        if (position < 0 || position >= tabs.size()) return;
        TabInfo tab = tabs.get(position);
        if (tab.session != null) {
            cancelPageLoadTimeout(tab);
            // 关闭标签前，清除该 session 所有待处理的 Handler 回调 (如 JS 摘要提取)，防止 native 崩溃
            mainHandler.removeCallbacksAndMessages(tab.session);
            tab.session.close();
        }
        if (tab.thumbnail != null && !tab.thumbnail.isRecycled()) {
            tab.thumbnail.recycle();
            tab.thumbnail = null;
        }
        tabs.remove(position);
        
        if (tabs.isEmpty()) {
            if (currentTabGroup != null) {
                TabStorage.deleteGroup(this, currentTabGroup);
                Toast.makeText(this, getString(R.string.msg_group_closed, currentTabGroup), Toast.LENGTH_SHORT).show();
                currentTabGroup = null;
                
                List<TabInfo> savedTabs = TabStorage.loadTabs(this, null);
                if (savedTabs != null && !savedTabs.isEmpty()) {
                    tabs.addAll(savedTabs);
                    for (TabInfo t : tabs) {
                        initializeSessionForTab(t);
                    }
                    if (tabSwitcherAdapter != null) {
                        tabSwitcherAdapter.notifyDataSetChanged();
                    }
                    switchToTab(tabs.size() - 1);
                } else {
                    createNewTab(Config.URL_BLANK);
                }
            } else {
                createNewTab(Config.URL_BLANK);
            }
            if (tabSwitcherAdapter != null) {
                tabSwitcherAdapter.notifyDataSetChanged();
            }
        } else {
            int newFocusIndex = position;
            if (newFocusIndex >= tabs.size()) {
                newFocusIndex = tabs.size() - 1;
            }
            currentTabIndex = newFocusIndex;
            final int targetIndex = newFocusIndex;
            
            // 当标签页数量较少时（<=2），使用notifyDataSetChanged确保布局完全重建
            // 避免notifyItemRemoved导致的布局状态不一致问题
            if (tabs.size() <= 2) {
                if (tabSwitcherAdapter != null) {
                    tabSwitcherAdapter.notifyDataSetChanged();
                }
                // 等待布局完全重建后再滚动到目标位置
                tabSwitcher.post(() -> {
                    tabSwitcher.post(() -> {
                        if (tabSwitcher.getLayoutManager() != null) {
                            tabSwitcher.smoothScrollToPosition(targetIndex);
                            tabSwitcher.postDelayed(() -> {
                                updateTabAnimations();
                                updateTabCount();
                            }, 100);
                        }
                    });
                });
            } else {
                // 标签页数量较多时，使用增量更新提高性能
                if (tabSwitcherAdapter != null) {
                    tabSwitcherAdapter.notifyItemRemoved(position);
                    tabSwitcherAdapter.notifyItemRangeChanged(position, tabs.size() - position);
                }
                // 使用多次post确保布局完全更新后再滚动
                tabSwitcher.post(() -> {
                    if (tabSwitcher.getLayoutManager() != null) {
                        tabSwitcher.post(() -> {
                            tabSwitcher.smoothScrollToPosition(targetIndex);
                            tabSwitcher.postDelayed(() -> {
                                updateTabAnimations();
                                updateTabCount();
                            }, 50);
                        });
                    }
                });
            }
        }
        updateTabCount();
        updateBottomTabCounter();
    }

    /**
     * 关闭当前扩展标签页(moz-extension://)并切换显示到新焦点标签。
     * 扩展页 goBack 会触发引擎跨边界历史切换导致 libxul 崩溃，因此返回操作改为关闭标签。
     * closeTab 仅更新 currentTabIndex 不切换显示，这里补一次 switchToTab 确保 geckoView 绑定正确 session。
     */
    private void closeExtensionTabAndSwitch() {
        if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) return;
        int closingIndex = currentTabIndex;
        closeTab(closingIndex);
        // closeTab 后 currentTabIndex 已指向新焦点标签；若还有标签则切换显示
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
            switchToTab(currentTabIndex);
        }
    }

    private void updateTabAnimations() {
        if (tabSwitcher.getChildCount() == 0) return;
        float centerX = tabSwitcher.getWidth() / 2f;
        for (int i = 0; i < tabSwitcher.getChildCount(); i++) {
            View itemView = tabSwitcher.getChildAt(i);
            int adapterPos = tabSwitcher.getChildAdapterPosition(itemView);
            if (adapterPos == RecyclerView.NO_POSITION) continue;
            View cardView = itemView.findViewById(R.id.tab_card_view);
            if (cardView != null) {
                itemView.setTranslationZ(adapterPos * 10f);
                float childCenterX = (itemView.getLeft() + itemView.getRight()) / 2f;
                float distFromCenter = Math.abs(centerX - childCenterX);
                float maxDist = centerX * 1.5f;
                float fraction = Math.min(1f, distFromCenter / maxDist);
                float minScale = 0.85f;
                float scale = 1f - (1f - minScale) * fraction;
                cardView.setScaleX(scale);
                cardView.setScaleY(scale);
                float direction = (childCenterX < centerX) ? 1f : -1f;
                float squeezeFactor = (float) Math.pow(fraction, 4.0);
                float cardWidth = cardView.getWidth();
                if (cardWidth <= 0) {
                    float screenRatio = (float) getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().heightPixels;
                    int cHeight = getResources().getDisplayMetrics().heightPixels - (int) (64 * getResources().getDisplayMetrics().density);
                    cardWidth = (int) (cHeight * screenRatio);
                    int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.85f);
                    if (cardWidth > maxW) cardWidth = maxW;
                }
                float maxSqueeze = cardWidth * 0.60f;
                cardView.setTranslationX(direction * maxSqueeze * squeezeFactor);
            }
        }
    }
    private void showTabManagerOptionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String title = currentTabGroup != null ? currentTabGroup : getString(R.string.default_group_name);
        builder.setTitle(title);
        
        List<CharSequence> options = new ArrayList<>();
        options.add(getString(R.string.action_close_all_tabs_in_group));
        options.add(getString(R.string.action_save_to_new_group));
        
        List<String> groups = TabStorage.getGroups(this);
        int[] groupColors = {0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFDD835, 0xFF8E24AA, 0xFFF4511E, 0xFF00ACC1};
        int colorIdx = 0;
        
        for (String g : groups) {
            String mark = (currentTabGroup != null && currentTabGroup.equals(g)) ? " *" : "";
            String fullStr = getString(R.string.action_switch_to_group, g) + mark;
            android.text.SpannableString sp = new android.text.SpannableString(fullStr);
            sp.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, fullStr.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new android.text.style.ForegroundColorSpan(groupColors[colorIdx % groupColors.length]), 0, fullStr.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            options.add(sp);
            colorIdx++;
        }
        if (currentTabGroup != null) {
            options.add(getString(R.string.action_return_to_default_group));
        }
        
        builder.setItems(options.toArray(new CharSequence[0]), (dialog, which) -> {
            if (which == 0) {
                showCloseAllTabsConfirmDialog();
            } else if (which == 1) {
                showSaveToNewGroupDialog();
            } else {
                int groupIndex = which - 2;
                if (groupIndex < groups.size()) {
                    switchToGroup(groups.get(groupIndex));
                } else {
                    switchToGroup(null);
                }
            }
        });
        
        builder.show();
    }
    
    private void showSaveToNewGroupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_new_tab_group);
        
        final EditText input = new EditText(this);
        input.setHint(R.string.hint_tab_group_name);
        builder.setView(input);
        
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            String newGroup = input.getText().toString().trim();
            if (!newGroup.isEmpty()) {

                TabStorage.saveTabs(this, newGroup, new ArrayList<>(tabs));

                TabStorage.saveTabs(this, currentTabGroup, new ArrayList<>());

                currentTabGroup = newGroup;
                
                Toast.makeText(this, R.string.msg_tab_group_saved, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void switchToGroup(String newGroup) {
        TabStorage.saveTabs(this, currentTabGroup, new ArrayList<>(tabs));
        
        for (TabInfo tab : tabs) {
            if (tab.session != null) {
                tab.session.close();
            }
        }
        tabs.clear();
        currentTabIndex = -1;
        
        currentTabGroup = newGroup;
        
        List<TabInfo> savedTabs = TabStorage.loadTabs(this, currentTabGroup);
        if (savedTabs != null && !savedTabs.isEmpty()) {
            tabs.addAll(savedTabs);
            for (TabInfo tab : tabs) {
                initializeSessionForTab(tab);
            }
            if (tabSwitcherAdapter != null) {
                tabSwitcherAdapter.notifyDataSetChanged();
            }
            switchToTab(tabs.size() - 1);
            updateBottomTabCounter();
        } else {
            createNewTab(Config.URL_BLANK);
        }
        
        if (isTabSwitcherVisible) toggleTabSwitcher();
    }
    
    private void showCloseAllTabsConfirmDialog() {
        new AlertDialog.Builder(this)
            .setMessage(R.string.msg_confirm_close_all_tabs)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                closeAllTabs();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }
    
    private void closeAllTabs() {
        if (tabs == null || tabs.isEmpty()) {
            return;
        }
        
        // 关闭所有标签页的session
        for (TabInfo tab : tabs) {
            if (tab.session != null) {
                mainHandler.removeCallbacksAndMessages(tab.session);
                tab.session.close();
            }
        }
        
        // 清空标签页列表
        tabs.clear();
        currentTabIndex = -1;
        
        if (currentTabGroup != null) {
            TabStorage.deleteGroup(this, currentTabGroup);
            Toast.makeText(this, getString(R.string.msg_group_closed, currentTabGroup), Toast.LENGTH_SHORT).show();
            currentTabGroup = null;
            
            List<TabInfo> savedTabs = TabStorage.loadTabs(this, null);
            if (savedTabs != null && !savedTabs.isEmpty()) {
                tabs.addAll(savedTabs);
                for (TabInfo t : tabs) {
                    initializeSessionForTab(t);
                }
                if (tabSwitcherAdapter != null) {
                    tabSwitcherAdapter.notifyDataSetChanged();
                }
                switchToTab(tabs.size() - 1);
            } else {
                createNewTab(Config.URL_BLANK);
            }
        } else {
            // 保存空的标签页状态（传递副本以确保线程安全）
            TabStorage.saveTabs(this, currentTabGroup, new ArrayList<>(tabs));
            // 创建一个新的空白标签页
            createNewTab(Config.URL_BLANK);
            Toast.makeText(this, R.string.msg_all_tabs_closed, Toast.LENGTH_SHORT).show();
        }
        
        updateBottomTabCounter();
        
        // 如果当前在标签页切换器界面，关闭它
        if (isTabSwitcherVisible) {
            toggleTabSwitcher();
        }
    }
    private void toggleTabSwitcher() {
        if (isProcessingAction) return;
        if (isTabSwitcherVisible) {
            performToggleAnimation(false);
            return;
        }
        isProcessingAction = true;
        captureScreenshot(() -> {
            performToggleAnimation(true);
            isProcessingAction = false;
        });
    }
    @SuppressLint("NotifyDataSetChanged")
    private void performToggleAnimation(boolean showSwitcher) {
        isTabSwitcherVisible = showSwitcher;
        ImageButton btnAddTab = findViewById(R.id.btn_add_tab);
        ImageButton btnMenu = findViewById(R.id.btn_menu);
        TextView tvTabCount = findViewById(R.id.tv_tab_count);
        DynamicBackgroundView dynamicBgView = findViewById(R.id.dynamic_background_view);
        if (isTabSwitcherVisible) {
            refreshBackgroundEffect();
            if (dynamicBgView != null) dynamicBgView.setVisibility(View.VISIBLE);
            tabSwitcher.setAlpha(0f);
            tabSwitcher.setVisibility(View.VISIBLE);
            geckoView.setVisibility(View.GONE);
            findViewById(R.id.layout_home).setVisibility(View.GONE);
            topBar.setVisibility(View.GONE);
            btnAddTab.setVisibility(View.VISIBLE);
            btnMenu.setVisibility(View.GONE);
            tvTabCount.setVisibility(View.VISIBLE);
            if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
                tabSwitcher.post(() -> {
                    if (tabSwitcherAdapter != null) {
                        tabSwitcherAdapter.notifyDataSetChanged();
                    }
                    if (tabSwitcher.getLayoutManager() != null) {
                        // 使用smoothScrollToPosition确保正确居中，修复标签页无法居中的BUG
                        tabSwitcher.smoothScrollToPosition(currentTabIndex);
                    }
                    tabSwitcher.postDelayed(() -> {
                         updateTabAnimations();
                         updateTabCount();
                         tabSwitcher.animate()
                             .alpha(1f)
                             .setDuration(200)
                             .start();
                    }, 100);
                });
            } else {
                 tabSwitcher.animate().alpha(1f).setDuration(200).start();
            }
        } else {
            if (dynamicBgView != null) dynamicBgView.setVisibility(View.GONE);
            tabSwitcher.setVisibility(View.GONE);
            tabSwitcher.setAlpha(1f);
            btnAddTab.setVisibility(View.GONE);
            btnMenu.setVisibility(View.VISIBLE);
            tvTabCount.setVisibility(View.GONE);
            if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
                switchToTab(currentTabIndex);
                TabInfo currentTab = tabs.get(currentTabIndex);
                boolean isHome = Config.URL_BLANK.equals(currentTab.url);
                if (isHome) {
                    View layoutHome = findViewById(R.id.layout_home);
                    geckoView.setVisibility(View.GONE);
                    layoutHome.setVisibility(View.VISIBLE);
                    layoutHome.setAlpha(1f);
                    updateViewVisibility();
                } else {
                    findViewById(R.id.layout_home).setVisibility(View.GONE);
                    geckoView.setVisibility(View.VISIBLE);
                    geckoView.setAlpha(1f);
                }
            } else {
                geckoView.setVisibility(View.VISIBLE);
                geckoView.setAlpha(1f);
            }
            updateViewVisibility();
        }
    }
    private void updateBottomTabCounter() {
        TextView tvBtnTabCount = findViewById(R.id.tv_btn_tab_count);
        if (tvBtnTabCount != null && tabs != null) {
            tvBtnTabCount.setText(String.valueOf(tabs.size()));
        }
    }
    private void captureScreenshot(Runnable onComplete) {
        if (isFinishing() || isDestroyed()) return;
        
        if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        
        TabInfo tab = tabs.get(currentTabIndex);
        
        // 逻辑A：非主页（GeckoView 渲染内容）的截图
        if (!Config.URL_BLANK.equals(tab.url) && geckoView.getVisibility() == View.VISIBLE && geckoView.getWidth() > 0 && geckoView.getHeight() > 0) {
            try {
                int viewWidth = geckoView.getWidth();
                int viewHeight = geckoView.getHeight();
                
                // 内存优化：1/3 比例
                int targetWidth = Math.max(1, viewWidth / 3);
                int targetHeight = Math.max(1, viewHeight / 3);
                
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                        targetWidth, targetHeight, android.graphics.Bitmap.Config.ARGB_8888);
                
                android.view.SurfaceView surfaceView = findSurfaceView(geckoView);
                if (surfaceView != null) {
                    android.view.PixelCopy.request(surfaceView, bitmap, copyResult -> {
                        if (copyResult == android.view.PixelCopy.SUCCESS) {
                            validateAndSetThumbnail(tab, bitmap);
                        } else {
                            bitmap.recycle();
                        }
                        handleScreenshotResult(onComplete);
                    }, new android.os.Handler(android.os.Looper.getMainLooper()));
                    return; // 异步回调执行
                } else {
                    int[] location = new int[2];
                    geckoView.getLocationInWindow(location);
                    android.view.PixelCopy.request(getWindow(),
                        new android.graphics.Rect(
                            location[0],
                            location[1],
                            location[0] + viewWidth,
                            location[1] + viewHeight),
                        bitmap,
                        copyResult -> {
                            if (copyResult == android.view.PixelCopy.SUCCESS) {
                                validateAndSetThumbnail(tab, bitmap);
                            } else {
                                bitmap.recycle();
                            }
                            handleScreenshotResult(onComplete);
                        },
                        new android.os.Handler(android.os.Looper.getMainLooper())
                    );
                    return; // 异步回调执行
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } 
        // 逻辑B：主页（空白页）布局截图
        else if (Config.URL_BLANK.equals(tab.url)) {
            View layoutHome = findViewById(R.id.layout_home);
            if (layoutHome != null && layoutHome.getVisibility() == View.VISIBLE && layoutHome.getWidth() > 0) {
                try {
                    int w = Math.max(1, layoutHome.getWidth() / 3);
                    int h = Math.max(1, layoutHome.getHeight() / 3);
                    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                            w, h, android.graphics.Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                    canvas.scale(1/3f, 1/3f);
                    layoutHome.draw(canvas);
                    validateAndSetThumbnail(tab, bitmap);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        
        // 如果是主页截图或之前逻辑走到了同步结束点
        if (onComplete != null) onComplete.run();
    }
    private void captureScreenshot() {
        captureScreenshot(null);
    }
    private void loadUrlInCurrentTab(String url) {
        if (isProcessingAction) return; 
        if (getCurrentSession() != null) {
            getCurrentSession().loadUri(url);
        }
    }
    private void updateUrlBar() {
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
            TabInfo tab = tabs.get(currentTabIndex);
            urlInput.setText(tab.url);
        }
    }
    private GeckoSession getCurrentSession() {
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
            return tabs.get(currentTabIndex).session;
        }
        return null;
    }
    
    private TabInfo getCurrentTab() {
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
            return tabs.get(currentTabIndex);
        }
        return null;
    }

    /** 当前标签页的 URL */
    private String getCurrentTabUrl() {
        TabInfo tab = getCurrentTab();
        return tab != null ? tab.url : null;
    }

    /** 是否为扩展页面 URL(moz-extension://)。此类页面 goBack 会触发引擎跨边界历史切换,导致 libxul 崩溃 */
    private boolean isExtensionPageUrl(String url) {
        return url != null && url.startsWith("moz-extension://");
    }
    private void updateTabCount() {
        TextView tvTabCount = findViewById(R.id.tv_tab_count);
        if (tvTabCount != null && tabs != null) {
            int total = tabs.size();
            int current = 0;
            RecyclerView.LayoutManager layoutManager = tabSwitcher.getLayoutManager();
            if (layoutManager instanceof androidx.recyclerview.widget.LinearLayoutManager) {
                current = ((androidx.recyclerview.widget.LinearLayoutManager) layoutManager).findFirstCompletelyVisibleItemPosition();
                if (current == RecyclerView.NO_POSITION) {
                     current = ((androidx.recyclerview.widget.LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
                }
            }
            current = Math.max(0, current) + 1;
            if (current > total) current = total;
            tvTabCount.setText(getString(R.string.tab_count_template, current, total));
        }
    }
    private android.view.SurfaceView findSurfaceView(View view) {
        if (view instanceof android.view.SurfaceView) {
            return (android.view.SurfaceView) view;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.view.SurfaceView result = findSurfaceView(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }
    private void handleScreenshotResult(Runnable onComplete) {
        runOnUiThread(() -> {
            if (tabSwitcherAdapter != null) {
                tabSwitcherAdapter.notifyItemChanged(currentTabIndex);
            }
            if (onComplete != null) onComplete.run();
        });
    }
    private void validateAndSetThumbnail(TabInfo tab, android.graphics.Bitmap bitmap) {
        if (bitmap == null) return;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] points = {
            bitmap.getPixel(w / 2, h / 2),      
            bitmap.getPixel(10, 10),            
            bitmap.getPixel(w - 10, 10),        
            bitmap.getPixel(10, h - 10),        
            bitmap.getPixel(w - 10, h - 10)     
        };
        boolean allWhite = true;
        for (int p : points) {
            if (p != 0xFFFFFFFF) {
                allWhite = false;
                break;
            }
        }
        if (allWhite && tab.thumbnail != null) {
            return;
        }
        tab.thumbnail = bitmap;
    }

    private void applyThemeToSession(GeckoSession session) {
        if (sRuntime == null || session == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME_THEME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(Config.PREF_KEY_DARK_MODE, false);
        sRuntime.getSettings().setPreferredColorScheme(isDarkMode ? 
            GeckoRuntimeSettings.COLOR_SCHEME_DARK : GeckoRuntimeSettings.COLOR_SCHEME_LIGHT);
        
        // 应用桌面模式设置
        android.content.SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isDesktopMode = defaultPrefs.getBoolean(Config.PREF_KEY_DESKTOP_MODE, false);
        
        if (isDesktopMode) {
            session.getSettings().setUserAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP);
            session.getSettings().setViewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP);
        } else {
            session.getSettings().setUserAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
            session.getSettings().setViewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        }

        session.getSettings().setUseTrackingProtection(true);
    }
     private void showWebContextMenu(ContextElement element) {
        final List<String> items = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>(); // 0: 新标签页, 1: 下载, 2: 下载链接, 3: 复制链接, 4: 复制图片地址, 5: 阅读模式, 6: 复制文本
        String url = element.linkUri != null ? element.linkUri : element.srcUri;
        
        if (url != null) {
            items.add(getString(R.string.action_open_new_tab));
            actions.add(0);
        }
        boolean isLink = element.linkUri != null;
        if (isLink) {
            items.add(getString(R.string.action_copy_link));
            actions.add(3);
            items.add(getString(R.string.action_copy_text));
            actions.add(6);
            String lowerLink = element.linkUri.toLowerCase();
            if (lowerLink.matches(".*\\.(mp4|mkv|webm|avi|mov|3gp|mp3|wav|ogg|m4a|aac|flac|jpg|jpeg|png|gif|webp|bmp|svg)$")) {
                items.add(getString(R.string.action_download_link));
                actions.add(2); 
            }
        }
        boolean isMedia = (element.type == ContextElement.TYPE_IMAGE || 
                          element.type == ContextElement.TYPE_VIDEO || 
                          element.type == ContextElement.TYPE_AUDIO);
        
        String downloadUrl = element.srcUri != null ? element.srcUri : element.linkUri;
        if (isMedia && downloadUrl != null) {
            items.add(getString(R.string.action_download));
            actions.add(1);
            items.add(getString(R.string.action_copy_image_link));
            actions.add(4);
        }

        if (element.type == ContextElement.TYPE_IMAGE && element.srcUri != null) {
            items.add(getString(R.string.action_scan_qr_code));
            actions.add(7);
        }
        
        items.add(getString(R.string.action_reader_mode));
        actions.add(5);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(url != null ? url : getString(R.string.app_name))
            .setItems(items.toArray(new String[0]), (dialog, which) -> {
                int action = actions.get(which);
                if (action == 0) {
                    createNewTab(url);
                } else if (action == 1) {
                    startManualDownload(downloadUrl);
                } else if (action == 2) {
                    startManualDownload(element.linkUri);
                } else if (action == 3) {
                    copyToClipboard(element.linkUri);
                } else if (action == 4) {
                    copyToClipboard(element.srcUri);
                } else if (action == 5) {
                    enableReaderMode();
                } else if (action == 6) {
                    copyLinkTextAtPoint();
                } else if (action == 7) {
                    handleScanQrCode(element.srcUri);
                }
            })
            .show();
    }

    private void handleScanQrCode(String imageUrl) {
        if (imageUrl == null) return;
        
        Toast.makeText(this, R.string.msg_scanning, Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(imageUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
                        .build();

                try (Response response = com.olsc.manorbrowser.utils.BrowserDownloader.getClient().newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        try (InputStream is = response.body().byteStream()) {
                            android.graphics.Bitmap bitmap = BitmapFactory.decodeStream(is);
                            String[] results = QrCodeHelper.scanMultiQrCodes(bitmap);
                            
                            runOnUiThread(() -> {
                                if (results != null && results.length > 0) {
                                    if (results.length == 1) {
                                        showQrResultDialog(results[0]);
                                    } else {
                                        showMultiQrResultDialog(results);
                                    }
                                } else {
                                    Toast.makeText(MainActivity.this, R.string.msg_no_qr_code_found, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, R.string.msg_load_error, Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, R.string.msg_load_error, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showMultiQrResultDialog(String[] results) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.msg_multi_qr_codes_found)
                .setItems(results, (dialog, which) -> {
                    showQrResultDialog(results[which]);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showQrResultDialog(String result) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.msg_qr_code_found)
                .setMessage(result)
                .setPositiveButton(android.R.string.copy, (dialog, which) -> copyToClipboard(result))
                .setNegativeButton(android.R.string.cancel, null);

        String trimmed = result.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            builder.setNeutralButton(R.string.action_open_url, (dialog, which) -> loadUrlInCurrentTab(trimmed));
        }

        builder.show();
    }

    private void copyLinkTextAtPoint() {
        GeckoSession session = getCurrentSession();
        if (session != null) {
            int[] location = new int[2];
            geckoView.getLocationOnScreen(location);
            int x = lastX - location[0];
            int y = lastY - location[1];
            String script = "javascript:(function(){" +
                "var el = document.elementFromPoint(" + x + ", " + y + ");" +
                "while(el && el.tagName !== 'A' && el.parentElement) el = el.parentElement;" +
                "if(el) { alert('COPY_TEXT:' + el.innerText); }" +
                "})()";
            session.loadUri(script);
        }
    }
    private void enableFreeCopyMode() {
        GeckoSession session = getCurrentSession();
        if (session != null) {
            int[] location = new int[2];
            geckoView.getLocationOnScreen(location);
            int x = lastX - location[0];
            int y = lastY - location[1];
            String script = "javascript:(function(){" +
                "var style = document.createElement('style');" +
                "style.innerHTML = '* { user-select: text !important; -webkit-user-select: text !important; }';" +
                "document.head.appendChild(style);" +
                "var el = document.elementFromPoint(" + x + ", " + y + ");" +
                "if(el) {" +
                "  var range = document.createRange();" +
                "  range.selectNodeContents(el);" +
                "  var sel = window.getSelection();" +
                "  sel.removeAllRanges();" +
                "  sel.addRange(range);" +
                "}" +
                "})()";
            session.loadUri(script);
            Toast.makeText(this, R.string.msg_free_copy_on, Toast.LENGTH_LONG).show();
        }
    }
    private void copyToClipboard(String text) {
        if (text == null) return;
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Copied Text", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.msg_copied, Toast.LENGTH_SHORT).show();
        }
    }
    private void startManualDownload(String url) {
        GeckoSession session = getCurrentSession();
        if (session == null) return;
        String ua = session.getSettings().getUserAgentOverride();
        if (ua == null || ua.isEmpty()) {
            ua = Config.DEFAULT_UA;
        }
        final String userAgentFinal = ua;
        String referer = null;
        for (TabInfo t : tabs) {
            if (t.session == session) {
                referer = t.url;
                break;
            }
        }
        final String refererFinal = referer;
        final String cookie = android.webkit.CookieManager.getInstance().getCookie(url);
        com.olsc.manorbrowser.utils.DownloadHelper.startDownload(
            this, 
            url, 
            userAgentFinal, 
            null, 
            null, 
            cookie, 
            refererFinal
        );
    }
    private void toggleTheme() {
        android.content.SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME_THEME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(Config.PREF_KEY_DARK_MODE, false);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Config.PREF_KEY_DARK_MODE, !isDarkMode);
        editor.apply();

        AppCompatDelegate.setDefaultNightMode(!isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        @SuppressLint("UnsafeIntentLaunch") android.content.Intent intent = getIntent();
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION);
        finish();
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0);
        } else {
            overridePendingTransition(0, 0);
        }
        startActivity(intent);
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out);
        } else {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }
    
    private void toggleFullScreenMode() {
        isFullScreenMode = !isFullScreenMode;
        if (isFullScreenMode) {
            TabInfo currentTab = getCurrentTab();
            if (currentTab != null && Config.URL_BLANK.equals(currentTab.url)) {
                isFullScreenMode = false;
                Toast.makeText(this, "主页模式下不开启全屏隐藏", Toast.LENGTH_SHORT).show();
            } else {
                setBarsVisible(false);
            }
        } else {
            setBarsVisible(true);
        }
        
        if (navigationView != null) {
            android.view.Menu menu = navigationView.getMenu();
            android.view.MenuItem fsItem = menu.findItem(R.id.nav_fullscreen);
            if (fsItem != null) {
                fsItem.setChecked(isFullScreenMode);
            }
        }
    }

    public void setBarsVisible(boolean visible) {
        if (topBar == null || bottomBar == null) return;
        
        // 如果 AI 遮罩正在显示，强制隐藏导航栏和搜索栏，防止在 AI 执行操作（如导航）时被强行显示出来
        View aiOverlay = findViewById(R.id.ai_overlay_include);
        if (aiOverlay != null && aiOverlay.getVisibility() == View.VISIBLE && visible) {
            visible = false;
        }

        // 如果状态没变，且不是正在强制刷新，则跳过以减少不必要的动画同步
        if (topBar.getVisibility() == (visible ? View.VISIBLE : View.GONE) &&
            bottomBar.getVisibility() == (visible ? View.VISIBLE : View.GONE) &&
            (fullscreenMenuContainer == null || fullscreenMenuContainer.getVisibility() == (!visible && isFullScreenMode ? View.VISIBLE : View.GONE))) {
            return;
        }

        android.view.ViewGroup root = findViewById(R.id.drawer_layout);
        // 使用简单的 Transition 动画
        android.transition.TransitionManager.beginDelayedTransition(root);

        topBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        
        // 手动请求应用 Insets，确保内容容器的 Padding 能够根据顶栏可见性及时更新，
        // 从而消除“网页与顶栏之间莫名多出一块空间”的问题。
        androidx.core.view.ViewCompat.requestApplyInsets(root);

        if (fullscreenMenuContainer != null) {
            fullscreenMenuContainer.setVisibility(!visible && isFullScreenMode ? View.VISIBLE : View.GONE);
        }

        // 重新请求 Insets 以触发 OnApplyWindowInsetsListener 中的 padding 逻辑
        ViewCompat.requestApplyInsets(root);
        
        // 强制刷新一次 padding，防止 Insets 监听器响应延迟导致闪烁或重叠
        if (contentContainer != null) {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
            if (insets != null) {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                if (!visible) {
                    contentContainer.setPadding(0, systemBars.top, 0, 0);
                } else {
                    contentContainer.setPadding(0, 0, 0, 0);
                }
            }
        }
    }
    
    private void toggleDesktopMode() {
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isDesktopMode = prefs.getBoolean(Config.PREF_KEY_DESKTOP_MODE, false);
        prefs.edit().putBoolean(Config.PREF_KEY_DESKTOP_MODE, !isDesktopMode).apply();
        
        // 更新所有session的桌面模式设置
        for (TabInfo tab : tabs) {
            if (tab.session != null) {
                applyThemeToSession(tab.session);
            }
        }
        
        // 刷新当前页面以应用新的User-Agent
        GeckoSession currentSession = getCurrentSession();
        if (currentSession != null) {
            currentSession.reload();
        }
        
        // 更新菜单项的状态
        if (navigationView != null) {
            android.view.Menu menu = navigationView.getMenu();
            android.view.MenuItem desktopItem = menu.findItem(R.id.nav_desktop_mode);
            if (desktopItem != null) {
                boolean newMode = !isDesktopMode;
                desktopItem.setChecked(newMode);
                desktopItem.setTitle(newMode ? R.string.title_mobile_mode : R.string.title_desktop_mode);
            }
        }
        
        int messageId = !isDesktopMode ? R.string.msg_desktop_mode_on : R.string.msg_desktop_mode_off;
        Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show();
    }
    
    private void refreshBackgroundEffect() {
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String effect = prefs.getString(Config.PREF_KEY_BG_EFFECT, Config.BG_EFFECT_METEOR);
        DynamicBackgroundView dynamicBgView = findViewById(R.id.dynamic_background_view);
        if (dynamicBgView != null) {
            switch (effect) {
                case Config.BG_EFFECT_RAIN:
                    dynamicBgView.setMode(DynamicBackgroundView.EffectMode.RAIN);
                    break;
                case Config.BG_EFFECT_SNOW:
                    dynamicBgView.setMode(DynamicBackgroundView.EffectMode.SNOW);
                    break;
                case Config.BG_EFFECT_AURORA:
                    dynamicBgView.setMode(DynamicBackgroundView.EffectMode.AURORA);
                    break;
                case Config.BG_EFFECT_SAKURA:
                    dynamicBgView.setMode(DynamicBackgroundView.EffectMode.SAKURA);
                    break;
                case Config.BG_EFFECT_IMAGE:
                    String imagePath = prefs.getString(Config.PREF_KEY_CUSTOM_BG_IMAGE, null);
                    dynamicBgView.setCustomImagePath(imagePath);
                    dynamicBgView.setMode(DynamicBackgroundView.EffectMode.IMAGE);
                    break;
                case Config.BG_EFFECT_SOLID:
                    dynamicBgView.setMode(DynamicBackgroundView.EffectMode.SOLID);
                    int solidColor = prefs.getInt(Config.PREF_KEY_SOLID_BG_COLOR, Config.DEFAULT_SOLID_BG_COLOR);
                    if (dynamicBgView.getChildCount() > 0) {
                        dynamicBgView.getChildAt(0).setBackgroundColor(solidColor);
                    }
                    break;
                default:
                    dynamicBgView.setMode(DynamicBackgroundView.EffectMode.METEOR);
                    break;
            }
        }
    }
    private void showAddBookmarkDialog() {
        TabInfo currentTab = null;
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
            currentTab = tabs.get(currentTabIndex);
        }
        final String title = (currentTab != null && currentTab.title != null) ? currentTab.title : "";
        final String url = (currentTab != null && currentTab.url != null) ? currentTab.url : "";
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);
        final android.widget.EditText etTitle = new android.widget.EditText(this);
        etTitle.setHint(R.string.label_bookmark_title);
        etTitle.setText(title);
        layout.addView(etTitle);
        final android.widget.EditText etUrl = new android.widget.EditText(this);
        etUrl.setHint(R.string.label_bookmark_url);
        etUrl.setText(url);
        layout.addView(etUrl);
        final android.widget.TextView tvFolderLabel = new android.widget.TextView(this);
        tvFolderLabel.setText(R.string.label_select_folder);
        tvFolderLabel.setPadding(0, 24, 0, 8);
        layout.addView(tvFolderLabel);
        final android.widget.Spinner spinnerFolder = new android.widget.Spinner(this);
        layout.addView(spinnerFolder);
        
        final List<com.olsc.manorbrowser.data.BookmarkItem> folders = com.olsc.manorbrowser.data.BookmarkStorage.getAllFolders(this);
        com.olsc.manorbrowser.data.BookmarkItem root = new com.olsc.manorbrowser.data.BookmarkItem(getString(R.string.root_folder));
        root.id = -1;
        folders.add(0, root);
        
        final java.util.List<String> folderNames = new java.util.ArrayList<>();
        for(com.olsc.manorbrowser.data.BookmarkItem f : folders) {
            folderNames.add(f.title);
        }
        
        final android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, folderNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFolder.setAdapter(adapter);
        final com.google.android.material.button.MaterialButton btnNewFolder = new com.google.android.material.button.MaterialButton(this);
        btnNewFolder.setText(R.string.title_new_folder);
        btnNewFolder.setAllCaps(false);
        btnNewFolder.setCornerRadius(24);
        btnNewFolder.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            androidx.core.content.ContextCompat.getColor(this, com.olsc.manorbrowser.R.color.purple_500)));
        btnNewFolder.setTextColor(android.graphics.Color.WHITE);
        android.view.ViewGroup.MarginLayoutParams folderBtnLp = new android.view.ViewGroup.MarginLayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        folderBtnLp.topMargin = (int)(12 * getResources().getDisplayMetrics().density);
        btnNewFolder.setLayoutParams(folderBtnLp);
        layout.addView(btnNewFolder);
        
        btnNewFolder.setOnClickListener(v -> {
            android.widget.EditText input = new android.widget.EditText(this);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_new_folder)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String newFolderTitle = input.getText().toString();
                    if(!newFolderTitle.isEmpty()) {
                        com.olsc.manorbrowser.data.BookmarkItem newFolder = new com.olsc.manorbrowser.data.BookmarkItem(newFolderTitle);
                        com.olsc.manorbrowser.data.BookmarkStorage.addBookmark(this, newFolder);
                        
                        folders.clear();
                        folders.addAll(com.olsc.manorbrowser.data.BookmarkStorage.getAllFolders(this));
                        folders.add(0, root);
                        
                        folderNames.clear();
                        for(com.olsc.manorbrowser.data.BookmarkItem f : folders) {
                            folderNames.add(f.title);
                        }
                        adapter.notifyDataSetChanged();
                        
                        for(int i=0; i<folders.size(); i++) {
                            if(folders.get(i).id == newFolder.id) {
                                spinnerFolder.setSelection(i);
                                break;
                            }
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        });
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_add_bookmark)
            .setView(layout)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String finalTitle = etTitle.getText().toString();
                String finalUrl = etUrl.getText().toString();
                if (!finalUrl.isEmpty()) {
                    
                    long folderId = -1;
                    if(spinnerFolder.getSelectedItemPosition() >= 0 && spinnerFolder.getSelectedItemPosition() < folders.size()) {
                        folderId = folders.get(spinnerFolder.getSelectedItemPosition()).id;
                    }
                    com.olsc.manorbrowser.data.BookmarkStorage.addBookmarkToFolder(this, 
                        new com.olsc.manorbrowser.data.BookmarkItem(finalTitle, finalUrl), folderId);
                    
                    android.widget.Toast.makeText(this, R.string.msg_bookmark_added, android.widget.Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }
    private void checkAndAutofill(GeckoSession session) {
        String url = null;
        for (TabInfo t : tabs) {
            if (t.session == session) {
                url = t.url;
                break;
            }
        }
        if (url == null) return;
        List<com.olsc.manorbrowser.data.PasswordItem> passwords = 
            com.olsc.manorbrowser.data.PasswordStorage.getPasswordsForUrl(this, url);
        if (passwords.isEmpty()) return;
        if (passwords.size() == 1) {
            com.olsc.manorbrowser.data.PasswordItem item = passwords.get(0);
            session.loadUri("javascript:" + com.olsc.manorbrowser.utils.JSInjector.getFillScript(item.username, item.password));
        } else {
            
            runOnUiThread(() -> showSelectAccountDialog(session, passwords));
        }
    }
    private void showSelectAccountDialog(GeckoSession session, List<com.olsc.manorbrowser.data.PasswordItem> items) {
       String[] names = new String[items.size()];
       for (int i = 0; i < items.size(); i++) {
           names[i] = items.get(i).username;
       }
       new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
           .setTitle(R.string.title_select_account)
           .setItems(names, (dialog, which) -> {
               com.olsc.manorbrowser.data.PasswordItem item = items.get(which);
               session.loadUri("javascript:" + com.olsc.manorbrowser.utils.JSInjector.getFillScript(item.username, item.password));
           })
           .show();
    }
    private void handleSavePasswordRequest(String msg) {
        
        String[] parts = msg.split("\\|", 4);
        if (parts.length < 4) return;
        final String url = parts[1];
        final String username = parts[2];
        final String password = parts[3];
        runOnUiThread(() -> {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_save_password)
                .setMessage(getString(R.string.msg_save_password, username))
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    com.olsc.manorbrowser.data.PasswordStorage.savePassword(getApplicationContext(), 
                        new com.olsc.manorbrowser.data.PasswordItem(url, username, password));
                    Toast.makeText(getApplicationContext(), R.string.msg_password_saved, Toast.LENGTH_SHORT).show();
                })

                .setNegativeButton(R.string.action_never, null)
                .show();
        });
    }
    

    private void showTabEditDialog(int position) {
        if (position < 0 || position >= tabs.size()) return;
        
        TabInfo tab = tabs.get(position);
        
        View dialogView = getLayoutInflater().inflate(R.layout.activity_bookmarks, null);
        EditText titleInput = new EditText(this);
        titleInput.setText(tab.title);
        titleInput.setHint(R.string.label_tab_title);
        titleInput.setPadding(50, 30, 50, 30);
        
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_edit_tab)
            .setView(titleInput)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String newTitle = titleInput.getText().toString().trim();
                if (!newTitle.isEmpty()) {
                    tab.title = newTitle;
                    tabSwitcherAdapter.notifyItemChanged(position);
                    TabStorage.saveTabs(this, currentTabGroup, tabs);
                    Toast.makeText(this, R.string.msg_tab_updated, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }
    private void enableReaderMode() {
        GeckoSession session = getCurrentSession();
        if (session != null) {
            Toast.makeText(this, R.string.msg_reader_mode_loading, Toast.LENGTH_SHORT).show();
            
            // 改进的内容提取脚本，提取更完整的网页文字
            String script = "javascript:(function(){" +
                "var content = '';" +
                "var title = document.title;" +
                
                // 尝试找到主要内容区域
                "var article = document.querySelector('article') || " +
                "              document.querySelector('[role=\"main\"]') || " +
                "              document.querySelector('main') || " +
                "              document.querySelector('.content') || " +
                "              document.querySelector('.article') || " +
                "              document.querySelector('#content') || " +
                "              document.body;" +
                
                // 递归提取文本的函数
                "function extractText(node) {" +
                "  if (!node) return;" +
                "  if (node.nodeType === Node.TEXT_NODE) {" +
                "    var text = node.textContent.trim();" +
                "    if (text.length > 0) {" +
                "      content += text + ' ';" +
                "    }" +
                "  } else if (node.nodeType === Node.ELEMENT_NODE) {" +
                "    var tag = node.tagName.toLowerCase();" +
                "    var style = window.getComputedStyle(node);" +
                
                // 跳过隐藏元素和不需要的标签
                "    if (style.display === 'none' || style.visibility === 'hidden') return;" +
                "    if (tag === 'script' || tag === 'style' || tag === 'noscript' || " +
                "        tag === 'iframe' || tag === 'nav' || tag === 'header' || " +
                "        tag === 'footer' || tag === 'aside') return;" +
                
                // 跳过广告和导航相关的class
                "    var className = node.className || '';" +
                "    if (typeof className === 'string' && " +
                "        (className.includes('ad') || className.includes('nav') || " +
                "         className.includes('menu') || className.includes('sidebar') || " +
                "         className.includes('comment'))) return;" +
                
                // 处理标题
                "    if (tag === 'h1' || tag === 'h2' || tag === 'h3' || tag === 'h4' || tag === 'h5' || tag === 'h6') {" +
                "      content += '\\n\\n=== ' + node.textContent.trim() + ' ===\\n\\n';" +
                "      return;" +
                "    }" +
                
                // 处理段落和列表
                "    if (tag === 'p' || tag === 'li' || tag === 'blockquote') {" +
                "      for (var i = 0; i < node.childNodes.length; i++) {" +
                "        extractText(node.childNodes[i]);" +
                "      }" +
                "      content += '\\n\\n';" +
                "      return;" +
                "    }" +
                
                // 处理换行
                "    if (tag === 'br') {" +
                "      content += '\\n';" +
                "      return;" +
                "    }" +
                
                // 递归处理子节点
                "    for (var i = 0; i < node.childNodes.length; i++) {" +
                "      extractText(node.childNodes[i]);" +
                "    }" +
                "  }" +
                "}" +
                
                "extractText(article);" +
                
                // 清理多余的空白
                "content = content.replace(/\\n{3,}/g, '\\n\\n').trim();" +
                
                "alert('READER_MODE:' + title + '|||' + content);" +
                "})()";
            
            session.loadUri(script);
        }
    }
    private void openReaderActivity(String title, String content) {
        android.content.Intent intent = new android.content.Intent(this, ReaderActivity.class);
        intent.putExtra("title", title);
        intent.putExtra("content", content);
        startActivity(intent);
    }
    
    private void showExtensionActionDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_extension_list, null);
        dialog.setContentView(view);
        
        androidx.recyclerview.widget.RecyclerView rv = view.findViewById(R.id.rv_extensions);
        android.widget.TextView tvEmpty = view.findViewById(R.id.tv_empty_extensions);
        
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        
        if (sRuntime != null) {
            sRuntime.getWebExtensionController().list().accept(extensions -> {
                runOnUiThread(() -> {
                    if (extensions == null || extensions.isEmpty()) {
                        tvEmpty.setVisibility(android.view.View.VISIBLE);
                        rv.setVisibility(android.view.View.GONE);
                    } else {
                        tvEmpty.setVisibility(android.view.View.GONE);
                        rv.setVisibility(android.view.View.VISIBLE);
                        rv.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                            @androidx.annotation.NonNull
                            @Override
                            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
                                android.view.View v = getLayoutInflater().inflate(R.layout.item_extension_action, parent, false);
                                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {};
                            }
                            @Override
                            public void onBindViewHolder(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                                org.mozilla.geckoview.WebExtension ext = extensions.get(position);
                                android.widget.TextView tvName = holder.itemView.findViewById(R.id.tv_ext_name);
                                tvName.setText(ext.metaData.name != null ? ext.metaData.name : "Unknown Extension");
                                holder.itemView.setOnClickListener(v -> {
                                    dialog.dismiss();
                                    triggerExtensionAction(ext);
                                });
                            }
                            @Override
                            public int getItemCount() {
                                return extensions.size();
                            }
                        });
                    }
                });
            }, e -> {
                runOnUiThread(() -> {
                    tvEmpty.setVisibility(android.view.View.VISIBLE);
                    rv.setVisibility(android.view.View.GONE);
                });
            });
        }
        
        dialog.show();
    }

    private void triggerExtensionAction(org.mozilla.geckoview.WebExtension extension) {
         if (extension.metaData.optionsPageUrl != null) {
              createNewTab(extension.metaData.optionsPageUrl);
         } else {
              Toast.makeText(this, getString(R.string.action_extension_options) + "\n" + extension.metaData.name, Toast.LENGTH_SHORT).show();
         }
    }

    private void updateHomeButton() {
        if (btnHome == null) return;
        
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String function = prefs.getString(Config.PREF_KEY_LEFT_BUTTON_FUNCTION, Config.FUNC_BOOKMARKS);
        
        int iconRes;
        String description;
        View.OnClickListener listener;
        
        switch (function) {
            case Config.FUNC_HOME:
                iconRes = R.drawable.ic_home;
                description = getString(R.string.action_home);
                listener = v -> loadUrlInCurrentTab(Config.URL_BLANK);
                break;
            case Config.FUNC_EXTENSIONS:
                iconRes = R.drawable.ic_extension;
                description = getString(R.string.action_extensions);
                listener = v -> showExtensionActionDialog();
                break;
            case Config.FUNC_BOOKMARKS:
                iconRes = R.drawable.ic_bookmark;
                description = getString(R.string.action_bookmarks);
                listener = v -> {
                    android.content.Intent intent = new android.content.Intent(this, BookmarkActivity.class);
                    startActivity(intent);
                };
                break;
            case Config.FUNC_HISTORY:
                iconRes = R.drawable.ic_history;
                description = getString(R.string.action_history);
                listener = v -> {
                    android.content.Intent intent = new android.content.Intent(this, HistoryActivity.class);
                    startActivity(intent);
                };
                break;
            case Config.FUNC_DOWNLOADS:
                iconRes = R.drawable.ic_download;
                description = getString(R.string.action_downloads);
                listener = v -> {
                    android.content.Intent intent = new android.content.Intent(this, DownloadsActivity.class);
                    startActivity(intent);
                };
                break;
            case Config.FUNC_DESKTOP_MODE:
                iconRes = R.drawable.ic_desktop;
                description = getString(R.string.title_desktop_mode);
                listener = v -> toggleDesktopMode();
                break;
            case Config.FUNC_ADD_BOOKMARK:
                iconRes = R.drawable.ic_bookmark_add;
                description = getString(R.string.action_add_bookmark);
                listener = v -> showAddBookmarkDialog();
                break;
            case Config.FUNC_THEME:
                iconRes = R.drawable.ic_theme;
                description = getString(R.string.action_theme);
                listener = v -> toggleTheme();
                break;
            default:
                iconRes = R.drawable.ic_bookmark;
                description = getString(R.string.action_bookmarks);
                listener = v -> {
                    android.content.Intent intent = new android.content.Intent(this, BookmarkActivity.class);
                    startActivity(intent);
                };
                break;
        }
        
        btnHome.setImageResource(iconRes);
        btnHome.setContentDescription(description);
        btnHome.setOnClickListener(listener);
    }

    private final Autocomplete.StorageDelegate mAutocompleteStorageDelegate = new Autocomplete.StorageDelegate() {
        @Override
        public GeckoResult<LoginEntry[]> onLoginFetch(@NonNull String domain) {
            List<PasswordItem> matches = PasswordStorage.getPasswordsForUrl(MainActivity.this, domain);
            if (matches.isEmpty()) {
                return GeckoResult.fromValue(null);
            }
            LoginEntry[] entries = new LoginEntry[matches.size()];
            for (int i = 0; i < matches.size(); i++) {
                PasswordItem item = matches.get(i);
                entries[i] = new LoginEntry.Builder()
                    .origin(item.url)
                    .username(item.username)
                    .password(item.password)
                    .build();
            }
            return GeckoResult.fromValue(entries);
        }

        @Override
        public void onLoginSave(@NonNull LoginEntry login) {

        }
    };

    /**
     * 弹出原生账号选择底部对话框
     */
    private void showPasswordSelectionDialogForNative(PromptDelegate.AutocompleteRequest<Autocomplete.LoginSelectOption> request, GeckoResult<PromptDelegate.PromptResponse> result) {
        Autocomplete.LoginSelectOption[] logins = request.options;
        final java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);

        // 使用专属样式创建，让系统框架背景透明，消除进场闪白
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogStyle);
        View view = getLayoutInflater().inflate(R.layout.dialog_password_selection, null);

        // 设置 alpha 0，随后淡入，避免布局测量/绘制阶段的视觉跳变
        view.setAlpha(0f);
        dialog.setContentView(view);

        // 跳过折叠态，直接完全展开，防止半展开→展开再展开的抖动
        dialog.setOnShowListener(d -> {
            com.google.android.material.bottomsheet.BottomSheetBehavior<?> behavior =
                    ((BottomSheetDialog) d).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);

            // 内容完全就位后再淡入
            view.animate().alpha(1f).setDuration(180).setInterpolator(
                    new android.view.animation.DecelerateInterpolator()).start();
        });

        RecyclerView rv = view.findViewById(R.id.rv_passwords);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new RecyclerView.Adapter<PasswordSelectionViewHolder>() {
            @NonNull
            @Override
            public PasswordSelectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.item_password_selection, parent, false);
                return new PasswordSelectionViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull PasswordSelectionViewHolder holder, int position) {
                Autocomplete.LoginSelectOption option = logins[position];
                LoginEntry item = option.value;
                holder.tvUsername.setText(item.username);
                holder.itemView.setOnClickListener(v -> {
                    if (completed.compareAndSet(false, true)) {
                        dialog.dismiss();
                        result.complete(request.confirm(option));
                    }
                });
            }

            @Override
            public int getItemCount() {
                return logins.length;
            }
        });

        dialog.setOnCancelListener(d -> {
            if (completed.compareAndSet(false, true)) {
                result.complete(request.dismiss());
            }
        });
        dialog.show();
    }


    private static class PasswordSelectionViewHolder extends RecyclerView.ViewHolder {
        TextView tvUsername;
        PasswordSelectionViewHolder(View v) {
            super(v);
            tvUsername = v.findViewById(R.id.tv_username);
        }
    }

    /**
     * 处理原生回调的账号保存/更新逻辑。
     */
    private void handleNativeSavePassword(PromptDelegate.AutocompleteRequest<Autocomplete.LoginSaveOption> request, GeckoResult<PromptDelegate.PromptResponse> result) {
        if (request.options.length == 0) {
             try { result.complete(request.dismiss()); } catch (Exception ignored) {}
             return;
        }

        final java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);

        // 查找保存选项和从不保存选项
        Autocomplete.LoginSaveOption saveOption = null;
        Autocomplete.LoginSaveOption neverOption = null;
        
        for (Autocomplete.LoginSaveOption opt : request.options) {
            if (saveOption == null) saveOption = opt;
            else neverOption = opt;
        }

        if (saveOption == null) {
            if (completed.compareAndSet(false, true)) {
                try { result.complete(request.dismiss()); } catch (Exception ignored) {}
            }
            return;
        }

        LoginEntry login = saveOption.value;
        final Autocomplete.LoginSaveOption finalSaveOption = saveOption;
        final Autocomplete.LoginSaveOption finalNeverOption = neverOption;

        // 检查是更新还是保存，以及密码是否真正变化
        boolean isUpdate = false;
        boolean isPasswordChanged = true;
        List<PasswordItem> matches = PasswordStorage.getPasswordsForUrl(getApplicationContext(), login.origin);
        for (PasswordItem item : matches) {
            // 健壮的用户名匹配逻辑
            boolean nameMatches = item.username == null || item.username.isEmpty() ?
                    login.username.isEmpty() :
                                item.username.equals(login.username);
            
            if (nameMatches) {
                isUpdate = true;
                if (item.password != null && item.password.equals(login.password)) {
                    isPasswordChanged = false;
                }
                break;
            }
        }

        // 如果是更新且密码未变化，直接静默确认，避免多余弹窗
        if (isUpdate && !isPasswordChanged) {
            if (completed.compareAndSet(false, true)) {
                try { result.complete(request.confirm(finalSaveOption)); } catch (Exception ignored) {}
            }
            return;
        }

        String msg = getString(isUpdate ? R.string.msg_update_password : R.string.msg_save_password, login.username != null ? login.username : "");
        int titleRes = isUpdate ? R.string.title_update_password : R.string.title_save_password;
        int actionRes = isUpdate ? R.string.action_update : R.string.action_save;
        final boolean finalIsUpdate = isUpdate;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
            .setTitle(titleRes)
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton(actionRes, (dialog, which) -> {
                if (completed.compareAndSet(false, true)) {
                    try {
                        PasswordStorage.savePassword(getApplicationContext(), 
                            new PasswordItem(login.origin, login.username, login.password));
                        Toast.makeText(getApplicationContext(), finalIsUpdate ? R.string.msg_password_updated : R.string.msg_password_saved, Toast.LENGTH_SHORT).show();
                        result.complete(request.confirm(finalSaveOption));
                    } catch (Exception e) {
                        try { result.complete(request.dismiss()); } catch (Exception ignored) {}
                    }
                }
            })
            .setNegativeButton(isUpdate ? android.R.string.cancel : R.string.action_never, (dialog, which) -> {
                if (completed.compareAndSet(false, true)) {
                    try {
                        result.complete(finalIsUpdate ? request.dismiss() : (finalNeverOption != null ? request.confirm(finalNeverOption) : request.dismiss()));
                    } catch (Exception ignored) {}
                }
            })
            .setOnDismissListener(d -> {
                if (completed.compareAndSet(false, true)) {
                    try {
                        result.complete(request.dismiss());
                    } catch (Exception ignored) {}
                }
            })
            .show();
    }

    /**
     * 初始化 AI 远程助手
     * 采用反向连接模式：手机主动连接 PC 服务端，无需手机 IP。
     */
    private void initAiRemoteAssistant() {
        // 初始化命令处理器（不启动内部 HTTP Server，只作为逻辑封装）
        commandServer = new BrowserCommandServer(new BrowserCommandServer.CommandHandler() {
            @Override
            public void navigate(String url) {
                runOnUiThread(() -> {
                    GeckoSession session = getCurrentSession();
                    if (session != null) session.loadUri(url);
                });
            }

            @Override
            public void goBack() {
                runOnUiThread(() -> {
                    GeckoSession session = getCurrentSession();
                    if (session == null) return;
                    // 扩展页(moz-extension://)的 goBack 会触发引擎跨边界历史切换,导致 libxul 原生崩溃
                    if (isExtensionPageUrl(getCurrentTabUrl())) {
                        closeExtensionTabAndSwitch();
                        return;
                    }
                    mainHandler.removeCallbacksAndMessages(session);
                    session.goBack();
                });
            }

            @Override
            public void goForward() {
                runOnUiThread(() -> {
                    GeckoSession session = getCurrentSession();
                    if (session != null) {
                        mainHandler.removeCallbacksAndMessages(session);
                        session.goForward();
                    }
                });
            }

            @Override
            public void reload() {
                runOnUiThread(() -> {
                    GeckoSession session = getCurrentSession();
                    if (session != null) session.reload();
                });
            }

            @Override
            public void evalJs(String js, BrowserCommandServer.EvalCallback callback) {
                runOnUiThread(() -> {
                    GeckoSession session = getCurrentSession();
                    if (session == null) { callback.onResult("null"); return; }

                    pendingJsCallback = callback;

                    // 通过 alert() 传回 JS 结果（解决 document.title 的长度限制问题）
                    String encodedJs = js.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
                    String bridge = "(function(){try{var __r=" + encodedJs + ";alert('__JSRESULT__:' + JSON.stringify(__r));}catch(__e){alert('__JSRESULT__:' + JSON.stringify('error:'+__e.message));}})()";
                    session.loadUri("javascript:" + bridge);
                });
            }

            @Override
            public java.util.List<com.olsc.manorbrowser.data.HistoryStorage.HistoryItem> getHistory(String timeFilter) {
                java.util.List<com.olsc.manorbrowser.data.HistoryStorage.HistoryItem> all = com.olsc.manorbrowser.data.HistoryStorage.loadHistory(MainActivity.this);
                if (timeFilter == null || timeFilter.isEmpty()) return all;
                java.util.List<com.olsc.manorbrowser.data.HistoryStorage.HistoryItem> filtered = new java.util.ArrayList<>();
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                for (com.olsc.manorbrowser.data.HistoryStorage.HistoryItem item : all) {
                    if (sdf.format(new java.util.Date(item.timestamp)).contains(timeFilter)) filtered.add(item);
                }
                return filtered;
            }

            @Override
            public String getCurrentUrl() {
                if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) return tabs.get(currentTabIndex).url;
                return "";
            }

            @Override
            public String getCurrentTitle() {
                if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) return tabs.get(currentTabIndex).title;
                return "";
            }

            @Override
            public java.util.List<com.olsc.manorbrowser.data.DownloadInfo> getDownloads() {
                return com.olsc.manorbrowser.data.DownloadStorage.getAllDownloads(MainActivity.this);
            }

            @Override
            public String getStatus() {
                try {
                    JSONObject s = new JSONObject();
                    s.put("url", getCurrentUrl());
                    s.put("title", getCurrentTitle());
                    s.put("tabCount", tabs.size());
                    s.put("currentTabIndex", currentTabIndex);
                    return s.toString();
                } catch (Exception e) { return "{}"; }
            }

            @Override
            public java.util.List<com.olsc.manorbrowser.data.TabInfo> getTabs() {
                return new java.util.ArrayList<>(tabs);
            }

            @Override
            public void switchTab(int index) {
                runOnUiThread(() -> switchToTab(index));
            }

            @Override
            public void closeTab(int index) {
                runOnUiThread(() -> MainActivity.this.closeTab(index));
            }

            @Override
            public void createTab(String url) {
                runOnUiThread(() -> createNewTab(url));
            }

            @Override
            public void scrollBy(int x, int y) {
                runOnUiThread(() -> {
                    GeckoSession session = getCurrentSession();
                    if (session != null) {
                        session.loadUri("javascript:window.scrollBy({top: " + y + ", left: " + x + ", behavior: 'smooth'})");
                    }
                });
            }

            @Override
            public void scrollTo(int x, int y) {
                runOnUiThread(() -> {
                    GeckoSession session = getCurrentSession();
                    if (session != null) {
                        session.loadUri("javascript:window.scrollTo({top: " + y + ", left: " + x + ", behavior: 'smooth'})");
                    }
                });
            }

            @Override
            public void clearHistory() {
                com.olsc.manorbrowser.data.HistoryStorage.clearHistory(MainActivity.this);
            }
            
            @Override
            public void clearDownloads() {
                com.olsc.manorbrowser.data.DownloadStorage.clearAllDownloads(MainActivity.this);
            }

            @Override
            public void exitAi() {
                if (getApplication() instanceof com.olsc.manorbrowser.ManorBrowserApp) {
                    com.olsc.manorbrowser.utils.AiCommandClient client = ((com.olsc.manorbrowser.ManorBrowserApp) getApplication()).getAiCommandClient();
                    if (client != null) client.stop();
                }
            }
        });

        // 注册到 Application 并启动轮询
        if (getApplication() instanceof com.olsc.manorbrowser.ManorBrowserApp) {
            com.olsc.manorbrowser.ManorBrowserApp app = (com.olsc.manorbrowser.ManorBrowserApp) getApplication();
            app.initAiCommandClient(commandServer.getHandler());
            
            // 启动定时器更新覆盖层可见性
            final View overlay = findViewById(R.id.ai_overlay_include);
            if (overlay != null) {
                final com.olsc.manorbrowser.utils.AiCommandClient client = app.getAiCommandClient();
                final com.olsc.manorbrowser.utils.AiOverlayManager overlayManager = new com.olsc.manorbrowser.utils.AiOverlayManager(this, overlay, client, commandServer.getHandler());
                
                final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        boolean isRunning = client != null && client.isRunning();
                        if (overlay.getVisibility() == View.VISIBLE != isRunning) {
                            if (isRunning) {
                                overlayManager.show();
                            } else {
                                overlayManager.hide();
                            }
                        }
                        if (isRunning) {
                            overlayManager.updateStatus();
                        }
                        h.postDelayed(this, 1000);
                    }
                });
            }
        }
    }

    /**
     * 更新侧边栏领主模式菜单项的状态
     */
    private void updateLordModeMenuItem() {
        if (navigationView != null) {
            android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            isLordMode = prefs.getBoolean(Config.PREF_KEY_LORD_MODE, false);
            android.view.Menu menu = navigationView.getMenu();
            android.view.MenuItem lordItem = menu.findItem(R.id.nav_lord_mode);
            if (lordItem != null) {
                lordItem.setChecked(isLordMode);
                lordItem.setTitle(!isLordMode ? R.string.title_lord_mode : R.string.title_normal_mode);
            }
        }
    }

    /**
     * 切换领主模式（Lord Mode）
     */
    private void toggleLordMode() {
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        isLordMode = !isLordMode;
        prefs.edit().putBoolean(Config.PREF_KEY_LORD_MODE, isLordMode).apply();
        
        updateLordModeMenuItem();
        
        if (isLordMode) {
            boolean introShown = prefs.getBoolean(Config.PREF_KEY_LORD_MODE_INTRO_SHOWN, false);
            if (!introShown) {
                showLordModeIntro();
                prefs.edit().putBoolean(Config.PREF_KEY_LORD_MODE_INTRO_SHOWN, true).apply();
            }
        }
        
        Toast.makeText(this, isLordMode ? R.string.title_lord_mode : R.string.title_normal_mode, Toast.LENGTH_SHORT).show();
    }

    private void showLordModeIntro() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_lord_mode)
            .setMessage(R.string.msg_lord_mode_intro)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    /**
     * 更新历史建议列表
     */
    private void updateRecommendations(String query) {
        if (recommendationAdapter == null || recommendationsContainer == null) return;
        
        // 基于线程池进行异步查询，避免卡顿主线程
        new Thread(() -> {
            List<com.olsc.manorbrowser.data.HistoryStorage.HistoryItem> suggestions = 
                com.olsc.manorbrowser.data.HistoryStorage.getRecommendations(this, query);
            
            runOnUiThread(() -> {
                // 如果搜索框失去焦点或输入已变更（对于延迟返回的旧请求），则不再处理
                if (!urlInput.hasFocus()) {
                    recommendationsContainer.setVisibility(View.GONE);
                    return;
                }
                
                if (suggestions.isEmpty()) {
                    recommendationsContainer.setVisibility(View.GONE);
                } else {
                    recommendationAdapter.setItems(suggestions);
                    recommendationsContainer.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null && urlInput != null) {
            imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        }
    }
    private boolean isSslOverridden(String host) {
        if (host == null || host.equals("unknown")) return false;
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        java.util.Set<String> overrides = prefs.getStringSet(Config.PREF_KEY_SSL_OVERRIDE_SITES, new java.util.HashSet<>());
        return overrides.contains(host);
    }

    private void addSslOverride(String host) {
        if (host == null || host.equals("unknown")) return;
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        java.util.Set<String> overrides = new java.util.HashSet<>(prefs.getStringSet(Config.PREF_KEY_SSL_OVERRIDE_SITES, new java.util.HashSet<>()));
        overrides.add(host);
        prefs.edit().putStringSet(Config.PREF_KEY_SSL_OVERRIDE_SITES, overrides).apply();
    }
}
