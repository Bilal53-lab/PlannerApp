package app.lumenpdf;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;

public class MainActivity extends Activity {
    // Bundled files are served to the WebView under this reserved, never-resolved host, so nothing leaves the device.
    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_OPEN = 1;
    private WebView web;
    private TextToSpeech tts;
    private boolean ttsReady, pageReady;
    private String pending, queuedSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0B0D17);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(false);
        s.setAllowFileAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                return serve(req.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                return !HOST.equals(req.getUrl().getHost());
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                pageReady = true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        readIntent(getIntent());
        web.loadUrl("https://" + HOST + "/index.html");
    }

    private WebResourceResponse serve(Uri u) {
        try {
            if (!HOST.equals(u.getHost())) throw new SecurityException();
            String path = u.getPath() == null ? "/" : u.getPath();
            if ("/doc".equals(path)) {
                Uri doc = Uri.parse(u.getQueryParameter("u"));
                if (!"content".equals(doc.getScheme())) throw new SecurityException();
                return new WebResourceResponse("application/pdf", null, getContentResolver().openInputStream(doc));
            }
            String p = path.length() > 1 ? path.substring(1) : "index.html";
            String mime = p.endsWith(".html") ? "text/html" : p.endsWith(".js") ? "application/javascript" : "application/octet-stream";
            InputStream in = getAssets().open(p);
            return new WebResourceResponse(mime, mime.startsWith("application/octet") ? null : "utf-8", in);
        } catch (Exception e) {
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<>(), new ByteArrayInputStream(new byte[0]));
        }
    }

    private String describe(Uri uri) {
        String name = null;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) name = c.getString(i);
            }
        } catch (Exception ignored) {
        }
        if (name == null) name = uri.getLastPathSegment() == null ? "Document.pdf" : uri.getLastPathSegment();
        try {
            return new JSONObject().put("uri", uri.toString()).put("name", name).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void deliver(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { // not every provider offers lasting access
        }
        String json = describe(uri);
        if (json == null) return;
        if (pageReady) web.evaluateJavascript("window.onPdfPicked && onPdfPicked(" + json + ")", null);
        else pending = json;
    }

    private void readIntent(Intent i) {
        if (i != null && Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) deliver(i.getData());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readIntent(intent);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OPEN && res == RESULT_OK && data != null && data.getData() != null) deliver(data.getData());
    }

    private void js(String code) {
        runOnUiThread(() -> web.evaluateJavascript(code, null));
    }

    private void speakNow(String text) {
        tts.stop();
        int max = Math.min(TextToSpeech.getMaxSpeechInputLength(), 3500);
        for (int i = 0; i < text.length(); i += max) {
            int end = Math.min(text.length(), i + max);
            tts.speak(text.substring(i, end), TextToSpeech.QUEUE_ADD, null, end == text.length() ? "last" : "part");
        }
    }

    private class Bridge {
        @JavascriptInterface
        public void pickPdf() {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/pdf");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i, REQ_OPEN);
        }

        @JavascriptInterface
        public String getPending() {
            String p = pending;
            pending = null;
            return p;
        }

        @JavascriptInterface
        public void keepAwake(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void speak(String text) {
            runOnUiThread(() -> {
                if (ttsReady) {
                    speakNow(text);
                    return;
                }
                queuedSpeech = text;
                if (tts != null) return;
                tts = new TextToSpeech(MainActivity.this, status -> {
                    if (status != TextToSpeech.SUCCESS) {
                        tts = null;
                        js("window.onSpeakError && onSpeakError()");
                        return;
                    }
                    ttsReady = true;
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String id) {
                        }

                        @Override
                        public void onDone(String id) {
                            if ("last".equals(id)) js("window.onSpeakDone && onSpeakDone()");
                        }

                        @Override
                        public void onError(String id) {
                            js("window.onSpeakError && onSpeakError()");
                        }
                    });
                    if (queuedSpeech != null) speakNow(queuedSpeech);
                    queuedSpeech = null;
                });
            });
        }

        @JavascriptInterface
        public void stopSpeak() {
            runOnUiThread(() -> {
                queuedSpeech = null;
                if (tts != null) tts.stop();
            });
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.handleBack ? handleBack() : false", v -> {
            if (!"true".equals(v)) finish();
        });
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
