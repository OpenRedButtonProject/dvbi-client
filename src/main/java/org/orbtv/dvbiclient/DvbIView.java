/**
 * ORB Software. Copyright (c) 2026 Ocean Blue Software Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.orbtv.dvbiclient;

import android.content.Context;
import android.net.Uri;
import android.graphics.Color;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.orbtv.companionlibrary.ServePhpOracle;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class DvbIView extends WebView {
    private static final String DVBI_PAGE = "file:///android_asset/polyfill/dvbipage.html";
    private static final String TAG = DvbIView.class.getSimpleName();
    private final Context mContext;
    private String mLastUrl = "about:blank";
    private boolean mSubsEnabled = false;
    private Boolean mPageLoaded = false;
    private Boolean mIsSuspended = false;
    /**
     * True while a DASH URL is selected for compositing (thread-safe; not a WebView getter).
     * Set on {@link #tune} early enough to hide the empty DTVKit plane; this is not a
     * guarantee that DASH frames are presenting. Cleared on every abort path.
     */
    private volatile boolean mDashTuned = false;
    /** Restore compositing after {@link #setPresentationSuspended}(false) if DASH is still selected. */
    private boolean mResumeDashCompositing = false;
    private DashTuneListener mDashTuneListener;

    public interface DashTuneListener {
        /** {@code tuned} hides the empty DTVKit plane; it is not “DASH is presenting”. */
        void onDashTunedChanged(boolean tuned);
    }

    public void setDashTuneListener(DashTuneListener listener) {
        mDashTuneListener = listener;
    }
    /** Drop dash.js events from a previous MPD across instance switch (ERRATA0900). */
    private volatile boolean mSuppressVideoEvents = false;
    private int mViewWidth = 0; // Await onLayoutChange to calculate View width
    private int mAppWidth = 1280; // Apps are 1280 by default

    /** Last video rectangle from the platform; applied once the DVBI page has finished loading. */
    private int mVideoRectX;
    private int mVideoRectY;
    private int mVideoRectW;
    private int mVideoRectH;
    private boolean mVideoRectValid = false;

    private final ArrayList<JSCallback> mJSCallbacks = new ArrayList<>();

    public class JavaScriptInterface {
        Context mContext;

        JavaScriptInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void onVideoEvent(String eventName, String eventData) {
            if (mSuppressVideoEvents) {
                Log.i(TAG, "Suppressing stale video event " + eventName);
                return;
            }
            if ("DVBI_PLAYBACK_ERROR".equals(eventName)) {
                abortDashCompositing();
            }
            Log.d("JavaScriptInterface", "Video event: " + eventName + ", data: " + eventData);
            try {
                JSONObject data = new JSONObject(eventData);
                for(JSCallback handler : mJSCallbacks) {
                    handler.onVideoEvent(eventName, data);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void onStreamEvent(String targetUrl, String eventName, String eventData) {
            Log.d("JavaScriptInterface", "Stream Event: " + targetUrl + ", eventName: " + eventName + ", data: " + eventData);
            try {
                JSONObject data = new JSONObject(eventData);
                for(JSCallback handler : mJSCallbacks) {
                    handler.onStreamEvent(targetUrl, eventName, data);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public DvbIView(Context context) {
        super(context);
        mContext = context;

        // Media player WebView only — must not steal Live Channels digit / channel keys.
        // Linked HbbTV apps run in the ORB browser, not here.
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(false);
        setLongClickable(false);

        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_NONE, null);
        getSettings().setJavaScriptEnabled(true);
        getSettings().setMediaPlaybackRequiresUserGesture(false);
        getSettings().setLoadWithOverviewMode(true);
        getSettings().setDomStorageEnabled(true);
        // serve.php records last_mpd_query_string only on a real MPD GET.
        // Cached dash.js fetches skip PHP, so APPS0350 sees app_id=no query.
        getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        /**
         * Disable support for the 'viewport' HTML meta tag to ensure that the layout width is
         * always equal to the WebView View's width. The initial scale is determined based on this
         * width, to scale the 1280x720 app to fit the WebView.
         */
        getSettings().setUseWideViewPort(false);
        addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int width = right - left;
                if (width != mViewWidth) {
                    mViewWidth = width;
                    updateScale();
                }
            }
        });


        final JavaScriptInterface jsInterface = new JavaScriptInterface(mContext);
        addJavascriptInterface(jsInterface, "Android");

        setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i(TAG, "onPageFinished " + url + "...");
                if (DVBI_PAGE.equals(url)) {
                    synchronized (mPageLoaded) {
                        evaluateJavascript("orb_loadMedia('" + mLastUrl + "', " + mSubsEnabled + ")", null);
                        mPageLoaded = true;
                        applyVideoRectangleJs();
                        mSuppressVideoEvents = false;
                    }
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description,
                    String failingUrl) {
                Log.e(TAG, "onReceivedError " + failingUrl + ": " + description);
                if (DVBI_PAGE.equals(failingUrl)) {
                    abortDashCompositing();
                }
            }
        });
    }

    /**
     * Always non-focusable: this WebView is the DVB-I AV player, not an input surface.
     * Ignores callers (e.g. OverlayView setInteractive) that would re-enable focus.
     */
    @Override
    public void setFocusable(boolean focusable) {
        super.setFocusable(false);
    }

    @Override
    public void setFocusableInTouchMode(boolean focusableInTouchMode) {
        super.setFocusableInTouchMode(false);
    }

    public void addJSCallback(JSCallback handler) {
        if (!mJSCallbacks.contains(handler)) {
            mJSCallbacks.add(handler);
        }
    }

    public void removeJSCallback(JSCallback handler) {
        mJSCallbacks.remove(handler);
    }

    public void suppressVideoEvents() {
        mSuppressVideoEvents = true;
    }

    public boolean tune(String url, boolean enableSubs) {
        Log.i(TAG, "Tuning to url " + url + "...");
        if (url != null && url.startsWith("http")) {
            mSuppressVideoEvents = true;
            // Early enough to hide the empty DTVKit plane; not a decoded-frame guarantee.
            // Stay false while type 1.2 holds the decoders (restore on unsuspend).
            boolean suspended;
            synchronized (mIsSuspended) {
                suspended = Boolean.TRUE.equals(mIsSuspended);
            }
            if (suspended) {
                synchronized (this) {
                    mResumeDashCompositing = true;
                }
                setDashCompositing(false);
            } else {
                setDashCompositing(true);
            }
            mLastUrl = url;
            mSubsEnabled = enableSubs;
            ServePhpOracle.recordMpdUrl(url);
            prefetchManifest(url);
            mContext.getMainExecutor().execute(() -> {
                synchronized (mPageLoaded) {
                    // RF tuneOff + setPresentationSuspended(false) while on about:blank
                    // skips onResume (mPageLoaded=false). A paused WebView will not load
                    // dvbipage.html or fetch the MPD (ERRATA0900 RF→DASH).
                    this.onResume();
                    // Opaque until the HTML5 overlay has frames; TRANSPARENT lets the empty
                    // DTVKit plane (emulator green) show through the video hole.
                    this.setBackgroundColor(Color.BLACK);
                    if (!mIsSuspended) {
                        this.setVisibility(View.VISIBLE);
                        this.clearFocus();
                    }
                    if (Boolean.TRUE.equals(mPageLoaded) && DVBI_PAGE.equals(this.getUrl())) {
                        evaluateJavascript("orb_loadMedia('" + mLastUrl + "', " + enableSubs + ")", null);
                        mSuppressVideoEvents = false;
                    } else {
                        mPageLoaded = false;
                        this.loadUrl(DVBI_PAGE);
                    }
                }
            });
            return true;
        }
        return false;
    }

    public void tuneOff() {
        Log.i(TAG, "Tuning off...");
        mSuppressVideoEvents = true;
        abortDashCompositing();
        mContext.getMainExecutor().execute(() -> {
            synchronized (mPageLoaded) {
                mPageLoaded = false;
                this.setBackgroundColor(Color.TRANSPARENT);
                this.setVisibility(View.INVISIBLE);
                this.loadUrl("about:blank");
            }
        });
    }

    /**
     * Native DASH is selected in this WebView (type 1.1 compositing). Safe from any thread.
     * True is early enough to hide the empty DTVKit plane; it is not “DASH is presenting”.
     */
    public boolean isDashTuned() {
        return mDashTuned;
    }

    /**
     * Hide the empty DTVKit plane while a DASH URL is selected. Not a presenting-frames signal.
     * No-op when the value is unchanged.
     */
    private void setDashCompositing(boolean selected) {
        DashTuneListener listener;
        synchronized (this) {
            if (mDashTuned == selected) {
                return;
            }
            mDashTuned = selected;
            listener = mDashTuneListener;
        }
        Log.i(TAG, "native DASH compositing=" + selected);
        if (listener != null) {
            listener.onDashTunedChanged(selected);
        }
    }

    /** tuneOff, failed load, or any other abort that must not leave compositing stuck true. */
    private void abortDashCompositing() {
        synchronized (this) {
            mResumeDashCompositing = false;
        }
        setDashCompositing(false);
    }

    public void setVideoRectangle(int x, int y, int width, int height) {
        mContext.getMainExecutor().execute(() -> {
            synchronized (mPageLoaded) {
                mVideoRectX = x;
                mVideoRectY = y;
                mVideoRectW = width;
                mVideoRectH = height;
                mVideoRectValid = true;
                if (Boolean.TRUE.equals(mPageLoaded) && DVBI_PAGE.equals(getUrl())) {
                    applyVideoRectangleJs();
                }
            }
        });
    }

    private void applyVideoRectangleJs() {
        if (!mVideoRectValid) {
            return;
        }
        evaluateJavascript("orb_setVideoRectangle(" + mVideoRectX + "," + mVideoRectY + ","
                + mVideoRectW + "," + mVideoRectH + ")", null);
    }

    public void selectTrack(String type, String id) {
        mContext.getMainExecutor().execute(() -> {
            evaluateJavascript("orb_selectTrack('" + type + "'," + id + ")", null);
        });
    }

    public void addStreamEventListener(String targetUrl, String eventName) {
        mContext.getMainExecutor().execute(() -> {
            evaluateJavascript("orb_addStreamEventListener('" + targetUrl + "','" + eventName + "')", null);
        });
    }

    public void removeStreamEventListener(String eventName) {
        mContext.getMainExecutor().execute(() -> {
            evaluateJavascript("orb_removeStreamEventListener('" + eventName + "')", null);
        });
    }

    public void setPresentationSuspended(boolean suspend) {
        synchronized (mIsSuspended) {
            if (mIsSuspended != suspend) {
                mIsSuspended = suspend;
                if (suspend) {
                    // Type 1.2 taking the AV decoders: DASH is no longer the presenting surface.
                    synchronized (this) {
                        mResumeDashCompositing = mDashTuned;
                    }
                    setDashCompositing(false);
                } else {
                    boolean resume;
                    synchronized (this) {
                        resume = mResumeDashCompositing;
                        mResumeDashCompositing = false;
                    }
                    if (resume) {
                        setDashCompositing(true);
                    }
                }
                mContext.getMainExecutor().execute(() -> {
                    if (suspend) {
                        this.setVisibility(View.INVISIBLE);
                        // TODO: we may need to consider an alternative solution, as this will pause the video
                        this.onPause();
                    } else {
                        // Resume even when the page is blank so a later DASH tune can load.
                        this.onResume();
                        if (Boolean.TRUE.equals(mPageLoaded)) {
                            this.setVisibility(View.VISIBLE);
                            this.clearFocus();
                        }
                    }
                });
            }
        }
    }

    /**
     * Hit serve.php on a raw TCP GET so send_mpd() runs, then play back the
     * Set-Cookie on the probe and (via CookieManager) on the 1.1 XHR.
     * ATE PHP ignores session_id() unless that cookie is sent (APPS0350).
     */
    private void prefetchManifest(String url) {
        new Thread(() -> {
            try {
                String bust = url + (url.contains("?") ? "&" : "?") + "_orb=" + System.nanoTime();
                RawHttp result = rawHttpGet(bust, null);
                String cookie = cookiePair(result.setCookie);
                storeServePhpCookie(url, result.setCookie);
                RawHttp replay = rawHttpGet(url, cookie);
                if (replay != null && replay.body != null && !replay.body.isEmpty()) {
                    ServePhpOracle.recordMpdUrl(url, replay.body);
                } else if (result.body != null && !result.body.isEmpty()) {
                    ServePhpOracle.recordMpdUrl(url, result.body);
                }
                String probe = servePhpQueryUrl(url, "mpd_query_parameter", "app_id");
                RawHttp seen = probe != null ? rawHttpGet(probe, cookie) : null;
                Log.i(TAG, "Prefetched MPD HTTP " + result.code
                        + " body=" + result.bodyPrefix
                        + " set-cookie=" + result.setCookie
                        + " replay=" + cookie
                        + " app_id=" + (seen != null ? seen.bodyPrefix : "null"));
            } catch (Exception e) {
                Log.w(TAG, "MPD prefetch failed for " + url + ": " + e);
            }
        }, "dvbi-mpd-prefetch").start();
    }

    private static final class RawHttp {
        final int code;
        final String setCookie;
        final String bodyPrefix;
        final String body;

        RawHttp(int code, String setCookie, String body) {
            this.code = code;
            this.setCookie = setCookie;
            this.body = body != null ? body : "";
            String prefix = this.body.replace("\r", " ").replace("\n", " ").trim();
            if (prefix.length() > 80) {
                prefix = prefix.substring(0, 80);
            }
            this.bodyPrefix = prefix;
        }
    }

    /** HTTP/1.0 so intermediaries do not reuse a cached HttpURLConnection body. */
    private static RawHttp rawHttpGet(String urlString, String cookie) throws Exception {
        URL url = new URL(urlString);
        if (!"http".equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalArgumentException("prefetch only supports http: " + urlString);
        }
        int port = url.getPort() == -1 ? 80 : url.getPort();
        String path = url.getFile();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(url.getHost(), port), 3000);
        socket.setSoTimeout(5000);
        try {
            String request = "GET " + path + " HTTP/1.0\r\n"
                    + "Host: " + url.getHost() + "\r\n"
                    + "Connection: close\r\n"
                    + "Cache-Control: no-cache\r\n"
                    + "Pragma: no-cache\r\n"
                    + (cookie != null ? "Cookie: " + cookie + "\r\n" : "")
                    + "\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            byte[] raw = readAll(socket.getInputStream());
            String text = new String(raw, StandardCharsets.ISO_8859_1);
            int split = text.indexOf("\r\n\r\n");
            String headerBlock = split >= 0 ? text.substring(0, split) : text;
            String body = split >= 0 ? text.substring(split + 4) : "";
            int code = 0;
            String setCookie = null;
            String[] lines = headerBlock.split("\r\n");
            if (lines.length > 0) {
                String[] status = lines[0].split(" ");
                if (status.length >= 2) {
                    try {
                        code = Integer.parseInt(status[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String name = lines[i].substring(0, colon).trim();
                if ("Set-Cookie".equalsIgnoreCase(name)) {
                    setCookie = lines[i].substring(colon + 1).trim();
                }
            }
            return new RawHttp(code, setCookie, body);
        } finally {
            socket.close();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = in.read(tmp)) != -1) {
            buf.write(tmp, 0, n);
        }
        return buf.toByteArray();
    }

    private static String cookiePair(String setCookie) {
        if (setCookie == null || setCookie.isEmpty()) {
            return null;
        }
        int semi = setCookie.indexOf(';');
        return semi >= 0 ? setCookie.substring(0, semi).trim() : setCookie.trim();
    }

    private static void storeServePhpCookie(String url, String setCookie) {
        if (setCookie == null || setCookie.isEmpty()) {
            return;
        }
        try {
            Uri uri = Uri.parse(url);
            String origin = uri.getScheme() + "://" + uri.getHost();
            if (uri.getPort() != -1) {
                origin += ":" + uri.getPort();
            }
            CookieManager cookies = CookieManager.getInstance();
            cookies.setAcceptCookie(true);
            cookies.setCookie(origin + "/", setCookie);
            cookies.setCookie(url, setCookie);
            cookies.flush();
        } catch (Exception e) {
            Log.w(TAG, "Failed to store serve.php Set-Cookie: " + e);
        }
    }

    private static String servePhpQueryUrl(String mpdUrl, String action, String parameter) {
        try {
            Uri uri = Uri.parse(mpdUrl);
            String testId = uri.getQueryParameter("hbbtv_test_id");
            if (testId == null || testId.isEmpty()) {
                return null;
            }
            return uri.buildUpon().clearQuery()
                    .appendQueryParameter("hbbtv_test_id", testId)
                    .appendQueryParameter("action", action)
                    .appendQueryParameter("parameter", parameter)
                    .build().toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void updateScale() {
        mContext.getMainExecutor().execute(() -> {
            int scale = 100;
            if (mViewWidth != 0) {
                scale = (mViewWidth * 100) / mAppWidth;
            }
            Log.d(TAG, "Set scale to " + scale);
            setInitialScale(scale);
        });
    }

    public interface JSCallback {
        void onVideoEvent(String eventName, JSONObject data);
        void onStreamEvent(String targetUrl, String eventName, JSONObject data);
    }
}