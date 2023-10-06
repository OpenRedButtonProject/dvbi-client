package org.orbtv.dvbiclient;


import android.content.Context;
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
import java.util.Arrays;
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

    public static final String PLAYER_EVENT_APP_SIGNALLING = "urn:dvb:dash:appsignalling:2016";

    public static final String LINKED_APP_SCHEME_1_1 = "urn:dvb:metadata:cs:LinkedApplicationCS:2019:1.1";
    public static final String LINKED_APP_SCHEME_1_2 = "urn:dvb:metadata:cs:LinkedApplicationCS:2019:1.2";
    public static final String LINKED_APP_SCHEME_2 = "urn:dvb:metadata:cs:LinkedApplicationCS:2019:2";
    public static final String LINKED_APP_SCHEME_1000_1 = "urn:dvb:metadata:cs:HowRelatedCS:2020:1000.1";

    private static final Map<String, Integer> HBBTV_CHANNEL_STATUS_LOOKUP = new HashMap<String, Integer>() {{
        // key names according to the events received from Javascript interface in DvbIView,
        // values according to DvbGlue CHANNEL_CHANGE codes as expected in TvInputService
        put(PLAYER_STATUS_STARTING, 129);
        put(PLAYER_STATUS_PLAYING, 130);
        put(PLAYER_STATUS_BAD_CONNECTION, 101);
        put(PLAYER_STATUS_ERROR, 100);
    }};

    private static final List<String> STREAM_EVENTS = new ArrayList<>(Arrays.asList(
            "urn:dvb:dash:appsignalling:2016"
    ));
    private static DvbIClient mSingleton;
    private static final String TAG = DvbIClient.class.getSimpleName();
    private DvbIView mDvbIView;
    private HashMap<Integer, String> mStreamEventsLookup = new HashMap<>();
    private ServiceListDiscoveryTask mLastDiscoveryTask = null;
    private DvbIDatabaseHandler mDbHandler;
    private DvbIService mLastService;
    private final ArrayList<DvbCallback> mDvbCallbacks = new ArrayList<>();
    private final ArrayList<HbbTVCallback> mHbbTVCallbacks = new ArrayList<>();
    private final ArrayList<BroadcastCallback> mBroadcastCallbacks = new ArrayList<>();
    private final ArrayList<StreamEventCallback> mStreamEventCallbacks = new ArrayList<>();
    private ProcessXmlAitCallback processXmlAitCallback;
    private Boolean mReadyForApps = false;
    private DvbIService.Callback mServiceCallback = new DvbIService.Callback() {
        @Override
        public void onInstanceChanged(DvbIService service, DvbIServiceInstance fromInstance, DvbIServiceInstance toInstance) {
            DvbIChannelAdapter channel;
            if (service != null && mLastService == service) {
                synchronized (mLastService) {
                    if (toInstance != null) {
                        Log.i(TAG, "--------- active instance ---------\n" + toInstance + "\n---------------------------");
                        String uri = toInstance.getUri();
                        channel = DvbIChannelAdapter.createChannel(mLastService, toInstance);
                        Log.i(TAG, "---------- channel info ----------\n" + channel + "\n------------------------------------");
                        if (channel.getAppControlUris().isEmpty()) {
                            if (toInstance.getDeliveryType().equals("dvb-dash")) {
                                if (mDvbIView.tune(uri)) {
                                    dispatchPlayerStatusChangedEvent(channel.getOnid(), channel.getTsid(), channel.getSid(), PLAYER_STATUS_STARTING);
                                    mReadyForApps = true;
                                }
                            } else {
                                mDvbIView.tuneOff();
                                mReadyForApps = true;
                                for (BroadcastCallback callback : mBroadcastCallbacks) {
                                    callback.onTune(toInstance.getTriplet().toString());
                                }
                            }
                        } else {
                            for (DvbIClient.BroadcastCallback cb : mBroadcastCallbacks) {
                                cb.onTuneOff();
                            }
                            mDvbIView.tuneOff();
                            dispatchPlayerStatusChangedEvent(channel.getOnid(), channel.getTsid(), channel.getSid(), PLAYER_STATUS_STARTING);
                            dispatchPlayerStatusChangedEvent(channel.getOnid(), channel.getTsid(), channel.getSid(), PLAYER_STATUS_PLAYING);

                            Log.i(TAG, "Found Hbbtv App with scheme '" + LINKED_APP_SCHEME_1_2 + "' from Related Materials (" + channel.getAppControlUris().get(0) + ")");
                            new GetXmlAitTask().execute(new XmlAitAttributes(channel.getAppControlUris().get(0), LINKED_APP_SCHEME_1_2));
                        }

                        if (toInstance != null) {
                            int index = mLastService.getInstances().indexOf(toInstance);
                            for (HbbTVCallback handler : mHbbTVCallbacks) {
                                handler.onServiceInstanceChange(index);
                            }
                        }
                    } else {
                        Log.i(TAG, "No service instance is currently available.");
                        for (DvbIClient.BroadcastCallback cb : mBroadcastCallbacks) {
                            cb.onTuneOff();
                        }
                        mDvbIView.tuneOff();
                        channel = DvbIChannelAdapter.createChannel(mLastService, fromInstance);
                        dispatchPlayerStatusChangedEvent(
                                channel.getOnid(),
                                channel.getTsid(),
                                channel.getSid(),
                                PLAYER_STATUS_STARTING
                        );
                        if (!channel.getAppInactiveUris().isEmpty()) {
                            Log.i(TAG, "Found Hbbtv App with scheme '" + LINKED_APP_SCHEME_2 + "' from Related Materials (" + channel.getAppInactiveUris().get(0) + ")");
                            new GetXmlAitTask().execute(new XmlAitAttributes(channel.getAppInactiveUris().get(0), LINKED_APP_SCHEME_2));
                        } else if (!channel.getOutOfServiceImages().isEmpty()) {
                            Log.i(TAG, "Found Hbbtv App with scheme '" + LINKED_APP_SCHEME_1000_1 + "' from Related Materials (" + channel.getOutOfServiceImages().get(0) + ")");
                            mDvbIView.getContext().getMainExecutor().execute(() -> {
                                mDvbIView.setVisibility(View.VISIBLE);
                                mDvbIView.loadUrl(channel.getOutOfServiceImages().get(0));
                            });
                        }
                    }
                }
            }
        }
    };

    private class XmlAitAttributes {
        public String xml;
        public String url;
        public String scheme;

        public XmlAitAttributes(String url, String scheme) {
            this.url = url;
            this.scheme = scheme;
        }
    }

    public interface ProcessXmlAitCallback {
        void processXmlAit(String xmlAit, String scheme);
    }

    public interface StreamEventCallback {
        void onStreamEvent(int listenId, String name, String status);
    }

    private final DvbIView.JSCallback mJSCallback = new DvbIView.JSCallback() {
        @Override
        public void onVideoEvent(String eventName, JSONObject data) {
            if (mLastService != null) {
                if (HBBTV_CHANNEL_STATUS_LOOKUP.containsKey(eventName)) {
                    Triplet triplet = mLastService.getTriplet();
                    int onid = 0;
                    int tsid = 0;
                    int sid = 0;
                    if (triplet != null) {
                        onid = triplet.getOrigNetId();
                        tsid = triplet.getTsId();
                        sid = triplet.getServiceId();
                    }
                    dispatchPlayerStatusChangedEvent(onid, tsid, sid, eventName);
                    Log.i(TAG, "Received video event " + eventName);

                    if (mReadyForApps && eventName.equals(PLAYER_STATUS_PLAYING) && processXmlAitCallback != null) {
                        List<String> appUri = DvbIChannelAdapter.createChannel(mLastService, mLastService.getTunedInstance()).getAppParallelUris();
                        if (!appUri.isEmpty()) {
                            Log.i(TAG, "Found Hbbtv App with scheme '" + LINKED_APP_SCHEME_1_1 + "' from Related Materials (" + appUri + ")");
                            new GetXmlAitTask().execute(new XmlAitAttributes(appUri.get(0), LINKED_APP_SCHEME_1_1));
                        }
                    }
                }
                else if (STREAM_EVENTS.contains(eventName)) {
                    for (Map.Entry<Integer, String> entry : mStreamEventsLookup.entrySet()) {
                        if (entry.getValue().equals(eventName)) {
                            try {
                                for (StreamEventCallback cb : mStreamEventCallbacks) {
                                    cb.onStreamEvent(
                                            entry.getKey(),
                                            data.getJSONObject("eventStream").getString("value"),
                                            "trigger"
                                    );
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    if (PLAYER_EVENT_APP_SIGNALLING.equals(eventName)) {
                        if (processXmlAitCallback != null && data != null) {
                            try {
                                processXmlAitCallback.processXmlAit(data.getString("messageData"), LINKED_APP_SCHEME_1_1);
                                mReadyForApps = false;
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
    };

    private class GetXmlAitTask extends AsyncUtils<XmlAitAttributes, XmlAitAttributes> {
        @Override
        protected XmlAitAttributes doInBackground(XmlAitAttributes... xmlAit) {
            xmlAit[0].xml = getXmlAit(xmlAit[0].url);
            return xmlAit[0];
        }
    
        @Override
        protected void onPostExecute(XmlAitAttributes result) {
            if (processXmlAitCallback != null && result != null) {
                processXmlAitCallback.processXmlAit(result.xml, result.scheme);
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

    public synchronized void addStreamEventCallback(StreamEventCallback handler) {
        if (!mStreamEventCallbacks.contains(handler)) {
            mStreamEventCallbacks.add(handler);
        }
    }

    public synchronized void removeStreamEventCallback(StreamEventCallback handler) {
        mStreamEventCallbacks.remove(handler);
    }

    public synchronized void setProcessXmlAitCallback(ProcessXmlAitCallback callback) {
        this.processXmlAitCallback = callback;
    }

    public synchronized boolean subscribeStreamEvent(int listenId, String targetUrl, String eventName) {
        if (targetUrl != null) {
            mStreamEventsLookup.put(listenId, targetUrl);
            return true;
        }
        return false;
    }

    public synchronized void unsubscribeStreamEvent(int listenId) {
        mStreamEventsLookup.remove(listenId);
    }

    public View getView() {
        return mDvbIView;
    }

    public synchronized boolean tune(String uid, int instanceIndex) {
        if (mLastService != null) {
            mLastService.setCallback(null);
            mLastService.tuneOff();
        }
        mLastService = mDbHandler.getServiceForUID(uid);
        mReadyForApps = false;
        if (mLastService != null) {
            Log.i(TAG, "---------- Tuning to service ----------\n" + mLastService + "\n------------------------------------");
            mLastService.setCallback(mServiceCallback);
            mLastService.tune(instanceIndex);
        }
        else {
            Log.i(TAG, "No service found with UID: " + uid);
        }
        return mLastService != null;
    }

    public synchronized void tuneOff() {
        if (mLastService != null) {
            mLastService.setCallback(null);
            mLastService.tuneOff();
            mLastService = null;
        }
        mDvbIView.tuneOff();
    }

    public void setPresentationSuspended(boolean suspend) {
        mDvbIView.setPresentationSuspended(suspend);
    }

    public void setVideoRectangle(int x, int y, int width, int height) {
        String deliveryType = "";
        try {
            deliveryType = mLastService.getTunedInstance().getDeliveryType();
        }
        catch (NullPointerException e) { }
        if (deliveryType.equals("dvb-dash")) {
            Log.i(TAG, "Setting video rectangle to " + x + ", " + y + ", " + width + ", " + height);
            mDvbIView.setVideoRectangle(x, y, width, height);
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

    public boolean startServiceSearch(String serviceListURL) {
        boolean ret = false;
        if (mLastDiscoveryTask == null) {
            mLastDiscoveryTask = new ServiceListDiscoveryTask();
            if (serviceListURL == null || serviceListURL.isEmpty()) {
                serviceListURL = "http://192.168.1.179/servicelist.xml";
            }
            mLastDiscoveryTask = new ServiceListDiscoveryTask();
            Log.d(TAG, "Starting service search at " + serviceListURL);
            //mLastDiscoveryTask.execute("http://stage.sofiadigital.fi/dvb/dvb-i-reference-application/backend/servicelists/example.xml?ts=1689243059951");
            //mLastDiscoveryTask.execute("http://192.168.1.145/config.xml");
            //mLastDiscoveryTask.execute("http://stage.sofiadigital.fi/dvb/dvb-i-reference-application/backend/servicelists/SofiaTestList.xml?ts=1689686736811"); //+app
            mLastDiscoveryTask.execute(serviceListURL);
            ret = true;
        }
        return ret;
    }

    private DvbChannel createChannel(DvbIService service) throws JSONException {
        DvbIChannelAdapter channel = DvbIChannelAdapter.createChannel(service);
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
            .setServiceType(channel.getChannelType())
            .setOriginalNetworkId(channel.getOnid())
            .setTransportStreamId(channel.getTsid())
            .setServiceId(channel.getSid())
            .setBrowsable(true)
            .setSearchable(true)
            .setInternalProviderData(data)
            .build();
    }

    private void dispatchPlayerStatusChangedEvent(int onid, int tsid, int sid, String event) {
        for (HbbTVCallback handler : mHbbTVCallbacks) {
            handler.onChannelChangeStatus(onid, tsid, sid, HBBTV_CHANNEL_STATUS_LOOKUP.get(event));
        }
        for (DvbCallback handler : mDvbCallbacks) {
            handler.onPlayerStatusChanged(event);
        }
    }

    private class ServiceListDiscoveryTask extends AsyncUtils<Void, String> {
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
                        Log.d(TAG, "--------- scanned service ----------\n" + service);
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
        void onTuneOff();
    }
}
