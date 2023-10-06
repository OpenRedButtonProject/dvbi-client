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
    private Boolean mPageLoaded = false;
    private Boolean mIsSuspended = false;

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
    }

    public DvbIView(Context context) {
        super(context);
        mContext = context;

        setBackgroundColor(Color.TRANSPARENT);
        getSettings().setJavaScriptEnabled(true);
        getSettings().setMediaPlaybackRequiresUserGesture(false);
        getSettings().setUseWideViewPort(true);
        getSettings().setLoadWithOverviewMode(true);

        final JavaScriptInterface jsInterface = new JavaScriptInterface(mContext);
        addJavascriptInterface(jsInterface, "Android");

        setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i(TAG, "onPageFinished " + url + "...");
                if (DVBI_PAGE.equals(url)) {
                    synchronized (mPageLoaded) {
                        evaluateJavascript("orb_loadMedia('" + mLastUrl + "')", null);
                        mPageLoaded = true;
                    }
                }
            }

            @Override
            public void onScaleChanged(WebView view, float oldScale, float newScale) {
                DvbIView.this.setInitialScale((int) (DvbIView.this.getHeight() / 720.0 * 100.0));
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

    public boolean tune(String url) {
        Log.i(TAG, "Tuning to url " + url + "...");
        if (url != null && url.startsWith("http")) {
            mLastUrl = url;
            mContext.getMainExecutor().execute(() -> {
                synchronized (mPageLoaded) {
                    if (!DVBI_PAGE.equals(this.getUrl())) {
                        mPageLoaded = false;
                        this.loadUrl(DVBI_PAGE);
                        if (!mIsSuspended) {
                            this.setVisibility(View.VISIBLE);
                        }
                    } else if (mPageLoaded) {
                        evaluateJavascript("orb_loadMedia('" + mLastUrl + "')", null);
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
            evaluateJavascript("setVideoRectangle(" + x + "," + y + ","
                    + width + "," + height + ")", null);
        });
    }

    public void setPresentationSuspended(boolean suspend) {
        synchronized (mIsSuspended) {
            if (mIsSuspended != suspend) {
                mIsSuspended = suspend;
                mContext.getMainExecutor().execute(() -> {
                    if (suspend) {
                        this.setVisibility(View.INVISIBLE);
                        this.onPause();
                    } else if (mPageLoaded) {
                        this.onResume();
                        this.setVisibility(View.VISIBLE);
                    }
                });
            }
        }
    }

    public interface JSCallback {
        void onVideoEvent(String eventName, JSONObject data);
    }
}