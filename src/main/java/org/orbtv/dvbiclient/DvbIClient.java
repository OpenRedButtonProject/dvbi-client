package org.orbtv.dvbiclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.orbtv.companionlibrary.callbacks.DvbCallback;
import org.orbtv.companionlibrary.callbacks.HbbTVCallback;
import org.orbtv.companionlibrary.model.DvbChannel;
import org.orbtv.companionlibrary.model.EventPeriod;
import org.orbtv.companionlibrary.model.InternalProviderData;
import org.orbtv.companionlibrary.model.Program;
import org.orbtv.companionlibrary.utils.AsyncUtils;
import org.orbtv.dvbiclient.model.*;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;

public class DvbIClient {
    private static DvbIClient mSingleton;
    private static final String TAG = DvbIClient.class.getSimpleName();
    private static final String PREF_DVBI_SERVICE_LIST_VERSION = "dvbi_service_list_version";
    private static final String PREF_DVBI_SERVICE_LIST_URL = "dvbi_service_list_url";

    public static final String TYPE_DVB_I = "TYPE_DVB_I";
    public static final String TYPE_OTHER = "TYPE_OTHER";
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
    private final ITvInputCallback mFallbackCallback = new ITvInputCallback();
    private final DvbIView mDvbIView;
    private final HashMap<Integer, StreamEventInfo> mStreamEventsLookup = new HashMap<>();
    private ServiceListDiscoveryTask mLastDiscoveryTask = null;
    private String mCurrentServiceListUrl = null; // Track URL being loaded for version saving
    private final DatabaseHandler mDbHandler;
    private final ArrayList<DvbCallback> mDvbCallbacks = new ArrayList<>();
    private final ArrayList<HbbTVCallback> mHbbTVCallbacks = new ArrayList<>();
    private final ArrayList<Callback> mCallbacks = new ArrayList<>();
    private boolean mBlocked = false; // TODO: should it be part of TunedServiceManager?
    /** Bumped at the start of each tune so stale now-programme callbacks cannot re-block the previous service. */
    private volatile int mTuneGeneration = 0;
    private String mLastState; // TODO: should it be part of TunedServiceManager?
    private List<TvTrackInfo> mTracks = new ArrayList<>(); // TODO: should it be part of TunedServiceManager?
    private HashMap<Integer, TvTrackInfo> mSelectedTracks = new HashMap<>(); // TODO: should it be part of TunedServiceManager?
    private boolean mSubtitlesEnabled = false;
    private HashMap<Integer, Boolean> mIsUnselected = new HashMap<>();
    private ITvInputCallback mTvInputCallback;
    private final EpgManager mEpgManager;
    private final TunedServiceManager mServiceManager;
    private List<String> mUpdatedServiceEPGs = new ArrayList<>();
    private Timer mPermanentErrorTimer;
    private boolean mOverrideRequestPending = false;
    private String mPendingLinkedAppUrl;
    private String mPendingLinkedAppScheme;
    /** App-pinned instance is outside its Availability Period; ignore DASH PLAYING until it returns. */
    private volatile boolean mPinnedInstanceOutsideWindow = false;
    /** App (LA 1.2 / A/V Control) holds AV decoders; native DASH/RF must wait (A.2.4.1). */
    private volatile boolean mPresentationSuspended = false;
    private String mPendingDashUri;
    private final TunedServiceManager.Callback mServiceManagerCallback = new TunedServiceManager.Callback() {
        @Override
        public void onInstanceChanged(ServiceInstance fromInstance, ServiceInstance toInstance) {
            DvbIChannelAdapter channel;
            Service service = mServiceManager.getTunedService();
            if (service == null) {
                Log.e(TAG, "onInstanceChanged; No tuned service");
            }
            else {
                Log.d(TAG, "PARENTAL_RATING_DEBUG: onInstanceChanged - service=" + service.getUniqueIdentifier() +
                    ", service.getParentalRating()=" + service.getParentalRating() + ", mBlocked=" + mBlocked);
                DvbIChannelAdapter.Builder channelBuilder = new DvbIChannelAdapter.Builder().setService(service);
                if (toInstance != null) {
                    Log.i(TAG, "--------- active instance ---------\n" + toInstance + "\n---------------------------");
                    String uri = toInstance.getUri();
                    channel = channelBuilder.setServiceInstance(toInstance).build();
                    Log.i(TAG, "---------- channel info ----------\n" + channel + "\n------------------------------------");
                    // Set HowRelated before tune/AIT so ChannelChangeSucceeded can read it
                    // (A.2.20.6 / ERRATA0900). handleNative generates RF status that the
                    // polyfill may treat as CCS while this method is still running.
                    publishApplicationHowRelatedHref(service, toInstance, false);
                    String app_1_2 = channel.getLinkedAppUri(LINKED_APP_SCHEME_1_2);
                    if (app_1_2 == null) {
                        handleNativeOrLinkedApp1_1(channel, toInstance, service, uri);
                    } else {
                        handleLinkedApp1_2(channel, app_1_2, service);
                    }

                    final int index = service.getInstances().indexOf(toInstance);
                    // tune()/tuneOff() post to the main executor; notify HbbTV after that work is
                    // queued so ServiceInstanceChanged is not delivered before loadUrl/orb_loadMedia runs.
                    mDvbIView.getContext().getMainExecutor().execute(() -> {
                        for (HbbTVCallback handler : mHbbTVCallbacks) {
                            handler.onServiceInstanceChange(index);
                        }
                    });
                } else {
                    // Unpinned path only: no instance is available, so the service is inactive.
                    // App-pinned instances (HbbTV O.5.4 / ERRATA0400) are kept selected by
                    // TunedServiceManager even when outside their availability window, so this
                    // branch must not run in that case (would launch the service-level
                    // "outside of availability window" app and drop the running 1.1 app).
                    Log.i(TAG, "No service instance is currently available.");
                    mTvInputCallback.tuneOffBroadcast();
                    mDvbIView.tuneOff();
                    channel = channelBuilder.setServiceInstance(fromInstance).build();
                    mTvInputCallback.notifyVideoAvailable();
                    if (!PLAYER_STATUS_STARTING.equals(mLastState)) {
                        mLastState = PLAYER_STATUS_STARTING;
                        dispatchPlayerStatusChangedEvent(
                                channel.getOnid(),
                                channel.getTsid(),
                                channel.getSid(),
                                PLAYER_STATUS_STARTING
                        );
                    }
                    if (channel.getLinkedAppUri(LINKED_APP_SCHEME_2) != null) {
                        launchApp(channel.getLinkedAppUri(LINKED_APP_SCHEME_2), LINKED_APP_SCHEME_2);
                    } else if (channel.getLinkedAppUri(LINKED_APP_SCHEME_1000_1) != null) {
                        launchApp(channel.getLinkedAppUri(LINKED_APP_SCHEME_1000_1), LINKED_APP_SCHEME_1000_1);
                        mDvbIView.getContext().getMainExecutor().execute(() -> {
                            mDvbIView.setVisibility(View.VISIBLE);
                            mDvbIView.clearFocus();
                            mDvbIView.loadUrl(channel.getLinkedAppUri(LINKED_APP_SCHEME_1000_1));
                        });
                    }
                    publishApplicationHowRelatedHref(service, fromInstance, true);
                }
            }
        }

        @Override
        public void onPinnedInstanceAvailabilityChanged(ServiceInstance instance, boolean available) {
            Service service = mServiceManager.getTunedService();
            if (service == null || instance == null) {
                Log.e(TAG, "onPinnedInstanceAvailabilityChanged; No tuned service/instance");
                return;
            }
            DvbIChannelAdapter channel = new DvbIChannelAdapter.Builder()
                    .setService(service)
                    .setServiceInstance(instance)
                    .build();
            if (!available) {
                Log.i(TAG, "PINNED_AVAIL: instance left availability window; stopping presentation, "
                        + "staying on instance (O.5.4 / ERRATA0400)");
                mPinnedInstanceOutsideWindow = true;
                mDvbIView.tuneOff();
                mTracks.clear();
                mSelectedTracks.clear();
                mIsUnselected.clear();
                mTvInputCallback.tuneOffBroadcast();
                invalidateErrorTimer();
                mLastState = PLAYER_STATUS_STARTING;
                dispatchPlayerStatusChangedEvent(
                        channel.getOnid(), channel.getTsid(), channel.getSid(), PLAYER_STATUS_STARTING);
                publishApplicationHowRelatedHref(service, instance, true);
                return;
            }
            if (!mPinnedInstanceOutsideWindow) {
                return;
            }
            Log.i(TAG, "PINNED_AVAIL: instance re-entered availability window; resuming presentation");
            mPinnedInstanceOutsideWindow = false;
            publishApplicationHowRelatedHref(service, instance, false);
            if (mBlocked) {
                return;
            }
            String uri = instance.getUri();
            if ("dvb-dash".equals(instance.getDeliveryType()) && uri != null) {
                mTvInputCallback.tuneOffBroadcast();
                if (mDvbIView.tune(uri, mSubtitlesEnabled)) {
                    mLastState = PLAYER_STATUS_STARTING;
                    dispatchPlayerStatusChangedEvent(
                            channel.getOnid(), channel.getTsid(), channel.getSid(), PLAYER_STATUS_STARTING);
                }
            }
        }

        @Override
        public void onNowProgrammeUpdated(Programme programme) {
            final int generation = mTuneGeneration;
            Service service = mServiceManager.getTunedService();
            if (service == null) {
                return;
            }
            final String uid = service.getUniqueIdentifier();
            int contentAge = 0;
            if (programme != null && programme.getMinimumAge() > 0) {
                contentAge = programme.getMinimumAge();
            } else {
                Service rated = tunedServiceFromDb();
                if (rated != null && rated.getParentalRating() != null) {
                    contentAge = rated.getParentalRating();
                }
            }
            int threshold = mTvInputCallback.getParentalControlAge();
            // Block at-or-above threshold (age 18 content blocked when threshold is 18).
            boolean naturallyBlocked = threshold > 0 && contentAge >= threshold;
            boolean blocked = naturallyBlocked && !mTvInputCallback.isParentalAccessOverridden();
            Log.d(TAG, "PARENTAL_RATING_DEBUG: onNowProgrammeUpdated - programme=" + programme +
                ", contentAge=" + contentAge + ", parentalControlAge="
                + threshold + ", blocked=" + blocked);
            Log.i(TAG, "Now programme updated: " + programme);
            if (mBlocked != blocked) {
                if (generation != mTuneGeneration) {
                    Log.i(TAG, "PARENTAL_RATING: ignoring now-programme update during tune");
                    return;
                }
                Service still = mServiceManager.getTunedService();
                if (still == null || !uid.equals(still.getUniqueIdentifier())) {
                    Log.i(TAG, "PARENTAL_RATING: ignoring now-programme update for previous service");
                    return;
                }
                boolean wasBlocked = mBlocked;
                mBlocked = blocked;
                invalidateErrorTimer();
                if (wasBlocked && !blocked && !naturallyBlocked) {
                    mTvInputCallback.clearParentalAccessOverride();
                }
                ServiceInstance instance = mServiceManager.getTunedInstance();
                if (instance != null) {
                    onInstanceChanged(instance, instance);
                }
                if (generation == mTuneGeneration) {
                    for (HbbTVCallback callback : mHbbTVCallbacks) {
                        callback.onParentalRatingChange(mBlocked);
                    }
                }
            }
        }

        /**
         * Native DASH / RF, with optional 1.1 media-in-parallel (ERRATA0300–0320).
         * Stop media and prompt PIN while blocked; do not kill a running 1.1 app.
         * Launch this service's 1.1 only once presentation is allowed.
         */
        private void handleNativeOrLinkedApp1_1(DvbIChannelAdapter channel,
                ServiceInstance toInstance, Service service, String uri) {
            if (!isStillTuned(service)) {
                Log.i(TAG, "PARENTAL_RATING 1.1/native: ignoring stale handler for "
                    + service.getUniqueIdentifier());
                return;
            }
            clearPendingNativePresentation();
            int contentAge = resolveContentAge(service);
            boolean blocked = isParentalBlocked(contentAge);
            Log.d(TAG, "PARENTAL_RATING 1.1/native: service=" + service.getUniqueIdentifier()
                + ", contentAge=" + contentAge + ", parentalControlAge="
                + mTvInputCallback.getParentalControlAge() + ", blocked=" + blocked);
            if (blocked) {
                mBlocked = true;
                handleRatingBlocked(channel);
                requestOverrideIfNeeded();
                return;
            }
            mBlocked = false;
            mOverrideRequestPending = false;
            // Do not clear a PIN override here: this service may still be over-threshold.
            // Override is cleared on channel change (tune) or when the rating drops.
            launchApp(channel.getLinkedAppUri(LINKED_APP_SCHEME_1_1), LINKED_APP_SCHEME_1_1);
            if ("dvb-dash".equals(toInstance.getDeliveryType())) {
                mTvInputCallback.tuneOffBroadcast();
                if (mPresentationSuspended) {
                    Log.i(TAG, "DECODER: deferring DASH until app releases decoders");
                    mPendingDashUri = uri;
                    dispatchStartingIfNeeded(channel.getOnid(), channel.getTsid(), channel.getSid());
                    return;
                }
                if (mDvbIView.tune(uri, mSubtitlesEnabled)) {
                    dispatchStartingIfNeeded(channel.getOnid(), channel.getTsid(), channel.getSid());
                } else {
                    Log.e(TAG, "DASH tune failed for " + uri);
                    mLastState = PLAYER_STATUS_ERROR;
                    dispatchPlayerStatusChangedEvent(channel.getOnid(), channel.getTsid(),
                        channel.getSid(), PLAYER_STATUS_ERROR);
                }
            } else {
                Log.i(TAG, "RF_TUNE_DEBUG: switching to broadcast instance, deliveryType="
                    + toInstance.getDeliveryType() + ", mLastState=" + mLastState);
                mDvbIView.tuneOff();
                mTracks.clear();
                mSelectedTracks.clear();
                mIsUnselected.clear();
                Triplet rfTriplet = toInstance.getTriplet();
                if (rfTriplet != null) {
                    if (!PLAYER_STATUS_STARTING.equals(mLastState)) {
                        mLastState = PLAYER_STATUS_STARTING;
                        dispatchPlayerStatusChangedEvent(
                            rfTriplet.getOrigNetId(),
                            rfTriplet.getTsId(),
                            rfTriplet.getServiceId(),
                            PLAYER_STATUS_STARTING);
                    }
                    // A.2.4.1 / ERRATA0710–0720: still tune RF so AIT/SI are
                    // acquired. DvbGlue.tune() keeps decoding stopped while
                    // the app holds the AV decoders; v/b stays CONNECTING.
                    if (mPresentationSuspended) {
                        Log.i(TAG, "DECODER: tuning RF with decoding suspended (AIT); app holds decoders");
                    }
                    Log.i(TAG, "RF_TUNE_DEBUG: calling tuneBroadcast(" + rfTriplet + ")");
                    boolean tuned = mTvInputCallback.tuneBroadcast(rfTriplet.toString());
                    if (!tuned) {
                        Log.e(TAG, "RF_TUNE_DEBUG: tuneBroadcast failed for " + rfTriplet);
                        mLastState = PLAYER_STATUS_ERROR;
                        dispatchPlayerStatusChangedEvent(
                            rfTriplet.getOrigNetId(),
                            rfTriplet.getTsId(),
                            rfTriplet.getServiceId(),
                            PLAYER_STATUS_ERROR);
                    }
                } else {
                    Log.w(TAG, "RF_TUNE_DEBUG: no RF triplet on broadcast instance, tuneOff only");
                    mDvbIView.tuneOff();
                }
            }
        }

        private void handleLinkedApp1_2(DvbIChannelAdapter channel, String appUrl, Service service) {
            if (!isStillTuned(service)) {
                Log.i(TAG, "PARENTAL_RATING 1.2: ignoring stale handler for "
                    + service.getUniqueIdentifier());
                return;
            }
            mPendingLinkedAppUrl = appUrl;
            mPendingLinkedAppScheme = LINKED_APP_SCHEME_1_2;
            int contentAge = resolveContentAge(service);
            boolean blocked = isParentalBlocked(contentAge);
            Log.d(TAG, "PARENTAL_RATING 1.2: contentAge=" + contentAge
                + ", threshold=" + mTvInputCallback.getParentalControlAge()
                + ", overridden=" + mTvInputCallback.isParentalAccessOverridden()
                + ", blocked=" + blocked);
            // Linked app controlling media: terminal does not start DASH itself.
            clearPendingNativePresentation();
            mTvInputCallback.tuneOffBroadcast();
            mDvbIView.tuneOff();
            if (blocked) {
                mBlocked = true;
                mTvInputCallback.destroyHbbtvApplication();
                handleRatingBlocked(channel);
                requestOverrideIfNeeded();
                return;
            }
            mBlocked = false;
            mOverrideRequestPending = false;
            // Do not clear a PIN override here: this service may still be over-threshold.
            // Override is cleared on channel change (tune) or when the rating drops.
            launchApp(appUrl, LINKED_APP_SCHEME_1_2);
            if (!PLAYER_STATUS_STARTING.equals(mLastState)) {
                mLastState = PLAYER_STATUS_STARTING;
                dispatchPlayerStatusChangedEvent(channel.getOnid(), channel.getTsid(),
                    channel.getSid(), PLAYER_STATUS_STARTING);
            }
            mTvInputCallback.notifyVideoAvailable();
        }

        private void requestOverrideIfNeeded() {
            if (mOverrideRequestPending) {
                return;
            }
            mOverrideRequestPending = true;
            mTvInputCallback.requestParentalAccessOverride(approved -> {
                mOverrideRequestPending = false;
                if (!approved) {
                    Log.i(TAG, "Parental override notApproved; remaining blocked");
                    return;
                }
                Log.i(TAG, "Parental override approved; retrying presentation");
                mBlocked = false;
                invalidateErrorTimer();
                ServiceInstance instance = mServiceManager.getTunedInstance();
                if (instance != null) {
                    onInstanceChanged(instance, instance);
                } else if (mPendingLinkedAppUrl != null) {
                    launchApp(mPendingLinkedAppUrl, mPendingLinkedAppScheme);
                    mTvInputCallback.notifyVideoAvailable();
                }
            });
        }

        private boolean isStillTuned(Service service) {
            Service tuned = mServiceManager.getTunedService();
            return service != null && tuned != null
                && service.getUniqueIdentifier().equals(tuned.getUniqueIdentifier());
        }

        private int resolveContentAge(Service service) {
            Programme now = mServiceManager.getNowProgramme();
            if (now != null && now.getMinimumAge() > 0) {
                return now.getMinimumAge();
            }
            Service rated = tunedServiceFromDb();
            if (rated == null) {
                rated = service;
            }
            if (rated != null && rated.getParentalRating() != null) {
                return rated.getParentalRating();
            }
            return 0;
        }

    private boolean isParentalBlocked(int contentAge) {
            if (mTvInputCallback.isParentalAccessOverridden()) {
                return false;
            }
            int threshold = mTvInputCallback.getParentalControlAge();
            return threshold > 0 && contentAge >= threshold;
        }

        private void launchApp(String appUrl, String scheme) {
            if (appUrl != null) {
                Log.i(TAG, "Found Hbbtv App with scheme '" + scheme + "' from Related Materials (" + appUrl + ")");
                new GetXmlAitTask().execute(new XmlAitAttributes(appUrl, scheme));
            }
        }

        /**
         * Keep video/broadcast in connecting (STARTING) without presenting DASH.
         * Do not schedule a permanent-error timeout: §10.2.6.1 allows staying in connecting
         * until PIN, rating drop, or the user leaves the channel (ERRATA0320).
         */
        private void handleRatingBlocked(DvbIChannelAdapter channel) {
            if (channel == null) {
                return;
            }
            Log.i(TAG, "CHANNEL_STATUS: parental block — stopping media, staying CONNECTING");
            mDvbIView.tuneOff();
            mTracks.clear();
            mSelectedTracks.clear();
            mIsUnselected.clear();
            mTvInputCallback.tuneOffBroadcast();
            invalidateErrorTimer();
            mLastState = PLAYER_STATUS_STARTING;
            dispatchPlayerStatusChangedEvent(
                channel.getOnid(), channel.getTsid(), channel.getSid(), PLAYER_STATUS_STARTING);
        }
    };

