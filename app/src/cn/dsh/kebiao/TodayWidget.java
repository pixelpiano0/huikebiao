package cn.dsh.kebiao;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 桌面小组件：整周课表缩略图（画成位图给 ImageView） */
public class TodayWidget extends AppWidgetProvider {

    private static final String PREFS = "kebiao_widget";
    private static final String[] WD = {"一", "二", "三", "四", "五", "六", "日"};

    public static void saveData(Context c, String json) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("json", json).apply();
        refreshAll(c);
    }

    public static void refreshAll(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, TodayWidget.class));
            for (int id : ids) m.updateAppWidget(id, build(c, id, m));
        } catch (Throwable ignored) {
        }
    }

    static final String ACTION_TICK = "cn.dsh.kebiao.WIDGET_TICK";

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(context, id, mgr));
        scheduleMidnight(context);
    }

    /** 每天零点后自动重绘一次，让「今天」的高亮跟着日期走 */
    static void scheduleMidnight(Context c) {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, TodayWidget.class);
            i.setAction(ACTION_TICK);
            PendingIntent pi = PendingIntent.getBroadcast(c, 12, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 5);
            cal.set(java.util.Calendar.SECOND, 0);
            am.setAndAllowWhileIdle(android.app.AlarmManager.RTC, cal.getTimeInMillis(), pi);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager mgr, int id, Bundle opts) {
        mgr.updateAppWidget(id, build(c, id, mgr));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String a = intent == null ? null : intent.getAction();
        if (ACTION_TICK.equals(a)) scheduleMidnight(context);
        refreshAll(context);
    }

    private static int resId(Context c, String name, String type) {
        return c.getResources().getIdentifier(name, type, c.getPackageName());
    }

    static RemoteViews build(Context c, int widgetId, AppWidgetManager mgr) {
        String pkg = c.getPackageName();
        RemoteViews rv = new RemoteViews(pkg, resId(c, "widget_today", "layout"));
        int imgId = resId(c, "w_img", "id");
        int fbId = resId(c, "w_fallback", "id");

        // 点一下打开 App
        try {
            Intent it = new Intent(c, MainActivity.class);
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, 0, it,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(imgId, pi);
        } catch (Throwable ignored) {
        }

        int wDp = 170, hDp = 240;
        try {
            Bundle o = mgr.getAppWidgetOptions(widgetId);
            int mw = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            int mh = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            if (mw > 0) wDp = mw;
            if (mh > 0) hDp = mh;
        } catch (Throwable ignored) {
        }

        String json = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("json", "");
        try {
            float d = c.getResources().getDisplayMetrics().density;
            // RemoteViews 的位图要走 Binder（~1MB），所以用 RGB_565 并限制尺寸
            int w = Math.min(360, Math.max(280, Math.round(wDp * d * 0.72f)));
            float ratio = hDp / (float) Math.max(1, wDp);
            int h = Math.min(560, Math.round(w * ratio));
            Bitmap bmp = render(c, w, h, json);
            rv.setImageViewBitmap(imgId, bmp);
        } catch (Throwable t) {
            rv.setViewVisibility(fbId, android.view.View.VISIBLE);
        }
        return rv;
    }

    /** 按小米原生 2×3 组件的实测比例套卡片：宽 91%、高 95.5%、上边距 4.4% */
    static Bitmap render(Context c, int W, int H, String json) throws Exception {
        float cardW = W * 0.91f;
        float cardHt = H * 0.965f;      // 比上一版高 1%
        float cardL = (W - cardW) / 2f;
        float cardT = H * 0.034f;       // 顶边同步上移，下沿保持在单元格底部
        Bitmap inner = renderCard(c, Math.round(cardW), Math.round(cardHt), json);
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out);
        Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
        card.setColor(Color.parseColor("#FAFAFB"));
        float r = 14 * (cardW / 170f);
        oc.drawRoundRect(new RectF(cardL, cardT, cardL + cardW, cardT + cardHt), r, r, card);
        oc.drawBitmap(inner, cardL, cardT, null);
        inner.recycle();
        return out;
    }

    /** 只画内容（透明底），由 render 负责套卡片 */
    static Bitmap renderCard(Context c, int W, int H, String json) throws Exception {
        Bitmap content = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(content);
        float s = W / 170f;                       // 以 170dp 宽为基准缩放

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.DEFAULT);

        if (json == null || json.length() < 10) {
            p.setColor(Color.parseColor("#7A828E"));
            p.setTextSize(11 * s);
            drawCenter(cv, "打开 App 导入课表", W / 2f, H / 2f - 6 * s, p);
            p.setTextSize(8 * s);
            drawCenter(cv, "绘课表", W / 2f, H / 2f + 10 * s, p);
            return content;
        }

        JSONObject root = new JSONObject(json);
        JSONArray periods = root.optJSONArray("periods");
        if (periods == null) periods = new JSONArray();
        JSONArray courses = root.optJSONArray("courses");
        if (courses == null) courses = new JSONArray();

        Date now = new Date();
        int todayDow = isoDow(now);
        Date mon = mondayOf(now);
        String monStr = ymd(mon), sunStr = ymd(addDays(mon, 6));

        float padL = 5 * s, padT = 6 * s, padR = 5 * s, padB = 6 * s;
        float timeW = 24 * s;
        float gridL = padL + timeW;
        float gridR = W - padR;
        float colW = (gridR - gridL) / 7f;

        float headTop = padT;
        float headH = 18 * s;          // 日期栏加高
        float headGap = 6 * s;         // 日期栏与课程区的间距
        float gridTop = headTop + headH + headGap;

        // ---- 星期表头 ----
        for (int i = 0; i < 7; i++) {
            float cx = gridL + colW * i + colW / 2f;
            Date d = addDays(mon, i);
            boolean isToday = (i + 1) == todayDow;
            if (isToday) {
                Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
                hl.setColor(Color.parseColor("#2F6BFF"));
                // 只调整高亮块：包住「二」和「11/17」两行文字，文字位置不动
                RectF rr = new RectF(cx - colW / 2f + 0.8f * s, headTop + 3.4f * s,
                        cx + colW / 2f - 0.8f * s, headTop + 20.6f * s);
                cv.drawRoundRect(rr, 5 * s, 5 * s, hl);
            }
            p.setColor(isToday ? Color.WHITE : Color.parseColor("#6B7482"));
            p.setTextSize(6.5f * s);
            drawCenter(cv, WD[i], cx, headTop + headH / 2f - 0.5f * s, p);
            p.setTextSize(5.2f * s);
            p.setColor(isToday ? Color.WHITE : Color.parseColor("#9AA2AE"));
            drawCenter(cv, (d.getMonth() + 1) + "/" + d.getDate(), cx, headTop + headH - 1.2f * s, p);
        }

        // ---- 行：节次 + 午休/晚饭分隔 ----
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < periods.length(); i++) {
            JSONObject pr = periods.optJSONObject(i);
            if (pr == null) continue;
            if (i > 0) {
                JSONObject prev = periods.optJSONObject(i - 1);
                if (prev != null) {
                    int gap = mins(pr.optString("s")) - mins(prev.optString("e"));
                    if (gap >= 60) rows.add(Row.gap(gap));
                }
            }
            rows.add(Row.period(pr.optInt("n"), pr.optString("s"), pr.optString("e")));
        }
        float unit = 0;
        for (Row r : rows) unit += r.gapRow ? 0.30f : 1f;
        if (unit <= 0) unit = 10f;

        // 与原生组件一致：10 节全画（空节次留白，不裁掉）
        int lastPeriod = 10;
        if (periods.length() > 0 && periods.optJSONObject(periods.length() - 1) != null) {
            lastPeriod = periods.optJSONObject(periods.length() - 1).optInt("n", 10);
        }
        lastPeriod = Math.max(4, Math.min(10, lastPeriod));

        // 基准行高按完整 10 节算，保证块大小不变；只在实际要画的范围内做压缩
        float baseTop = padT + 15 * s;
        float drawnUnit = 0;
        for (Row r : rows) {
            if (!r.gapRow && r.n > lastPeriod) break;
            drawnUnit += r.gapRow ? 0.30f : 1f;
        }
        if (drawnUnit <= 0) drawnUnit = unit;
        float rowH = (H - padB - baseTop) / unit;
        float maxRowH = (H - padB - gridTop) / drawnUnit;
        if (rowH > maxRowH) rowH = maxRowH;

        float y = gridTop;
        Map<Integer, float[]> pos = new LinkedHashMap<>();
        for (Row r : rows) {
            if (!r.gapRow && r.n > lastPeriod) break;
            float h = r.gapRow ? rowH * 0.30f : rowH;
            if (!r.gapRow) pos.put(r.n, new float[]{y, h});
            else {
                p.setColor(Color.parseColor("#D9DDE4"));
                p.setStrokeWidth(0.6f * s);
                cv.drawLine(padL, y + h / 2f, gridR, y + h / 2f, p);
                p.setColor(Color.parseColor("#A9B1BC"));
                p.setTextSize(5f * s);
                drawCenter(cv, r.label, (gridL + gridR) / 2f, y + h / 2f, p);
            }
            y += h;
        }

        // 时间列
        for (Row r : rows) {
            if (r.gapRow) continue;
            float[] pr = pos.get(r.n);
            if (pr == null) continue;
            p.setColor(Color.parseColor("#9AA2AE"));
            p.setTextSize(5.2f * s);
            cv.drawText(r.start, padL, pr[0] + 5.4f * s, p);
        }

        // ---- 课程块 ----
        for (int i = 0; i < courses.length(); i++) {
            JSONObject o = courses.optJSONObject(i);
            if (o == null) continue;
            JSONArray dates = o.optJSONArray("dates");
            if (dates != null && dates.length() > 0) {
                boolean hit = false;
                for (int k = 0; k < dates.length(); k++) {
                    String ds = dates.optString(k);
                    if (ds.compareTo(monStr) >= 0 && ds.compareTo(sunStr) <= 0) { hit = true; break; }
                }
                if (!hit) continue;
            }
            int day = o.optInt("day");
            int start = o.optInt("start");
            int span = Math.max(1, o.optInt("span"));
            float[] r0 = pos.get(start);
            if (r0 == null || day < 1 || day > 7) continue;
            float top = r0[0];
            float bottom = top + r0[1] * span;
            float left = gridL + colW * (day - 1) + 1 * s;
            float right = left + colW - 2 * s;
            int hue = o.has("hue") ? o.optInt("hue", 220) : nameHue(o.optString("name"));
            int fill = hsl(hue, 0.62f, 0.55f, 0.30f);
            int line = hsl(hue, 0.62f, 0.50f, 1f);

            Paint pf = new Paint(Paint.ANTI_ALIAS_FLAG);
            pf.setColor(fill);
            RectF rect = new RectF(left, top + 0.6f * s, right, bottom - 0.6f * s);
            cv.drawRoundRect(rect, 3 * s, 3 * s, pf);
            pf.setColor(line);
            cv.drawRoundRect(new RectF(left, top + 0.6f * s, left + 1.4f * s, bottom - 0.6f * s), 1 * s, 1 * s, pf);

            // 课程名：完整显示（行数按色块高度决定）
            p.setColor(Color.parseColor("#323A44"));
            p.setTextSize(6.8f * s);
            String name = o.optString("name", "");
            float pad = 2.0f * s;
            float maxW = right - left - pad * 2;
            List<String> lines = wrap(name, p, maxW);
            Paint.FontMetrics fm = p.getFontMetrics();
            float step = 7.2f * s;
            int maxLines = (int) Math.floor((bottom - top - pad * 2) / step);
            if (maxLines < 1) maxLines = 1;
            if (maxLines > 12) maxLines = 12;
            float lastTextBottom = top;
            float ty = top + pad - fm.ascent;
            int drawn = 0;
            for (int li = 0; li < lines.size() && li < maxLines; li++) {
                cv.drawText(lines.get(li), left + pad, ty, p);
                ty += step;
                drawn++;
            }
            if (drawn > 0) ty -= step;             // 最后一行的基线
            ty += fm.descent;
            lastTextBottom = ty;
            // 结束时间：文字下方还有余量才画，避免和课程名挤在一起
            if (bottom - lastTextBottom > 8 * s) {
                p.setColor(Color.parseColor("#6B7482"));
                p.setTextSize(5f * s);
                String et = endTime(periods, start + span - 1);
                cv.drawText(et, right - p.measureText(et) - 1.5f * s, bottom - 2.2f * s, p);
            }
        }

        return content;
    }

    // ---------- 小工具 ----------
    static class Row {
        boolean gapRow;
        int n;
        String start = "", end = "", label = "";
        static Row gap(int g) { Row r = new Row(); r.gapRow = true; r.label = g >= 100 ? "午" : "晚"; return r; }
        static Row period(int n, String s, String e) { Row r = new Row(); r.n = n; r.start = s; r.end = e; return r; }
    }

    private static void drawCenter(Canvas cv, String t, float cx, float cy, Paint p) {
        Paint.FontMetrics fm = p.getFontMetrics();
        float base = cy - (fm.ascent + fm.descent) / 2f;
        cv.drawText(t, cx - p.measureText(t) / 2f, base, p);
    }

    private static List<String> wrap(String text, Paint p, float maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(text.charAt(i));
            if (p.measureText(sb.toString()) > maxW) {
                sb.deleteCharAt(sb.length() - 1);
                if (sb.length() > 0) out.add(sb.toString());
                sb.setLength(0);
                sb.append(text.charAt(i));
            }
        }
        if (sb.length() > 0) out.add(sb.toString());
        return out;
    }

    private static String endTime(JSONArray periods, int n) {
        for (int i = 0; i < periods.length(); i++) {
            JSONObject pr = periods.optJSONObject(i);
            if (pr != null && pr.optInt("n") == n) return pr.optString("e", "");
        }
        return "";
    }

    private static int mins(String hm) {
        try {
            String[] a = hm.split(":");
            return Integer.parseInt(a[0]) * 60 + Integer.parseInt(a[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    static int nameHue(String name) {
        int h = 0;
        for (int i = 0; i < name.length(); i++) h = (h * 31 + name.charAt(i)) % 360;
        return 190 + (h % 150);
    }

    static int hsl(int hue, float sat, float light, float alpha) {
        float c = (1 - Math.abs(2 * light - 1)) * sat;
        float hp = (hue % 360) / 60f;
        float x = c * (1 - Math.abs(hp % 2 - 1));
        float r = 0, g = 0, b = 0;
        if (hp < 1) { r = c; g = x; }
        else if (hp < 2) { r = x; g = c; }
        else if (hp < 3) { g = c; b = x; }
        else if (hp < 4) { g = x; b = c; }
        else if (hp < 5) { r = x; b = c; }
        else { r = c; b = x; }
        float m = light - c / 2;
        return Color.argb((int) (alpha * 255), (int) ((r + m) * 255), (int) ((g + m) * 255), (int) ((b + m) * 255));
    }

    private static String weekLabel(JSONObject root, Date now) {
        try {
            String mon = ymd(mondayOf(now));
            JSONObject map = root.optJSONObject("weekMap");
            if (map != null && map.has(mon)) return "第 " + map.optInt(mon) + " 周";
            JSONObject tw = root.optJSONObject("teachWeek");
            if (tw != null) {
                String anchor = tw.optString("anchor");
                if (anchor.length() >= 10) {
                    Date a = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(anchor);
                    long diff = (mondayOf(now).getTime() - mondayOf(a).getTime()) / (7L * 86400000L);
                    int w = tw.optInt("week") + (int) Math.round(diff);
                    if (w >= 1 && w <= 30) return "第 " + w + " 周";
                }
            }
        } catch (Throwable ignored) {
        }
        return "本周";
    }

    private static int isoDow(Date d) {
        int d0 = d.getDay();
        return d0 == 0 ? 7 : d0;
    }

    private static Date mondayOf(Date d) {
        Date x = new Date(d.getYear(), d.getMonth(), d.getDate());
        return new Date(x.getTime() - (isoDow(x) - 1) * 86400000L);
    }

    private static Date addDays(Date d, int n) {
        return new Date(d.getTime() + n * 86400000L);
    }

    private static String ymd(Date d) {
        return String.format(Locale.US, "%04d-%02d-%02d", d.getYear() + 1900, d.getMonth() + 1, d.getDate());
    }
}
