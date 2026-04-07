package org.orbtv.dvbiclient;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
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

        setBackgroundColor(Color.TRANSPARENT);
        getSettings().setJavaScriptEnabled(true);
        getSettings().setMediaPlaybackRequiresUserGesture(false);
        getSettings().setLoadWithOverviewMode(true);

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
                    }
                }
            }
        });
    }

    public void addJSCallback(JSCallback handler) {
        if (!mJSCallbacks.contains(handler)) {
            mJSCallbacks.add(handler);
        }
    }

    public void removeJSCallback(JSCallback handler) {
        mJSCallbacks.remove(handler);
    }

    public boolean tune(String url, boolean enableSubs) {
        Log.i(TAG, "Tuning to url " + url + "...");
        if (url != null && url.startsWith("http")) {
            mLastUrl = url;
            mSubsEnabled = enableSubs;
            mContext.getMainExecutor().execute(() -> {
                synchronized (mPageLoaded) {
                    if (!DVBI_PAGE.equals(this.getUrl())) {
                        mPageLoaded = false;
                        this.loadUrl(DVBI_PAGE);
                        if (!mIsSuspended) {
                            this.setVisibility(View.VISIBLE);
                        }
                    } else if (mPageLoaded) {
                        evaluateJavascript("orb_loadMedia('" + mLastUrl + "', " + enableSubs + ")", null);
                    }
                }
            });
            return true;
        }
        return false;
    }

    public void tuneOff() {
        Log.i(TAG, "Tuning off...");
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
                    } else if (mPageLoaded) {
                        this.onResume();
                        this.setVisibility(View.VISIBLE);
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