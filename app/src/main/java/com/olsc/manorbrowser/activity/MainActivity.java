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

import com.google.android.material.navigation.NavigationView;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

import org.mozilla.geckoview.WebResponse;
import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.content.SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME_THEME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(Config.PREF_KEY_DARK_MODE, false);
        AppCompatDelegate.setDefaultNightMode(isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawer_layout);
        geckoView = findViewById(R.id.geckoview);
        tabSwitcher = findViewById(R.id.tab_switcher);
        urlInput = findViewById(R.id.et_url);
        topBar = findViewById(R.id.top_bar);
        progressBar = findViewById(R.id.progress_bar);
        swipeRefresh = findViewById(R.id.swipe_refresh);

        btnBack = findViewById(R.id.btn_back);
        btnRefresh = findViewById(R.id.btn_refresh);

        btnHome = findViewById(R.id.btn_home);
        btnTabs = findViewById(R.id.btn_tabs);
        btnMenu = findViewById(R.id.btn_menu);
        ImageButton btnAddTab = findViewById(R.id.btn_add_tab);

        btnAddTab.setOnClickListener(v -> {
            captureScreenshot(() -> {
                createNewTab(Config.URL_BLANK);
                toggleTabSwitcher();
            });
        });

        navigationView = findViewById(R.id.nav_view);

        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(getApplicationContext());
        }
        applyThemeToSession(null);

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
               // Inject password detection logic
               if (success) {
                    session.loadUri("javascript:" + com.olsc.manorbrowser.utils.JSInjector.INJECT_LOGIN_DETECT);
                    checkAndAutofill(session);
               }
            }
            // @Override public void onSecurityChange(@NonNull GeckoSession session, @NonNull SecurityInformation securityInfo) {}
            @Override public void onSessionStateChange(@NonNull GeckoSession session, @NonNull GeckoSession.SessionState sessionState) {}
        });

        /*
        session.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override
            public GeckoResult<PromptResponse> onPrompt(@NonNull GeckoSession session, @NonNull PromptRequest request) {
                if (request.type == PromptRequest.TYPE_PROMPT_TEXT) { // Actually JS prompt()
                    String msg = request.message;
                    if (msg != null && msg.startsWith("MANOR_SAVE_PASS|")) {
                        handleSavePasswordRequest(msg);
                        return GeckoResult.fromValue(new PromptResponse.Builder(PromptRequest.TYPE_PROMPT_TEXT).dismiss().build());
                    }
                }
                return GeckoResult.fromValue(null);
            }
        });
        */

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
                 runOnUiThread(() -> showWebContextMenu(element));
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

        if (tab.url != null && !tab.url.isEmpty() && !Config.URL_BLANK.equals(tab.url)) {
            session.loadUri(tab.url);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBackgroundEffect();
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
        int cardWidthPx = (int) (280 * displayMetrics.density);
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
            loadUrlInCurrentTab(Config.URL_BLANK);
            if (isTabSwitcherVisible) {
                toggleTabSwitcher();
            }
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
            } else if (id == R.id.nav_theme) {
                navigationView.postDelayed(this::toggleTheme, 300);
            }
            if (isTabSwitcherVisible && id != R.id.nav_theme) toggleTabSwitcher();
            return true;
        });
    }

    private void createNewTab(String url) {
        TabInfo info = new TabInfo(null);
        info.url = url;
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
                if (cardWidth <= 0) cardWidth = 280 * getResources().getDisplayMetrics().density;

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

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (isTabSwitcherVisible) {
            toggleTabSwitcher();
        } else if (getCurrentSession() != null) {
             getCurrentSession().goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void applyThemeToSession(GeckoSession session) {
        if (sRuntime == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences(Config.PREF_NAME_THEME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(Config.PREF_KEY_DARK_MODE, false);
        sRuntime.getSettings().setPreferredColorScheme(isDarkMode ? 
            GeckoRuntimeSettings.COLOR_SCHEME_DARK : GeckoRuntimeSettings.COLOR_SCHEME_LIGHT);
    }

    private void showWebContextMenu(ContextElement element) {
        final List<String> items = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>(); // 0: new tab, 1: download

        String url = element.linkUri != null ? element.linkUri : element.srcUri;
        if (url == null) return;

        items.add(getString(R.string.action_open_new_tab));
        actions.add(0);

        boolean isMedia = (element.type == ContextElement.TYPE_IMAGE || 
                          element.type == ContextElement.TYPE_VIDEO || 
                          element.type == ContextElement.TYPE_AUDIO);
        
        String downloadUrl = element.srcUri != null ? element.srcUri : element.linkUri;

        if (isMedia && downloadUrl != null) {
            items.add(getString(R.string.action_download));
            actions.add(1);
        }

        if (element.linkUri != null) {
            String lowerLink = element.linkUri.toLowerCase();
            if (lowerLink.matches(".*\\.(mp4|mkv|webm|avi|mov|3gp|mp3|wav|ogg|m4a|aac|flac|jpg|jpeg|png|gif|webp|bmp|svg)$")) {
                items.add(getString(R.string.action_download_link));
                actions.add(2); // 2: download link
            }
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(url)
            .setItems(items.toArray(new String[0]), (dialog, which) -> {
                int action = actions.get(which);
                if (action == 0) {
                    createNewTab(url);
                    Toast.makeText(this, R.string.title_new_tab, Toast.LENGTH_SHORT).show();
                } else if (action == 1) {
                    startManualDownload(downloadUrl);
                } else if (action == 2) {
                    startManualDownload(element.linkUri);
                }
            })
            .show();
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

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.title_add_bookmark)
            .setView(layout)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String finalTitle = etTitle.getText().toString();
                String finalUrl = etUrl.getText().toString();
                if (!finalUrl.isEmpty()) {
                    com.olsc.manorbrowser.data.BookmarkStorage.addBookmark(this, 
                        new com.olsc.manorbrowser.data.BookmarkItem(finalTitle, finalUrl));
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
            // Multiple accounts: prompt user
            runOnUiThread(() -> showSelectAccountDialog(session, passwords));
        }
    }

    private void showSelectAccountDialog(GeckoSession session, List<com.olsc.manorbrowser.data.PasswordItem> items) {
       String[] names = new String[items.size()];
       for (int i = 0; i < items.size(); i++) {
           names[i] = items.get(i).username;
       }

       new androidx.appcompat.app.AlertDialog.Builder(this)
           .setTitle(R.string.title_select_account)
           .setItems(names, (dialog, which) -> {
               com.olsc.manorbrowser.data.PasswordItem item = items.get(which);
               session.loadUri("javascript:" + com.olsc.manorbrowser.utils.JSInjector.getFillScript(item.username, item.password));
           })
           .show();
    }

    private void handleSavePasswordRequest(String msg) {
        // Msg format: MANOR_SAVE_PASS|url|user|pass
        String[] parts = msg.split("\\|", 4);
        if (parts.length < 4) return;

        final String url = parts[1];
        final String username = parts[2];
        final String password = parts[3];

        runOnUiThread(() -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
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
}
