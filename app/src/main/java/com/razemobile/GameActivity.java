package com.razemobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class GameActivity extends Activity {
    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        int slot = getIntent().getIntExtra("slot", 2);
        String renderer = getIntent().getStringExtra("renderer");
        if (renderer == null) renderer = "auto";
        File swfDir = new File(getFilesDir(), "swf");
        swfDir.mkdirs();

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/swf/", new WebViewAssetLoader.InternalStoragePathHandler(this, swfDir))
                .build();

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setLongClickable(false);
        web.setHapticFeedbackEnabled(false);
        web.setOnLongClickListener(v -> true);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"appassets.androidplatform.net".equals(uri.getHost())) return proxy(request);
                WebResourceResponse res = loader.shouldInterceptRequest(uri);
                String p = uri.getPath();
                if (res != null && p != null) {
                    if (p.endsWith(".wasm")) res.setMimeType("application/wasm");
                    else if (p.endsWith(".js")) res.setMimeType("text/javascript");
                }
                return res;
            }
        });

        setContentView(web);
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html?swf=/swf/raze" + slot + ".swf&renderer=" + renderer);
    }

    /** Fetch outside URLs natively and add CORS headers so Ruffle is allowed to read them. */
    private WebResourceResponse proxy(WebResourceRequest req) {
        Map<String, String> cors = new HashMap<>();
        cors.put("Access-Control-Allow-Origin", "*");
        cors.put("Access-Control-Allow-Headers", "*");
        cors.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        String method = req.getMethod();
        try {
            if ("OPTIONS".equals(method)) {
                return new WebResourceResponse("text/plain", "utf-8", 204, "No Content", cors,
                        new ByteArrayInputStream(new byte[0]));
            }
            String host = req.getUrl().getHost();
            if (host != null && host.endsWith("playtomic.com")) {
                // Playtomic shut down. Give the game a well-formed "request failed" answer so it
                // carries on instead of waiting forever for a reply that never comes.
                String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                        + "<data><status>0</status><errorcode>1</errorcode></data>";
                return new WebResourceResponse("text/xml", "utf-8", 200, "OK", cors,
                        new ByteArrayInputStream(xml.getBytes("UTF-8")));
            }
            if (!"GET".equals(method)) return null; // POST bodies aren't visible here; let WebView handle it
            HttpURLConnection c = (HttpURLConnection) new URL(req.getUrl().toString()).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(true);
            String ua = req.getRequestHeaders().get("User-Agent");
            if (ua != null) c.setRequestProperty("User-Agent", ua);
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) in = new ByteArrayInputStream(new byte[0]);
            String type = c.getContentType();
            String mime = "application/octet-stream";
            String enc = null;
            if (type != null) {
                String[] parts = type.split(";");
                mime = parts[0].trim();
                for (String part : parts) {
                    part = part.trim();
                    if (part.toLowerCase().startsWith("charset=")) enc = part.substring(8);
                }
            }
            String reason = c.getResponseMessage();
            if (reason == null || reason.isEmpty()) reason = "OK";
            return new WebResourceResponse(mime, enc, code, reason, cors, in);
        } catch (Exception e) {
            return new WebResourceResponse("text/plain", "utf-8", 502, "Bad Gateway", cors,
                    new ByteArrayInputStream(new byte[0]));
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override protected void onPause() { super.onPause(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        web.destroy();
    }
}
