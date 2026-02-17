/**
 * 浏览器主界面，处理网页加载、导航、菜单、多标签页等核心逻辑。
 */
package com.olsc.manorbrowser.activity;
import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.view.DynamicBackgroundView;
import com.olsc.manorbrowser.adapter.TabSwitcherAdapter;
import com.olsc.manorbrowser.adapter.TabSwipeCallback;
import com.olsc.manorbrowser.Config;
import com.olsc.manorbrowser.data.TabInfo;
import com.olsc.manorbrowser.data.TabStorage;
import com.olsc.manorbrowser.data.HistoryStorage;
import com.olsc.manorbrowser.utils.SearchHelper;
import android.text.TextUtils;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsControllerCompat;
import com.google.android.material.navigation.NavigationView;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebResponse;
import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement;
import org.mozilla.geckoview.GeckoSession.NavigationDelegate.LoadRequest;
import org.mozilla.geckoview.AllowOrDeny;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
public class MainActivity extends AppCompatActivity {
    private DrawerLayout drawerLayout;
    private GeckoView geckoView;
    private androidx.recyclerview.widget.RecyclerView tabSwitcher;
    private EditText urlInput;
    private View topBar;
    private ImageButton btnBack, btnRefresh, btnHome, btnTabs, btnMenu;
    private NavigationView navigationView;
    private android.widget.ProgressBar progressBar;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private static GeckoRuntime sRuntime;
    private List<TabInfo> tabs = new ArrayList<>();
    private TabSwitcherAdapter tabSwitcherAdapter;
    private int currentTabIndex = -1;
    private boolean isTabSwitcherVisible = false;
    private int lastX, lastY;
    private long lastBackTime = 0;
    private boolean urlInputFirstClick = true; // 跟踪URL输入框是否是第一次点击
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.content.SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME_THEME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(Config.PREF_KEY_DARK_MODE, false);
        AppCompatDelegate.setDefaultNightMode(isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(!isDarkMode);
            controller.setAppearanceLightNavigationBars(!isDarkMode);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        drawerLayout = findViewById(R.id.drawer_layout);
        geckoView = findViewById(R.id.geckoview);
        tabSwitcher = findViewById(R.id.tab_switcher);
        urlInput = findViewById(R.id.et_url);
        topBar = findViewById(R.id.top_bar);
        progressBar = findViewById(R.id.progress_bar);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawer_layout), (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            
            topBar.setPadding(topBar.getPaddingLeft(), systemBars.top, topBar.getPaddingRight(), 0);
            
            
            View bottomBar = findViewById(R.id.bottom_bar);
            bottomBar.setPadding(bottomBar.getPaddingLeft(), bottomBar.getPaddingTop(), bottomBar.getPaddingRight(), systemBars.bottom);
            
            
            navigationView.setPadding(0, 0, 0, systemBars.bottom);
            
            
            return windowInsets;
        });
        swipeRefresh = findViewById(R.id.swipe_refresh);
        navigationView = findViewById(R.id.nav_view);
        btnBack = findViewById(R.id.btn_back);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnHome = findViewById(R.id.btn_home);
        btnTabs = findViewById(R.id.btn_tabs);
        btnMenu = findViewById(R.id.btn_menu);
        if (isPrivacyAgreed()) {
            initializeApp();
        } else {
            showPrivacyDialog();
        }
    }
    private boolean isPrivacyAgreed() {
        android.content.SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME_THEME, MODE_PRIVATE);
        return prefs.getBoolean(Config.PREF_KEY_PRIVACY_AGREED, false);
    }
    
    private void setPrivacyAgreed(boolean agreed) {
        android.content.SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME_THEME, MODE_PRIVATE);
        prefs.edit().putBoolean(Config.PREF_KEY_PRIVACY_AGREED, agreed).apply();
    }
    private void showPrivacyDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_privacy_policy)
            .setMessage(R.string.msg_privacy_policy)
            .setCancelable(false)
            .setPositiveButton(R.string.action_agree, (dialog, which) -> {
                setPrivacyAgreed(true);
                initializeApp();
            })
            .setNegativeButton(R.string.action_disagree, (dialog, which) -> {
                finish();
            })
            .show();
    }
    private void initializeApp() {
        ImageButton btnAddTab = findViewById(R.id.btn_add_tab);
        if (btnAddTab != null) {
            btnAddTab.setOnClickListener(v -> {
                captureScreenshot(() -> {
                    createNewTab(Config.URL_BLANK);
                    toggleTabSwitcher();
                });
            });
        }
        navigationView = findViewById(R.id.nav_view);
        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(getApplicationContext());
        }
        setupTabSwitcher();
        if (getIntent() != null && getIntent().hasExtra("url")) {
            restoreTabsOrInit();
            String url = getIntent().getStringExtra("url");
            if (url != null && !url.isEmpty()) {
                loadUrlInCurrentTab(url);
            }
        } else {
            restoreTabsOrInit();
        }
        setupListeners();
        setupSwipeRefresh();
        checkDownloadPermissions();
    }
    
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(com.olsc.manorbrowser.utils.LocaleHelper.onAttach(newBase));
    }
    private void checkDownloadPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
        
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S_V2) {
             if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1002);
            }
        }
    }
    private void setupSwipeRefresh() {
        if (swipeRefresh == null) return;
        swipeRefresh.setColorSchemeResources(R.color.purple_500);
        swipeRefresh.setOnRefreshListener(() -> {
            GeckoSession session = getCurrentSession();
            if (session != null) {
                session.reload();
            }
            swipeRefresh.setRefreshing(false);
        });
    }
    private void restoreTabsOrInit() {
        List<TabInfo> savedTabs = TabStorage.loadTabs(this);
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
               updateTabInfo(session, url, null);
               for (TabInfo t : tabs) {
                   if (t.session == session) {
                       t.scrollY = 0;
                       break;
                   }
               }
               runOnUiThread(() -> {
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
                    } else {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setAlpha(1f);
                    }
                });
            }
            @Override
            public void onPageStop(@NonNull GeckoSession session, boolean success) {
               updateTabInfo(session, null, null);
               runOnUiThread(() -> {
                   if (progressBar != null) progressBar.setVisibility(View.INVISIBLE);
                   if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
               });
               
               if (success) {
                    session.loadUri("javascript:" + com.olsc.manorbrowser.utils.JSInjector.INJECT_LOGIN_DETECT);
                    checkAndAutofill(session);
               }
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
                        HistoryStorage.addHistory(MainActivity.this, title, tabInfo.url);
                   }
                }
            }
            
            @Override
            public void onExternalResponse(@NonNull GeckoSession session, @NonNull WebResponse response) {
                final String url = response.uri;
                if (url == null) return;
                String tempMime = null;
                String tempDisp = null;
                if (response.headers != null) {
                    if (response.headers.containsKey("Content-Type")) {
                        tempMime = response.headers.get("Content-Type");
                    } else if (response.headers.containsKey("content-type")) {
                        tempMime = response.headers.get("content-type");
                    }
                    
                    if (response.headers.containsKey("Content-Disposition")) {
                        tempDisp = response.headers.get("Content-Disposition");
                    } else if (response.headers.containsKey("content-disposition")) {
                        tempDisp = response.headers.get("content-disposition");
                    }
                }
                final String mimeTypeFinal = tempMime;
                final String contentDispositionFinal = tempDisp;
                
                String ua = session.getSettings().getUserAgentOverride();
                if (ua == null || ua.isEmpty()) {
                    ua = Config.DEFAULT_UA;
                }
                final String userAgentFinal = ua;
                String ref = null;
                for (TabInfo t : tabs) {
                    if (t.session == session) {
                        ref = t.url;
                        break;
                    }
                }
                final String refererFinal = ref;
                runOnUiThread(() -> {
                    String cookies = android.webkit.CookieManager.getInstance().getCookie(url);
                    
                    String referer = null;
                    for (TabInfo t : tabs) {
                        if (t.session == session) {
                            referer = t.url;
                            break;
                        }
                    }
                    String finalUa = userAgentFinal;
                    if (TextUtils.isEmpty(finalUa)) {
                        finalUa = Config.CHROME_UA;
                    }
                    
                    com.olsc.manorbrowser.utils.DownloadHelper.startDownload(
                        MainActivity.this, 
                        url, 
                        finalUa, 
                        contentDispositionFinal, 
                        mimeTypeFinal,
                        cookies, 
                        referer
                    );
                });
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
        session.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override
            public GeckoResult<PromptResponse> onAlertPrompt(@NonNull GeckoSession session, @NonNull AlertPrompt prompt) {
                String message = prompt.message;
                if (message != null && message.startsWith("COPY_TEXT:")) {
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
                }
                return null; 
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
                
                // 使用改进的IntentHelper处理外部应用跳转
                if (com.olsc.manorbrowser.utils.IntentHelper.shouldInterceptUrl(uri)) {
                    boolean handled = com.olsc.manorbrowser.utils.IntentHelper.tryOpenExternalApp(MainActivity.this, uri);
                    if (handled) {
                        return GeckoResult.fromValue(AllowOrDeny.DENY);
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
    }
    @Override
    protected void onStop() {
        super.onStop();
        TabStorage.saveTabs(this, tabs);
    }
    @Override
    protected void onDestroy() {
        if (geckoView != null) {
            geckoView.releaseSession();
        }
        for (TabInfo tab : tabs) {
            if (tab.session != null) {
                tab.session.setProgressDelegate(null);
                tab.session.setContentDelegate(null);
                tab.session.close();
                tab.session = null;
            }
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
        TabSwipeCallback swipeCallback = new TabSwipeCallback(position -> closeTab(position));
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
                // 获得焦点时，重置为第一次点击状态
                urlInputFirstClick = true;
            } else {
                // 失去焦点时，重置状态
                urlInputFirstClick = true;
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
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
                }
                urlInput.clearFocus();
                String input = urlInput.getText().toString();
                String url = SearchHelper.getSearchUrl(this, input);
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
                    homeSearch.post(() -> homeSearch.selectAll());
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
            GeckoSession session = getCurrentSession();
            if (session != null) {
                session.goBack();
            }
        });
        btnRefresh.setOnClickListener(v -> {
            if (getCurrentSession() != null) {
                getCurrentSession().reload();
            }
        });
        btnHome.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(MainActivity.this, BookmarkActivity.class);
            startActivity(intent);
        });
        btnTabs.setOnClickListener(v -> toggleTabSwitcher());
        btnMenu.setOnClickListener(v -> {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.openDrawer(GravityCompat.START);
            } else {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
        });
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            drawerLayout.closeDrawer(GravityCompat.START);
            if (id == R.id.nav_home) {
                loadUrlInCurrentTab(Config.URL_BLANK);
            } else if (id == R.id.nav_history) {
                android.content.Intent intent = new android.content.Intent(this, HistoryActivity.class);
                startActivity(intent);
            } else if (id == R.id.nav_settings) {
                 android.content.Intent intent = new android.content.Intent(this, SettingsActivity.class);
                 startActivity(intent);
            } else if (id == R.id.nav_downloads) {
                 android.content.Intent intent = new android.content.Intent(this, DownloadsActivity.class);
                 startActivity(intent);
            } else if (id == R.id.nav_add_bookmark) {
                showAddBookmarkDialog();
            } else if (id == R.id.nav_bookmarks) {
                android.content.Intent intent = new android.content.Intent(this, BookmarkActivity.class);
                startActivity(intent);
            } else if (id == R.id.nav_passwords) {
                android.content.Intent intent = new android.content.Intent(this, PasswordManagerActivity.class);
                startActivity(intent);
            } else if (id == R.id.nav_theme) {
                navigationView.postDelayed(this::toggleTheme, 300);
            } else if (id == R.id.nav_desktop_mode) {
                toggleDesktopMode();
            } else if (id == R.id.nav_about) {
                 android.content.Intent intent = new android.content.Intent(this, AboutActivity.class);
                 startActivity(intent);
            }
            if (isTabSwitcherVisible && id != R.id.nav_theme) toggleTabSwitcher();
            return true;
        });
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
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
                if (currentTab != null && currentTab.session != null && currentTab.canGoBack) {
                    currentTab.session.goBack();
                } else {
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
        
        // 初始化桌面模式菜单项的选中状态
        if (navigationView != null) {
            android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
            boolean isDesktopMode = prefs.getBoolean(Config.PREF_KEY_DESKTOP_MODE, false);
            android.view.Menu menu = navigationView.getMenu();
            android.view.MenuItem desktopItem = menu.findItem(R.id.nav_desktop_mode);
            if (desktopItem != null) {
                desktopItem.setChecked(isDesktopMode);
            }
        }
    }
    private void createNewTab(String url) {
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
    private void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        if (index != tabs.size() - 1) {
            TabInfo tab = tabs.remove(index);
            tabs.add(tab);
            if (tabSwitcherAdapter != null) {
                tabSwitcherAdapter.notifyItemMoved(index, tabs.size() - 1);
                tabSwitcherAdapter.notifyItemRangeChanged(0, tabs.size());
            }
            index = tabs.size() - 1;
        }
        if (isTabSwitcherVisible) {
            toggleTabSwitcher();
        }
        TabInfo tab = tabs.get(index);
        geckoView.releaseSession();
        geckoView.setSession(tab.session);
        currentTabIndex = index;
        if (swipeRefresh != null) {
            swipeRefresh.setEnabled(tab.scrollY <= 0);
        }
        updateUrlBar();
        updateViewVisibility();
    }
    private void updateViewVisibility() {
        if (isTabSwitcherVisible) return;
        View layoutHome = findViewById(R.id.layout_home);
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
            TabInfo tab = tabs.get(currentTabIndex);
            if (Config.URL_BLANK.equals(tab.url)) {
                geckoView.setVisibility(View.GONE);
                layoutHome.setVisibility(View.VISIBLE);
                urlInput.setText("");
            } else {
                geckoView.setVisibility(View.VISIBLE);
                layoutHome.setVisibility(View.GONE);
            }
        }
    }
    private void closeTab(int position) {
        if (position < 0 || position >= tabs.size()) return;
        TabInfo tab = tabs.get(position);
        if (tab.session != null) {
            tab.session.close();
        }
        tabs.remove(position);
        if (tabSwitcherAdapter != null) {
            tabSwitcherAdapter.notifyItemRemoved(position);
            tabSwitcherAdapter.notifyItemRangeChanged(position, tabs.size() - position);
        }
        if (tabs.isEmpty()) {
            createNewTab("about:blank");
        } else {
            int newFocusIndex = position;
            if (newFocusIndex >= tabs.size()) {
                newFocusIndex = tabs.size() - 1;
            }
            currentTabIndex = newFocusIndex;
            final int targetIndex = newFocusIndex;
            tabSwitcher.post(() -> {
                if (tabSwitcher.getLayoutManager() != null) {
                   tabSwitcher.scrollToPosition(targetIndex);
                   updateTabAnimations();
                   updateTabCount();
                }
            });
        }
        updateTabCount();
        updateBottomTabCounter();
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
    private void toggleTabSwitcher() {
        if (isTabSwitcherVisible) {
            performToggleAnimation(false);
            return;
        }
        captureScreenshot(() -> {
            performToggleAnimation(true);
        });
    }
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
                        tabSwitcher.scrollToPosition(currentTabIndex);
                    }
                    tabSwitcher.post(() -> {
                         updateTabAnimations();
                         updateTabCount();
                         tabSwitcher.animate()
                             .alpha(1f)
                             .setDuration(200)
                             .start();
                    });
                });
            } else {
                 tabSwitcher.animate().alpha(1f).setDuration(200).start();
            }
        } else {
            if (dynamicBgView != null) dynamicBgView.setVisibility(View.GONE);
            tabSwitcher.setVisibility(View.GONE);
            tabSwitcher.setAlpha(1f);
            btnAddTab.setVisibility(View.GONE);
            topBar.setVisibility(View.VISIBLE);
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
        if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        TabInfo tab = tabs.get(currentTabIndex);
        if (!Config.URL_BLANK.equals(tab.url) && geckoView.getVisibility() == View.VISIBLE && geckoView.getWidth() > 0 && geckoView.getHeight() > 0) {
            try {
                int[] location = new int[2];
                geckoView.getLocationInWindow(location);
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                        geckoView.getWidth(),
                        geckoView.getHeight(),
                        android.graphics.Bitmap.Config.ARGB_8888);
                android.view.SurfaceView surfaceView = findSurfaceView(geckoView);
                if (surfaceView != null) {
                    android.view.PixelCopy.request(surfaceView, bitmap, copyResult -> {
                        if (copyResult == android.view.PixelCopy.SUCCESS) {
                            validateAndSetThumbnail(tab, bitmap);
                        }
                         handleScreenshotResult(onComplete);
                    }, new android.os.Handler(android.os.Looper.getMainLooper()));
                } else {
                    android.view.PixelCopy.request(getWindow(),
                        new android.graphics.Rect(
                            location[0],
                            location[1],
                            location[0] + geckoView.getWidth(),
                            location[1] + geckoView.getHeight()),
                        bitmap,
                        copyResult -> {
                            if (copyResult == android.view.PixelCopy.SUCCESS) {
                                validateAndSetThumbnail(tab, bitmap);
                            }
                            handleScreenshotResult(onComplete);
                        },
                        new android.os.Handler(android.os.Looper.getMainLooper())
                    );
                }
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (Config.URL_BLANK.equals(tab.url)) {
            View layoutHome = findViewById(R.id.layout_home);
            if (layoutHome != null && layoutHome.getVisibility() == View.VISIBLE && layoutHome.getWidth() > 0) {
                try {
                    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                            layoutHome.getWidth(),
                            layoutHome.getHeight(),
                            android.graphics.Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                    layoutHome.draw(canvas);
                    validateAndSetThumbnail(tab, bitmap);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if (onComplete != null) onComplete.run();
    }
    private void captureScreenshot() {
        captureScreenshot(null);
    }
    private void loadUrlInCurrentTab(String url) {
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
    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.hasExtra("url")) {
            String url = intent.getStringExtra("url");
            if (url != null && !url.isEmpty()) {
                loadUrlInCurrentTab(url);
            }
        }
    }
    private void applyThemeToSession(GeckoSession session) {
        if (sRuntime == null || session == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME_THEME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(Config.PREF_KEY_DARK_MODE, false);
        sRuntime.getSettings().setPreferredColorScheme(isDarkMode ? 
            GeckoRuntimeSettings.COLOR_SCHEME_DARK : GeckoRuntimeSettings.COLOR_SCHEME_LIGHT);
        
        // 应用桌面模式设置
        android.content.SharedPreferences defaultPrefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        boolean isDesktopMode = defaultPrefs.getBoolean(Config.PREF_KEY_DESKTOP_MODE, false);
        
        if (isDesktopMode) {
            session.getSettings().setUserAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP);
            session.getSettings().setViewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP);
        } else {
            session.getSettings().setUserAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
            session.getSettings().setViewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        }
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
                }
            })
            .show();
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
        if (geckoView != null) {
            geckoView.releaseSession();
        }
        AppCompatDelegate.setDefaultNightMode(!isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        android.content.Intent intent = getIntent();
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION);
        finish();
        overridePendingTransition(0, 0);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
    
    private void toggleDesktopMode() {
        android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
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
        
        // 更新菜单项的选中状态
        if (navigationView != null) {
            android.view.Menu menu = navigationView.getMenu();
            android.view.MenuItem desktopItem = menu.findItem(R.id.nav_desktop_mode);
            if (desktopItem != null) {
                desktopItem.setChecked(!isDesktopMode);
            }
        }
        
        int messageId = !isDesktopMode ? R.string.msg_desktop_mode_on : R.string.msg_desktop_mode_off;
        Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show();
    }
    
    private void refreshBackgroundEffect() {
        android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        String effect = prefs.getString(Config.PREF_KEY_BG_EFFECT, Config.BG_EFFECT_METEOR);
        DynamicBackgroundView dynamicBgView = findViewById(R.id.dynamic_background_view);
        if (dynamicBgView != null) {
            switch (effect) {
                case "rain":
                    dynamicBgView.setMode(DynamicBackgroundView.EffectMode.RAIN);
                    break;
                case "snow":
                    dynamicBgView.setMode(DynamicBackgroundView.EffectMode.SNOW);
                    break;
                case "aurora":
                    dynamicBgView.setMode(DynamicBackgroundView.EffectMode.AURORA);
                    break;
                case "sakura":
                    dynamicBgView.setMode(DynamicBackgroundView.EffectMode.SAKURA);
                    break;
                case "solid":
                    dynamicBgView.setMode(DynamicBackgroundView.EffectMode.SOLID);
                    int solidColor = prefs.getInt(Config.PREF_KEY_SOLID_BG_COLOR, Config.DEFAULT_SOLID_BG_COLOR);
                    if (dynamicBgView.getChildCount() > 0) {
                        dynamicBgView.getChildAt(0).setBackgroundColor(solidColor);
                    }
                    break;
                case "meteor":
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
        final android.widget.Button btnNewFolder = new android.widget.Button(this);
        btnNewFolder.setText(R.string.title_new_folder);
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
                    com.olsc.manorbrowser.data.PasswordStorage.savePassword(this, 
                        new com.olsc.manorbrowser.data.PasswordItem(url, username, password));
                    Toast.makeText(this, R.string.msg_password_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.action_never, null)
                .show();
        });
    }
    
    private boolean isExternalAppLink(String uri) {
        String[] externalSchemes = {
            "mailto:",
            "tel:",
            "sms:",
            "market:",
            "play.google.com",
            "maps:",
            "geo:",
            "youtube:",
            "twitter:",
            "facebook:",
            "instagram:",
            "whatsapp:",
            "tg:",
            "snapchat:",
            "pinterest:",
            "linkedin:",
            "skype:",
            "slack:",
            "discord:",
            "spotify:",
            "paypal:",
            "fb-messenger:",
            "viber:",
            "telegram:",
            "signal:",
            "teams:",
            "zoomus:",
            "tiktok:",
            "twitch:",
            "etsy:",
            "ebay:",
            "amazon:",
            "netflix:",
            "primevideo:",
            "hulu:",
            "disneyplus:",
            "apple-music:",
            "itunes:",
            "calshow:",
            "caladd:",
            "itms-apps:",
            "itms-books:",
            "itms-itunes:",
            "itms-podcasts:",
            "itms-music:",
            "google.streetview:",
            "comgooglemaps:",
            "comgooglecalendar:",
            "gmm:",
            "fb:",
            "line:",
            "kakaotalk:",
            "kakaoplus:",
            "navermap:",
            "nmap:",
            "daummap:",
            "viki:",
            "watcha:",
            "wavve:",
            "tving:",
            "genie:",
            "bugs:",
            "melon:",
            "flo:",
            "yes24:",
            "aladdin:",
            "kyobo:",
            "ridibooks:",
            "bookcube:",
            "mangabox:",
            "cartoon365:",
            "watchmart:",
            "auction:",
            "gmarket:",
            "11st:",
            "wemakeprice:",
            "coupang:",
            "tmon:",
            "hottracksonline:",
            "yes24movie:",
            "lottecinema:",
            "megabox:",
            "cgv:",
            "watcha-party:",
            "netmarble:",
            "com.nhnent.wannaplay:",
            "com.gamevil.nary:",
            // 中国区应用
            "bilibili:",
            "zhihu:",
            "csdn:",
            "weixin:",
            "wechat:",
            "alipays:",
            "alipay:",
            "taobao:",
            "tbopen:",
            "openapp.jdmobile:",
            "jd:",
            "snssdk1128:",
            "douyin:",
            "mqq:",
            "mqqapi:",
            "tim:",
            "sinaweibo:",
            "weibo:",
            "imeituan:",
            "meituan:",
            "dianping:",
            "orpheus:",
            "neteasemusic:",
            "fleamarket:",
            "xianyu:",
            "youku:",
            "iqiyi:",
            "tudou:",
            "sohuvideo:",
            "baiduyun:",
            "baidunetdisk:",
            "wangpan:",
            "kwai:",
            "gifshow:",
            "pinduoduo:",
            "pddopen:",
            "xiaohongshu:",
            "xhsdiscover:",
            "qqmusic:",
            "kugou:",
            "kuwo:",
            "baidumap:",
            "bdapp:",
            "iosamap:",
            "androidamap:",
            "autonavi:"
        };
        
        for (String scheme : externalSchemes) {
            if (uri.toLowerCase().startsWith(scheme)) {
                return true;
            }
        }
        
        if (uri.contains("//play.google.com/store/apps/details?id=") || 
            uri.contains("//play.google.com/store/search?q=") ||
            uri.contains("//apps.apple.com/") ||
            uri.contains("//itunes.apple.com/")) {
            return true;
        }
        
        return false;
    }
    
    private void handleExternalAppRedirect(String uri) {
        runOnUiThread(() -> {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_external_app_redirect)
                .setMessage(getString(R.string.msg_external_app_redirect, uri))
                .setPositiveButton(R.string.action_allow, (dialog, which) -> {
                    try {
                        android.content.Intent intent = android.content.Intent.parseUri(uri, android.content.Intent.URI_INTENT_SCHEME);
                        
                        android.content.pm.PackageManager pm = getPackageManager();
                        if (intent.resolveActivity(pm) != null) {
                            startActivity(intent);
                        } else {
                            Toast.makeText(this, R.string.error_no_app_found, Toast.LENGTH_SHORT).show();
                        }
                    } catch (java.net.URISyntaxException e) {
                        Toast.makeText(this, R.string.error_invalid_uri, Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                })
                .setNegativeButton(R.string.action_deny, null)
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
                    TabStorage.saveTabs(this, tabs);
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
}
