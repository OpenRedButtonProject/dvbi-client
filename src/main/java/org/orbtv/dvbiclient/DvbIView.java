package org.orbtv.dvbiclient;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class DvbIView extends WebView {
    private static final String DVBI_PAGE = "file:///android_asset/polyfill/dvbipage.html";
    private static final String TAG = DvbIView.class.getSimpleName();
    private final Context mContext;
    private String mLastUrl = "about:blank";
    private boolean mSubsEnabled = false;
    private Boolean mPageLoaded = false;
    private Boolean mIsSuspended = false;
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
        getSettings().setJavaScriptEnabled(true);
        getSettings().setMediaPlaybackRequiresUserGesture(false);
        getSettings().setLoadWithOverviewMode(true);
        getSettings().setDomStorageEnabled(true);
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
            mLastUrl = url;
            mSubsEnabled = enableSubs;
            mContext.getMainExecutor().execute(() -> {
                synchronized (mPageLoaded) {
                    // RF tuneOff + setPresentationSuspended(false) while on about:blank
                    // skips onResume (mPageLoaded=false). A paused WebView will not load
                    // dvbipage.html or fetch the MPD (ERRATA0900 RF→DASH).
                    this.onResume();
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
        mContext.getMainExecutor().execute(() -> {
            synchronized (mPageLoaded) {
                mPageLoaded = false;
                this.setVisibility(View.INVISIBLE);
                this.loadUrl("about:blank");
            }
        });
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