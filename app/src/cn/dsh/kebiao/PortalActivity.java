package cn.dsh.kebiao;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class PortalActivity extends Activity {

    private WebView web;
    private TextView hint;
    private LinearLayout overlay;
    private TextView overlayText;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean autoTried = false;   // 已经自动触发过导入
    private boolean autoMode = false;    // 当前这次导入是自动触发的
    private int pollTries = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Diag.init(this);
        Diag.add("portal", "onCreate");

        FrameLayout root = new FrameLayout(this);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.parseColor("#F4F5F7"));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        bar.setBackgroundColor(Color.parseColor("#FFFFFF"));

        Button back = new Button(this);
        back.setText("返回");
        back.setTextSize(13);
        back.setAllCaps(false);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        hint = new TextView(this);
        hint.setText("登录后会自动导入课表");
        hint.setTextSize(12);
        hint.setTextColor(Color.parseColor("#666C76"));
        hint.setSingleLine(true);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        hintLp.setMargins(dp(8), 0, dp(8), 0);

        Button imp = new Button(this);
        imp.setText("手动导入");
        imp.setTextSize(13);
        imp.setAllCaps(false);
        imp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { autoMode = false; grab(); }
        });

        bar.addView(back);
        bar.addView(hint, hintLp);
        bar.addView(imp);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUserAgentString(s.getUserAgentString());
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Diag.add("portal", "onPageFinished " + url);
                view.evaluateJavascript(HOOK_JS, null);
                startLoginWatch();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Diag.add("portal", "onReceivedError " + errorCode + " " + description + " " + failingUrl);
            }
        });
        web.addJavascriptInterface(new ResultBridge(), "PortalBridge");

        content.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        content.addView(web, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 半透明遮罩：正在导入
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(Color.parseColor("#CC000000"));
        overlay.setVisibility(View.GONE);
        ProgressBar pb = new ProgressBar(this);
        overlayText = new TextView(this);
        overlayText.setText("正在导入课程信息…");
        overlayText.setTextColor(Color.WHITE);
        overlayText.setTextSize(15);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tLp.setMargins(0, dp(14), 0, 0);
        overlay.addView(pb);
        overlay.addView(overlayText, tLp);

        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        web.loadUrl("https://portal.csuft.edu.cn/main.html#/ScheduleCenter");
    }

    /** 在门户页面里挂钩 fetch / XHR，把 /portal-api/ 的响应都记下来 */
    private static final String HOOK_JS = "(function(){" +
        "if(window.__dshHooked)return;window.__dshHooked=true;window.__apiLog=[];" +
        "function push(u,t){try{window.__apiLog.push({u:u,b:t});if(window.__apiLog.length>40)window.__apiLog.shift();}catch(e){}}" +
        "try{var of=window.fetch;window.fetch=function(i,o){var u=(typeof i==='string')?i:((i&&i.url)||'');" +
        "return of.apply(this,arguments).then(function(r){try{if(u.indexOf('/portal-api/')>-1){var c=r.clone();c.text().then(function(t){push(u,t);})['catch'](function(){});}}catch(e){}return r;});};}catch(e){}" +
        "try{var oo=XMLHttpRequest.prototype.open,os=XMLHttpRequest.prototype.send;" +
        "XMLHttpRequest.prototype.open=function(m,u){this.__u=u;return oo.apply(this,arguments);};" +
        "XMLHttpRequest.prototype.send=function(){var x=this;x.addEventListener('load',function(){try{if((x.__u||'').indexOf('/portal-api/')>-1)push(x.__u,x.responseText);}catch(e){}});return os.apply(this,arguments);};}catch(e){}" +
        "})();";

    /** 轮询 localStorage.token，登录成功后自动导入 */
    private final Runnable watchRunnable = new Runnable() {
        @Override public void run() {
            if (autoTried || web == null || isFinishing()) return;
            web.evaluateJavascript(
                "(function(){try{var t=localStorage.getItem('token')||'';return t.length>20?'1':'0'}catch(e){return '0'}})()",
                new ValueCallback<String>() {
                    @Override public void onReceiveValue(String v) {
                        if (v != null && v.indexOf("1") >= 0) {
                            autoTried = true;
                            autoMode = true;
                            Diag.add("portal", "检测到登录态，自动导入");
                            showOverlay();
                            handler.postDelayed(new Runnable() {
                                @Override public void run() { grab(); }
                            }, 600);
                        } else if (++pollTries < 120) {
                            handler.postDelayed(watchRunnable, 1000);
                        } else {
                            Diag.add("portal", "等待登录超时");
                            hint.setText("没检测到登录，可手动导入");
                        }
                    }
                });
        }
    };

    private void startLoginWatch() {
        if (autoTried || pollTries >= 120) return;
        handler.removeCallbacks(watchRunnable);
        handler.postDelayed(watchRunnable, 900);
    }

    private void showOverlay() {
        if (overlay != null) overlay.setVisibility(View.VISIBLE);
        if (overlayText != null) overlayText.setText("正在导入课程信息…");
    }

    private void hideOverlay() {
        if (overlay != null) overlay.setVisibility(View.GONE);
    }

    private void grab() {
        Diag.add("portal", "grab 开始 auto=" + autoMode);
        hint.setText("正在读取…");
        String js = "" +
            "(async function(){\n" +
            "  try {\n" +
            "    var t = localStorage.getItem('token') || '';\n" +
            "    var h = {'X-Id-Token': t, 'X-Device-Info':'PC', 'X-Terminal-Info':'PC', 'Accept':'application/json'};\n" +
            "    function fmt(d){var p=function(n){return (n<10?'0':'')+n;};return d.getFullYear()+'-'+p(d.getMonth()+1)+'-'+p(d.getDate());}\n" +
            "    var now = new Date();\n" +
            "    var s1 = fmt(new Date(now.getTime() - 84*86400000));\n" +
            "    var s2 = fmt(new Date(now.getTime() + 140*86400000));\n" +
            "    var out = {token: t ? 'yes' : 'no', log: [], replay: {}, range: [s1, s2], errors: []};\n" +
            "    var types = ['WeekView', 'MonthView', 'ListView', ''];\n" +
            "    var got = false;\n" +
            "    for (var i = 0; i < types.length && !got; i++) {\n" +
            "      var q = '/portal-api/v1/calendar/share/schedule/getEvents?startDate=' + s1 + '&endDate=' + s2 + '&reqType=' + types[i] + '&calendarId=';\n" +
            "      try {\n" +
            "        var r = await fetch(q, {headers: h, credentials: 'include'});\n" +
            "        var txt = await r.text();\n" +
            "        out.replay['range:' + types[i]] = txt;\n" +
            "        var j = null; try { j = JSON.parse(txt); } catch (e) {}\n" +
            "        if (j && j.code === 0 && j.data) got = true;\n" +
            "      } catch (e) { out.errors.push(types[i] + ': ' + e); }\n" +
            "    }\n" +
            "    try {\n" +
            "      var wq = '/portal-api/v1/calendar/share/schedule/getWeekOfTeaching?today=' + fmt(now);\n" +
            "      var wr = await fetch(wq, {headers: h, credentials: 'include'});\n" +
            "      out.week = await wr.text();\n" +
            "    } catch (e) { out.weekErr = '' + e; }\n" +
            "    var md = {};\n" +
            "    try {\n" +
            "      var reD = /\"startDateStr\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})/g;\n" +
            "      var vals = Object.keys(out.replay).map(function(k){return out.replay[k];});\n" +
            "      vals.forEach(function(txt){ var m; while ((m = reD.exec(txt))) { md[m[1]] = 1; } });\n" +
            "    } catch (e) {}\n" +
            "    var mons = {}, mkeys = Object.keys(md).sort();\n" +
            "    for (var mi = 0; mi < mkeys.length; mi++) {\n" +
            "      var dd = new Date(mkeys[mi] + 'T00:00:00');\n" +
            "      var dow = (dd.getDay() + 6) % 7;\n" +
            "      mons[fmt(new Date(dd.getTime() - dow * 86400000))] = 1;\n" +
            "    }\n" +
            "    var mlist = Object.keys(mons).sort(), weekMap = {};\n" +
            "    for (var wi = 0; wi < mlist.length && wi < 40; wi++) {\n" +
            "      try {\n" +
            "        var wr2 = await fetch('/portal-api/v1/calendar/share/schedule/getWeekOfTeaching?today=' + mlist[wi], {headers: h, credentials: 'include'});\n" +
            "        var jw = await wr2.json();\n" +
            "        var aw = jw && jw.code === 0 && jw.data && jw.data.data && jw.data.data.date;\n" +
            "        if (aw && aw.length) { var nw = parseInt(aw[0], 10); if (nw > 0) weekMap[mlist[wi]] = nw; }\n" +
            "      } catch (e) {}\n" +
            "    }\n" +
            "    out.weeks = weekMap;\n" +
            "    var L = (window.__apiLog || []); var keep = [];\n" +
            "    for (var k = 0; k < L.length && keep.length < 6; k++) { if (L[k] && L[k].b && L[k].b.length < 200000) keep.push(L[k]); }\n" +
            "    out.log = keep;\n" +
            "    PortalBridge.done(JSON.stringify(out));\n" +
            "  } catch (e) {\n" +
            "    PortalBridge.done(JSON.stringify({fatal: '' + e}));\n" +
            "  }\n" +
            "})();";
        web.evaluateJavascript(js, null);
    }

    public class ResultBridge {
        @JavascriptInterface
        public void done(final String payload) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    Diag.add("portal", "done payload=" + (payload == null ? "null" : payload.length() + "B"));
                    if (payload == null || payload.length() < 10) {
                        hideOverlay();
                        hint.setText("没拿到数据，可重试");
                        Toast.makeText(PortalActivity.this, "没拿到数据，确认已登录", Toast.LENGTH_SHORT).show();
                        autoMode = false;
                        return;
                    }
                    // 数据可能有几 MB，走 Intent 会触发 TransactionTooLargeException，
                    // 所以先落盘，只把文件路径传回去
                    try {
                        java.io.File f = new java.io.File(getCacheDir(), "portal-payload.json");
                        java.io.FileWriter w = new java.io.FileWriter(f, false);
                        w.write(payload);
                        w.close();
                        Intent data = new Intent();
                        data.putExtra("payloadPath", f.getAbsolutePath());
                        setResult(RESULT_OK, data);
                        finish();
                    } catch (Throwable t) {
                        Diag.addErr("portal", "写 payload 文件失败", t);
                        hideOverlay();
                        hint.setText("写入失败");
                        Toast.makeText(PortalActivity.this, "写入失败: " + t, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
