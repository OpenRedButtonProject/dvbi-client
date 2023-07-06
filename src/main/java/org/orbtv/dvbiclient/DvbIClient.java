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
import org.orbtv.companionlibrary.utils.AsyncUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class DvbIClient {
    private static DvbIClient mSingleton;
    private static final String TAG = DvbIClient.class.getSimpleName();
    private DvbIView mDvbIView;
    private Context mContext;
    private List<DvbChannel> mServices;
    private ServiceListDiscoveryTask mLastDiscoveryTask = null;
    private List<DvbIChannel> mChannels;

    private final ArrayList<DvbCallback> mDvbCallbacks = new ArrayList<>();
    protected DvbIClient(Context context) {
        mContext = context;
        mDvbIView = new DvbIView(context);
        mServices = new ArrayList<>();
        mChannels = new ArrayList<>();

        populateServices();
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
        synchronized (mDvbCallbacks) {
            if (!mDvbCallbacks.contains(handler)) {
                mDvbCallbacks.add(handler);
            }
        }
    }

    public void removeDvbCallback(DvbCallback handler) {
        synchronized (mDvbCallbacks) {
            mDvbCallbacks.remove(handler);
        }
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
        ArrayList<DvbChannel> ret = null;
        synchronized (mServices) {
            ret = new ArrayList<DvbChannel>(mServices);
        }
        return ret;
    }

    public boolean startServiceSearch() {
        boolean ret = false;
        if (mLastDiscoveryTask == null) {
            synchronized (mServices) {
                mServices.clear();
            }
            mLastDiscoveryTask = new ServiceListDiscoveryTask();
            //mLastDiscoveryTask.execute("http://stage.sofiadigital.fi/dvb/dvb-i-reference-application/backend/servicelists/SofiaTestList.xml?ts=1687942834080");
            //mLastDiscoveryTask.execute("http://192.168.1.145/config.xml");
            mLastDiscoveryTask.execute("http://stage.sofiadigital.fi/dvb/dvb-i-reference-application/backend/servicelists/example.xml?ts=1688483136151");
            ret = true;
        }
        return ret;
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
            .setOriginalNetworkId(0)
            .setTransportStreamId(0)
            .setServiceId(0)
            .setBrowsable(true)
            .setSearchable(true)
            .setInternalProviderData(data)
            .build();
    }

    private void populateServices() {
        //mServices.clear();
        Cursor cursor = null;
        ContentResolver resolver = mContext.getContentResolver();
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
    }

    public class ServiceListDiscoveryTask extends AsyncUtils<Void, String> {
        @Override
        protected Void doInBackground(String... uris) {
            try {
                for (String uri : uris) {
                    URL url = new URL(uri);
                    boolean useHttps = false;
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//                if (useHttps) {
//                    // Configure the SSL context for HTTPS connections
//                    SSLContext sslContext = SSLContext.getInstance("TLS");
//                    sslContext.init(null, new TrustManager[]{new TrustAllManager()}, null);
//                    ((HttpsURLConnection) connection).setSSLSocketFactory(sslContext.getSocketFactory());
//                    ((HttpsURLConnection) connection).setHostnameVerifier((hostname, session) -> true);
//                }

                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Content-Type", "application/xml");

                    int responseCode = connection.getResponseCode();
                    Log.i(TAG, "Response Code: " + responseCode);

                    // Read the response from the input stream
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                    reader.close();

                    // Return the response as a string
                    Log.i(TAG, responseBuilder.toString());

                    ServiceList serviceList = ServiceList.parseFromXML(responseBuilder.toString());
                    List<DvbIService> services = DvbIService.parseFromXML(responseBuilder.toString());
                    serviceList.setServices(services);

                    String targetRegion = "";
                    serviceList.setLCN(targetRegion);

                    String preferredLanguage = "";
                    for (DvbIService service : services) {
                        mChannels.add(DvbIChannel.createChannel(service, preferredLanguage));
                        List<DvbIServiceInstance> instances = service.getInstances();
                        for (DvbIServiceInstance instance : instances) {
                            mChannels.add(DvbIChannel.createChannel(instance));
                        }
                    }

                    for (DvbIChannel channel : mChannels) {
                        channel.printChannelProperties();
                    }

                    synchronized (mServices) {
                        mServices.add(createChannel());
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "Error sending request", e);
            } catch (Exception e) {
                Log.e(TAG, "Error configuring SSL context", e);
            }
            return null;
        }


        @Override
        public void onPostExecute(Void success) {
            finalizeSearch();
        }

        @Override
        public void onCancelled(Void ignore) {
            finalizeSearch();
        }

        private void finalizeSearch() {
            synchronized (mDvbCallbacks) {
                for (DvbCallback handler : mDvbCallbacks) {
                    handler.onDvbiStatusChanged(100);
                }
            }
            synchronized (mLastDiscoveryTask) {
                mLastDiscoveryTask = null;
            }
        }
    }

    public static class DvbCallback {
        protected void onDvbiStatusChanged(int progress) { }
    }
}
