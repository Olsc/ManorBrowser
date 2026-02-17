package com.olsc.manorbrowser.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.graphics.Insets;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.olsc.manorbrowser.Config;
import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.adapter.BookmarkAdapter;
import com.olsc.manorbrowser.data.BookmarkItem;
import com.olsc.manorbrowser.data.BookmarkStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class BookmarkActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private BookmarkAdapter adapter;
    private List<BookmarkItem> displayList = new ArrayList<>();
    private List<BookmarkItem> rootList;
    private Stack<BookmarkItem> folderStack = new Stack<>();

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
        setContentView(R.layout.activity_bookmarks);

        Toolbar toolbar = findViewById(R.id.toolbar);
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

        recyclerView = findViewById(R.id.rv_bookmarks);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        rootList = BookmarkStorage.loadBookmarks(this);
        refreshList();

        adapter = new BookmarkAdapter(displayList, new BookmarkAdapter.OnBookmarkClickListener() {
            @Override
            public void onBookmarkClick(BookmarkItem item) {
                if (item.type == BookmarkItem.Type.FOLDER) {
                    enterFolder(item);
                } else {
                    openBookmark(item);
                }
            }

            @Override
            public void onBookmarkLongClick(BookmarkItem item) {
                showActionDialog(item);
            }
        });
        recyclerView.setAdapter(adapter);

        findViewById(R.id.fab_add_folder).setOnClickListener(v -> showAddFolderDialog());
    }

    private void refreshList() {
        displayList.clear();
        if (folderStack.isEmpty()) {
            displayList.addAll(rootList);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.title_bookmarks);
            }
        } else {
            BookmarkItem currentFolder = folderStack.peek();
            displayList.addAll(currentFolder.children);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(currentFolder.title);
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void enterFolder(BookmarkItem folder) {
        folderStack.push(folder);
        refreshList();
    }

    private void openBookmark(BookmarkItem item) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("url", item.url);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void showAddFolderDialog() {
        EditText input = new EditText(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        input.setLayoutParams(lp);
        input.setHint(R.string.label_bookmark_title);
        input.setPadding(48, 24, 48, 24);

        new AlertDialog.Builder(this)
            .setTitle(R.string.title_new_folder)
            .setView(input)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String title = input.getText().toString();
                if (!title.isEmpty()) {
                    BookmarkItem folder = new BookmarkItem(title);
                    if (folderStack.isEmpty()) {
                        rootList.add(folder);
                    } else {
                        folderStack.peek().children.add(folder);
                    }
                    saveAll();
                    refreshList();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showActionDialog(BookmarkItem item) {
        String[] options;
        if (item.type == BookmarkItem.Type.FOLDER) {
            options = new String[]{
                getString(R.string.action_edit),
                getString(R.string.action_delete),
                getString(R.string.action_move_to)
            };
        } else {
            options = new String[]{
                getString(R.string.action_edit),
                getString(R.string.action_delete),
                getString(R.string.action_move_to)
            };
        }
        
        new AlertDialog.Builder(this)
            .setTitle(item.title)
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    showEditDialog(item);
                } else if (which == 1) {
                    showDeleteConfirm(item);
                } else if (which == 2) {
                    showMoveDialog(item);
                }
            })
            .show();
    }

    private void showEditDialog(BookmarkItem item) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_password, null);
        EditText titleInput = dialogView.findViewById(R.id.input_url);
        EditText urlInput = dialogView.findViewById(R.id.input_username);
        EditText passwordInput = dialogView.findViewById(R.id.input_password);
        
        // 重用布局，但改变标签含义
        ((com.google.android.material.textfield.TextInputLayout) titleInput.getParent().getParent())
            .setHint(getString(R.string.label_bookmark_title));
        
        titleInput.setText(item.title);
        
        if (item.type == BookmarkItem.Type.FOLDER) {
            urlInput.setVisibility(View.GONE);
            ((View) urlInput.getParent().getParent()).setVisibility(View.GONE);
        } else {
            ((com.google.android.material.textfield.TextInputLayout) urlInput.getParent().getParent())
                .setHint(getString(R.string.label_bookmark_url));
            urlInput.setText(item.url);
        }
        
        // 隐藏密码输入框
        View passwordLayout = (View) passwordInput.getParent().getParent();
        passwordLayout.setVisibility(View.GONE);

        new AlertDialog.Builder(this)
            .setTitle(item.type == BookmarkItem.Type.FOLDER ? 
                R.string.title_edit_folder : R.string.title_edit_bookmark)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String newTitle = titleInput.getText().toString().trim();
                if (newTitle.isEmpty()) {
                    Toast.makeText(this, R.string.msg_fill_all_fields, Toast.LENGTH_SHORT).show();
                    return;
                }
                
                item.title = newTitle;
                if (item.type != BookmarkItem.Type.FOLDER) {
                    String newUrl = urlInput.getText().toString().trim();
                    if (!newUrl.isEmpty()) {
                        item.url = newUrl;
                    }
                }
                
                saveAll();
                refreshList();
                Toast.makeText(this, R.string.msg_bookmark_updated, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showMoveDialog(BookmarkItem item) {
        List<BookmarkItem> allFolders = BookmarkStorage.getAllFolders(this);
        
        List<BookmarkItem> validFolders = new ArrayList<>();
        validFolders.add(new BookmarkItem(getString(R.string.root_folder))); 
        validFolders.get(0).id = -1;

        for (BookmarkItem folder : allFolders) {
            if (isValidMoveTarget(folder, item)) {
                validFolders.add(folder);
            }
        }

        if (validFolders.isEmpty()) {
            Toast.makeText(this, R.string.msg_no_folders, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] folderNames = new String[validFolders.size()];
        for (int i = 0; i < validFolders.size(); i++) {
            folderNames[i] = validFolders.get(i).title;
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.action_move_to);
        builder.setItems(folderNames, (dialog, which) -> {
                BookmarkItem target = validFolders.get(which);
                
                if (folderStack.isEmpty()) {
                    rootList.remove(item);
                } else {
                    folderStack.peek().children.remove(item);
                }
                
                BookmarkStorage.addBookmarkToFolder(BookmarkActivity.this, item, target.id);
                
                Toast.makeText(BookmarkActivity.this, R.string.msg_move_success, Toast.LENGTH_SHORT).show();
                
                rootList = BookmarkStorage.loadBookmarks(BookmarkActivity.this);
                if (!folderStack.isEmpty()) {
                    rebuildStack();
                }
                refreshList();
            });
            
            builder.show();
    }
    
    private boolean isValidMoveTarget(BookmarkItem target, BookmarkItem item) {
        if (target.id == item.id) return false;
        if (item.type != BookmarkItem.Type.FOLDER) return true;
        
        return true; 
    }

    private void rebuildStack() {
        
        List<Long> ids = new ArrayList<>();
        for(BookmarkItem item : folderStack) {
            ids.add(item.id);
        }
        folderStack.clear();
        
        List<BookmarkItem> currentLevel = rootList;
        for(Long id : ids) {
            for(BookmarkItem item : currentLevel) {
                if(item.id == id && item.type == BookmarkItem.Type.FOLDER) {
                    folderStack.push(item);
                    if(item.children != null) {
                       currentLevel = item.children; 
                    }
                    break;
                }
            }
        }
    }

    private void showDeleteConfirm(BookmarkItem item) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.action_delete)
            .setMessage(item.title)
            .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                if (folderStack.isEmpty()) {
                    rootList.remove(item);
                } else {
                    folderStack.peek().children.remove(item);
                }
                saveAll();
                refreshList();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void saveAll() {
        BookmarkStorage.saveBookmarks(this, rootList);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (!folderStack.isEmpty()) {
            folderStack.pop();
            refreshList();
        } else {
            super.onBackPressed();
        }
    }
}
