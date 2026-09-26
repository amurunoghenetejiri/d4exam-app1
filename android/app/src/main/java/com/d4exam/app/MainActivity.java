package com.d4exam.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

/**
 * D4EXAM MainActivity — Capacitor WebView application (not Chrome).
 *
 * Online: loads https://d4exam.name.ng inside this WebView so login and all
 * routes work. Offline: errorPath offline.html still inside the WebView.
 *
 * Plugins are registered explicitly so they exist even when the WebView loads
 * the remote server.url (capacitor.plugins.json discovery is unreliable there).
 */
public class MainActivity extends BridgeActivity {
  private static final int REQ_POST_NOTIFICATIONS = 8801;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(ExamImmersivePlugin.class);
    registerPlugin(ScreenSharePlugin.class);
    registerPlugin(ee.forgr.biometric.NativeBiometric.class);
    registerPlugin(com.capacitorjs.plugins.localnotifications.LocalNotificationsPlugin.class);
    registerPlugin(com.capacitorjs.plugins.pushnotifications.PushNotificationsPlugin.class);
    registerPlugin(com.capacitorjs.plugins.camera.CameraPlugin.class);
    registerPlugin(com.capacitorjs.plugins.splashscreen.SplashScreenPlugin.class);
    registerPlugin(com.capacitorjs.plugins.statusbar.StatusBarPlugin.class);
    registerPlugin(com.capacitorjs.plugins.app.AppPlugin.class);
    // server.url loads D4EXAM inside this WebView; never hand off to Chrome.
    super.onCreate(savedInstanceState);
    applyChromeColors();
    tagNativeUserAgent();
    installInAppNavigationClient();
    requestNotificationPermission();
  }

  @Override
  public void onResume() {
    super.onResume();
    applyChromeColors();
    tagNativeUserAgent();
    installInAppNavigationClient();
  }

  /**
   * Android 13+ requires an explicit runtime grant before ANY notification,
   * including the MediaProjection foreground-service notification that screen
   * sharing depends on. Ask natively at launch — a remote page cannot.
   */
  private void requestNotificationPermission() {
    try {
      if (Build.VERSION.SDK_INT < 33) return;
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
          == PackageManager.PERMISSION_GRANTED) {
        return;
      }
      ActivityCompat.requestPermissions(
          this, new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
    } catch (Throwable ignored) {
      // Never block launch
    }
  }

  /**
   * Mark the WebView user agent so the web app knows it runs inside the APK
   * even while loading the remote https origin, where isNativePlatform() can
   * be false. Without this, fingerprint + native notifications are skipped.
   */
  private void tagNativeUserAgent() {
    try {
      Bridge bridge = getBridge();
      if (bridge == null) return;
      WebView webView = bridge.getWebView();
      if (webView == null) return;
      WebSettings settings = webView.getSettings();
      if (settings == null) return;
      String ua = settings.getUserAgentString();
      if (ua == null) ua = "";
      if (!ua.contains("D4EXAM_ANDROID_NATIVE")) {
        settings.setUserAgentString(ua.trim() + " D4EXAM_ANDROID_NATIVE Capacitor");
      }
    } catch (Throwable ignored) {
      // Never block launch
    }
  }

  /**
   * Keep D4EXAM + auth/API hosts inside the WebView. Only unknown external
   * hosts use Capacitor's default handling (may open browser).
   */
  private void installInAppNavigationClient() {
    try {
      Bridge bridge = getBridge();
      if (bridge == null) return;
      WebView webView = bridge.getWebView();
      if (webView == null) return;
      webView.setWebViewClient(
          new BridgeWebViewClient(bridge) {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
              if (request == null || request.getUrl() == null) {
                return super.shouldOverrideUrlLoading(view, request);
              }
              if (shouldStayInApp(request.getUrl())) {
                // false = load in this WebView (never Chrome)
                return false;
              }
              return super.shouldOverrideUrlLoading(view, request);
            }
          });
    } catch (Throwable ignored) {
      // Never block launch
    }
  }

  private static boolean shouldStayInApp(Uri uri) {
    try {
      String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
      if ("file".equals(scheme) || "about".equals(scheme) || "data".equals(scheme)
          || "capacitor".equals(scheme) || "http".equals(scheme) || "https".equals(scheme)) {
        // continue host checks for http(s)
      } else {
        return false;
      }
      if ("file".equals(scheme) || "about".equals(scheme) || "data".equals(scheme)
          || "capacitor".equals(scheme)) {
        return true;
      }
      String host = uri.getHost();
      if (host == null) return true;
      host = host.toLowerCase();
      if (host.equals("localhost") || host.equals("127.0.0.1")) return true;
      if (host.contains("d4exam.name.ng")) return true;
      if (host.contains("d4exam-platform.vercel.app")) return true;
      if (host.endsWith("vercel.app") && host.contains("d4exam")) return true;
      if (host.contains("supabase.co")) return true;
      if (host.contains("googleapis.com") || host.contains("gstatic.com")) return true;
      if (host.contains("firebaseio.com")
          || host.contains("firebasestorage.app")
          || host.contains("firebaseapp.com")) {
        return true;
      }
      return false;
    } catch (Throwable t) {
      return true;
    }
  }

  private void applyChromeColors() {
    try {
      Window w = getWindow();
      if (w == null) return;
      int navy = Color.parseColor("#0b1b3a");
      w.setStatusBarColor(navy);
      w.setNavigationBarColor(navy);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        w.setNavigationBarContrastEnforced(false);
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        View decor = w.getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        decor.setSystemUiVisibility(flags);
      }
    } catch (Exception ignored) {
    }
  }
}
