package app.myworkspace;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQ_SAVE = 1, REQ_OPEN = 2;
    private WebView web;
    private String pendingContent;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return !url.startsWith("file:///android_asset/"); // never navigate away from the bundled app
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_OPEN);
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
    }

    private class Bridge {
        @JavascriptInterface
        public void saveFile(String name, String mime, String content) {
            pendingContent = content;
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType(mime);
            i.putExtra(Intent.EXTRA_TITLE, name);
            startActivityForResult(i, REQ_SAVE);
        }

        @JavascriptInterface
        public void shareText(String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, "Share report"));
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OPEN) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(res == RESULT_OK && data != null && data.getData() != null
                        ? new Uri[]{data.getData()} : null);
                fileCallback = null;
            }
        } else if (req == REQ_SAVE) {
            String content = pendingContent;
            pendingContent = null;
            if (res != RESULT_OK || data == null || data.getData() == null || content == null) return;
            boolean ok = true;
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                ok = false;
            }
            web.evaluateJavascript("window.onFileSaved && onFileSaved(" + ok + ")", null);
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.handleBack ? handleBack() : false", v -> {
            if (!"true".equals(v)) finish();
        });
    }
}
