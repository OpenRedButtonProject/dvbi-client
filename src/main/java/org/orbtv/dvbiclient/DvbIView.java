package org.orbtv.dvbiclient;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.util.Arrays;

public class DvbIView extends WebView {
    private static final String DVBI_PAGE = "file:///android_asset/polyfill/dvbipage.html";
    private static final String TAG = DvbIView.class.getSimpleName();
    private final Context mContext;
    private String mLastUrl = "about:blank";

    public DvbIView(Context context) {
        super(context);
        mContext = context;

        setBackgroundColor(Color.TRANSPARENT);
        getSettings().setJavaScriptEnabled(true);
        getSettings().setMediaPlaybackRequiresUserGesture(false);
        setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i(TAG, "onPageFinished" + url + "...");
                evaluateJavascript("orb_loadMedia('" + mLastUrl + "')", null);
            }

            @Override
            public void onScaleChanged(WebView view, float oldScale, float newScale) {
                DvbIView.this.setInitialScale((int) (DvbIView.this.getHeight() / 720.0 * 100.0));
            }
        });
        try {
            Log.i(TAG, "Found Assets: " + Arrays.toString(mContext.getAssets().list("polyfill")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean tune(String url) {
        Log.i(TAG, "Tuning to url " + url + "...");
        if (url.startsWith("http")) {
            mLastUrl = url;
            mContext.getMainExecutor().execute(() -> {
                this.setVisibility(View.VISIBLE);
                this.loadUrl(DVBI_PAGE);
            });
            return true;
        }
        return false;
    }

    public void tuneOff() {
        Log.i(TAG, "Tuning off...");
        mLastUrl = "about:blank";
        mContext.getMainExecutor().execute(() -> {
            this.setVisibility(View.INVISIBLE);
            this.loadUrl(mLastUrl);
        });
    }
}
