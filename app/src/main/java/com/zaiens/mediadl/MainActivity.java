package com.zaiens.mediadl;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
import java.net.URI;
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
    private Button btn, btnProxy;
    private SharedPreferences prefs;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<String, Bitmap> thumbCache = new LinkedHashMap<>();
    private final Map<String, ProgressBar> progressBars = new LinkedHashMap<>();

    private static class MediaItem {
        String url, type, thumb, title;
        MediaItem(String u, String t, String th, String ti) { url = u; type = t; thumb = th; title = ti; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0F17);
        int p = dp(16);
        root.setPadding(p, dp(20), p, p);

        TextView title = new TextView(this);
        title.setTextSize(26);
        title.setTypeface(title.getTypeface(), 1);
        SpannableString ts = new SpannableString("媒体批量下载");
        ts.setSpan(new ForegroundColorSpan(0xFF3B82F6), 2, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(ts);
        title.setPadding(0, 0, 0, dp(2));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("支持 Twitter/X · Bluesky · 图片/GIF — 一次粘贴多个链接，自动识别拆分");
        sub.setTextSize(12);
        sub.setTextColor(0xFF8B94A7);
        sub.setPadding(0, dp(4), 0, dp(16));
        root.addView(sub);

        input = new EditText(this);
        input.setHint("每行一个链接，或直接粘贴一大段（自动识别其中所有链接）\n例如：\nhttps://x.com/xxx/status/123\nhttps://bsky.app/profile/xxx/post/abc\nhttps://example.com/pic.jpg");
        input.setHintTextColor(0xFF5A657A);
        input.setTextColor(0xFFE5E7EB);
        input.setTextSize(14);
        input.setMinLines(4);
        input.setGravity(Gravity.TOP);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable ibg = new GradientDrawable();
        ibg.setColor(0xFF131A26);
        ibg.setCornerRadius(dp(14));
        ibg.setStroke(dp(1), 0xFF1F2937);
        input.setBackground(ibg);
        root.addView(input);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(14), 0, dp(4));

        btn = new Button(this);
        btn.setText("批量解析下载");
        btn.setTextSize(16);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTypeface(btn.getTypeface(), 1);
        btn.setAllCaps(false);
        GradientDrawable bgb = new GradientDrawable();
        bgb.setColor(0xFF3B82F6);
        bgb.setCornerRadius(dp(14));
        btn.setBackground(bgb);
        btn.setPadding(dp(24), dp(10), dp(24), dp(10));
        btn.setOnClickListener(v -> start());
        bar.addView(btn);

        btnProxy = new Button(this);
        btnProxy.setText("⚙️ 代理");
        btnProxy.setTextSize(13);
        btnProxy.setTextColor(0xFF9AA3B2);
        btnProxy.setAllCaps(false);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(0x00000000);
        pbg.setCornerRadius(dp(12));
        pbg.setStroke(dp(1), 0xFF374151);
        btnProxy.setBackground(pbg);
        btnProxy.setPadding(dp(14), 0, dp(14), 0);
        btnProxy.setOnClickListener(v -> showProxyDialog());
        bar.addView(btnProxy);

        Button btnAbout = new Button(this);
        btnAbout.setText("ℹ️ 关于");
        btnAbout.setTextSize(13);
        btnAbout.setTextColor(0xFF9AA3B2);
        btnAbout.setAllCaps(false);
        GradientDrawable abg = new GradientDrawable();
        abg.setColor(0x00000000);
        abg.setCornerRadius(dp(12));
        abg.setStroke(dp(1), 0xFF374151);
        btnAbout.setBackground(abg);
        btnAbout.setPadding(dp(14), 0, dp(14), 0);
        btnAbout.setOnClickListener(v -> showAboutDialog());
        bar.addView(btnAbout);
        root.addView(bar);

        TextView hint = new TextView(this);
        hint.setText("支持换行 / 空格 / 逗号分隔，自动去重");
        hint.setTextSize(11);
        hint.setTextColor(0xFF5A657A);
        hint.setPadding(dp(2), 0, 0, dp(12));
        root.addView(hint);

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(0xFF8B94A7);
        status.setPadding(dp(2), dp(4), 0, dp(10));
        root.addView(status);

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setUseDefaultMargins(true);
        grid.setPadding(0, dp(2), 0, dp(6));
        sc.addView(grid);
        root.addView(sc);

        setContentView(root);
        prefs = getSharedPreferences("media_dl", MODE_PRIVATE);
        maybeShowAbout();
    }

    private void maybeShowAbout() {
        int shown = prefs.getInt("about_shown", 0);
        if (shown < 5) {
            prefs.edit().putInt("about_shown", shown + 1).apply();
            ui.postDelayed(this::showAboutDialog, 800);
        }
    }

    private void showAboutDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        box.setPadding(p, dp(12), p, dp(4));
        TextView name = new TextView(this);
        name.setText("📥 媒体批量下载");
        name.setTextSize(17);
        name.setTypeface(name.getTypeface(), 1);
        name.setTextColor(0xFFE5E7EB);
        box.addView(name);
        TextView made = new TextView(this);
        made.setText("本应用由 Zaienscookie 开发");
        made.setTextSize(14);
        made.setTextColor(0xFF8B94A7);
        made.setPadding(0, dp(8), 0, dp(4));
        box.addView(made);
        TextView desc = new TextView(this);
        desc.setText("开源项目，支持 Twitter/X · Bluesky · YouTube · 图片/GIF 批量下载。\n\n如果你发现 Bug、想要新功能或有任何建议，欢迎到 GitHub 提交 Issues 反馈，感谢你的支持！");
        desc.setTextSize(13);
        desc.setTextColor(0xFFC7CEDB);
        desc.setLineSpacing(dp(2), 1.15f);
        box.addView(desc);
        TextView link = new TextView(this);
        link.setText("🌐 https://github.com/Zaienscookie/media-downloader-app");
        link.setTextSize(13);
        link.setTextColor(0xFF3B82F6);
        link.setPadding(0, dp(10), 0, dp(4));
        link.setClickable(true);
        link.setOnClickListener(v -> openRepo());
        box.addView(link);
        new AlertDialog.Builder(this)
            .setTitle("关于")
            .setView(box)
            .setPositiveButton("知道了", null)
            .setNegativeButton("去 GitHub", (d, w) -> openRepo())
            .show();
    }

    private void openRepo() {
        try {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                Uri.parse("https://github.com/Zaienscookie/media-downloader-app")));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private void showProxyDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        box.setPadding(p, dp(10), p, 0);
        final EditText pe = new EditText(this);
        String cur = prefs.getString("proxy_url", "");
        pe.setHint("如 http://127.0.0.1:7890  或 socks5://127.0.0.1:7891");
        pe.setText(cur);
        box.addView(pe);
        TextView tip = new TextView(this);
        tip.setText("支持格式：\nhttp://地址:端口（clash 等）\nsocks4:// 或 socks5://（支持 SOCKS 的代理）\n留空 = 自动（读手机系统代理，VPN/TUN 模式无需配置）");
        tip.setTextSize(12);
        tip.setTextColor(0xFF8B94A7);
        box.addView(tip);
        new AlertDialog.Builder(this)
            .setTitle("网络代理设置")
            .setView(box)
            .setPositiveButton("保存", (d, w) -> {
                String v = pe.getText().toString().trim();
                prefs.edit().putString("proxy_url", v).apply();
                Toast.makeText(this, v.isEmpty() ? "已设为自动" : "代理已保存: " + v, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    private void log(String s, int color) { ui.post(() -> { status.setText(s); status.setTextColor(color); }); }

    private void log(String s) { log(s, 0xFF8B94A7); }

    private List<String> splitUrls(String raw) {
        List<String> urls = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://").matcher(raw);
        List<String> segs = new ArrayList<>();
        int last = -1;
        while (m.find()) {
            if (last >= 0) {
                String seg = raw.substring(last, m.start());
                seg = cleanUrl(seg);
                if (!seg.isEmpty()) segs.add(seg);
            }
            last = m.start();
        }
        if (last >= 0) {
            String seg = cleanUrl(raw.substring(last));
            if (!seg.isEmpty()) segs.add(seg);
        }
        Set<String> seen = new HashSet<>();
        for (String s : segs) if (seen.add(s)) urls.add(s);
        return urls;
    }

    private String cleanUrl(String seg) {
        int end = seg.length();
        while (end > 0) {
            char c = seg.charAt(end - 1);
            if (isUrlChar(c)) break;
            end--;
        }
        return seg.substring(0, end);
    }

    private boolean isUrlChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
            || "-._~:/?#[]@!$&'()*+,;=%".indexOf(c) >= 0;
    }

    private void start() {
        List<String> urls = splitUrls(input.getText().toString());
        if (urls.isEmpty()) { log("请输入链接", 0xFFF87171); return; }
        grid.removeAllViews();
        progressBars.clear();
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
            ui.post(() -> log("解析到 " + all.size() + " 个媒体（来自 " + (urls.size() - errors.size()) + " 个链接），排队下载中..."));
            final List<MediaItem> flist = new ArrayList<>(all);
            final int[] counter = {0};
            final int[] seq = {0};
            final Object lock = new Object();
            for (MediaItem it : flist) {
                final int id = seq[0]++;
                final String fn = nameOf(it);
                ui.post(() -> addCard(it, fn, id));
                pool.execute(() -> {
                    final ProgressBar pb = progressBars.get("dl_" + id);
                    try {
                        if (it.url.contains("playlist.m3u8")) {
                            downloadHlsToFile(it.url, fn, pct -> ui.post(() -> {
                                if (pb != null) pb.setProgress(pct);
                            }));
                        } else {
                            downloadToFile(it.url, fn, pct -> ui.post(() -> {
                                if (pb != null) pb.setProgress(pct);
                            }));
                        }
                        if (pb != null) ui.post(() -> pb.setProgress(100));
                    } catch (Exception e) {
                        log("下载失败: " + it.url + " " + e.getMessage(), 0xFFF87171);
                    }
                    synchronized (lock) { counter[0]++; }
                    if (counter[0] == flist.size()) {
                        ui.post(() -> {
                            log("✅ 完成：" + flist.size() + " 个媒体已保存到系统「下载」目录"
                                + (errors.isEmpty() ? "" : "，另有 " + errors.size() + " 个链接解析失败"), 0xFF4ADE80);
                            btn.setEnabled(true);
                        });
                    }
                });
            }
        });
    }

    private void addCard(MediaItem it, String fn, int id) {
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
        GradientDrawable tg = new GradientDrawable();
        tg.setColor(0xFF000000);
        float[] rad = new float[]{dp(10), dp(10), dp(10), dp(10), 0, 0, 0, 0};
        tg.setCornerRadii(rad);
        iv.setBackground(tg);
        iv.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(110)));
        thumb.addView(iv);
        if (it.thumb != null && !it.thumb.isEmpty()) {
            loadThumb(it, iv);
        } else {
            TextView empty = new TextView(this);
            empty.setText(it.type.equals("video") ? "🎬" : "🖼️");
            empty.setTextSize(30);
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
        badge.setPadding(dp(8), dp(2), dp(8), dp(2));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.TOP | Gravity.RIGHT;
        blp.setMargins(0, dp(6), dp(6), 0);
        thumb.addView(badge, blp);
        card.addView(thumb);

        TextView meta = new TextView(this);
        meta.setText(it.title == null || it.title.isEmpty() ? "未命名媒体" : it.title);
        meta.setTextSize(12);
        meta.setTextColor(0xFFD5DCE8);
        meta.setMaxLines(2);
        meta.setPadding(dp(10), dp(8), dp(10), dp(2));
        card.addView(meta);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgress(0);
        pb.setPadding(dp(10), 0, dp(10), 0);
        try {
            android.content.res.ColorStateList tint = android.content.res.ColorStateList.valueOf(0xFF3B82F6);
            pb.setProgressTintList(tint);
            pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1F2937));
        } catch (Exception e) { }
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
        plp.setMargins(dp(10), dp(4), dp(10), dp(6));
        card.addView(pb, plp);
        progressBars.put("dl_" + id, pb);

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
        if (it.url.contains("playlist.m3u8")) fn += ".ts";
        else if (!fn.contains(".")) fn += (it.type.equals("video") ? ".mp4" : ".jpg");
        return System.currentTimeMillis() % 10000 + "_" + fn;
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
                            String enc = URLEncoder.encode(did, "UTF-8");
                            String u = "https://video.bsky.app/watch/" + enc + "/" + cid + "/playlist.m3u8";
                            String th = "https://video.bsky.app/watch/" + enc + "/" + cid + "/thumbnail.jpg";
                            out.add(new MediaItem(u, "video", th, title));
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
        HttpURLConnection c;
        Proxy p = getProxy();
        if (p != null) {
            c = (HttpURLConnection) new URL(urlStr).openConnection(p);
        } else {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
        }
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

    private String httpGetString(String urlStr) throws Exception {
        return new String(httpGetBytes(urlStr), "UTF-8");
    }

    private interface ProgressCb {
        void onProgress(int pct);
    }

    private void downloadToFile(String urlStr, String name, ProgressCb cb) throws Exception {
        HttpURLConnection c;
        Proxy p = getProxy();
        if (p != null) {
            c = (HttpURLConnection) new URL(urlStr).openConnection(p);
        } else {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
        }
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
        c.setConnectTimeout(15000);
        c.setReadTimeout(180000);
        c.setInstanceFollowRedirects(true);
        int total = c.getContentLength();
        OutputStream os = openDownloadStream(name);
        InputStream in = c.getInputStream();
        byte[] buf = new byte[65536];
        int n;
        long read = 0;
        while ((n = in.read(buf)) > 0) {
            os.write(buf, 0, n);
            read += n;
            if (cb != null && total > 0) {
                cb.onProgress((int) Math.min(100, read * 100 / total));
            }
        }
        in.close();
        os.close();
    }

    private void downloadHlsToFile(String masterUrl, String name, ProgressCb cb) throws Exception {
        String master = httpGetString(masterUrl);
        List<String[]> variants = new ArrayList<>();
        String[] lines = master.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].trim();
            if (l.startsWith("#EXT-X-STREAM-INF")) {
                if (i + 1 < lines.length) {
                    long bw = 0;
                    long res = 0;
                    java.util.regex.Matcher bm = java.util.regex.Pattern.compile("BANDWIDTH=(\\d+)").matcher(l);
                    if (bm.find()) bw = Long.parseLong(bm.group(1));
                    java.util.regex.Matcher rm = java.util.regex.Pattern.compile("RESOLUTION=(\\d+)x(\\d+)").matcher(l);
                    if (rm.find()) res = Long.parseLong(rm.group(1)) * Long.parseLong(rm.group(2));
                    variants.add(new String[]{lines[i + 1].trim(), String.valueOf(bw), String.valueOf(res)});
                }
            }
        }
        String variantUrl = null;
        if (!variants.isEmpty()) {
            long best = -1;
            for (String[] v : variants) {
                long score = Long.parseLong(v[2]);   // 优先按分辨率选
                if (score <= 0) score = Long.parseLong(v[1]);  // 无分辨率则按码率
                if (score > best) { best = score; variantUrl = v[0]; }
            }
        } else {
            variantUrl = masterUrl;
        }
        if (variantUrl == null) throw new Exception("HLS: 无可用清晰度");
        if (!variantUrl.startsWith("http")) {
            int slash = masterUrl.lastIndexOf('/');
            variantUrl = masterUrl.substring(0, slash + 1) + variantUrl;
        }
        String vplay = httpGetString(variantUrl);
        int q = variantUrl.indexOf('?');
        String base = variantUrl.substring(0, (q > 0 ? q : variantUrl.length()));
        base = base.substring(0, base.lastIndexOf('/') + 1);
        List<String> segs = new ArrayList<>();
        for (String l : vplay.split("\n")) {
            String s = l.trim();
            if (s.isEmpty() || s.startsWith("#")) continue;
            if (s.contains("session_id=")) {
                s = s.replaceAll("&?session_id=[^&]*", "");
            }
            if (!s.isEmpty()) segs.add(s);
        }
        if (segs.isEmpty()) throw new Exception("HLS: 视频流中没有分片");
        OutputStream os = openDownloadStream(name);
        try {
            for (int si = 0; si < segs.size(); si++) {
                String seg = segs.get(si);
                String segUrl = seg.startsWith("http") ? seg : base + seg;
                HttpURLConnection c;
                Proxy p = getProxy();
                if (p != null) {
                    c = (HttpURLConnection) new URL(segUrl).openConnection(p);
                } else {
                    c = (HttpURLConnection) new URL(segUrl).openConnection();
                }
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
                c.setConnectTimeout(15000);
                c.setReadTimeout(180000);
                c.setInstanceFollowRedirects(true);
                InputStream in = c.getInputStream();
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                in.close();
                if (cb != null) {
                    cb.onProgress((int) ((si + 1L) * 100 / segs.size()));
                }
            }
        } finally {
            os.close();
        }
    }

    private Proxy getProxy() {
        String cfg = prefs != null ? prefs.getString("proxy_url", "") : "";
        if (cfg != null && !cfg.trim().isEmpty()) {
            try {
                String s = cfg.trim();
                if (!s.contains("://")) s = "http://" + s;
                URI u = URI.create(s);
                String host = u.getHost();
                int port = u.getPort();
                if (host != null && port > 0) {
                    String scheme = (u.getScheme() == null ? "" : u.getScheme()).toLowerCase();
                    if (scheme.startsWith("socks")) {
                        return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port));
                    }
                    return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
                }
            } catch (Exception e) { }
        }
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

    private OutputStream openDownloadStream(String name) throws Exception {
        String mime;
        if (name.endsWith(".ts")) mime = "video/mp2t";
        else if (name.endsWith(".mp4")) mime = "video/mp4";
        else if (name.endsWith(".gif")) mime = "image/gif";
        else if (name.endsWith(".png")) mime = "image/png";
        else if (name.endsWith(".webp")) mime = "image/webp";
        else mime = "image/jpeg";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(MediaStore.Downloads.MIME_TYPE, mime);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri != null) {
                return getContentResolver().openOutputStream(uri);
            }
            throw new Exception("无法创建下载文件");
        } else {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File f = new File(dir, name);
            return new FileOutputStream(f);
        }
    }
}
