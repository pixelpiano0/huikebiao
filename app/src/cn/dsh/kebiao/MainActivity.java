package cn.dsh.kebiao;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private WebView webView;
    private android.widget.FrameLayout host;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Diag.init(this);
        Diag.add("main", "onCreate");

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });
        applyBarStyle(false);
        enableEdgeToEdge();
        webView.addJavascriptInterface(new Bridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");

        host = new android.widget.FrameLayout(this);
        host.setBackgroundColor(android.graphics.Color.parseColor("#F4F5F7"));
        host.addView(webView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(host);
        applyInsetsFromWindow(0);

        // 把系统栏真实高度传给页面，彻底躲开状态栏和手势条
        webView.setOnApplyWindowInsetsListener(new android.view.View.OnApplyWindowInsetsListener() {
            @Override
            public android.view.WindowInsets onApplyWindowInsets(android.view.View v, android.view.WindowInsets insets) {
                int top, bottom;
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets sb = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars()
                            | android.view.WindowInsets.Type.displayCutout());
                    top = sb.top;
                    bottom = sb.bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                pushInsets(top, bottom);
                return insets;
            }
        });
    }

    public class Bridge {
        @JavascriptInterface
        public void openPortal() {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    startActivityForResult(new Intent(MainActivity.this, PortalActivity.class), 1001);
                }
            });
        }

        @JavascriptInterface
        public String platform() {
            return "android-app";
        }

        @JavascriptInterface
        public void saveWidgetData(final String json) {
            try { TodayWidget.saveData(MainActivity.this, json); } catch (Throwable t) { Diag.addErr("widget", "save", t); }
        }

        @JavascriptInterface
        public void setBars(final int dark) {
            runOnUiThread(new Runnable() {
                @Override public void run() { applyBarStyle(dark == 1); }
            });
        }

        @JavascriptInterface
        public void log(String msg) {
            Diag.add("js", String.valueOf(msg));
        }

        @JavascriptInterface
        public String getDiag() {
            return Diag.dump();
        }

        @JavascriptInterface
        public void clearDiag() {
            Diag.clear();
        }

        @JavascriptInterface
        public void copyText(final String text) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("portal", text));
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            String path = data.getStringExtra("payloadPath");
            if (path == null) return;
            String payload;
            try {
                payload = readFile(path);
            } catch (Throwable t) {
                return;
            }
            if (payload == null || payload.length() == 0) { Diag.add("main", "payload 为空"); return; }
            Diag.add("main", "读到大 JSON " + payload.length() + " 字节");
            // 大 JSON 分片注入，避免单次 evaluateJavascript 过长
            final String text = payload;
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    final int CHUNK = 120000;
                    webView.evaluateJavascript("window.__portalBuf = '';", null);
                    for (int i = 0; i < text.length(); i += CHUNK) {
                        String part = text.substring(i, Math.min(text.length(), i + CHUNK));
                        String q;
                        try { q = JSONObject.quote(part); } catch (Exception e) { return; }
                        webView.evaluateJavascript("window.__portalBuf += " + q + ";", null);
                    }
                    webView.evaluateJavascript(
                        "try{ window.__importPortal(window.__portalBuf); }catch(e){ alert('导入失败: '+e); }", null);
                }
            });
        }
    }

    private String readFile(String path) throws Exception {
        java.io.File f = new java.io.File(path);
        if (!f.exists()) return null;
        byte[] buf = new byte[(int) f.length()];
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n <= 0) break;
                off += n;
            }
        } finally {
            in.close();
        }
        return new String(buf, "UTF-8");
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /** 直接读窗口 insets 给容器加内边距；系统可能延迟一段时间才给出，所以重试几次 */
    private void applyInsetsFromWindow(final int attempt) {
        if (android.os.Build.VERSION.SDK_INT < 30) return;
        try {
            android.view.WindowInsets wi = getWindow().getDecorView().getRootWindowInsets();
            if (wi == null) {
                if (attempt < 20) {
                    handler.postDelayed(new Runnable() {
                        @Override public void run() { applyInsetsFromWindow(attempt + 1); }
                    }, 100);
                }
                return;
            }
            android.graphics.Insets sb = wi.getInsets(
                android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            if (host != null) host.setPadding(0, sb.top, 0, sb.bottom);
            Diag.add("main", "applyInsets top=" + sb.top + " bottom=" + sb.bottom + " attempt=" + attempt);
        } catch (Throwable t) {
            Diag.addErr("main", "applyInsetsFromWindow", t);
        }
    }

    /** 全面屏：页面铺到状态栏和手势条下面，再由页面的 CSS 变量补内边距 */
    private void enableEdgeToEdge() {
        try {
            android.view.Window w = getWindow();
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                w.setDecorFitsSystemWindows(false);
            } else {
                w.getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
            w.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            w.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        } catch (Throwable t) {
            Diag.addErr("main", "enableEdgeToEdge", t);
        }
    }

    private int lastTop = -1, lastBottom = -1;

    private void pushInsets(int topPx, int bottomPx) {
        final float d = getResources().getDisplayMetrics().density;
        final int t = Math.round(topPx / d);
        final int b = Math.round(bottomPx / d);
        if (t == lastTop && b == lastBottom) return;
        lastTop = t; lastBottom = b;
        Diag.add("main", "insets top=" + t + " bottom=" + b);
        if (webView == null) return;
        // 直接给 WebView 加内边距：系统栏区域留给窗口背景，页面视口不再被遮挡
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (webView == null) return;
                int topPx = Math.round(t * d);
                int botPx = Math.round(b * d);
                if (host != null) host.setPadding(0, topPx, 0, botPx);
            }
        });
    }

    /** 让系统状态栏/导航栏和页面同色，避免底部出现一条突兀的色带 */
    private void applyBarStyle(boolean dark) {
        try {
            android.view.Window w = getWindow();
            int bg = dark ? android.graphics.Color.parseColor("#0F1216")
                          : android.graphics.Color.parseColor("#F4F5F7");
            int nav = dark ? android.graphics.Color.parseColor("#0F1216")
                           : android.graphics.Color.WHITE;
            w.setStatusBarColor(bg);
            w.setNavigationBarColor(nav);
            w.getDecorView().setBackgroundColor(bg);
            if (host != null) host.setBackgroundColor(bg);
            android.view.View v = w.getDecorView();
            int flags = v.getSystemUiVisibility();
            if (dark) flags &= ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags |= android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                if (dark) flags &= ~android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags |= android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            v.setSystemUiVisibility(flags);
        } catch (Throwable t) {
            Diag.addErr("main", "applyBarStyle", t);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyInsetsFromWindow(0);
    }

    @Override
    protected void onDestroy() {
        Diag.add("main", "onDestroy");
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
