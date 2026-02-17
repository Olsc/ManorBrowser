package com.olsc.manorbrowser.activity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.data.PasswordItem;
import com.olsc.manorbrowser.data.PasswordStorage;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PasswordManagerActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private PasswordAdapter adapter;
    private List<PasswordItem> passwords;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_manager);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recycler_view);
        emptyView = findViewById(R.id.empty_view);
        FloatingActionButton fab = findViewById(R.id.fab_add);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        loadPasswords();

        fab.setOnClickListener(v -> showAddPasswordDialog());
    }

    private void loadPasswords() {
        passwords = PasswordStorage.loadPasswords(this);
        adapter = new PasswordAdapter(passwords);
        recyclerView.setAdapter(adapter);
        
        if (passwords.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showAddPasswordDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_password, null);
        EditText urlInput = dialogView.findViewById(R.id.input_url);
        EditText usernameInput = dialogView.findViewById(R.id.input_username);
        EditText passwordInput = dialogView.findViewById(R.id.input_password);

        new AlertDialog.Builder(this)
            .setTitle(R.string.title_add_password)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String url = urlInput.getText().toString().trim();
                String username = usernameInput.getText().toString().trim();
                String password = passwordInput.getText().toString().trim();

                if (!url.isEmpty() && !username.isEmpty() && !password.isEmpty()) {
                    PasswordStorage.savePassword(this, new PasswordItem(url, username, password));
                    loadPasswords();
                    Toast.makeText(this, R.string.msg_password_saved, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.msg_fill_all_fields, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showEditPasswordDialog(int position) {
        PasswordItem item = passwords.get(position);
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_password, null);
        EditText urlInput = dialogView.findViewById(R.id.input_url);
        EditText usernameInput = dialogView.findViewById(R.id.input_username);
        EditText passwordInput = dialogView.findViewById(R.id.input_password);

        urlInput.setText(item.url);
        usernameInput.setText(item.username);
        passwordInput.setText(item.password);

        new AlertDialog.Builder(this)
            .setTitle(R.string.title_edit_password)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String url = urlInput.getText().toString().trim();
                String username = usernameInput.getText().toString().trim();
                String password = passwordInput.getText().toString().trim();

                if (!url.isEmpty() && !username.isEmpty() && !password.isEmpty()) {
                    PasswordStorage.deletePassword(this, item);
                    PasswordStorage.savePassword(this, new PasswordItem(url, username, password));
                    loadPasswords();
                    Toast.makeText(this, R.string.msg_password_updated, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void deletePassword(int position) {
        PasswordItem item = passwords.get(position);
        
        new AlertDialog.Builder(this)
            .setTitle(R.string.title_delete_password)
            .setMessage(R.string.msg_confirm_delete_password)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                PasswordStorage.deletePassword(this, item);
                loadPasswords();
                Toast.makeText(this, R.string.msg_password_deleted, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private class PasswordAdapter extends RecyclerView.Adapter<PasswordAdapter.ViewHolder> {
        private final List<PasswordItem> items;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        public PasswordAdapter(List<PasswordItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_password, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PasswordItem item = items.get(position);
            holder.urlText.setText(item.url);
            holder.usernameText.setText(item.username);
            holder.passwordText.setText("••••••••");
            holder.dateText.setText(dateFormat.format(new Date(item.timestamp)));

            holder.itemView.setOnClickListener(v -> showEditPasswordDialog(position));
            
            holder.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(PasswordManagerActivity.this)
                    .setTitle(item.username)
                    .setItems(new String[]{
                        getString(R.string.action_edit),
                        getString(R.string.action_show_password),
                        getString(R.string.action_copy_password),
                        getString(R.string.action_delete)
                    }, (dialog, which) -> {
                        switch (which) {
                            case 0: // Edit
                                showEditPasswordDialog(position);
                                break;
                            case 1: // Show
                                showPasswordDialog(item);
                                break;
                            case 2: // Copy
                                copyToClipboard(item.password);
                                break;
                            case 3: // Delete
                                deletePassword(position);
                                break;
                        }
                    })
                    .show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView urlText, usernameText, passwordText, dateText;

            ViewHolder(View itemView) {
                super(itemView);
                urlText = itemView.findViewById(R.id.text_url);
                usernameText = itemView.findViewById(R.id.text_username);
                passwordText = itemView.findViewById(R.id.text_password);
                dateText = itemView.findViewById(R.id.text_date);
            }
        }
    }

    private void showPasswordDialog(PasswordItem item) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.title_password_details)
            .setMessage(getString(R.string.msg_password_details, 
                item.url, item.username, item.password))
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private void copyToClipboard(String text) {
        android.content.ClipboardManager clipboard = 
            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Password", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.msg_password_copied, Toast.LENGTH_SHORT).show();
        }
    }
}
