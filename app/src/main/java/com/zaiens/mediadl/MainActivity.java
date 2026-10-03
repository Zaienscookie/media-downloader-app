package com.zaiens.mediadl;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.ProxyInfo;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private EditText input;
    private TextView status;
    private GridLayout grid;
    private Button btn;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<String, Bitmap> thumbCache = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("媒体批量下载");
        title.setTextSize(22);
        title.setTypeface(title.getTypeface(), 1);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("支持 Twitter/X · Bluesky · 图片/GIF — 一次粘贴多个链接，自动识别拆分");
        sub.setTextSize(12);
        sub.setTextColor(0xFF8B94A7);
        sub.setPadding(0, dp(4), 0, dp(10));
        root.addView(sub);

        input = new EditText(this);
        input.setHint("每行一个链接，或直接粘贴一大段（自动识别其中所有链接）\n例如：\nhttps://x.com/xxx/status/123\nhttps://bsky.app/profile/xxx/post/abc\nhttps://example.com/pic.jpg");
        input.setMinLines(4);
        input.setGravity(Gravity.TOP);
        root.addView(input);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        btn = new Button(this);
        btn.setText("批量解析下载");
        btn.setOnClickListener(v -> start());
        bar.addView(btn);
        TextView hint = new TextView(this);
        hint.setText("  支持换行 / 空格 / 逗号分隔，自动去重");
        hint.setTextSize(11);
        hint.setTextColor(0xFF5A657A);
        bar.addView(hint);
        root.addView(bar);

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(0xFF8B94A7);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        ScrollView sc = new ScrollView(this);
        grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setUseDefaultMargins(true);
        grid.setPadding(0, dp(6), 0, dp(6));
        sc.addView(grid);
        root.addView(sc);

        setContentView(root);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    private void log(String s, int color) { ui.post(() -> { status.setText(s); status.setTextColor(color); }); }

    private void log(String s) { log(s, 0xFF8B94A7); }

    private List<String> splitUrls(String raw) {
        List<String> urls = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://[^\\s,，、;；\\u4e00-\\u9fff]+").matcher(raw);
        Set<String> seen = new HashSet<>();
        while (m.find()) {
            String u = m.group();
            if (seen.add(u)) urls.add(u);
        }
        return urls;
    }

    private void start() {
        List<String> urls = splitUrls(input.getText().toString());
        if (urls.isEmpty()) { log("请输入链接", 0xFFF87171); return; }
        grid.removeAllViews();
        btn.setEnabled(false);
        log("已识别 " + urls.size() + " 个链接，解析中...");
        pool.execute(() -> {
            List<MediaItem> all = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (int i = 0; i < urls.size(); i++) {
                log("解析链接 " + (i + 1) + "/" + urls.size() + " → " + urls.get(i));
                try {
                    List<MediaItem> r = parse(urls.get(i));
                    if (!r.isEmpty()) all.addAll(r);
                    else errors.add(urls.get(i) + " 未解析到媒体");
                } catch (Exception e) {
                    errors.add(urls.get(i) + " " + e.getMessage());
                }
            }
            if (all.isEmpty()) {
                ui.post(() -> {
                    log("❌ 全部失败：\n" + String.join("\n", errors), 0xFFF87171);
                    btn.setEnabled(true);
                });
                return;
            }
            ui.post(() -> log("解析到 " + all.size() + " 个媒体（来自 " + (urls.size() - errors.size()) + " 个链接），开始下载..."));
            int ok = 0;
            for (MediaItem it : all) {
                log("下载中：" + it.title);
                try {
                    byte[] data = httpGetBytes(it.url);
                    saveToDownloads(nameOf(it), data);
                    ui.post(() -> addCard(it));
                    ok++;
                } catch (Exception e) {
                    log("下载失败: " + it.url + " " + e.getMessage(), 0xFFF87171);
                }
            }
            final int fok = ok;
            ui.post(() -> {
                log("✅ 完成：" + fok + "/" + all.size() + " 已保存到系统「下载」目录"
                    + (errors.isEmpty() ? "" : "，另有 " + errors.size() + " 个链接失败"), 0xFF4ADE80);
                btn.setEnabled(true);
            });
        });
    }

    private void addCard(MediaItem it) {
        FrameLayout wrap = new FrameLayout(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF131A26);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), 0xFF1F2937);
        card.setBackground(bg);

        FrameLayout thumb = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(0xFF000000);
        iv.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(110)));
        thumb.addView(iv);
        if (it.thumb != null && !it.thumb.isEmpty()) {
            loadThumb(it, iv);
        } else {
            TextView empty = new TextView(this);
            empty.setText(it.type.equals("video") ? "🎬" : "🖼️");
            empty.setTextSize(28);
            empty.setGravity(Gravity.CENTER);
            empty.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(110)));
            thumb.addView(empty);
        }
        TextView badge = new TextView(this);
        badge.setText(it.type.equals("video") ? "🎬 视频" : "🖼️ 图片");
        badge.setTextSize(10);
        badge.setTypeface(badge.getTypeface(), 1);
        badge.setTextColor(it.type.equals("video") ? 0xFFFFB74D : 0xFF7EC7FF);
        GradientDrawable bd = new GradientDrawable();
        bd.setColor(0xCC0B0F17);
        bd.setCornerRadius(dp(999));
        bd.setStroke(dp(1), it.type.equals("video") ? 0xFFF59E0B : 0xFF3B82F6);
        badge.setBackground(bd);
        badge.setPadding(dp(6), dp(1), dp(6), dp(1));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.TOP | Gravity.RIGHT;
        blp.setMargins(0, dp(6), dp(6), 0);
        thumb.addView(badge, blp);
        card.addView(thumb);

        TextView meta = new TextView(this);
        meta.setText(it.title == null || it.title.isEmpty() ? "未命名媒体" : it.title);
        meta.setTextSize(11);
        meta.setTextColor(0xFFC7CEDB);
        meta.setMaxLines(2);
        meta.setPadding(dp(8), dp(6), dp(8), dp(8));
        card.addView(meta);

        wrap.addView(card);
        grid.addView(wrap);
    }

    private void loadThumb(MediaItem it, ImageView iv) {
        Bitmap hit = thumbCache.get(it.url);
        if (hit != null) { iv.setImageBitmap(hit); return; }
        pool.execute(() -> {
            try {
                byte[] b = httpGetBytes(it.thumb);
                final Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length);
                if (bm != null) {
                    thumbCache.put(it.url, bm);
                    ui.post(() -> iv.setImageBitmap(bm));
                }
            } catch (Exception e) { /* 缩略图失败忽略 */ }
        });
    }

    private String nameOf(MediaItem it) {
        String base = it.url.split("\\?")[0];
        int i = base.lastIndexOf('/');
        String fn = i >= 0 ? base.substring(i + 1) : "media";
        if (!fn.contains(".")) fn += (it.type.equals("video") ? ".mp4" : ".jpg");
        return System.currentTimeMillis() % 10000 + "_" + fn;
    }

    private static class MediaItem {
        String url, type, thumb, title;
        MediaItem(String u, String t, String th, String ti) { url = u; type = t; thumb = th; title = ti; }
    }

    private List<MediaItem> parse(String url) throws Exception {
        List<MediaItem> out = new ArrayList<>();
        if (url.matches(".*(?:twitter\\.com|x\\.com)/.*/status/\\d+.*")) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:twitter\\.com|x\\.com)/([^/]+)/status/(\\d+)").matcher(url);
            if (m.find()) {
                String api = "https://api.fxtwitter.com/" + m.group(1) + "/status/" + m.group(2);
                JSONObject d = new JSONObject(new String(httpGetBytes(api), "UTF-8"));
                JSONObject t = d.optJSONObject("tweet");
                if (t != null) {
                    String title = t.optString("text", "");
                    JSONObject media = t.optJSONObject("media");
                    if (media != null) addMedia(out, media.optJSONArray("all"), title);
                    JSONObject q = t.optJSONObject("quote");
                    if (q != null) {
                        JSONObject qm = q.optJSONObject("media");
                        if (qm != null) addMedia(out, qm.optJSONArray("all"), title);
                    }
                }
            }
        } else if (url.contains("bsky.app") || url.contains("bsky.social")) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("/profile/([^/]+)/post/(\\w+)").matcher(url);
            if (m.find()) {
                String handle = m.group(1), rkey = m.group(2);
                String rj = new String(httpGetBytes(
                    "https://public.api.bsky.app/xrpc/com.atproto.identity.resolveHandle?handle=" + handle), "UTF-8");
                String did = new JSONObject(rj).optString("did");
                String uri = "at://" + did + "/app.bsky.feed.post/" + rkey;
                String pj = new String(httpGetBytes(
                    "https://public.api.bsky.app/xrpc/app.bsky.feed.getPosts?uris=" + URLEncoder.encode(uri, "UTF-8")), "UTF-8");
                JSONArray posts = new JSONObject(pj).optJSONArray("posts");
                if (posts != null && posts.length() > 0) {
                    JSONObject post = posts.getJSONObject(0);
                    JSONObject rec = post.optJSONObject("record");
                    String title = rec != null ? rec.optString("text", "") : "";
                    JSONObject emb = rec != null ? rec.optJSONObject("embed") : null;
                    if (emb != null) {
                        String et = emb.optString("$type");
                        if (et.equals("app.bsky.embed.images")) {
                            JSONArray imgs = emb.optJSONArray("images");
                            for (int i = 0; i < imgs.length(); i++) {
                                JSONObject blob = imgs.getJSONObject(i).optJSONObject("image");
                                String cid = blob.optJSONObject("ref").optString("$link");
                                String ext = blob.optString("mimeType", "image/jpeg").replace("image/", "");
                                String u = "https://cdn.bsky.app/img/feed_fullsize/plain/" + did + "/" + cid + "@" + ext;
                                out.add(new MediaItem(u, "image", u, title));
                            }
                        } else if (et.equals("app.bsky.embed.video")) {
                            JSONObject blob = emb.optJSONObject("video");
                            String cid = blob.optJSONObject("ref").optString("$link");
                            String u = "https://cdn.bsky.app/img/feed_fullsize/plain/" + did + "/" + cid + "@mp4";
                            out.add(new MediaItem(u, "video", null, title));
                        }
                    }
                }
            }
        } else if (url.matches(".*\\.(gif|jpe?g|png|webp)(\\?.*)?$")) {
            out.add(new MediaItem(url, "image", url, url));
        }
        return out;
    }

    private void addMedia(List<MediaItem> out, JSONArray all, String title) {
        if (all == null) return;
        for (int i = 0; i < all.length(); i++) {
            JSONObject m = all.optJSONObject(i);
            String u = m.optString("url", "");
            if (u.isEmpty()) u = m.optString("media_url_https", "");
            if (u.isEmpty()) continue;
            u = u.replace("name=small", "name=large").replace("name=medium", "name=large").replace("name=orig", "name=large");
            String type = m.optString("type", "photo").equals("video") ? "video" : "image";
            String thumb = m.optString("thumbnail_url", "");
            if (thumb.isEmpty()) thumb = u;
            out.add(new MediaItem(u, type, thumb, title));
        }
    }

    private byte[] httpGetBytes(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection(getProxy());
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
        c.setConnectTimeout(15000);
        c.setReadTimeout(120000);
        c.setInstanceFollowRedirects(true);
        InputStream in = c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toByteArray();
    }

    private Proxy getProxy() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && Build.VERSION.SDK_INT >= 23) {
                ProxyInfo pi = cm.getDefaultProxy();
                if (pi != null && pi.getHost() != null) {
                    return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(pi.getHost(), pi.getPort()));
                }
            }
        } catch (Exception e) { }
        try {
            String host = System.getProperty("http.proxyHost");
            String port = System.getProperty("http.proxyPort");
            if (host != null && !host.isEmpty() && port != null && !port.isEmpty()) {
                return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, Integer.parseInt(port)));
            }
        } catch (Exception e) { }
        return null;
    }

    private void saveToDownloads(String name, byte[] data) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(MediaStore.Downloads.MIME_TYPE, name.endsWith(".mp4") ? "video/mp4" : "image/jpeg");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri != null) {
                OutputStream os = getContentResolver().openOutputStream(uri);
                os.write(data);
                os.close();
            }
        } else {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File f = new File(dir, name);
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(data);
            fos.close();
        }
    }
}
