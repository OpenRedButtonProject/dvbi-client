package org.orbtv.dvbiclient;


import android.content.Context;
import android.media.tv.TvContract;
import android.util.Log;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;
import org.orbtv.companionlibrary.callbacks.DvbCallback;
import org.orbtv.companionlibrary.callbacks.HbbTVCallback;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DvbIClient {
    public static final String TYPE_DVB_I = "TYPE_DVB_I";
    public static final String KEY_DVBI_UID = "DVBI_UID";

    public static final String PLAYER_STATUS_STARTING = "DVBI_PLAYBACK_STARTED";
    public static final String PLAYER_STATUS_PLAYING = "DVBI_PLAYBACK_PLAYING";
    public static final String PLAYER_STATUS_BAD_CONNECTION = "DVBI_PLAYBACK_STALLED";
    public static final String PLAYER_STATUS_ERROR = "DVBI_PLAYBACK_ERROR";

    public static final int DELIVERY_TYPE_BROADCAST = 0;
    public static final int DELIVERY_TYPE_BROADBAND = 1;
    public static final int DELIVERY_TYPE_UNKNOWN = -1;

    private static final Map<String, Integer> HBBTV_CHANNEL_STATUS_LOOKUP = new HashMap<String, Integer>() {{
        // key names according to the events received from Javascript interface in DvbIView
        // values according to DvbGlue CHANNEL_CHANGE codes as expected in TvInputService
        put(PLAYER_STATUS_STARTING, 129);
        put(PLAYER_STATUS_PLAYING, 130);
        put(PLAYER_STATUS_BAD_CONNECTION, 101);
        put(PLAYER_STATUS_ERROR, 100);
    }};
    private static DvbIClient mSingleton;
    private static final String TAG = DvbIClient.class.getSimpleName();
    private DvbIView mDvbIView;
    private ServiceListDiscoveryTask mLastDiscoveryTask = null;
    private DvbIDatabaseHandler mDbHandler;
    private DvbIService mLastService;
    private DvbIServiceInstance mLastServiceInstace;
    private final ArrayList<DvbCallback> mDvbCallbacks = new ArrayList<>();
    private final ArrayList<HbbTVCallback> mHbbTVCallbacks = new ArrayList<>();
    private final ArrayList<BroadcastCallback> mBroadcastCallbacks = new ArrayList<>();
    private ProcessXmlAitCallback processXmlAitCallback;
    private Boolean mReadyForApps = false;

    public interface ProcessXmlAitCallback {
        void processXmlAit(String xmlAit);
    }

    private final DvbIView.JSCallback mJSCallback = new DvbIView.JSCallback() {
        @Override
        public void onVideoEvent(String eventName, JSONObject data) {
            if (mLastService != null && HBBTV_CHANNEL_STATUS_LOOKUP.containsKey(eventName)) {
                Triplet triplet = mLastService.getTriplet();
                int onid = 0;
                int tsid = 0;
                int sid = 0;
                if (triplet != null) {
                    onid = triplet.getOrigNetId();
                    tsid = triplet.getTsId();
                    sid = triplet.getServiceId();
                }
                for (HbbTVCallback handler : mHbbTVCallbacks) {
                    handler.onChannelChangeStatus(onid, tsid, sid, HBBTV_CHANNEL_STATUS_LOOKUP.get(eventName));
                }
                for (DvbCallback handler : mDvbCallbacks) {
                    handler.onPlayerStatusChanged(eventName);
                }
                Log.i(TAG, "Received video event " + eventName);

                if (mReadyForApps && eventName.equals(PLAYER_STATUS_PLAYING)) {
                    List<String> appUri;
                    if (processXmlAitCallback != null) {
                        appUri = DvbIChannelAdapter.createChannel(mLastService, mLastServiceInstace, "").getAppParallelUris();
                        if (appUri.isEmpty()) {
                            appUri = DvbIChannelAdapter.createChannel(mLastService, "").getAppParallelUris();
                        }
                        Log.i(TAG, "Found Hbbtv App from Related Materials (" + appUri + ")");
                        new GetXmlAitTask().execute(appUri.get(0));
                    }
                }
            }
        }
    };

    private class GetXmlAitTask extends AsyncUtils<String, String> {
        @Override
        protected String doInBackground(String... uris) {
            return getXmlAit(uris[0]);
        }
    
        @Override
        protected void onPostExecute(String result) {
            if (processXmlAitCallback != null && result != null) {
                processXmlAitCallback.processXmlAit(result);
                mReadyForApps = false;
            }
        }
    }

    private String getXmlAit(String uri) {
        try {
            URL url = new URL(uri);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
    
            int responseCode = connection.getResponseCode();
    
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                return content.toString();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error fetching XML AIT", e);
        }
        return null;
    }

    private DvbIClient(Context context) {
        mDbHandler = new DvbIDatabaseHandler(context);
        mDvbIView = new DvbIView(context);
        mDvbIView.addJSCallback(mJSCallback);
    }

    public static void instantiate(Context context) {
        if (mSingleton == null) {
            mSingleton = new DvbIClient(context);
        }
    }

    public static DvbIClient getInstance() {
        return mSingleton;
    }

    public synchronized void addDvbCallback(DvbCallback handler) {
        if (!mDvbCallbacks.contains(handler)) {
            mDvbCallbacks.add(handler);
        }
    }

    public synchronized void removeDvbCallback(DvbCallback handler) {
        mDvbCallbacks.remove(handler);
    }

    public synchronized void addHbbTVCallback(HbbTVCallback handler) {
        if (!mHbbTVCallbacks.contains(handler)) {
            mHbbTVCallbacks.add(handler);
        }
    }

    public synchronized void removeHbbTVCallback(HbbTVCallback handler) {
        mHbbTVCallbacks.remove(handler);
    }

    public synchronized void addBroadcastCallback(BroadcastCallback handler) {
        if (!mBroadcastCallbacks.contains(handler)) {
            mBroadcastCallbacks.add(handler);
        }
    }

    public synchronized void removeBroadcastCallback(BroadcastCallback handler) {
        mBroadcastCallbacks.remove(handler);
    }

    public synchronized void setProcessXmlAitCallback(ProcessXmlAitCallback callback) {
        this.processXmlAitCallback = callback;
    }

    public View getView() {
        return mDvbIView;
    }

    public int tune(String uid, int instanceIndex) {
        mLastService = mDbHandler.getServiceForUID(uid);
        mLastServiceInstace = null;
        mReadyForApps = false;
        Log.i(TAG, "---------- Tuning to service ----------\n" + mLastService + "\n------------------------------------");
        if (mLastService != null) {
            DvbIServiceInstance maxPriorityInstance = null;
            int maxPriorityIndex = -1;
            if (instanceIndex < 0) {
                for (int i = 0; i < mLastService.getInstances().size(); i++) {
                    DvbIServiceInstance instance = mLastService.getInstances().get(i);
                    if (maxPriorityInstance == null || maxPriorityInstance.getPriority() < instance.getPriority()) {
                        maxPriorityInstance = instance;
                        maxPriorityIndex = i;
                    }
                }
            }
            else {
                maxPriorityIndex = instanceIndex;
                maxPriorityInstance = mLastService.getInstances().get(instanceIndex);
            }

            mLastServiceInstace = maxPriorityInstance;
            if (maxPriorityInstance != null) {
                String uri = maxPriorityInstance.getUri();
                Log.i(TAG, "Max priority instance of type " + maxPriorityInstance.getDeliveryType());
                if (maxPriorityInstance.getDeliveryType().equals("dvb-dash")) {
                    if (mDvbIView.tune(uri)) {
                        mReadyForApps = true;
                        for (HbbTVCallback handler : mHbbTVCallbacks) {
                            handler.onServiceInstanceChange(maxPriorityIndex);
                        }
                        return DELIVERY_TYPE_BROADBAND;
                    }
                }
                else {
                    mDvbIView.tuneOff();
                    mReadyForApps = true;
                    for (BroadcastCallback callback : mBroadcastCallbacks) {
                        callback.onTune(maxPriorityInstance.getTriplet().toString());
                    }

                    for (HbbTVCallback handler : mHbbTVCallbacks) {
                            handler.onServiceInstanceChange(maxPriorityIndex);
                    }
                    return DELIVERY_TYPE_BROADCAST;
                }
            }
        }
        Log.i(TAG, "Failed to tune to service.");
        return DELIVERY_TYPE_UNKNOWN;
    }

    public void tuneOff() {
        mLastService = null;
        mDvbIView.tuneOff();
    }

    public void setPresentationSuspended(boolean suspend) {
        mDvbIView.setPresentationSuspended(suspend);
    }

    public void setVideoRectangle(int x, int y, int width, int height) {
        if (mLastServiceInstace != null && mLastServiceInstace.getDeliveryType().equals("dvb-dash")) {
            for (DvbCallback handler : mDvbCallbacks) {
                handler.onSetVideoRectangle(x, y, width, height);
            }
        }
    }

    public List<DvbChannel> getListOfServices() {
        ArrayList<DvbChannel> ret = new ArrayList<>();
        List<DvbIService> services = mDbHandler.getServices();
        for (DvbIService service : services) {
            try {
                ret.add(createChannel(service));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        Log.i(TAG, "Number of services: " + services.size());
        return ret;
    }

    public DvbIService getServiceByUID(String uid) {
        return mDbHandler.getServiceForUID(uid);
    }

    public boolean startServiceSearch() {
        boolean ret = false;
        if (mLastDiscoveryTask == null) {
            mLastDiscoveryTask = new ServiceListDiscoveryTask();
            //mLastDiscoveryTask.execute("http://stage.sofiadigital.fi/dvb/dvb-i-reference-application/backend/servicelists/example.xml?ts=1689243059951");
            //mLastDiscoveryTask.execute("http://192.168.1.145/config.xml");
            mLastDiscoveryTask.execute("http://192.168.1.179/servicelist.xml");
            //mLastDiscoveryTask.execute("http://stage.sofiadigital.fi/dvb/dvb-i-reference-application/backend/servicelists/SofiaTestList.xml?ts=1689686736811"); //+app
            ret = true;
        }
        return ret;
    }

    private DvbChannel createChannel(DvbIService service) throws JSONException {
        DvbIChannelAdapter channel = DvbIChannelAdapter.createChannel(service, "");
        InternalProviderData data = new InternalProviderData();
        data.put("DVB_URI", "");
        data.put("NET_ID", channel.getNid() == null ? "0" : channel.getNid());
        data.put("LOCKED", false);
        data.put("ORIG_LCN", service.getLCNNumber());
        data.put(KEY_DVBI_UID, service.getUniqueIdentifier());
        return new DvbChannel.Builder()
            .setDisplayName(channel.getName())
            .setDisplayNumber(channel.getMajorChannel())
            .setType(TYPE_DVB_I)
            .setServiceType(TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO)
            .setOriginalNetworkId(channel.getOnid())
            .setTransportStreamId(channel.getTsid())
            .setServiceId(channel.getSid())
            .setBrowsable(true)
            .setSearchable(true)
            .setInternalProviderData(data)
            .build();
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

                    String targetRegion = "";
                    ServiceList serviceList = ServiceList.parseFromXML(responseBuilder.toString(), targetRegion);

                    for (DvbIService service : serviceList.services) {
                        DvbIChannelAdapter.createChannel(service,"").printChannelProperties();
                        List<DvbIServiceInstance> instances = service.getInstances();
                        for (DvbIServiceInstance instance : instances) {
                            DvbIChannelAdapter.createChannel(service, instance, "").printChannelProperties();
                        }
                     //   mServices.add(service);
                    }
                    mDbHandler.updateServices(serviceList.services);

                }
            }
            catch(IOException e) {
                Log.e(TAG, "Error sending request", e);
            }
            catch(Exception e) {
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

        private synchronized void finalizeSearch() {
            for (DvbCallback handler : mDvbCallbacks) {
                handler.onDvbtStatusChanged(100);
            }
            mLastDiscoveryTask = null;
        }
    }

    public interface BroadcastCallback {
        void onTune(String uri);
    }
}
