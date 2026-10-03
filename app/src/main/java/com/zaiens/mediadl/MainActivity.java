package com.zaiens.mediadl;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private EditText input;
    private TextView status;
    private LinearLayout results;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("媒体下载器");
        title.setTextSize(22);
        root.addView(title);

        input = new EditText(this);
        input.setHint("粘贴链接：Twitter/X · Bluesky · 图片/GIF");
        root.addView(input);

        Button btn = new Button(this);
        btn.setText("解析并下载");
        root.addView(btn);

        status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        ScrollView sc = new ScrollView(this);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        sc.addView(results);
        root.addView(sc);

        setContentView(root);
        btn.setOnClickListener(v -> start());
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    private void log(String s) { ui.post(() -> status.setText(s)); }

    private void start() {
        String url = input.getText().toString().trim();
        if (url.isEmpty()) { log("请输入链接"); return; }
        results.removeAllViews();
        log("解析中...");
        pool.execute(() -> {
            try {
                List<String[]> media = parse(url);
                if (media.isEmpty()) { log("未解析到媒体（链接无效/无媒体）"); return; }
                log("解析到 " + media.size() + " 个媒体，开始下载...");
                int ok = 0;
                for (String[] m : media) {
                    try {
                        byte[] data = httpGetBytes(m[0]);
                        String name = guessName(m[0], m[1]);
                        saveToDownloads(name, data);
                        ui.post(() -> addResult(name, m[1]));
                        ok++;
                    } catch (Exception e) { log("下载失败: " + e.getMessage()); }
                }
                final int fok = ok;
                log("完成：" + fok + "/" + media.size() + " 已保存到「下载」目录");
            } catch (Exception e) {
                log("错误: " + e.getMessage());
            }
        });
    }

    private void addResult(String name, String type) {
        TextView tv = new TextView(this);
        tv.setText((type.equals("video") ? "🎬 " : "🖼️ ") + name);
        tv.setPadding(0, dp(6), 0, dp(6));
        results.addView(tv);
    }

    private String guessName(String url, String type) {
        String base = url.split("\\?")[0];
        int i = base.lastIndexOf('/');
        String fn = i >= 0 ? base.substring(i + 1) : "media";
        if (!fn.contains(".")) fn += (type.equals("video") ? ".mp4" : ".jpg");
        return fn;
    }

    private List<String[]> parse(String url) throws Exception {
        List<String[]> out = new ArrayList<>();
        if (url.matches(".*(twitter\\.com|x\\.com)/.*/status/\\d+.*")) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:twitter\\.com|x\\.com)/([^/]+)/status/(\\d+)").matcher(url);
            if (m.find()) {
                String api = "https://api.fxtwitter.com/" + m.group(1) + "/status/" + m.group(2);
                JSONObject d = new JSONObject(new String(httpGetBytes(api), "UTF-8"));
                JSONObject t = d.optJSONObject("tweet");
                if (t != null) {
                    JSONObject media = t.optJSONObject("media");
                    if (media != null) addMedia(out, media.optJSONArray("all"));
                    JSONObject q = t.optJSONObject("quote");
                    if (q != null) {
                        JSONObject qm = q.optJSONObject("media");
                        if (qm != null) addMedia(out, qm.optJSONArray("all"));
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
                    JSONObject rec = posts.getJSONObject(0).optJSONObject("record");
                    JSONObject emb = rec != null ? rec.optJSONObject("embed") : null;
                    if (emb != null) {
                        String et = emb.optString("$type");
                        if (et.equals("app.bsky.embed.images")) {
                            JSONArray imgs = emb.optJSONArray("images");
                            for (int i = 0; i < imgs.length(); i++) {
                                JSONObject blob = imgs.getJSONObject(i).optJSONObject("image");
                                String cid = blob.optJSONObject("ref").optString("$link");
                                String ext = blob.optString("mimeType", "image/jpeg").replace("image/", "");
                                out.add(new String[]{"https://cdn.bsky.app/img/feed_fullsize/plain/" + did + "/" + cid + "@" + ext, "image"});
                            }
                        } else if (et.equals("app.bsky.embed.video")) {
                            JSONObject blob = emb.optJSONObject("video");
                            String cid = blob.optJSONObject("ref").optString("$link");
                            out.add(new String[]{"https://cdn.bsky.app/img/feed_fullsize/plain/" + did + "/" + cid + "@mp4", "video"});
                        }
                    }
                }
            }
        } else if (url.matches(".*\\.(gif|jpe?g|png|webp)(\\?.*)?$")) {
            out.add(new String[]{url, "image"});
        }
        return out;
    }

    private void addMedia(List<String[]> out, JSONArray all) {
        if (all == null) return;
        for (int i = 0; i < all.length(); i++) {
            JSONObject m = all.optJSONObject(i);
            String u = m.optString("url", "");
            if (u.isEmpty()) u = m.optString("media_url_https", "");
            if (u.isEmpty()) continue;
            u = u.replace("name=small", "name=large").replace("name=medium", "name=large").replace("name=orig", "name=large");
            out.add(new String[]{u, m.optString("type", "photo")});
        }
    }

    private byte[] httpGetBytes(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        InputStream in = c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toByteArray();
    }

    private void saveToDownloads(String name, byte[] data) throws Exception {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
        cv.put(MediaStore.Downloads.MIME_TYPE, name.endsWith(".mp4") ? "video/mp4" : "image/jpeg");
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        if (uri != null) {
            java.io.OutputStream os = getContentResolver().openOutputStream(uri);
            os.write(data);
            os.close();
        }
    }
}