    /**
     * HbbTV A.2.20.6: current HowRelated@href for the running app, or null if the
     * app is not currently attributed to DVB-I signalling (e.g. classic AIT).
     */
    private void publishApplicationHowRelatedHref(Service service, ServiceInstance instance,
            boolean outsideAvailability) {
        String href = null;
        if (outsideAvailability) {
            if (serviceHasLinkedApp(service, LINKED_APP_SCHEME_2)) {
                href = LINKED_APP_SCHEME_2;
            }
        } else {
            href = getInstanceHowRelatedHref(instance);
        }
        Log.i(TAG, "HOW_RELATED: href=" + href + ", outside=" + outsideAvailability);
        final String publishedHref = href;
        Runnable publish = () -> {
            for (Callback cb : mCallbacks) {
                cb.onApplicationHowRelatedHrefChanged(publishedHref);
            }
        };
        // OrbSession JNI must not run on TunedServiceManager's poller. Queue on
        // main before handleNative so HowRelated is ahead of ChannelChangeSucceeded.
        if (Looper.getMainLooper().isCurrentThread()) {
            publish.run();
        } else {
            mDvbIView.getContext().getMainExecutor().execute(publish);
        }
    }

    /** Instance-level 1.1 / 1.2 only; does not fall back to service-level type 2. */
    private static String getInstanceHowRelatedHref(ServiceInstance instance) {
        if (instance == null) {
            return null;
        }
        String href11 = null;
        for (RelatedMaterial rm : instance.getRelatedMaterials()) {
            String href = rm.getHowRelatedHref();
            if (href == null || !rm.isXmlAitContentType()) {
                continue;
            }
            if (LINKED_APP_SCHEME_1_2.equals(href)) {
                return href;
            }
            if (LINKED_APP_SCHEME_1_1.equals(href)) {
                href11 = href;
            }
        }
        return href11;
    }

