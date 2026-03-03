/**
 * 扩展安装确认代理类
 * 用于处理 GeckoView 扩展安装时的权限请求，当前逻辑为自动同意安装。
 */
package com.olsc.manorbrowser.utils;

import android.app.AlertDialog;
import android.content.Context;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;
import android.widget.Toast;
import androidx.annotation.NonNull;

public class ExtensionPromptDelegate implements WebExtensionController.PromptDelegate {
    private final Context context;

    public ExtensionPromptDelegate(Context context) {
        this.context = context;
    }

    /**
     * 当收到扩展安装请求时触发
     *
     * @param extension                 待安装的扩展信息
     * @param permissions               请求的 API 权限
     * @param origins                   请求的主机权限
     * @param dataCollectionPermissions 数据收集权限
     * @return 返回 GeckoResult，其中包含许可响应
     */
    @Override
    public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
            @NonNull WebExtension extension,
            @NonNull String[] permissions,
            @NonNull String[] origins,
            @NonNull String[] dataCollectionPermissions) {

        // 在主线程提示正在安装
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, context.getString(com.olsc.manorbrowser.R.string.msg_installing_extension), Toast.LENGTH_SHORT).show();
            });
        }
        
        try {
            // 目前逻辑：自动授权安装 (Allow=true)
            WebExtension.PermissionPromptResponse response = new WebExtension.PermissionPromptResponse(true, false, false);
            return GeckoResult.fromValue(response);
        } catch (Exception e) {
            e.printStackTrace();
            return GeckoResult.fromValue(null);
        }
    }
}
