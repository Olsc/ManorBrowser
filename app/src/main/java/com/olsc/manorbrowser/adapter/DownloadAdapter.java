package com.olsc.manorbrowser.adapter;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.olsc.manorbrowser.R;
import com.olsc.manorbrowser.data.DownloadInfo;

import java.util.List;

public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.DownloadViewHolder> {
    
    private final List<DownloadInfo> list;
    private final Context context;

    public DownloadAdapter(Context context, List<DownloadInfo> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public DownloadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_download, parent, false);
        return new DownloadViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DownloadViewHolder holder, int position) {
        DownloadInfo info = list.get(position);
        
        holder.tvFilename.setText(info.title);
        
        int percent = 0;
        if (info.totalBytes > 0) {
            percent = (int) ((info.currentBytes * 100) / info.totalBytes);
        }
        holder.progressBar.setProgress(percent);
        
        String sizeStr = Formatter.formatFileSize(context, info.currentBytes) + " / " + 
                         Formatter.formatFileSize(context, info.totalBytes);
        holder.tvSize.setText(sizeStr);
        
        String statusText;
        int actionIcon = android.R.drawable.ic_menu_close_clear_cancel;
        boolean enableAction = true;
        
        switch (info.status) {
            case 0: // Pending
                statusText = context.getString(R.string.download_status_waiting);
                holder.progressBar.setIndeterminate(true);
                break;
            case 1: // Running
                statusText = context.getString(R.string.download_status_running, percent);
                holder.progressBar.setIndeterminate(info.totalBytes <= 0);
                break;
            case 2: // Paused
                statusText = context.getString(R.string.download_status_paused) + ": " + getPausedReason(info.reason);
                holder.progressBar.setIndeterminate(false);
                break;
            case 3: // Success
                statusText = context.getString(R.string.download_status_completed);
                holder.progressBar.setIndeterminate(false);
                holder.progressBar.setProgress(100);
                actionIcon = android.R.drawable.ic_menu_view; // Open
                break;
            case 4: // Failed
                statusText = context.getString(R.string.download_status_failed) + ": " + getErrorReason(info.reason);
                holder.progressBar.setIndeterminate(false);
                actionIcon = android.R.drawable.ic_menu_revert; // Retry? Or just show fail
                break;
            default:
                statusText = context.getString(R.string.download_status_unknown);
                break;
        }
        holder.tvStatus.setText(statusText);
        holder.btnAction.setImageResource(actionIcon);

        holder.itemView.setOnClickListener(v -> {
            if (info.status == 3 && info.filePath != null) {
                openFile(info);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            showDeleteDialog(info);
            return true;
        });
        
        holder.btnAction.setOnClickListener(v -> {
             if (info.status == 1 || info.status == 0) {
                 // Cancel
                 cancelDownload(info.id);
             } else if (info.status == 3) {
                 // Open
                 openFile(info);
             } else if (info.status == 4) {
                 // Retry (not easily supported via ID, usually re-download URL)
                 Toast.makeText(context, R.string.msg_retry_download, Toast.LENGTH_SHORT).show();
             }
        });
    }

    private void showDeleteDialog(DownloadInfo info) {
        new androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.action_delete)
            .setMessage(R.string.msg_confirm_delete_download)
            .setPositiveButton(R.string.action_delete_with_file, (dialog, which) -> {
                deleteDownload(info, true);
            })
            .setNeutralButton(R.string.action_delete_record_only, (dialog, which) -> {
                deleteDownload(info, false);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void deleteDownload(DownloadInfo info, boolean deleteFile) {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (!deleteFile && info.filePath != null) {
            try {
                android.net.Uri uri = android.net.Uri.parse(info.filePath);
                if ("file".equals(uri.getScheme())) {
                    java.io.File file = new java.io.File(uri.getPath());
                    if (file.exists()) {
                        java.io.File dest = new java.io.File(file.getParent(), "kept_" + file.getName());
                        file.renameTo(dest);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        dm.remove(info.id);
        Toast.makeText(context, R.string.msg_download_cancelled, Toast.LENGTH_SHORT).show();
    }

    @Override
    public int getItemCount() {
        return list.size();
    }
    
    private void openFile(DownloadInfo info) {
        try {
            Uri uri = Uri.parse(info.filePath);
            // If local path is file://, convert to content:// via FileProvider if targeting N+
            // But DownloadManager usually gives content:// if queried via COLUMN_LOCAL_URI?
            // Actually COLUMN_LOCAL_URI returns file path or content uri.
            // Let's rely on stored uri or filePath.
            
            // If filePath starts with file://, use simple intent (might fail on N+)
            // Better use DownloadManager to open
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            Uri fileUri = dm.getUriForDownloadedFile(info.id);
            if (fileUri != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri, info.mimeType);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(intent);
            } else {
                 Toast.makeText(context, R.string.msg_file_not_found, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.msg_file_open_error, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void cancelDownload(long id) {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        dm.remove(id);
        Toast.makeText(context, R.string.msg_download_cancelled, Toast.LENGTH_SHORT).show();
    }

    private String getPausedReason(int reason) {
        switch (reason) {
            case DownloadManager.PAUSED_WAITING_TO_RETRY: return context.getString(R.string.paused_waiting_to_retry);
            case DownloadManager.PAUSED_WAITING_FOR_NETWORK: return context.getString(R.string.paused_waiting_for_network);
            case DownloadManager.PAUSED_QUEUED_FOR_WIFI: return context.getString(R.string.paused_queued_for_wifi);
            default: return context.getString(R.string.download_status_unknown);
        }
    }

    private String getErrorReason(int reason) {
        switch (reason) {
            case DownloadManager.ERROR_CANNOT_RESUME: return context.getString(R.string.error_cannot_resume);
            case DownloadManager.ERROR_DEVICE_NOT_FOUND: return context.getString(R.string.error_device_not_found);
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS: return context.getString(R.string.error_file_already_exists);
            case DownloadManager.ERROR_FILE_ERROR: return context.getString(R.string.error_file_error);
            case DownloadManager.ERROR_HTTP_DATA_ERROR: return context.getString(R.string.error_http_data_error);
            case DownloadManager.ERROR_INSUFFICIENT_SPACE: return context.getString(R.string.error_insufficient_space);
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS: return context.getString(R.string.error_too_many_redirects);
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE: return context.getString(R.string.error_unhandled_http_code);
            default: return context.getString(R.string.error_unknown, reason);
        }
    }

    static class DownloadViewHolder extends RecyclerView.ViewHolder {
        TextView tvFilename, tvStatus, tvSize;
        ProgressBar progressBar;
        ImageView btnAction;

        public DownloadViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFilename = itemView.findViewById(R.id.tv_filename);
            tvStatus = itemView.findViewById(R.id.tv_status);
            tvSize = itemView.findViewById(R.id.tv_size);
            progressBar = itemView.findViewById(R.id.pb_progress);
            btnAction = itemView.findViewById(R.id.btn_action);
        }
    }
}