    private static boolean serviceHasLinkedApp(Service service, String scheme) {
        if (service == null) {
            return false;
        }
        for (RelatedMaterial rm : service.getRelatedMaterials()) {
            if (scheme.equals(rm.getHowRelatedHref()) && rm.isXmlAitContentType()) {
                return true;
            }
        }
        return false;
    }

    private final DvbIView.JSCallback mJSCallback = new DvbIView.JSCallback() {

        @Override
        public void onVideoEvent(String eventName, JSONObject data) {
            Log.i(TAG, "Received video event " + eventName);
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
                    } else {
                        handleEventDispatch(eventName, data);
                    }
                    break;
            }
        }

        @Override
        public synchronized void onStreamEvent(String targetUrl, String eventName, JSONObject data) {
            Log.i(TAG, "Received stream event " + targetUrl + " with eventName " + eventName);
            try {
                String Id = "";
                String status = "error";
                double startTime = -1;
                double duration = -1;
                String messageData = "";
                String contentEncoding = "binary";
                if (!data.isNull("id")) {
                    Id = data.getString("id");
                }
                if (!data.isNull("calculatedPresentationTime")) {
                    startTime = data.getDouble("calculatedPresentationTime");
                }
                if (!data.isNull("duration")) {
                    duration = data.getDouble("duration");
                }
                if (!data.isNull("status")) {
                    status = data.getString("status");
                }
                if (!data.isNull("__orb_204data__")) {
                    JSONObject data204 = data.getJSONObject("__orb_204data__");
                    if (!data204.isNull("payload")) {
                        messageData = data204.getString("payload");
                    }
                    contentEncoding = data204.getString("type");
                }
                for (Map.Entry<Integer, StreamEventInfo> entry : mStreamEventsLookup.entrySet()) {
                    if (targetUrl.equals(entry.getValue().targetUrl) && eventName.equals(entry.getValue().eventName)) {
                        for (Callback cb : mCallbacks) {
                            cb.onDashStreamEvent(
                                    entry.getKey(),
                                    eventName,
                                    status,
                                    Id,
                                    startTime,
                                    duration,
                                    messageData,
                                    contentEncoding
                            );
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
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
                        .setLanguage(language)
                        .setEncrypted(!track.isNull("contentProtection"))
                        .setEncoding(track.getString("encoding"));
                switch (trackType) {
                    case TvTrackInfo.TYPE_AUDIO:
                        if ("descriptions".equals(track.optString("kind", ""))) {
                            builder.setDescription("AD")
                                    .setAudioDescription(true);
                        }
                        if (!track.isNull("audioChannelConfiguration")) {
                            builder.setAudioChannelCount(track.getInt("audioChannelConfiguration"));
                        }
                        break;
                    case TvTrackInfo.TYPE_VIDEO:
                        if (!track.isNull("aspectRatio")) {
                            builder.setVideoPixelAspectRatio((float)track.getDouble("aspectRatio"));
                        }
                        break;
                    case TvTrackInfo.TYPE_SUBTITLE:
                        if ("hearingImpaired".equals(track.optString("kind", ""))) {
                            builder.setHardOfHearing(true);
                        }
                        break;
                    default:
                        break;
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
            int onid = 0;
            int tsid = 0;
            int sid = 0;
            Triplet triplet = getHbbtvChannelStatusTriplet();
            if (triplet != null) {
                onid = triplet.getOrigNetId();
                tsid = triplet.getTsId();
                sid = triplet.getServiceId();
            }
            ServiceInstance tunedInstance = mServiceManager.getTunedInstance();
            Log.i(TAG, "handleChannelStatusChange; onid=" + onid + ", tsid=" + tsid + ", sid=" + sid
                + ", event=" + eventName + ", mLastState=" + mLastState
                + ", tunedInstance=" + (tunedInstance != null ? tunedInstance.getDeliveryType() + ":" + tunedInstance.getTriplet() : "null"));
            if (PLAYER_STATUS_PLAYING.equals(eventName) && mBlocked) {
                Log.w(TAG, "Ignoring PLAYING while parental blocked");
                return;
            }
            if (PLAYER_STATUS_PLAYING.equals(eventName) && mPinnedInstanceOutsideWindow) {
                Log.w(TAG, "Ignoring PLAYING while pinned instance is outside availability window");
                return;
            }
            if (PLAYER_STATUS_PLAYING.equals(eventName) && mPresentationSuspended) {
                Log.w(TAG, "Ignoring PLAYING while app holds decoders");
                return;
            }
            dispatchPlayerStatusChangedEvent(onid, tsid, sid, eventName);
            switch (eventName) {
                case PLAYER_STATUS_PLAYING:
                    mLastState = PLAYER_STATUS_PLAYING;
                    DvbIChannelAdapter channel = mServiceManager.getTunedChannel();
                    if (channel == null) {
                        Log.e(TAG, "RF_TUNE_DEBUG: PLAYING received but no tuned channel; notifyVideoAvailable skipped");
                    }
                    else {
                        Log.i(TAG, "RF_TUNE_DEBUG: PLAYING received, calling notifyVideoAvailable");
                        mTvInputCallback.notifyVideoAvailable();
                    }
                    break;
                case PLAYER_STATUS_BAD_CONNECTION:
                    mTvInputCallback.notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL);
                    break;
                case PLAYER_STATUS_ERROR:
                    mTvInputCallback.notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                    break;
            }
        }

        private synchronized void handleEventDispatch(String eventName, JSONObject data) {
            switch (eventName) {
                case PLAYER_EVENT_APP_SIGNALLING:
                    try {
                        String messageData = data.getString("messageData");
                        for (Callback cb : mCallbacks) {
                            cb.onProcessXmlAit(messageData, PLAYER_EVENT_APP_SIGNALLING);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case PLAYER_EVENT_EPG_METADATA:
                    mEpgManager.requestUpdateFromEventStream(mServiceManager.getTunedService(), data);
                    break;
            }
        }
    };

    private DvbIClient(Context context) {
        mTvInputCallback = mFallbackCallback;
        mDbHandler = new DatabaseHandler(context);
        mDvbIView = new DvbIView(context);
        mDvbIView.addJSCallback(mJSCallback);
        mEpgManager = new EpgManager(mDbHandler);
        mEpgManager.registerCallback(serviceUIDs -> {
            long startMs = System.currentTimeMillis();
            Log.i(TAG, "EPG_DEBUG: onEpgUpdated on thread=" + Thread.currentThread().getName()
                + ", serviceCount=" + serviceUIDs.size() + ", uids=" + serviceUIDs);
            mUpdatedServiceEPGs = serviceUIDs;
            mTvInputCallback.updateEventPeriods();
            Log.i(TAG, "EPG_DEBUG: onEpgUpdated completed in "
                + (System.currentTimeMillis() - startMs) + "ms");
        });
        mServiceManager = new TunedServiceManager(mEpgManager, mDbHandler);
        mServiceManager.registerCallback(mServiceManagerCallback);
    }

    public void setTvInputCallback(ITvInputCallback callback) {
        if (callback == null) {
            mTvInputCallback = mFallbackCallback;
        }
        else {
            mTvInputCallback = callback;
            mTvInputCallback.updateEventPeriods();
        }
    }

    public static void instantiate(Context context) {
        if (mSingleton == null) {
            mSingleton = new DvbIClient(context);
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
        Log.i(TAG, "Subscribe Stream event with targetUrl '" + targetUrl + "' and eventName '" + eventName + "'.");
        if (targetUrl != null) {
            mStreamEventsLookup.put(listenId, new StreamEventInfo(targetUrl, eventName));
            mDvbIView.addStreamEventListener(targetUrl, eventName);
            return true;
        }
        return false;
    }

    public synchronized void unsubscribeStreamEvent(int listenId) {
        if (mStreamEventsLookup.containsKey(listenId)) {
            mDvbIView.removeStreamEventListener(mStreamEventsLookup.get(listenId).targetUrl);
            mStreamEventsLookup.remove(listenId);
        }
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

    public void onParentalControlAgeChanged() {
        if (mServiceManager.getTunedService() != null) {
            mServiceManagerCallback.onNowProgrammeUpdated(mServiceManager.getNowProgramme());
        }
    }

    /**
     * Linked application type 1.2 could not be started (XML AIT or first page). Discard this
     * service instance and select the next (HbbTV O.3 / ERRATA0600–0630).
     */
    public void onLinkedApp12StartFailed(String reason) {
        ServiceInstance current = mServiceManager.getTunedInstance();
        Service service = mServiceManager.getTunedService();
        if (current == null || service == null) {
            Log.w(TAG, "LA12_FAIL: no tuned instance (" + reason + ")");
            return;
        }
        DvbIChannelAdapter channel = new DvbIChannelAdapter.Builder()
                .setService(service)
                .setServiceInstance(current)
                .build();
        if (channel == null || channel.getLinkedAppUri(LINKED_APP_SCHEME_1_2) == null) {
            Log.i(TAG, "LA12_FAIL: current instance is not LA 1.2; ignoring (" + reason + ")");
            return;
        }
        Log.i(TAG, "LA12_FAIL: cannot start (" + reason + "); discarding instance");
        mPendingLinkedAppUrl = null;
        mPendingLinkedAppScheme = null;
        mTvInputCallback.destroyHbbtvApplication();
        mDvbIView.tuneOff();
        mTvInputCallback.tuneOffBroadcast();
        if (!mServiceManager.discardCurrentInstanceAndReselect()) {
            Log.w(TAG, "LA12_FAIL: discard/reselect did not change instance");
        }
    }

    /** Latest service-list row for the tuned service (ratings may have changed). */
    private Service tunedServiceFromDb() {
        Service s = mServiceManager.getTunedService();
        if (s == null) {
            return null;
        }
        Service fresh = mDbHandler.getServiceForUID(s.getUniqueIdentifier());
        return fresh != null ? fresh : s;
    }

    /**
     * setChannel onto the already-selected DVB-I instance (O.5.4) must not drop DASH tracks.
     * Clearing them makes getComponents empty and the 1.1 v/b object never leaves CONNECTING.
     */
    private boolean shouldRetainTracks(String uid, int instanceIndex) {
        Service current = mServiceManager.getTunedService();
        ServiceInstance currentInst = mServiceManager.getTunedInstance();
        if (current == null || currentInst == null || uid == null
                || !uid.equals(current.getUniqueIdentifier())) {
            return false;
        }
        if (instanceIndex < 0) {
            return true;
        }
        return instanceIndex == current.getInstances().indexOf(currentInst);
    }

    public synchronized boolean tune(String uid, int instanceIndex) {
        mTuneGeneration++;
        boolean blocked = mBlocked;
        mBlocked = false;
        mPinnedInstanceOutsideWindow = false;
        clearPendingNativePresentation();
        mOverrideRequestPending = false;
        mTvInputCallback.clearParentalAccessOverride();
        mPendingLinkedAppUrl = null;
        mPendingLinkedAppScheme = null;
        invalidateErrorTimer();
        mLastState = null;
        boolean retainTracks = shouldRetainTracks(uid, instanceIndex);
        if (mServiceManager.tune(mDbHandler.getServiceForUID(uid), instanceIndex)) {
            if (!retainTracks) {
                mTracks.clear();
                mStreamEventsLookup.clear();
                mSelectedTracks.clear();
                mIsUnselected.clear();
            }
            return true;
        }
        mBlocked = blocked;
        Log.i(TAG, "Failed to tune to service with UID: " + uid);
        return false;
    }

    public synchronized void tuneOff() {
        mLastState = null;
        mPinnedInstanceOutsideWindow = false;
        clearPendingNativePresentation();
        mServiceManager.tuneOff();
        mStreamEventsLookup.clear();
        mTracks.clear();
        mSelectedTracks.clear();
        mIsUnselected.clear();
        mDvbIView.tuneOff();
    }

    public void setPresentationSuspended(boolean suspend) {
        mPresentationSuspended = suspend;
        mDvbIView.setPresentationSuspended(suspend);
        if (!suspend) {
            startPendingNativePresentation();
        }
    }

    private void clearPendingNativePresentation() {
        mPendingDashUri = null;
    }

    /** A.2.4.1: start deferred DASH once the app has released the decoders.
     * RF is already tuned (AIT); DvbGlue startDecoding runs from unsuspend. */
    private void startPendingNativePresentation() {
        if (mPresentationSuspended || mBlocked) {
            return;
        }
        if (mPendingDashUri != null) {
            String uri = mPendingDashUri;
            mPendingDashUri = null;
            Log.i(TAG, "DECODER: starting deferred DASH after app released decoders");
            mTvInputCallback.tuneOffBroadcast();
            if (!mDvbIView.tune(uri, mSubtitlesEnabled)) {
                Log.e(TAG, "DECODER: deferred DASH tune failed for " + uri);
                mLastState = PLAYER_STATUS_ERROR;
                Triplet triplet = getHbbtvChannelStatusTriplet();
                if (triplet != null) {
                    dispatchPlayerStatusChangedEvent(
                        triplet.getOrigNetId(), triplet.getTsId(), triplet.getServiceId(),
                        PLAYER_STATUS_ERROR);
                }
            }
        }
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

    public List<Program> getListOfEvents(DvbChannel channel, long startMs, long endMs) {
        return getProgramsForDvbChannel(channel, startMs / 1000, endMs / 1000, Integer.MAX_VALUE);
    }

    public List<Program> getNowNextEvents(DvbChannel channel) {
        long currentTime = System.currentTimeMillis() / 1000;
        return getProgramsForDvbChannel(channel, currentTime, currentTime + 86400, 2);
    }

    public List<EventPeriod> getListOfUpdatedEventPeriods() {
        ArrayList<EventPeriod> eventPeriods = new ArrayList<>();
        long currentTime = System.currentTimeMillis() / 1000;
        long start = (currentTime - 10800) - currentTime % 10800;
        long end = (currentTime + 10800 * 7) - currentTime % 10800;
        for (String uid : mUpdatedServiceEPGs) {
            Service service = mDbHandler.getServiceForUID(uid);
            //Log.d(TAG, "Get updated event period for service " + uid);

            // TODO: In case the triplet is null, return the child instance's triplet
            if (service != null && service.getTriplet() != null) {
                eventPeriods.add(new EventPeriod(service.getTriplet().toString(), start, end));
            }
            else {
                Log.w(TAG, "Either the service with uid " + uid + " or its triplet is null!!!");
            }
        }
        return eventPeriods;
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
                // Default to HbbTV test harness service list URL for test environment
                serviceListURL = "http://hbbtv1.test/servicelist.xml";
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

    /**
     * Update the DVB-I service list if the version has changed.
     * Per TS 103 770, the @version attribute is incremented for each change in the data.
     * This method checks the version before reloading to avoid unnecessary updates.
     *
     * @param serviceListURL URL to the service list XML, or null to use default
     * @return true if service list was updated or is being updated, false if version unchanged or error
     */
    public boolean updateServiceList(String serviceListURL) {
        if (serviceListURL == null || serviceListURL.isEmpty()) {
            // Default to HbbTV test harness service list URL for test environment
            serviceListURL = "http://hbbtv1.test/servicelist.xml";
        }

        // Get current version from service list
        String currentVersion = getServiceListVersion(serviceListURL);
        if (currentVersion == null) {
            // Could not determine version, reload to be safe
            Log.d(TAG, "Could not determine DVB-I service list version, reloading to ensure latest data");
            return startServiceSearch(serviceListURL);
        }

        // Check stored version
        SharedPreferences prefs = mDvbIView.getContext().getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        String lastKnownVersion = prefs.getString(PREF_DVBI_SERVICE_LIST_VERSION, null);
        String lastKnownUrl = prefs.getString(PREF_DVBI_SERVICE_LIST_URL, null);

        if (lastKnownVersion == null || !currentVersion.equals(lastKnownVersion) ||
            !serviceListURL.equals(lastKnownUrl)) {
            Log.d(TAG, String.format("DVB-I service list version changed from %s to %s, reloading",
                lastKnownVersion, currentVersion));
            // Store URL and version to save after successful load
            mCurrentServiceListUrl = serviceListURL;
            boolean started = startServiceSearch(serviceListURL);
            return started;
        } else {
            Log.d(TAG, String.format("DVB-I service list version unchanged (%s), skipping reload", currentVersion));
            return false;
        }
    }

    /**
     * Extract the version attribute from the DVB-I service list XML string.
     * Per TS 103 770, the @version attribute is incremented for each change.
     * @param xmlContent The XML content as a string
     * @return Version string, or null if unable to parse
     */
    private String extractVersionFromXml(String xmlContent) {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new java.io.StringReader(xmlContent));

            // Parse until we find the ServiceList element
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("ServiceList".equals(tagName)) {
                        // Found ServiceList - get version attribute
                        String version = parser.getAttributeValue(null, "version");
                        return version;
                    }
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing service list version: " + e.getMessage());
        }
        return null;
    }

    /**
     * Fetch the version attribute from the DVB-I service list XML via HTTP.
     * Per TS 103 770, the @version attribute is incremented for each change.
     * This is used for version checking before downloading the full XML.
     * @param serviceListUrl URL to the service list XML
     * @return Version string, or null if unable to fetch/parse
     */
    private String getServiceListVersion(String serviceListUrl) {
        try {
            URL url = new URL(serviceListUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/xml, text/xml, */*");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                try {
                    // Read just enough to parse the root element
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder xmlStart = new StringBuilder();
                    String line;
                    int lineCount = 0;
                    while ((line = reader.readLine()) != null && lineCount < 10) {
                        xmlStart.append(line);
                        xmlStart.append("\n");
                        lineCount++;
                        // Check if we've seen the ServiceList tag
                        if (line.contains("<ServiceList")) {
                            // Try to extract version from this line
                            int versionIndex = line.indexOf("version=\"");
                            if (versionIndex >= 0) {
                                int start = versionIndex + 9; // "version=\"" is 9 chars
                                int end = line.indexOf("\"", start);
                                if (end > start) {
                                    return line.substring(start, end);
                                }
                            }
                        }
                    }
                    // If not found in first few lines, parse with XML parser
                    String xmlContent = xmlStart.toString();
                    return extractVersionFromXml(xmlContent);
                } finally {
                    inputStream.close();
                }
            } else {
                Log.w(TAG, String.format("Failed to fetch service list version: HTTP %d", responseCode));
            }
            connection.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "Error fetching service list version: " + e.getMessage());
        }
        return null;
    }

    /**
     * Save the service list version and URL to SharedPreferences.
     * This should be called after a successful service list update.
     */
    private void saveServiceListVersion(String serviceListUrl, String version) {
        SharedPreferences prefs = mDvbIView.getContext().getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_DVBI_SERVICE_LIST_VERSION, version);
        editor.putString(PREF_DVBI_SERVICE_LIST_URL, serviceListUrl);
        editor.apply();
        Log.d(TAG, String.format("Saved DVB-I service list version %s for URL %s", version, serviceListUrl));
    }

    public boolean isInstanceInCurrentService(int onid, int tsid, int sid) {
        Service service = mServiceManager.getTunedService();
        if (service != null) {
            for (ServiceInstance instance: service.getInstances()) {
                DvbIChannelAdapter channel = new DvbIChannelAdapter.Builder()
                    .setService(service)
                    .setServiceInstance(instance)
                    .build();
                if (channel.getOnid() == onid && channel.getTsid() == tsid && channel.getSid() == sid) {
                    Log.d(TAG, "Instance with triplet dvb://" + onid + "." + tsid + "." + sid + " belongs to the current service.");
                    return true;
                }
            }
        }
        Log.d(TAG, "Instance with triplet dvb://" + onid + "." + tsid + "." + sid + " does not belong to the current service.");
        return false;
    }

    public void launchBackwardsEpgApp() {
       Programme programme = mServiceManager.getNowProgramme();
       if (programme != null) {
           String onDemandUrl = programme.getOnDemandURL();
           if (onDemandUrl != null && !onDemandUrl.isEmpty()) {
               new GetXmlAitTask().execute(new XmlAitAttributes(onDemandUrl, LINKED_APP_SCHEME_1_1 + "?lloc=epg"));
           }
       }
    }

    private void invalidateErrorTimer() {
        if (mPermanentErrorTimer != null) {
            mPermanentErrorTimer.cancel();
            mPermanentErrorTimer = null;
        }
    }

    private List<Program> getProgramsForDvbChannel(DvbChannel channel, long start, long end, Integer limit) {
        ArrayList<Program> events = new ArrayList<>();
        String uid = extractUidFromDvbChannel(channel);
        if (uid != null) {
            List<Programme> programmes = mDbHandler.getProgrammesForService(uid, start, end, limit);
            Service service = mDbHandler.getServiceForUID(uid);
            for (Programme programme : programmes) {
                InternalProviderData data = new InternalProviderData();
                int minAge = programme.getMinimumAge();
                String ratingScheme = programme.getParentalRatingScheme();
                if (minAge <= 0) {
                    if (service != null && service.getParentalRating() != null) {
                        minAge = service.getParentalRating();
                        if (minAge != 255) {
                            ratingScheme = "dvb-si";
                        }
                    }
                } else if (minAge != 255 && (ratingScheme == null || ratingScheme.isEmpty())) {
                    ratingScheme = "dvb-si";
                }
                try {
                    data.put("RATING", minAge);
                    data.put("RATING_SCHEME", ratingScheme);
                    data.put("DVB_URI", programme.getProgramId());
                    data.put("PROGRAM_ID_TYPE", 0); // ID_TVA_CRID (HbbTV O.6.2.2)
                } catch (InternalProviderData.ParseException e) {
                    e.printStackTrace();
                }

                events.add(new Program.Builder()
                        .setChannelId(channel.getId())
                        .setTitle(programme.getTitle())
                        .setDescription(programme.getMediumDescription())
                        // HbbTV O.6.2.2: longDescription is undefined for DVB-I DASH.
                        .setStartTimeUtcMillis(programme.getStartTime() * 1000)
                        .setEndTimeUtcMillis(programme.getEndTime() * 1000)
                        // Age stays in InternalProviderData for HbbTV. Do not publish
                        // TvContentRating — Live Channels would show its own lock UI.
                        .setInternalProviderData(data)
                        .build());
            }
        }
        //Log.i(TAG, "Retrieved " + events.size() + " programs for service " + uid + " with id " + channel.getId() + " in the range of " + start + " and " + end);
        return events;
    }

    private String extractUidFromDvbChannel(DvbChannel channel) {
        try {
            InternalProviderData provider = channel.getInternalProviderData();
            if (provider != null) {
                return provider.get(KEY_DVBI_UID).toString();
            }
        } catch (Exception e) {
        }
        return null;
    }

    private DvbChannel createChannel(Service service) throws JSONException {
        DvbIChannelAdapter channel = new DvbIChannelAdapter.Builder().setService(service).build();
        InternalProviderData data = new InternalProviderData();
        data.put("DVB_URI", new Triplet.Builder()
                .setOrigNetId(channel.getOnid())
                .setTsId(channel.getTsid())
                .setServiceId(channel.getSid())
                .build().toString());
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

    /**
     * Triplet for HbbTV channel-status events: prefer the active delivery instance
     * (e.g. RF 99:1:12) over the DVB-I service-identifier triplet (e.g. 5:2774:2).
     */
    private Triplet getHbbtvChannelStatusTriplet() {
        ServiceInstance instance = mServiceManager.getTunedInstance();
        if (instance != null && instance.getTriplet() != null) {
            return instance.getTriplet();
        }
        Service service = mServiceManager.getTunedService();
        if (service != null && service.getTriplet() != null) {
            return service.getTriplet();
        }
        DvbIChannelAdapter channel = mServiceManager.getTunedChannel();
        if (channel != null) {
            return new Triplet.Builder()
                .setOrigNetId(channel.getOnid())
                .setTsId(channel.getTsid())
                .setServiceId(channel.getSid())
                .build();
        }
        return null;
    }

    private void dispatchStartingIfNeeded(int onid, int tsid, int sid) {
        if (!PLAYER_STATUS_STARTING.equals(mLastState)) {
            mLastState = PLAYER_STATUS_STARTING;
            dispatchPlayerStatusChangedEvent(onid, tsid, sid, PLAYER_STATUS_STARTING);
        }
    }

    private void dispatchPlayerStatusChangedEvent(int onid, int tsid, int sid, String event) {
        Integer statusCode = HBBTV_CHANNEL_STATUS_LOOKUP.get(event);
        Log.i(TAG, "dispatchPlayerStatusChangedEvent called - onid=" + onid +
            ", tsid=" + tsid + ", sid=" + sid + ", event=" + event +
            ", statusCode=" + statusCode + ", mLastState=" + mLastState);
        if (statusCode != null) {
            for (HbbTVCallback handler : mHbbTVCallbacks) {
                Log.i(TAG, "Calling onChannelChangeStatus on HbbTVCallback - statusCode=" + statusCode);
                handler.onChannelChangeStatus(onid, tsid, sid, statusCode);
            }
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

                    // Handle non-200 response codes gracefully (e.g., 404 when servicelist.xml doesn't exist yet)
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        Log.w(TAG, "Service list URL returned " + responseCode + ": " + uri +
                              " (Service list may not be available yet - this is OK for auto-loading)");
                        continue; // Skip this URI and continue to next if any
                    }

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

                    //for (Service service : serviceList.getServices()) {
                    //    Log.d(TAG, "--------- scanned service ----------\n" + service);
                    //}
                    mDbHandler.updateServiceList(serviceList);

                    // Extract and save version from the loaded XML
                    if (mCurrentServiceListUrl != null && uri.equals(mCurrentServiceListUrl)) {
                        String version = extractVersionFromXml(responseBuilder.toString());
                        if (version != null) {
                            saveServiceListVersion(uri, version);
                        }
                    }
                }
                mEpgManager.refreshServiceLists();
            }
            catch(IOException e) {
                Log.e(TAG, "Error sending request", e);
            }
            catch(Exception e) {
                Log.e(TAG, "Error loading service list", e);
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
            Log.d(TAG, "finalizeSearch: Service list discovery completed, triggering callbacks");
            mLastDiscoveryTask = null; // Reset so new searches can be started
            mCurrentServiceListUrl = null; // Clear tracked URL
            for (DvbCallback handler : mDvbCallbacks) {
                handler.onDvbtStatusChanged(100);
            }
            // Trigger channel sync after service list is loaded/updated
            // This ensures DVB-I channels are added to TV Contract so they appear in the channel list
            Log.d(TAG, "finalizeSearch: Triggering onServiceAdded() to sync channels. Callback count: " + mDvbCallbacks.size());
            for (DvbCallback handler : mDvbCallbacks) {
                handler.onServiceAdded();
            }
            // Service-list ParentalRating may have changed (ERRATA0310).
            onParentalControlAgeChanged();

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
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

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
                Log.w(TAG, "XML AIT HTTP " + responseCode + " for " + uri);
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
            if (result == null) {
                return;
            }
            if (result.xml == null || result.xml.isEmpty()) {
                Log.e(TAG, "XML AIT fetch failed scheme=" + result.scheme + " url=" + result.url);
                if (LINKED_APP_SCHEME_1_2.equals(result.scheme)) {
                    onLinkedApp12StartFailed("XML AIT fetch failed");
                }
                return;
            }
            for (Callback cb : mCallbacks) {
                cb.onProcessXmlAit(result.xml, result.scheme);
            }
        }
    }

    private static class StreamEventInfo {
        public String targetUrl;
        public String eventName;
        public StreamEventInfo(String targetUrl, String eventName) {
            this.targetUrl = targetUrl;
            this.eventName = eventName;
        }
    }

    private static class XmlAitAttributes {
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
        protected void onApplicationHowRelatedHrefChanged(String href) { }
        protected void onDashStreamEvent(int listenId, String name, String status, String Id,
                                         double startTime, double duration, String data, String contentEncoding) { }
        protected void onTracksUpdated() { }
        protected void onTrackChanged(int type, String id) { }
    }
}
