package org.orbtv.dvbiclient;

import android.content.Context;
import android.util.Log;
import android.view.View;

import org.json.JSONException;
import org.orbtv.companionlibrary.model.DvbChannel;
import org.orbtv.companionlibrary.model.InternalProviderData;

import java.util.ArrayList;
import java.util.List;

public class DvbIClient {
    private static DvbIClient mSingleton;
    private static final String TAG = DvbIClient.class.getSimpleName();
    private DvbIView mDvbIView;

    private final ArrayList<DvbCallback> mDvbCallbacks = new ArrayList<>();
    protected DvbIClient() {
        //retrieve service list
        //http request --> list
        //service instance --> xml ait
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
        mDvbIView = new DvbIView(context);
    }

    public View getView() {
        return mDvbIView;
    }

    public boolean tune(String url) {
        return mDvbIView.tune(url);
    }

    public void tuneOff() {
        mDvbIView.tuneOff();
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
        data.put("DVB_URI", "https://livesim.dashif.org/livesim/testpic_2s/Manifest.mpd");
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
