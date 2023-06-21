package org.orbtv.dvbiclient;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;

import org.json.JSONException;
import org.orbtv.companionlibrary.model.DvbChannel;
import org.orbtv.companionlibrary.model.InternalProviderData;

import java.util.ArrayList;
import java.util.List;

public class DvbIClient {
    private static DvbIClient mSingleton;
    private static final String TAG = DvbIClient.class.getSimpleName();
    private WebView mWebView;

    private final ArrayList<DvbCallback> mDvbCallbacks = new ArrayList<>();
    protected DvbIClient() {

    }

    public static DvbIClient getInstance() {
        if (mSingleton == null) {
            mSingleton = new DvbIClient();
        }
        return mSingleton;
    }

    public void addDvbCallback(DvbCallback handler) {
        if (!mDvbCallbacks.contains(handler)) {
            mDvbCallbacks.add(handler);
        }
    }

    public void removeDvbCallback(DvbCallback handler) {
        mDvbCallbacks.remove(handler);
    }

    public void initialiseView(Context context) {
        mWebView = new WebView(context);
    }

    public View getView() {
        return mWebView;
    }

    public boolean tune(String url) {
        Log.i(TAG, "Tuning to url " + url + "...");
        if (url.startsWith("http")) {
            mWebView.post(() -> {
                mWebView.setVisibility(View.VISIBLE);
                mWebView.loadUrl(url);
            });
            return true;
        }
        return false;
    }

    public void tuneOff() {
        mWebView.post(() -> {
            mWebView.setVisibility(View.INVISIBLE);
            mWebView.loadUrl("about:blank");
        });
    }

    public List<DvbChannel> getListOfServices() {
        List<DvbChannel> channels = new ArrayList<>();
        try {
            channels.add(createChannel());
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.i(TAG, "Requesting list of services...");

        return channels;
    }

    public int getNumberOfServices() {
        return 1;
    }

    public boolean startServiceSearch() {
        for (DvbCallback handler : mDvbCallbacks) {
            handler.onDvbiStatusChanged(100);
        }
        return true;
    }

    private DvbChannel createChannel() throws JSONException {
        InternalProviderData data = new InternalProviderData();
        data.put("DVB_URI", "http://192.168.1.145/dvbi.html");
        data.put("NET_ID", 254);
        data.put("LOCKED", false);
        data.put("ORIG_LCN", 799);

        return new DvbChannel.Builder()
            .setDisplayName("My DVB-I Service")
            .setDisplayNumber("799")
            .setType("TYPE_DVB_I")
            .setServiceType("SERVICE_TYPE_DVBI")
            .setOriginalNetworkId(1)
            .setTransportStreamId(2)
            .setServiceId(3)
            .setBrowsable(true)
            .setSearchable(true)
            .setInternalProviderData(data)
            .build();
    }

    public static class DvbCallback {
        protected void onDvbiStatusChanged(int progress) { }
    }
}
