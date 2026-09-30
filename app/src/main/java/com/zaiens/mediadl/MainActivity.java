package com.zaiens.mediadl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.*;
import android.widget.EditText;
import android.widget.Toast;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Environment;

public class MainActivity extends Activity {
    private WebView webView;
    private SharedPreferences sp;
    private static final String KEY_URL = "server_url";
    private static final String DEFAULT_URL = "http://192.168.2.126:8891";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("cfg", Context.MODE_PRIVATE);

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        // 文件下载支持
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                String fn = URLUtil.guessFileName(url, contentDisposition, mimetype);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fn);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(req);
                Toast.makeText(this, "开始下载: " + fn, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        setContentView(webView);
        webView.loadUrl(sp.getString(KEY_URL, DEFAULT_URL));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "设置服务器地址");
        menu.add(0, 2, 1, "返回首页");
        menu.add(0, 3, 2, "刷新");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1: showUrlDialog(); return true;
            case 2: webView.loadUrl(sp.getString(KEY_URL, DEFAULT_URL)); return true;
            case 3: webView.reload(); return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showUrlDialog() {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        et.setText(sp.getString(KEY_URL, DEFAULT_URL));
        new AlertDialog.Builder(this)
            .setTitle("服务器地址")
            .setView(et)
            .setPositiveButton("保存并打开", (d, w) -> {
                String u = et.getText().toString().trim();
                if (!u.startsWith("http")) u = "http://" + u;
                sp.edit().putString(KEY_URL, u).apply();
                webView.loadUrl(u);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
