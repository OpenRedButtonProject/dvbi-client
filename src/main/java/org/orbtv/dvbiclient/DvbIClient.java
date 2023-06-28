package org.orbtv.dvbiclient;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
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
    private Context mContext;
    private List<DvbChannel> mServices;

    private final ArrayList<DvbCallback> mDvbCallbacks = new ArrayList<>();
    protected DvbIClient(Context context) {
        mContext = context;
        mDvbIView = new DvbIView(context);

        mServices = new ArrayList<>();
        Cursor cursor = null;
        ContentResolver resolver = context.getContentResolver();
        try {
            cursor = resolver.query(TvContract.Channels.CONTENT_URI, DvbChannel.PROJECTION,
                    TvContract.Channels.COLUMN_SERVICE_TYPE + "='SERVICE_TYPE_DVBI'",
                    null, null);
            if (cursor != null && cursor.getCount() != 0) {
                while (cursor.moveToNext()) {
                    mServices.add(DvbChannel.fromCursor(cursor));
                }
            }
        }
        catch (Exception e) {
            Log.w(TAG, "Unable to get channels", e);
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        Log.i(TAG, "Number of DVBI services: " + mServices.size());

        //http request --> list
        //service instance --> xml ait
    }

    public static void instantiate(Context context) {
        if (mSingleton == null) {
            mSingleton = new DvbIClient(context);
        }
    }

    public static DvbIClient getInstance() {
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
        return mServices;
    }

    public boolean startServiceSearch() {
        mServices.clear();
        try {
            mServices.add(createChannel());
        } catch (JSONException e) {
            e.printStackTrace();
        }
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
