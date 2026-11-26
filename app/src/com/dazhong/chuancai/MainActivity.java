package com.dazhong.chuancai;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 1001;
    private static final String FOLDER = "大众川菜";
    private WebView web;
    private ValueCallback<Uri[]> fileCb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        s.setTextZoom(100);
        web.setBackgroundColor(0xFFF4EFE4);
        web.setWebViewClient(new WebViewClient());
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new Host(), "appHost");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                if (fileCb != null) {
                    fileCb.onReceiveValue(null);
                }
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(pickMime(params));
                try {
                    startActivityForResult(Intent.createChooser(i, "选择文件"), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    if (cb != null) cb.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    /** accept 类型归一到系统能识别的 MIME，避免 .json 这类后缀直接传进去报错 */
    private String pickMime(WebChromeClient.FileChooserParams params) {
        String t = "*/*";
        if (params != null) {
            String[] acc = params.getAcceptTypes();
            if (acc != null && acc.length > 0 && acc[0] != null) t = acc[0].toLowerCase();
        }
        if (t.indexOf(',') >= 0) t = t.split(",")[0].trim();
        if (t.indexOf("json") >= 0) return "application/json";
        if (t.indexOf("image") >= 0) return "image/*";
        if (t.equals("") || t.startsWith(".")) return "*/*";
        if (t.indexOf("/") < 0) return "*/*";
        return t;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (fileCb == null) return;
            Uri[] out = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    out = new Uri[n];
                    for (int k = 0; k < n; k++) {
                        out[k] = data.getClipData().getItemAt(k).getUri();
                    }
                } else if (data.getData() != null) {
                    out = new Uri[]{data.getData()};
                }
            }
            fileCb.onReceiveValue(out);
            fileCb = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private static String mimeOf(String filename) {
        String f = filename.toLowerCase();
        if (f.endsWith(".pdf")) return "application/pdf";
        if (f.endsWith(".html") || f.endsWith(".htm")) return "text/html";
        if (f.endsWith(".csv")) return "text/csv";
        if (f.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    /** 写到手机「下载/大众川菜」，同名文件先删再写（覆盖，不堆副本） */
    private String writeBytes(byte[] data, String filename, String mime) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                String rel = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER;
                try {
                    getContentResolver().delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                                    + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                            new String[]{filename, rel});
                } catch (Exception ignored) {
                }
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri != null) {
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os != null) {
                        os.write(data);
                        os.flush();
                        os.close();
                        return "下载/" + FOLDER + "/" + filename;
                    }
                }
            }
            File dir = new File(getExternalFilesDir(null), "backup");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, filename);
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(data);
            fo.flush();
            fo.close();
            return f.getAbsolutePath();
        } catch (Exception e) {
            return "";
        }
    }

    private static Bitmap decodePhoto(String dataUrl) {
        try {
            if (dataUrl == null || dataUrl.length() < 32) return null;
            String b64 = dataUrl;
            int c = b64.indexOf(',');
            if (c > 0) b64 = b64.substring(c + 1);
            byte[] raw = Base64.decode(b64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(raw, 0, raw.length);
        } catch (Exception e) {
            return null;
        }
    }

    /** 按字宽折行（中文逐字，英文按整词不强求） */
    private static List<String> wrapText(String text, Paint paint, float width) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        String[] raw = text.split("\n", -1);
        for (int k = 0; k < raw.length; k++) {
            String line = raw[k];
            if (line.length() == 0) {
                out.add("");
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (paint.measureText(sb.toString() + ch) > width && sb.length() > 0) {
                    out.add(sb.toString());
                    sb.setLength(0);
                }
                sb.append(ch);
            }
            if (sb.length() > 0) out.add(sb.toString());
        }
        return out;
    }

    private String buildPdf(String json, String filename) {
        PdfDocument doc = new PdfDocument();
        try {
            JSONArray arr = new JSONArray(json);
            int W = 595, H = 842, M = 42;
            float colW = W - M * 2 - 156;

            Paint pTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
            pTitle.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
            pTitle.setTextSize(22f);
            pTitle.setColor(Color.rgb(29, 26, 22));

            Paint pMeta = new Paint(Paint.ANTI_ALIAS_FLAG);
            pMeta.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
            pMeta.setTextSize(9.5f);
            pMeta.setColor(Color.rgb(122, 112, 98));

            Paint pName = new Paint(Paint.ANTI_ALIAS_FLAG);
            pName.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
            pName.setTextSize(15f);
            pName.setColor(Color.rgb(29, 26, 22));

            Paint pNote = new Paint(Paint.ANTI_ALIAS_FLAG);
            pNote.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
            pNote.setTextSize(10.5f);
            pNote.setColor(Color.rgb(58, 52, 43));

            Paint pRule = new Paint();
            pRule.setColor(Color.rgb(206, 195, 176));
            pRule.setStrokeWidth(0.7f);

            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");

            int pageNo = 1;
            PdfDocument.Page page = doc.startPage(
                    new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
            Canvas cv = page.getCanvas();
            float y = M + 16;
            cv.drawText("我 的 菜 谱", M, y, pTitle);
            y += 16;
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
            cv.drawText("共 " + arr.length() + " 条 · 导出于 " + stamp + " · 大众川菜选菜", M, y, pMeta);
            y += 12;
            cv.drawLine(M, y, W - M, y, pRule);
            y += 18;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name", "");
                String cat = o.optString("cat", "");
                String note = o.optString("note", "");
                long ts = o.optLong("ts", 0);
                Bitmap bmp = decodePhoto(o.optString("img", ""));

                List<String> lines = wrapText(note, pNote, colW);
                float lineH = 14f;
                float textH = 20f + 11f + lines.size() * lineH;
                float blockH = Math.max(152f, textH) + 18f;

                if (y + blockH > H - M) {
                    doc.finishPage(page);
                    pageNo++;
                    page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
                    cv = page.getCanvas();
                    y = M;
                }

                if (bmp != null) {
                    int bw = bmp.getWidth(), bh = bmp.getHeight();
                    int side = Math.min(bw, bh);
                    int sx = (bw - side) / 2, sy = (bh - side) / 2;
                    Rect src = new Rect(sx, sy, sx + side, sy + side);
                    RectF dst = new RectF(M, y, M + 150f, y + 150f);
                    cv.drawBitmap(bmp, src, dst, null);
                }

                float tx = bmp != null ? M + 150f + 16f : M;
                float ty = y + 14f;
                cv.drawText(name, tx, ty, pName);
                ty += 15f;
                String meta = cat;
                if (ts > 0) meta = meta + (meta.length() > 0 ? " · " : "") + fmt.format(new Date(ts));
                cv.drawText(meta, tx, ty, pMeta);
                ty += 14f;
                for (int k = 0; k < lines.size(); k++) {
                    cv.drawText(lines.get(k), tx, ty, pNote);
                    ty += lineH;
                }

                y += blockH - 10f;
                cv.drawLine(M, y, W - M, y, pRule);
                y += 12f;
            }

            doc.finishPage(page);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.writeTo(bos);
            doc.close();
            return writeBytes(bos.toByteArray(), filename, "application/pdf");
        } catch (Exception e) {
            try { doc.close(); } catch (Exception ignored) { }
            return "";
        }
    }

    /** 供页面调用的原生能力 */
    private class Host {
        @JavascriptInterface
        public void exit() {
            runOnUiThread(new Runnable() {
                public void run() {
                    finish();
                }
            });
        }

        /** 写文本文件（JSON 自动备份） */
        @JavascriptInterface
        public String saveText(String content, String filename) {
            if (content == null || filename == null) return "";
            try {
                return writeBytes(content.getBytes("UTF-8"), filename, mimeOf(filename));
            } catch (Exception e) {
                return "";
            }
        }

        /** 生成 PDF：菜名 / 分类 / 时间 / 备注 / 照片（照片内嵌，单文件） */
        @JavascriptInterface
        public String savePdf(String recordsJson, String filename) {
            if (recordsJson == null) return "";
            return buildPdf(recordsJson, filename == null ? "我的菜谱.pdf" : filename);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null) {
            web.evaluateJavascript(
                    "(function(){"
                            + "var sh=document.querySelector('.sheet.on');"
                            + "if(sh){var c=sh.querySelector('[data-mclose],[data-tclose]');if(c){c.click();}else{sh.classList.remove('on');}return 'sheet';}"
                            + "var el=document.querySelector('.screen.on');"
                            + "if(el&&el.id!=='home'){document.querySelector('[data-back]').click();return el.id;}"
                            + "return 'home';})()",
                    new ValueCallback<String>() {
                        public void onReceiveValue(String v) {
                            if (v != null && v.contains("home")) {
                                finish();
                            }
                        }
                    });
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
