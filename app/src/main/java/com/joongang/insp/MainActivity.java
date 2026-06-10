package com.joongang.insp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String START_URL = "https://jaitpms.com/printer-monitor/nas-web/m.php";

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraImageUri;

    private static final int REQ_FILE = 1001;
    private static final int REQ_PERMS = 2002;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestStartupPermissions();

        MyFirebaseMessagingService.ensureChannel(this);
        MyFirebaseMessagingService.registerToken(this);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String scheme = u.getScheme();
                if (scheme != null && (scheme.equals("http") || scheme.equals("https"))) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "처리할 앱이 없습니다", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                openFileChooser(params);
                return true;
            }
        });

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "다운로드를 열 수 없습니다", Toast.LENGTH_SHORT).show();
                }
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web.canGoBack()) web.goBack();
                else finish();
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);
    }

    private void openFileChooser(WebChromeClient.FileChooserParams params) {
        Intent content = new Intent(Intent.ACTION_GET_CONTENT);
        content.addCategory(Intent.CATEGORY_OPENABLE);
        content.setType("*/*");
        String[] accept = params.getAcceptTypes();
        if (accept != null && accept.length > 0 && accept[0] != null && !accept[0].isEmpty()) {
            content.setType(accept[0].contains("image") ? "image/*" : accept[0]);
        } else {
            content.setType("*/*");
        }

        List<Intent> extras = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (cam.resolveActivity(getPackageManager()) != null) {
                try {
                    File dir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "");
                    if (!dir.exists()) dir.mkdirs();
                    String name = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
                    File f = new File(dir, name);
                    cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                    cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                    cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    extras.add(cam);
                } catch (Exception ignored) {}
            }
        }

        Intent chooser = Intent.createChooser(content, "사진·파일 선택");
        if (!extras.isEmpty()) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extras.toArray(new Intent[0]));
        }
        try {
            startActivityForResult(chooser, REQ_FILE);
        } catch (Exception e) {
            if (filePathCallback != null) { filePathCallback.onReceiveValue(null); filePathCallback = null; }
            Toast.makeText(this, "파일 선택을 열 수 없습니다", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK) {
                if (data != null && data.getDataString() != null) {
                    results = new Uri[]{ Uri.parse(data.getDataString()) };
                } else if (data != null && data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    results = new Uri[n];
                    for (int i = 0; i < n; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                } else if (cameraImageUri != null) {
                    results = new Uri[]{ cameraImageUri };
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            cameraImageUri = null;
        }
    }

    private void requestStartupPermissions() {
        List<String> need = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            need.add(android.Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                need.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!need.isEmpty())
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }
}
