package org.orbtv.dvbiclient;

import android.content.Context;
import android.media.tv.TvTrackInfo;
import android.util.Log;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.orbtv.companionlibrary.callbacks.DvbCallback;
import org.orbtv.companionlibrary.callbacks.HbbTVCallback;
import org.orbtv.companionlibrary.model.DvbChannel;
import org.orbtv.companionlibrary.model.InternalProviderData;
import org.orbtv.companionlibrary.utils.AsyncUtils;
import org.orbtv.dvbiclient.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DvbIClient {
    private static DvbIClient mSingleton;
    private static final String TAG = DvbIClient.class.getSimpleName();

    public static final String TYPE_DVB_I = "TYPE_DVB_I";
    public static final String KEY_DVBI_UID = "DVBI_UID";

    public static final String PLAYER_STATUS_STARTING = "DVBI_PLAYBACK_STARTED";
    public static final String PLAYER_STATUS_PLAYING = "DVBI_PLAYBACK_PLAYING";
    public static final String PLAYER_STATUS_BAD_CONNECTION = "DVBI_PLAYBACK_STALLED";
    public static final String PLAYER_STATUS_ERROR = "DVBI_PLAYBACK_ERROR";
    public static final String PLAYER_STATUS_BLOCKED = "blocked";

    public static final String PLAYER_EVENT_APP_SIGNALLING = "urn:dvb:dash:appsignalling:2016";
    public static final String PLAYER_EVENT_EPG_METADATA = "urn:dvb:iptv:cpm:2014";

    private static final String TRACKS_UPDATED_EVENT = "DVBI_TRACKS_UPDATED";
    private static final String TRACK_CHANGED_EVENT = "DVBI_TRACK_CHANGED";

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
        put(PLAYER_STATUS_BLOCKED, 3);
    }};
    private static final Map<Integer, String> TRACK_TYPE_LOOKUP = new HashMap<Integer, String>() {{
        put(TvTrackInfo.TYPE_AUDIO, "audio");
        put(TvTrackInfo.TYPE_VIDEO, "video");
        put(TvTrackInfo.TYPE_SUBTITLE, "text");
    }};
    private static final List<String> STREAM_EVENTS = new ArrayList<>(Arrays.asList(
            PLAYER_EVENT_APP_SIGNALLING,
            PLAYER_EVENT_EPG_METADATA
    ));

    private final DvbIView mDvbIView;
    private final HashMap<Integer, String> mStreamEventsLookup = new HashMap<>();
    private ServiceListDiscoveryTask mLastDiscoveryTask = null;
    private final DatabaseHandler mDbHandler;
    private final ArrayList<DvbCallback> mDvbCallbacks = new ArrayList<>();
    private final ArrayList<HbbTVCallback> mHbbTVCallbacks = new ArrayList<>();
    private final ArrayList<Callback> mCallbacks = new ArrayList<>();
    private boolean mBlocked = false; // TODO: should it be part of TunedServiceManager?
    private List<TvTrackInfo> mTracks = new ArrayList<>(); // TODO: should it be part of TunedServiceManager?
    private HashMap<Integer, TvTrackInfo> mSelectedTracks = new HashMap<>(); // TODO: should it be part of TunedServiceManager?
    private boolean mSubtitlesEnabled = false;
    private HashMap<Integer, Boolean> mIsUnselected = new HashMap<>();
    private final ITvInputCallback mTvInputCallback;

    private final TunedServiceManager mServiceManager = new TunedServiceManager(new TunedServiceManager.Callback() {
        @Override
        public void onEpgEventsUpdated() {
            mBlocked = false;
            List<TunedServiceManager.EpgMetadata> epgMetadata = mServiceManager.getEpgMetadata();
            if (epgMetadata != null) {
                Service service = mServiceManager.getTunedService();
                int rating = mTvInputCallback.getParentalControlAge();
                mBlocked = service.getParentalRating() != null && service.getParentalRating() < rating;
                if (!epgMetadata.isEmpty() && epgMetadata.get(0).getParentalRating() != null) {
                    mBlocked = epgMetadata.get(0).getParentalRating() > rating;
                }
                for (HbbTVCallback callback : mHbbTVCallbacks) {
                    callback.onParentalRatingChange(mBlocked);
                }
            }
        }

        @Override
        public void onInstanceChanged(ServiceInstance fromInstance, ServiceInstance toInstance) {
            DvbIChannelAdapter channel;
            Service service = mServiceManager.getTunedService();
            if (service != null) {
                DvbIChannelAdapter.Builder channelBuilder = new DvbIChannelAdapter.Builder().setService(service);
                if (toInstance != null) {
                    Log.i(TAG, "--------- active instance ---------\n" + toInstance + "\n---------------------------");
                    String uri = toInstance.getUri();
                    channel = channelBuilder.setServiceInstance(toInstance).build();
                    Log.i(TAG, "---------- channel info ----------\n" + channel + "\n------------------------------------");
                    String app_1_2 = channel.getLinkedAppUri(LINKED_APP_SCHEME_1_2);
                    if (app_1_2 == null) {
                        if (!mBlocked) {
                            if ("dvb-dash".equals(toInstance.getDeliveryType())) {
                                mTvInputCallback.tuneOff();
                                if (mDvbIView.tune(uri, mSubtitlesEnabled)) {
                                    dispatchPlayerStatusChangedEvent(channel.getOnid(), channel.getTsid(), channel.getSid(), PLAYER_STATUS_STARTING);
                                }
                            } else {
                                mDvbIView.tuneOff();
                                mTracks.clear();
                                mSelectedTracks.clear();
                                mIsUnselected.clear();
                                mTvInputCallback.tune(toInstance.getTriplet().toString());
                            }
                        }
                        else {
                            mDvbIView.tuneOff();
                            mTracks.clear();
                            mSelectedTracks.clear();
                            mIsUnselected.clear();
                            dispatchPlayerStatusChangedEvent(channel.getOnid(), channel.getTsid(), channel.getSid(), PLAYER_STATUS_BLOCKED);
                            mTvInputCallback.tuneOff();
                        }
                    } else {
                        mTvInputCallback.tuneOff();
                        mDvbIView.tuneOff();
                        dispatchPlayerStatusChangedEvent(channel.getOnid(), channel.getTsid(), channel.getSid(), PLAYER_STATUS_STARTING);
                        dispatchPlayerStatusChangedEvent(channel.getOnid(), channel.getTsid(), channel.getSid(), PLAYER_STATUS_PLAYING);

                        Log.i(TAG, "Found Hbbtv App with scheme '" + LINKED_APP_SCHEME_1_2 + "' from Related Materials (" + app_1_2 + ")");
                        new GetXmlAitTask().execute(new XmlAitAttributes(app_1_2, LINKED_APP_SCHEME_1_2));
                    }

                    int index = service.getInstances().indexOf(toInstance);
                    for (HbbTVCallback handler : mHbbTVCallbacks) {
                        handler.onServiceInstanceChange(index);
                    }
                } else {
                    Log.i(TAG, "No service instance is currently available.");
                    mTvInputCallback.tuneOff();
                    mDvbIView.tuneOff();
                    channel = channelBuilder.setServiceInstance(fromInstance).build();
                    dispatchPlayerStatusChangedEvent(
                            channel.getOnid(),
                            channel.getTsid(),
                            channel.getSid(),
                            PLAYER_STATUS_STARTING
                    );
                    if (channel.getLinkedAppUri(LINKED_APP_SCHEME_2) != null) {
                        Log.i(TAG, "Found Hbbtv App with scheme '" + LINKED_APP_SCHEME_2 + "' from Related Materials (" + channel.getLinkedAppUri(LINKED_APP_SCHEME_2) + ")");
                        new GetXmlAitTask().execute(new XmlAitAttributes(channel.getLinkedAppUri(LINKED_APP_SCHEME_2), LINKED_APP_SCHEME_2));
                    } else if (channel.getLinkedAppUri(LINKED_APP_SCHEME_1000_1) != null) {
                        Log.i(TAG, "Found Hbbtv App with scheme '" + LINKED_APP_SCHEME_1000_1 + "' from Related Materials (" + channel.getLinkedAppUri(LINKED_APP_SCHEME_1000_1) + ")");
                        mDvbIView.getContext().getMainExecutor().execute(() -> {
                            mDvbIView.setVisibility(View.VISIBLE);
                            mDvbIView.loadUrl(channel.getLinkedAppUri(LINKED_APP_SCHEME_1000_1));
                        });
                    }
                }
            }
        }
    });

    private final DvbIView.JSCallback mJSCallback = new DvbIView.JSCallback() {

        @Override
        public void onVideoEvent(String eventName, JSONObject data) {
            switch (eventName) {
                case TRACKS_UPDATED_EVENT:
                    handleTracksUpdate(data);
                    break;
                case TRACK_CHANGED_EVENT:
                    handleTrackChange(data);
                    break;
                default:
                    if (HBBTV_CHANNEL_STATUS_LOOKUP.containsKey(eventName)) {
                        handleChannelStatusChange(eventName);
                    } else if (STREAM_EVENTS.contains(eventName)) {
                        handleStreamEventDispatch(eventName, data);
                    }
                    break;
            }
        }

        private synchronized void handleTrackChange(JSONObject data) {
            try {
                for (Map.Entry<Integer, String> it : TRACK_TYPE_LOOKUP.entrySet()) {
                    if (it.getValue().equals(data.getString("type"))) {
                        int key = it.getKey();
                        for (TvTrackInfo track : mTracks) {
                            String id = track.getId();
                            if (track.getType() == key) {
                                if (data.isNull("id")) {
                                    // TODO: do we need to remove the track at key?
                                    for (Callback cb : mCallbacks) {
                                        cb.onTrackChanged(key, null);
                                    }
                                    break;
                                }
                                else if (id.split(":")[0].equals(data.getString("id"))) {
                                    mSelectedTracks.put(key, track);
                                    for (Callback cb : mCallbacks) {
                                        cb.onTrackChanged(key, track.getId());
                                    }
                                    break;
                                }
                            }
                        }
                        break;
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private synchronized void handleTracksUpdate(JSONObject data) {
            ArrayList<TvTrackInfo> tracks = new ArrayList<>();
            HashMap<Integer, TvTrackInfo> selectedTracks = new HashMap<>();
            HashMap<Integer, String> changedTracks = new HashMap<>();
            try {
                for (int key : TRACK_TYPE_LOOKUP.keySet()) {
                    JSONArray tracksPh = data.getJSONArray(TRACK_TYPE_LOOKUP.get(key));
                    for (int i = 0; i < tracksPh.length(); ++i) {
                        JSONObject trackData = tracksPh.getJSONObject(i);
                        TvTrackInfo track = createTrackInfo(trackData, key);
                        if (track != null) {
                            tracks.add(track);
                            if (trackData.getBoolean("selected")) {
                                selectedTracks.put(key, track);
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            for (int key : TRACK_TYPE_LOOKUP.keySet()) {
                if (key != TvTrackInfo.TYPE_SUBTITLE || mSubtitlesEnabled) {
                    if (mSelectedTracks.containsKey(key)) {
                        if (!selectedTracks.containsKey(key)) {
                            changedTracks.put(key, null);
                        } else if (!mSelectedTracks.get(key).getId().equals(selectedTracks.get(key).getId())) {
                            changedTracks.put(key, selectedTracks.get(key).getId());
                        }
                    } else if (selectedTracks.containsKey(key)) {
                        changedTracks.put(key, selectedTracks.get(key).getId());
                    }
                }
            }
            mTracks = tracks;
            mSelectedTracks = selectedTracks;
            for (Callback cb : mCallbacks) {
                cb.onTracksUpdated();
                for (Map.Entry<Integer, String> entry : changedTracks.entrySet()) {
                    cb.onTrackChanged(entry.getKey(), entry.getValue());
                }
            }
        }

        private TvTrackInfo createTrackInfo(JSONObject track, int trackType) {
            TvTrackInfo.Builder builder = null;
            try {
                String language = track.getString("lang");
                String trackId = (track.getInt("id") + ":" + language);
                builder = new TvTrackInfo.Builder(trackType, trackId)
                        .setLanguage(new Locale(language).getISO3Language())
                        .setEncrypted(!track.isNull("contentProtection"))
                        .setEncoding(track.getString("mimeType"));
                if (trackType == TvTrackInfo.TYPE_AUDIO) {
                    if ("descriptions".equals(track.getString("kind"))) {
                        builder.setDescription("AD")
                                .setAudioDescription(true);
                    }
                    builder.setAudioChannelCount(track.getInt("audioChannelConfiguration"));
                } else if (trackType == TvTrackInfo.TYPE_VIDEO) {
                    builder.setVideoPixelAspectRatio((float)track.getDouble("aspectRatio"));
                }
            }
            catch (JSONException e) {
                e.printStackTrace();
            }
            if (builder != null) {
                return builder.build();
            }
            return null;
        }

        private synchronized void handleChannelStatusChange(String eventName) {
            Service service = mServiceManager.getTunedService();
            Triplet triplet = service.getTriplet();
            int onid = 0;
            int tsid = 0;
            int sid = 0;
            if (triplet != null) {
                onid = triplet.getOrigNetId();
                tsid = triplet.getTsId();
                sid = triplet.getServiceId();
            }
            dispatchPlayerStatusChangedEvent(onid, tsid, sid, eventName);
            if (PLAYER_STATUS_PLAYING.equals(eventName)) {
                DvbIChannelAdapter channel = mServiceManager.getTunedChannel();
                if (channel != null) {
                    String appUri = channel.getLinkedAppUri(LINKED_APP_SCHEME_1_1);
                    if (appUri != null) {
                        Log.i(TAG, "Found Hbbtv App with scheme '" + LINKED_APP_SCHEME_1_1 + "' from Related Materials (" + appUri + ")");
                        new GetXmlAitTask().execute(new XmlAitAttributes(appUri, LINKED_APP_SCHEME_1_1));
                    }
                }
            }
            Log.i(TAG, "Received video event " + eventName);
        }

        private synchronized void handleStreamEventDispatch(String eventName, JSONObject data) {
            for (Map.Entry<Integer, String> entry : mStreamEventsLookup.entrySet()) {
                if (entry.getValue().equals(eventName)) {
                    try {
                        for (Callback cb : mCallbacks) {
                            cb.onDashStreamEvent(
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
            switch (eventName) {
                case PLAYER_EVENT_APP_SIGNALLING:
                    if (data != null) {
                        try {
                            String messageData = data.getString("messageData");
                            for (Callback cb : mCallbacks) {
                                cb.onProcessXmlAit(messageData, LINKED_APP_SCHEME_1_1);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case PLAYER_EVENT_EPG_METADATA:
                    if (data != null) {
                        try {
                            mServiceManager.updateEpgMetadata(data.getString("messageData"));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
            }
        }
    };

    private DvbIClient(ITvInputCallback callback) {
        Context context = callback.getContext();
        mTvInputCallback = callback;
        mDbHandler = new DatabaseHandler(context);
        mDvbIView = new DvbIView(context);
        mDvbIView.addJSCallback(mJSCallback);
    }

    public static void instantiate(ITvInputCallback callback) {
        if (mSingleton == null) {
            mSingleton = new DvbIClient(callback);
        }
    }

    public static DvbIClient getInstance() {
        return mSingleton;
    }

    public synchronized void registerCallback(DvbCallback handler) {
        if (!mDvbCallbacks.contains(handler)) {
            mDvbCallbacks.add(handler);
        }
    }

    public synchronized void unregisterCallback(DvbCallback handler) {
        mDvbCallbacks.remove(handler);
    }

    public synchronized void registerCallback(HbbTVCallback handler) {
        if (!mHbbTVCallbacks.contains(handler)) {
            mHbbTVCallbacks.add(handler);
        }
    }

    public synchronized void unregisterCallback(HbbTVCallback handler) {
        mHbbTVCallbacks.remove(handler);
    }

    public synchronized void registerCallback(Callback handler) {
        if (!mCallbacks.contains(handler)) {
            mCallbacks.add(handler);
        }
    }

    public synchronized void unregisterCallback(Callback handler) {
        mCallbacks.remove(handler);
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

    public synchronized List<TvTrackInfo> getTracks() {
        return new ArrayList<>(mTracks);
    }

    public synchronized TvTrackInfo getSelectedTrack(int type) { return mSelectedTracks.get(type); }

    public void setSubtitlesEnabled(boolean enable) {
        if (enable != mSubtitlesEnabled) {
            mSubtitlesEnabled = enable;
            TvTrackInfo subsTrack = mSelectedTracks.get(TvTrackInfo.TYPE_SUBTITLE);
            if (subsTrack != null) {
                mDvbIView.selectTrack(
                        TRACK_TYPE_LOOKUP.get(TvTrackInfo.TYPE_SUBTITLE),
                        enable ? subsTrack.getId().split(":")[0] : "undefined"
                );
            }
        }
    }

    public boolean getSubtitlesEnabled() { return mSubtitlesEnabled; }

    public synchronized boolean selectTrack(int type, String id) {
        // mandatory condition as OrbProvider sends an id of "0" when a track was previously unselected
        if ("0".equals(id) && Boolean.TRUE.equals(mIsUnselected.getOrDefault(type, false)) && mSelectedTracks.get(type) != null) {
            Log.i(TAG, "Restoring previously selected track of type: " + type);
            mIsUnselected.remove(type);
            mDvbIView.selectTrack(TRACK_TYPE_LOOKUP.get(type), mSelectedTracks.get(type).getId().split(":")[0]);
            return true;
        }
        for (TvTrackInfo track : mTracks) {
            if (type == track.getType()) {
                if (id == null || id.isEmpty()) {
                    mDvbIView.selectTrack(TRACK_TYPE_LOOKUP.get(type), "undefined");
                    mIsUnselected.put(type, true);
                    return true;
                } else if (track.getId().equals(id)) {
                    if (type == TvTrackInfo.TYPE_SUBTITLE) {
                        mSubtitlesEnabled = true;
                    }
                    mIsUnselected.remove(type);
                    mDvbIView.selectTrack(TRACK_TYPE_LOOKUP.get(type), id.split(":")[0]);
                    return true;
                }
            }
        }
        return false;
    }

    public View getView() {
        return mDvbIView;
    }

    public synchronized boolean tune(String uid, int instanceIndex) {
        if (mServiceManager.tune(mDbHandler.getServiceForUID(uid), instanceIndex)) {
            mTracks.clear();
            mSelectedTracks.clear();
            mIsUnselected.clear();
            mBlocked = false;
            return true;
        }
        Log.i(TAG, "Failed to tune to service with UID: " + uid);
        return false;
    }

    public synchronized void tuneOff() {
        mServiceManager.tuneOff();
        mTracks.clear();
        mSelectedTracks.clear();
        mIsUnselected.clear();
        mDvbIView.tuneOff();
    }

    public void setPresentationSuspended(boolean suspend) {
        mDvbIView.setPresentationSuspended(suspend);
    }

    public void setVideoRectangle(int x, int y, int width, int height) {
        String deliveryType = "";
        try {
            deliveryType = mServiceManager.getTunedInstance().getDeliveryType();
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
        List<ServiceList> serviceLists = mDbHandler.getServiceLists();
        for (ServiceList serviceList : serviceLists) {
            List<Service> services = serviceList.getServices();
            for (Service service : services) {
                try {
                    ret.add(createChannel(service));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        Log.i(TAG, "Number of services: " + ret.size());
        return ret;
    }

    public TunedServiceManager getServiceManager() {
        return mServiceManager;
    }

    /**
     * Get the service stored in the database by its UID.
     * NOTE: Calling the method with the same UID will return each time
     * a new instance
     * @param uid
     * @return
     */
    public Service getServiceByUID(String uid) {
        if (uid != null) {
            return mDbHandler.getServiceForUID(uid);
        }
        return null;
    }

    public List<Component> getListOfComponents(int type) {
        ArrayList<Component> components = new ArrayList<>();
        boolean all = !TRACK_TYPE_LOOKUP.containsKey(type);
        for (TvTrackInfo track : mTracks) {
            if (all || track.getType() == type) {
                int pid = Integer.parseInt(track.getId().split(":")[0]);
                Component component = new Component(
                        track.getType(),
                        track.getId(),
                        pid,
                        pid,
                        track.getEncoding(),
                        track.isEncrypted(),
                        mSelectedTracks.get(track.getType()) == track,
                        false
                );
                switch (track.getType()) {
                    case TvTrackInfo.TYPE_AUDIO:
                        component.audioDescription = track.isAudioDescription();
                        component.audioChannels = track.getAudioChannelCount();
                        component.language = track.getLanguage();
                        break;
                    case TvTrackInfo.TYPE_VIDEO:
                        component.aspectRatio = track.getVideoPixelAspectRatio();
                        break;
                    case TvTrackInfo.TYPE_SUBTITLE:
                        component.language = track.getLanguage();
                        component.hearingImpaired = track.isHardOfHearing();
                        // TODO: component.label = ?
                        break;
                    default:
                        Log.e(TAG, "Faulty TvTrackInfo type.");
                        break;
                }
                components.add(component);
            }
        }
        return components;
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

    private DvbChannel createChannel(Service service) throws JSONException {
        DvbIChannelAdapter channel = new DvbIChannelAdapter.Builder().setService(service).build();
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
                    ServiceList serviceList = ServiceList.parseFromXML(uri, responseBuilder.toString(), targetRegion);

                    for (Service service : serviceList.getServices()) {
                        Log.d(TAG, "--------- scanned service ----------\n" + service);
                    }
                    mDbHandler.updateServiceList(serviceList);
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

            //TOREMOVE
//            String serviceUniqueIdentifier = "tag:sofiadigital.com,2023:org.hbbtv_DVBI_VBO0200-1";
//            ContentGuide.getEpgMetadata(serviceUniqueIdentifier, "", "", "", "true");
        }
    }

    private class GetXmlAitTask extends AsyncUtils<XmlAitAttributes, XmlAitAttributes> {
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

        @Override
        protected XmlAitAttributes doInBackground(XmlAitAttributes... xmlAit) {
            xmlAit[0].xml = getXmlAit(xmlAit[0].url);
            return xmlAit[0];
        }

        @Override
        protected void onPostExecute(XmlAitAttributes result) {
            if (result != null) {
                for (Callback cb : mCallbacks) {
                    cb.onProcessXmlAit(result.xml, result.scheme);
                }
            }
        }
    }

    private class XmlAitAttributes {
        public String xml;
        public String url;
        public String scheme;

        public XmlAitAttributes(String url, String scheme) {
            this.url = url;
            this.scheme = scheme;
        }
    }

    public static final class Component {
        private int type;
        private String id; // Platform-defined ID that is usable with overrideComponentSelection
        private int componentTag;
        private int pid;
        private String encoding;
        private float aspectRatio; // Video - ASPECT_RATIO_...
        private String language; // Audio and subtitle
        private boolean audioDescription; // Audio
        private int audioChannels; // Audio
        private boolean hearingImpaired; // Subtitle
        private boolean encrypted;
        private boolean active;
        private boolean hidden;
        private String label; // Subtitle

        private Component(int type, String id, int componentTag, int pid, String encoding, boolean encrypted, boolean active, boolean hidden) {
            this.type = type;
            this.id = id;
            this.componentTag = componentTag;
            this.pid = pid;
            this.encoding = encoding;
            this.encrypted = encrypted;
            this.active = active;
            this.hidden = hidden;
        }
        public int getType() { return type; }
        public String getId() { return id; }
        public int getComponentTag() { return componentTag; }
        public int getPid() { return pid; }
        public String getEncoding() { return encoding; }
        public float getAspectRatio() { return aspectRatio; }
        public String getLanguage() { return language; }
        public boolean isAudioDescription() { return audioDescription; }
        public int getAudioChannels() { return audioChannels; }
        public boolean isHearingImpaired() { return hearingImpaired; }
        public boolean isEncrypted() { return encrypted; }
        public boolean isActive() { return active; }
        public boolean isHidden() { return hidden; }
        public String getLabel() { return label; }
    }

    public static class Callback {
        protected void onProcessXmlAit(String xmlAit, String scheme) { }
        protected void onDashStreamEvent(int listenId, String name, String status) { }
        protected void onTracksUpdated() { }
        protected void onTrackChanged(int type, String id) { }
    }
}
