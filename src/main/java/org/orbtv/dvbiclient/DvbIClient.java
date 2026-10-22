/**
 * ORB Software. Copyright (c) 2026 Ocean Blue Software Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.orbtv.dvbiclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.os.Handler;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DvbIClient {
    private static DvbIClient mSingleton;
    private static final String TAG = DvbIClient.class.getSimpleName();
    private static final String PREF_DVBI_SERVICE_LIST_VERSION = "dvbi_service_list_version";
    private static final String PREF_DVBI_SERVICE_LIST_URL = "dvbi_service_list_url";
    /** UIDs that already completed type 4.1 install (TS 103 770 §5.2.3.6.1 Note 2). Not 4.2/4.3. */
    private static final String PREF_LA41_COMPLETED_UIDS = "dvbi_la41_completed_uids";
    private static final String PREF_LA41_QUERY_PREFIX = "dvbi_la41_query:";
    private static final String PREF_LA_ACTIVE_UID = "dvbi_la_active_uid";
    private static final String PREF_LA_WITHDRAWN_UIDS = "dvbi_la_withdrawn_uids";
    private static final String PREF_LA_RENEWURL_PREFIX = "dvbi_la_renewurl:";
    private static final String PREF_LA_TOKEN_PREFIX = "dvbi_la_token:";
    private static final String PREF_LA42_URI_PREFIX = "dvbi_la42_uri:";
    private static final String PREF_LA42_CTYPE_PREFIX = "dvbi_la42_ctype:";
    private static final String PREF_LA43_URI_PREFIX = "dvbi_la43_uri:";
    private static final String PREF_LA43_CTYPE_PREFIX = "dvbi_la43_ctype:";
    private static final String PREF_LA_DEADLINE_PREFIX = "dvbi_la_deadline:";
    private static final String PREF_LA_NEXT_POLL_PREFIX = "dvbi_la_next_poll:";
    private static final String PREF_LA_RENEW_FAILED_PREFIX = "dvbi_la_renew_failed:";
    private static final String PREF_LA_AWAITING_TIME_PREFIX = "dvbi_la_awaiting_time:";
    private static final String PREF_LA43_PRESENTED_PREFIX = "dvbi_la43_presented:";
    private static final String PREF_LA43_SUCCEEDED_PREFIX = "dvbi_la43_succeeded:";
    private static final String PREF_LA13_PERSIST_PREFIX = "dvbi_la13_persist:";
    private static final String PREF_LA13_QUERY_PREFIX = "dvbi_la13_query:";
    private static final String PREF_LA13_RETRY_UNTIL_PREFIX = "dvbi_la13_retry_until:";
    private static final String PREF_LA13_RETRY_SCOPE_PREFIX = "dvbi_la13_retry_scope:";
    private static final long TYPE_41_INSTALL_TIMEOUT_MS = 60_000L;
    private static final long EMPTY_RENEW_DEFAULT_WAIT_MS = 5_000L;
    private static final Pattern APPLICATION_LOCATION = Pattern.compile(
            "(?i)(<(?:[\\w.-]+:)?applicationLocation(?:\\s[^>]*)?>)([^<]*)(</(?:[\\w.-]+:)?applicationLocation>)");
    private static final Pattern APPLICATION_ORG_ID = Pattern.compile(
            "(?i)<(?:[\\w.-]+:)?orgId>(\\d+)</(?:[\\w.-]+:)?orgId>");
    private static final Pattern APPLICATION_APP_ID = Pattern.compile(
            "(?i)<(?:[\\w.-]+:)?appId>(\\d+)</(?:[\\w.-]+:)?appId>");
    private static final Pattern CACHE_MAX_AGE = Pattern.compile("(?i)max-age\\s*=\\s*(\\d+)");

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
    public static final String LINKED_APP_SCHEME_1_3 = "urn:dvb:metadata:cs:LinkedApplicationCS:2019:1.3";
    public static final String LINKED_APP_SCHEME_2 = "urn:dvb:metadata:cs:LinkedApplicationCS:2019:2";
    public static final String LINKED_APP_SCHEME_4_1 = "urn:dvb:metadata:cs:LinkedApplicationCS:2019:4.1";
    public static final String LINKED_APP_SCHEME_4_2 = "urn:dvb:metadata:cs:LinkedApplicationCS:2019:4.2";
    public static final String LINKED_APP_SCHEME_4_3 = "urn:dvb:metadata:cs:LinkedApplicationCS:2019:4.3";
    public static final String LINKED_APP_SCHEME_1000_1 = "urn:dvb:metadata:cs:HowRelatedCS:2020:1000.1";

    public static final String LA_SL_INSTALL_SUCCESS = "org.dvb.la.sl_install_success";
    public static final String LA_SL_INSTALL_FAILURE = "org.dvb.la.sl_install_failure";
    public static final String LA_CONSENT_WITHDRAWN = "org.dvb.la.consent_withdrawn";
    public static final String LA_CONSENT_UNCHANGED = "org.dvb.la.consent_unchanged";
    public static final String LA_RENEW_SUCCESS = "org.dvb.la.renew_success";
    public static final String LA_RENEW_FAILURE = "org.dvb.la.renew_failure";
    public static final String LA_PREPLAY_SUCCESS = "org.dvb.la.preplay_success";
    public static final String LA_PREPLAY_FAILURE = "org.dvb.la.preplay_failure";

    private static final int PREPLAY_NONE = 0;
    private static final int PREPLAY_RUNNING = 1;
    private static final int PREPLAY_SUCCEEDED = 2;
    private static final int PREPLAY_FAILED = 3;

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
    private String mPreferredSubtitleLanguage = "";
    private HashMap<Integer, Boolean> mIsUnselected = new HashMap<>();
    private volatile ITvInputCallback mTvInputCallback;
    private final EpgManager mEpgManager;
    private final TunedServiceManager mServiceManager;
    private List<String> mUpdatedServiceEPGs = new ArrayList<>();
    private Timer mPermanentErrorTimer;
    private boolean mOverrideRequestPending = false;
    private String mPendingLinkedAppUrl;
    private String mPendingLinkedAppScheme;
    private volatile LinkedAppCompletion mLastLinkedAppCompletion;
    private volatile LinkedAppJsonRpcListener mLinkedAppJsonRpcListener;
    private volatile boolean mInstallGateActive = false;
    private int mDiscoveryEpoch;
    private GetXmlAitTask mType41AitTask;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mInstallGateTimeout = this::onType41InstallTimeout;
    private ServiceList mPendingServiceList;
    private String mPendingServiceListUri;
    private String mPendingServiceListXml;
    private final List<QueryPair> mInstallQueryPairs = new ArrayList<>();
    private final List<QueryPair> mPreplayQueryPairs = new ArrayList<>();
    private int mPreplayState = PREPLAY_NONE;
    private String mPreplayServiceUid;
    private String mPreplayAitUrl;
    private String mPreplayAppIdentity;
    private boolean mPreplaySignalledOnInstance;
    private final Runnable mType13RetryRunnable = this::onType13RetryElapsed;
    private String mType13RetryUid;
    private String mPendingRenewUrl;
    private String mPendingInstallationToken;
    private String mActiveRenewUrl;
    private String mActiveInstallationToken;
    private String mActiveListUid;
    private String mType42Uri;
    private String mType42ContentType;
    private String mType43Uri;
    private String mType43ContentType;
    private long mRenewDeadlineMs = -1L;
    /** Wall-clock time of the next renewurl GET. Handler delays use uptime and freeze in standby. */
    private long mNextRenewPollAtWallMs;
    /**
     * Do not GET this renewurl again before this wall time. Set from Cache-Control
     * max-age and kept across search/re-arm; cleared on a new 4.1, a new URL, or withdraw.
     */
    private long mRenewCacheUntilWallMs;
    /** True while a renewurl GET is in flight. A second start must not issue another GET. */
    private boolean mRenewPollInFlight;
    private boolean mResumeRearmPosted;
    private boolean mType42AppRunning;
    private boolean mType43AppRunning;
    /** 4.3 is due but no linked-app host has registered yet. */
    private boolean mType43AwaitingHost;
    /**
     * 4.1 just supplied a renewurl and we have not yet received a time document.
     * Cleared on ISO time, 4.3 completion, or withdraw — not left set for the
     * whole lifetime of a list that happens to have a renewurl.
     */
    private boolean mAwaitingRenewTimeDocument;
    /** A type 4.3 app already completed renew_success for this install. */
    private boolean mType43RenewSucceeded;
    /** 4.3 was shown for this deadline; do not relaunch if the host is torn down. */
    private boolean mType43Presented;
    private boolean mLastRenewFailed;
    private int mRenewPollRetry;
    /** Bumped to drop in-flight renewurl results after search / credential change. */
    private int mRenewEpoch;
    private GetXmlAitTask mType4xAitTask;
    private final Executor mIoExecutor = Executors.newSingleThreadExecutor();
    private final Runnable mRenewPollRunnable = this::runRenewUrlPoll;
    private final Runnable mLaunchType43Runnable = this::runScheduledType43Launch;
    private final Runnable mRenewDeadlineExpired = this::onRenewDeadlineExpired;
    /** App-pinned instance is outside its Availability Period; ignore DASH PLAYING until it returns. */
    private volatile boolean mPinnedInstanceOutsideWindow = false;
    /** App (LA 1.2 / A/V Control) holds AV decoders; native DASH/RF must wait (A.2.4.1). */
    private volatile boolean mPresentationSuspended = false;
    /**
     * Last {@link #tune(String, int)} instance index. {@code >= 0} is an O.5.4 instance
     * lock (ERRATA0900 i2); {@code < 0} is unpinned service select (ERRATA0700/0800).
     */
    private volatile int mRequestedInstanceIndex = -1;
    private String mPendingDashUri;
    /** Last native DASH MPD (no 4.1/4.3 query). 4.3 tuneOff clears the current instance. */
    private String mLastNativeDashUri;
    /**
     * Set by {@link #mServiceManagerCallback}. Re-shows the PIN dialog when a tune finds the
     * terminal already on a service that is still parentally blocked and the dialog was dismissed.
     */
    private Runnable mRePromptBlocked;

    private final TunedServiceManager.Callback mServiceManagerCallback = new TunedServiceManager.Callback() {
        {
            mRePromptBlocked = this::requestOverrideIfNeeded;
        }
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
                    boolean instanceInWindow = toInstance == null
                            || mServiceManager.isInstanceInAvailabilityWindow(toInstance);
                    String app_1_3 = channel.getLinkedAppUri(LINKED_APP_SCHEME_1_3);
                    if (!instanceInWindow) {
                        Log.i(TAG, "Type 1.3 skipped — instance outside availability window (APPS0360)");
                        app_1_3 = null;
                    }
                    if (app_1_3 != null && !continueAfterType13Preplay(channel, service, toInstance,
                            app_1_3)) {
                        return;
                    }
                    if (!instanceInWindow) {
                        handleOutsideAvailabilityWindow(service, fromInstance, channelBuilder);
                        return;
                    }
                    String app_1_2 = channel.getLinkedAppUri(LINKED_APP_SCHEME_1_2);
                    String dashUri = resolveNativeDashUri(toInstance, uri);
                    boolean instanceLock = mRequestedInstanceIndex >= 0;
                    Log.i(TAG, "INSTANCE_ROUTE: nativeDash=" + (dashUri != null)
                        + " delivery=" + toInstance.getDeliveryType()
                        + " app12=" + (app_1_2 != null)
                        + " instanceLock=" + instanceLock
                        + " uri=" + dashUri);
                    // Drop leftover PLAYING from the previous MPD before this instance
                    // presents (ERRATA0900 step 5 false CCS while CONNECTING).
                    mDvbIView.suppressVideoEvents();
                    // Type 1.2 owns media (ERRATA0700/0800): tuneOff native DASH even
                    // when the instance has a UriBasedLocation (same MPD the LA HTML5
                    // plays). Native DASH + 1.2 HowRelated only when the app locked
                    // the instance Channel (O.5.4 / ERRATA0900 i2).
                    if (app_1_2 != null && (dashUri == null || !instanceLock)) {
                        handleLinkedApp1_2(channel, app_1_2, service);
                    } else {
                        if (dashUri != null) {
                            mLastNativeDashUri = dashUri;
                            loadPersistedType13Query(channel.getLinkedAppUri(LINKED_APP_SCHEME_1_3));
                            uri = applyInstallQuery(dashUri);
                        }
                        handleNativeOrLinkedApp1_1(channel, toInstance, service, uri);
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
                invalidateErrorTimer();
                if (wasBlocked && !blocked && !naturallyBlocked) {
                    mTvInputCallback.clearParentalAccessOverride();
                }
                // Do not set mBlocked here. handleLinkedApp1_2 / handleNativeOrLinkedApp1_1
                // own that flag when they enforce (kill/PIN). Setting it first made the
                // "already blocked" guard skip the first pass (ERRATA0100/0120 spinner).
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
            String app_1_1 = channel.getLinkedAppUri(LINKED_APP_SCHEME_1_1);
            String app_1_2 = channel.getLinkedAppUri(LINKED_APP_SCHEME_1_2);
            if (app_1_1 != null) {
                launchApp(app_1_1, LINKED_APP_SCHEME_1_1);
            } else if (app_1_2 != null) {
                // Native DASH instance whose HowRelated is 1.2 (ERRATA0900).
                launchApp(app_1_2, LINKED_APP_SCHEME_1_2);
            }
            if ("dvb-dash".equals(toInstance.getDeliveryType())) {
                // Player.stop is synchronous through decoder teardown. Tuner CLOSING
                // may still run in the background; DASH does not need the frontend.
                Log.i(TAG, "RF_TUNE_DEBUG: switching to DASH after tuneOffBroadcast");
                mTracks.clear();
                mSelectedTracks.clear();
                mIsUnselected.clear();
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
                        // HbbTV CCS is for the DVB-I Channel the app setChannel'd
                        // (e.g. 6,16561,2), not the RF delivery triplet (99,1,12).
                        Triplet status = hbbtvStatusTriplet(service, channel);
                        if (status == null) {
                            status = rfTriplet;
                        }
                        dispatchPlayerStatusChangedEvent(
                            status.getOrigNetId(),
                            status.getTsId(),
                            status.getServiceId(),
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
                        Triplet status = hbbtvStatusTriplet(service, channel);
                        if (status == null) {
                            status = rfTriplet;
                        }
                        dispatchPlayerStatusChangedEvent(
                            status.getOrigNetId(),
                            status.getTsId(),
                            status.getServiceId(),
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
                if (mBlocked) {
                    Log.i(TAG, "PARENTAL_RATING 1.2: already blocked; ignore extra instance change");
                    return;
                }
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
            if (mServiceManager.getTunedService() == null) {
                Log.i(TAG, "PARENTAL_RATING: skip PIN; no longer tuned");
                return;
            }
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

        /**
         * TS 103 770 §5.2.3.2.4: run type 1.3 before media / 1.1 / 1.2.
         * @return true if 1.3 already succeeded for this selection (caller may chain).
         */
        private boolean continueAfterType13Preplay(DvbIChannelAdapter channel, Service service,
                ServiceInstance instance, String appUrl) {
            if (isType13RetryHeld(service, instance, appUrl)) {
                Log.i(TAG, "Type 1.3 on retry hold; media not started");
                applyType13RetryHold(service, instance, appUrl);
                return false;
            }
            if (isType13PersistentSuppressed(service, instance, appUrl)) {
                Log.i(TAG, "Type 1.3 suppressed by persistent success");
                loadPersistedType13Query(appUrl);
                mPreplayState = PREPLAY_SUCCEEDED;
                mPreplayServiceUid = service.getUniqueIdentifier();
                return true;
            }
            if (mPreplayState == PREPLAY_SUCCEEDED && mPreplayServiceUid != null
                    && mPreplayServiceUid.equals(service.getUniqueIdentifier())) {
                return true;
            }
            if (mPreplayState == PREPLAY_RUNNING) {
                Log.i(TAG, "Type 1.3 pre-play already running");
                return false;
            }
            if (mPreplayState == PREPLAY_FAILED) {
                // Retry prefs already expired (isType13RetryHeld returned false).
                // Keep FAILED only while the hold is active.
                Log.i(TAG, "Type 1.3 retry hold expired; allowing relaunch");
                mPreplayState = PREPLAY_NONE;
            }
            int contentAge = resolveContentAge(service, instance);
            Log.i(TAG, "Type 1.3 parental check: contentAge=" + contentAge
                    + " threshold=" + mTvInputCallback.getParentalControlAge()
                    + " overridden=" + mTvInputCallback.isParentalAccessOverridden()
                    + " instanceRating=" + (instance != null ? instance.getParentalRating() : null)
                    + " serviceRating=" + (service != null ? service.getParentalRating() : null));
            if (isParentalBlocked(contentAge)) {
                Log.i(TAG, "Type 1.3 deferred until parental override (APPS0370)");
                mBlocked = true;
                handleRatingBlocked(channel);
                requestOverrideIfNeeded();
                return false;
            }
            mPreplayState = PREPLAY_RUNNING;
            mPreplayServiceUid = service.getUniqueIdentifier();
            mPreplayAitUrl = appUrl;
            mPreplaySignalledOnInstance = instanceHasLinkedApp(instance, LINKED_APP_SCHEME_1_3);
            mPreplayQueryPairs.clear();
            loadPersistedType13Query(appUrl);
            Log.i(TAG, "Type 1.3 pre-play gate: lloc=preroll url=" + appUrl);
            mDvbIView.suppressVideoEvents();
            mTvInputCallback.tuneOffBroadcast();
            mDvbIView.tuneOff();
            publishApplicationHowRelatedHref(service, mServiceManager.getTunedInstance(), false);
            launchApp(appUrl, LINKED_APP_SCHEME_1_3);
            if (!PLAYER_STATUS_STARTING.equals(mLastState)) {
                mLastState = PLAYER_STATUS_STARTING;
                dispatchStartingIfNeeded(channel.getOnid(), channel.getTsid(), channel.getSid());
            }
            return false;
        }

        private void handleOutsideAvailabilityWindow(Service service, ServiceInstance fromInstance,
                DvbIChannelAdapter.Builder channelBuilder) {
            Log.i(TAG, "No service instance is currently available.");
            mTvInputCallback.tuneOffBroadcast();
            mDvbIView.tuneOff();
            DvbIChannelAdapter channel = channelBuilder.setServiceInstance(fromInstance).build();
            mTvInputCallback.notifyVideoAvailable();
            if (!PLAYER_STATUS_STARTING.equals(mLastState)) {
                mLastState = PLAYER_STATUS_STARTING;
                dispatchPlayerStatusChangedEvent(
                        channel.getOnid(), channel.getTsid(), channel.getSid(),
                        PLAYER_STATUS_STARTING);
            }
            if (channel.getLinkedAppUri(LINKED_APP_SCHEME_2) != null) {
                launchApp(channel.getLinkedAppUri(LINKED_APP_SCHEME_2), LINKED_APP_SCHEME_2);
            } else if (channel.getLinkedAppUri(LINKED_APP_SCHEME_1000_1) != null) {
                launchApp(channel.getLinkedAppUri(LINKED_APP_SCHEME_1000_1), LINKED_APP_SCHEME_1000_1);
            }
            publishApplicationHowRelatedHref(service, fromInstance, true);
        }

        private boolean isStillTuned(Service service) {
            Service tuned = mServiceManager.getTunedService();
            return service != null && tuned != null
                && service.getUniqueIdentifier().equals(tuned.getUniqueIdentifier());
        }

        private int resolveContentAge(Service service) {
            return resolveContentAge(service, mServiceManager.getTunedInstance());
        }

        private int resolveContentAge(Service service, ServiceInstance instance) {
            Programme now = mServiceManager.getNowProgramme();
            if (now != null && now.getMinimumAge() > 0) {
                return now.getMinimumAge();
            }
            if (instance != null && instance.getParentalRating() != null
                    && instance.getParentalRating() > 0) {
                return instance.getParentalRating();
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
                new GetXmlAitTask().execute(
                    new XmlAitAttributes(appUrl, scheme, mTuneGeneration));
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
        } else if (mPreplayState == PREPLAY_RUNNING) {
            href = LINKED_APP_SCHEME_1_3;
        } else {
            href = getFollowOnHowRelatedHref(service, instance);
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

    /**
     * Terminal-presented DASH MPD, or null for app-only 1.2 (ERRATA0700/0800).
     * Key off dvb-dash + a usable location; do not require getUri() to already
     * be a trimmed http(s) string (ERRATA0900 i2).
     */
    private static String resolveNativeDashUri(ServiceInstance instance, String uri) {
        if (instance == null || !"dvb-dash".equals(instance.getDeliveryType())) {
            return null;
        }
        String loc = uri != null ? uri.trim() : "";
        if (loc.isEmpty() && instance.getUri() != null) {
            loc = instance.getUri().trim();
        }
        if (loc.isEmpty()) {
            return null;
        }
        if (loc.startsWith("http://") || loc.startsWith("https://")) {
            return loc;
        }
        Log.w(TAG, "INSTANCE_ROUTE: dvb-dash location is not http(s): " + loc);
        return loc.startsWith("http") ? loc : null;
    }

    /**
     * After pre-play, publish the same 1.2 / 1.1 href used to choose the
     * follow-on app (service or instance RelatedMaterial).
     */
    private static String getFollowOnHowRelatedHref(Service service, ServiceInstance instance) {
        if (instanceHasLinkedApp(instance, LINKED_APP_SCHEME_1_2)
                || serviceHasLinkedApp(service, LINKED_APP_SCHEME_1_2)) {
            return LINKED_APP_SCHEME_1_2;
        }
        if (instanceHasLinkedApp(instance, LINKED_APP_SCHEME_1_1)
                || serviceHasLinkedApp(service, LINKED_APP_SCHEME_1_1)) {
            return LINKED_APP_SCHEME_1_1;
        }
        return getInstanceHowRelatedHref(instance);
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

    private static boolean instanceHasLinkedApp(ServiceInstance instance, String scheme) {
        if (instance == null) {
            return false;
        }
        List<RelatedMaterial> materials = instance.getRelatedMaterials();
        if (materials == null) {
            return false;
        }
        for (RelatedMaterial rm : materials) {
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
            applySubtitleSelectionLocked();
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
        mDvbIView.setDashTuneListener(tuned -> {
            if (mTvInputCallback != null) {
                // Compositing hint (hide empty DTVKit plane), not a presenting-frames signal.
                mTvInputCallback.onNativeDashPresenting(tuned);
            }
        });
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
        restoreLinkedAppState();
    }

    public synchronized void setTvInputCallback(ITvInputCallback callback) {
        if (callback == null) {
            mTvInputCallback = mFallbackCallback;
        }
        else {
            mTvInputCallback = callback;
            mTvInputCallback.updateEventPeriods();
            onParentalControlAgeChanged();
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
            if (handler != null && handler.canLaunchLinkedApp()) {
                mMainHandler.post(this::maybeLaunchDeferredType43);
            }
        }
    }

    public synchronized void unregisterCallback(Callback handler) {
        mCallbacks.remove(handler);
    }

    /**
     * True if a registered callback can actually launch HTML / XML AIT linked apps
     * (not a track-only Session callback).
     */
    public synchronized boolean hasLinkedAppCallback() {
        for (Callback cb : mCallbacks) {
            if (cb.canLaunchLinkedApp()) {
                return true;
            }
        }
        return false;
    }

    public boolean isType41InstallGateActive() {
        return mInstallGateActive;
    }

    /**
     * Abort discovery and a type 4.1 install gate without scan-complete. Used when Setup
     * Finish / destroy must not wait for the 60s timeout or sync a list about to be discarded.
     */
    public void cancelServiceSearch() {
        mDiscoveryEpoch++;
        if (mType41AitTask != null) {
            mType41AitTask.cancel(true);
            mType41AitTask = null;
        }
        if (mType4xAitTask != null) {
            mType4xAitTask.cancel(true);
            mType4xAitTask = null;
        }
        if (mInstallGateActive) {
            Log.w(TAG, "Cancelling type 4.1 install gate (Setup aborted search)");
            cancelType41InstallGate();
        }
        ServiceListDiscoveryTask previous = mLastDiscoveryTask;
        mLastDiscoveryTask = null;
        if (previous != null) {
            previous.cancel(true);
        }
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

    public synchronized void setPreferredSubtitleLanguage(String language) {
        mPreferredSubtitleLanguage = language != null ? language : "";
        Log.i(TAG, "DASH_SUBS preferred language=" + mPreferredSubtitleLanguage);
        mDvbIView.setSubtitles(mSubtitlesEnabled, mPreferredSubtitleLanguage);
        if (mSubtitlesEnabled) {
            applySubtitleSelectionLocked();
        }
    }

    public synchronized void setSubtitlesEnabled(boolean enable) {
        Log.i(TAG, "DASH_SUBS enabled=" + enable + " lang=" + mPreferredSubtitleLanguage
                + " tracks=" + mTracks.size());
        mSubtitlesEnabled = enable;
        mDvbIView.setSubtitles(enable, mPreferredSubtitleLanguage);
        if (!enable) {
            mSelectedTracks.remove(TvTrackInfo.TYPE_SUBTITLE);
            mDvbIView.selectTrack(TRACK_TYPE_LOOKUP.get(TvTrackInfo.TYPE_SUBTITLE), "undefined");
            return;
        }
        applySubtitleSelectionLocked();
    }

    public boolean getSubtitlesEnabled() { return mSubtitlesEnabled; }

    private void applySubtitleSelectionLocked() {
        if (!mSubtitlesEnabled) {
            mSelectedTracks.remove(TvTrackInfo.TYPE_SUBTITLE);
            return;
        }
        TvTrackInfo chosen = pickSubtitleTrackLocked();
        if (chosen == null) {
            return;
        }
        TvTrackInfo current = mSelectedTracks.get(TvTrackInfo.TYPE_SUBTITLE);
        if (current != null && current.getId().equals(chosen.getId())
                && !Boolean.TRUE.equals(mIsUnselected.get(TvTrackInfo.TYPE_SUBTITLE))) {
            return;
        }
        mSelectedTracks.put(TvTrackInfo.TYPE_SUBTITLE, chosen);
        mIsUnselected.remove(TvTrackInfo.TYPE_SUBTITLE);
        mDvbIView.selectTrack(TRACK_TYPE_LOOKUP.get(TvTrackInfo.TYPE_SUBTITLE),
                chosen.getId().split(":")[0]);
    }

    private TvTrackInfo pickSubtitleTrackLocked() {
        TvTrackInfo fallback = null;
        for (TvTrackInfo track : mTracks) {
            if (track.getType() != TvTrackInfo.TYPE_SUBTITLE) {
                continue;
            }
            if (fallback == null) {
                fallback = track;
            }
            if (languageMatches(track.getLanguage(), mPreferredSubtitleLanguage)) {
                return track;
            }
        }
        TvTrackInfo selected = mSelectedTracks.get(TvTrackInfo.TYPE_SUBTITLE);
        if (selected != null) {
            return selected;
        }
        return fallback;
    }

    private static final Map<String, String> ISO639_2_TO_1 = createIso639Map();

    private static Map<String, String> createIso639Map() {
        Map<String, String> map = new HashMap<>();
        String[] pairs = {
            "aar","aa","abk","ab","afr","af","aka","ak","amh","am","ara","ar","arg","an",
            "asm","as","ava","av","ave","ae","aym","ay","aze","az","bak","ba","bam","bm",
            "bel","be","ben","bn","bis","bi","bod","bo","bos","bs","bre","br","bul","bg",
            "cat","ca","ces","cs","cha","ch","che","ce","chu","cu","chv","cv","cor","kw",
            "cos","co","cre","cr","cym","cy","dan","da","deu","de","div","dv","dzo","dz",
            "ell","el","eng","en","epo","eo","est","et","eus","eu","ewe","ee","fao","fo",
            "fas","fa","fij","fj","fin","fi","fra","fr","fry","fy","ful","ff","gla","gd",
            "gle","ga","glg","gl","glv","gv","grn","gn","guj","gu","hat","ht","hau","ha",
            "heb","he","her","hz","hin","hi","hmo","ho","hrv","hr","hun","hu","hye","hy",
            "ibo","ig","ido","io","iii","ii","iku","iu","ile","ie","ina","ia","ind","id",
            "ipk","ik","isl","is","ita","it","jav","jv","jpn","ja","kal","kl","kan","kn",
            "kas","ks","kat","ka","kau","kr","kaz","kk","khm","km","kik","ki","kin","rw",
            "kir","ky","kom","kv","kon","kg","kor","ko","kua","kj","kur","ku","lao","lo",
            "lat","la","lav","lv","lim","li","lin","ln","lit","lt","ltz","lb","lub","lu",
            "lug","lg","mah","mh","mal","ml","mar","mr","mkd","mk","mlg","mg","mlt","mt",
            "mon","mn","mri","mi","msa","ms","mya","my","nau","na","nav","nv","nbl","nr",
            "nde","nd","ndo","ng","nep","ne","nld","nl","nno","nn","nob","nb","nor","no",
            "nya","ny","oci","oc","oji","oj","ori","or","orm","om","oss","os","pan","pa",
            "pli","pi","pol","pl","por","pt","pus","ps","que","qu","roh","rm","ron","ro",
            "run","rn","rus","ru","sag","sg","san","sa","sin","si","slk","sk","slv","sl",
            "sme","se","smo","sm","sna","sn","snd","sd","som","so","sot","st","spa","es",
            "sqi","sq","srd","sc","srp","sr","ssw","ss","sun","su","swa","sw","swe","sv",
            "tah","ty","tam","ta","tat","tt","tel","te","tgk","tg","tgl","tl","tha","th",
            "tir","ti","ton","to","tsn","tn","tso","ts","tuk","tk","tur","tr","twi","tw",
            "uig","ug","ukr","uk","urd","ur","uzb","uz","ven","ve","vie","vi","vol","vo",
            "wln","wa","wol","wo","xho","xh","yid","yi","yor","yo","zha","za","zho","zh",
            "zul","zu",
            "chi","zh","cze","cs","dut","nl","fre","fr","ger","de","gre","el","ice","is",
            "mac","mk","mao","mi","may","ms","per","fa","slo","sk","tib","bo","wel","cy"
        };
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    private static String primaryLanguageSubtag(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        String t = code.toLowerCase(Locale.ROOT).replace('_', '-');
        int dash = t.indexOf('-');
        if (dash >= 0) {
            t = t.substring(0, dash);
        }
        t = t.trim();
        String mapped = ISO639_2_TO_1.get(t);
        return mapped != null ? mapped : t;
    }

    private static boolean languageMatches(String trackLang, String preferred) {
        if (preferred == null || preferred.isEmpty()) {
            return true;
        }
        String t = primaryLanguageSubtag(trackLang);
        if (t.isEmpty()) {
            return false;
        }
        for (String token : preferred.split(",")) {
            String p = primaryLanguageSubtag(token);
            if (!p.isEmpty() && t.equals(p)) {
                return true;
            }
        }
        return false;
    }

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
        discardLinkedApp12Instance("cannot start (" + reason + ")", true);
    }

    /**
     * Type 1.2 app explicitly exited (Application.destroyApplication). Discard this
     * service instance and select the next (TS 103 770 §5.2.13 / errata #13697).
     * The application is already killed; do not restart it on this instance.
     */
    public void onLinkedApp12ExplicitlyExited() {
        discardLinkedApp12Instance("explicit destroyApplication", false);
    }

    /**
     * Discard the current LA 1.2 instance and select the next remaining instance.
     *
     * @param killRunningApp if true, also kill the HbbTV app (XML AIT / first-page
     *        start-failure). False when destroyApplication() already exited the app;
     *        do not call destroyHbbtvApplication() in that case (parental kill).
     */
    private void discardLinkedApp12Instance(String reason, boolean killRunningApp) {
        ServiceInstance current = mServiceManager.getTunedInstance();
        Service service = mServiceManager.getTunedService();
        if (current == null || service == null) {
            Log.w(TAG, "LA12_DISCARD: no tuned instance (" + reason + ")");
            return;
        }
        DvbIChannelAdapter channel = new DvbIChannelAdapter.Builder()
                .setService(service)
                .setServiceInstance(current)
                .build();
        if (channel == null || channel.getLinkedAppUri(LINKED_APP_SCHEME_1_2) == null) {
            Log.i(TAG, "LA12_DISCARD: current instance is not LA 1.2; ignoring (" + reason + ")");
            return;
        }
        // Claim before tuneOff. The duplicate 404 otherwise stops the fallback DASH
        // the first caller has already selected (ERRATA0610).
        if (!mServiceManager.claimDiscard(current)) {
            Log.i(TAG, "LA12_DISCARD: instance already discarded; ignoring (" + reason + ")");
            return;
        }
        try {
            Log.i(TAG, "LA12_DISCARD: " + reason);
            mPendingLinkedAppUrl = null;
            mPendingLinkedAppScheme = null;
            if (killRunningApp) {
                mTvInputCallback.destroyHbbtvApplication();
            }
            // Native DASH/RF and any A.2.4.1 decoder hold must be released before the
            // next instance presents. After destroyApplication() the WebView is
            // about:blank, so JS will not call setPresentationSuspended(false).
            clearPendingNativePresentation();
            mDvbIView.tuneOff();
            mTvInputCallback.tuneOffBroadcast();
            setPresentationSuspended(false);
            if (!mServiceManager.discardCurrentInstanceAndReselect(reason)) {
                Log.w(TAG, "LA12_DISCARD: discard/reselect did not change instance (" + reason + ")");
            }
        } finally {
            mServiceManager.releaseDiscardClaim();
        }
    }

    /**
     * O.3 restart cap after irrecoverable error. Select another instance if one
     * exists; do not kill/tuneOff the only instance (ERRATA0800).
     */
    public void onLinkedAppRestartLimitReached() {
        ServiceInstance current = mServiceManager.getTunedInstance();
        Service service = mServiceManager.getTunedService();
        if (current == null || service == null) {
            Log.w(TAG, "LA12_RESTART: no tuned instance at restart limit");
            return;
        }
        if (!mServiceManager.discardCurrentInstanceAndReselect("restart limit")) {
            Log.w(TAG, "LA12_RESTART: no other instance; keeping current (O.3)");
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
            // Service-level setChannel that actually changes instance (ERRATA0900 unlock)
            // must drop DASH tracks so RF getComponents is not leftover IP media.
            return false;
        }
        return instanceIndex == current.getInstances().indexOf(currentInst);
    }

    public synchronized boolean tune(String uid, int instanceIndex) {
        Service current = mServiceManager.getTunedService();
        ServiceInstance currentInst = mServiceManager.getTunedInstance();
        if (current != null && uid != null
                && uid.equals(current.getUniqueIdentifier())) {
            if (instanceIndex < 0) {
                // Duplicate setChannel to the DVB-I *service* Channel: keep PIN when
                // the terminal would already choose this instance (ERRATA0120).
                // If an O.5.4 pin is holding a lower-priority instance (DASH), unlock
                // and select highest-priority available (RF) (ERRATA0900 step 9).
                if (isCurrentHighestPriorityInstance(current, currentInst)) {
                    if (mPreplayState == PREPLAY_FAILED
                            && !isType13RetryHeld(current, currentInst, mPreplayAitUrl)) {
                        Log.i(TAG, "tune: already on " + uid
                                + "; type 1.3 retry elapsed, relaunching");
                        mPreplayState = PREPLAY_NONE;
                        mServiceManager.reselectAfterType13RetryHold();
                        return true;
                    }
                    Log.i(TAG, "tune: already on " + uid + "; keep parental state");
                    rePromptParentalIfBlocked();
                    return true;
                }
                Log.i(TAG, "tune: service Channel unlocks instance; selecting highest-priority "
                    + "(O.5.4 / ERRATA0900) uid=" + uid);
            } else if (isAlreadySelectedInstance(current, currentInst, instanceIndex)) {
                // O.5.4 / ERRATA0400: setChannel to the already-selected *instance*
                // must pin it and complete CCS without tearing down DASH or PIN.
                completeInstanceLockWithoutRetune(instanceIndex, uid);
                return true;
            }
        }
        mTuneGeneration++;
        cancelType13RetryRelaunch();
        mPreplayState = PREPLAY_NONE;
        mPreplayServiceUid = null;
        mPreplayAitUrl = null;
        mPreplayAppIdentity = null;
        mPreplayQueryPairs.clear();
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
        int previousInstanceIndex = mRequestedInstanceIndex;
        mRequestedInstanceIndex = instanceIndex;
        if (mServiceManager.tune(mDbHandler.getServiceForUID(uid), instanceIndex)) {
            if (!retainTracks) {
                mTracks.clear();
                mStreamEventsLookup.clear();
                mSelectedTracks.clear();
                mIsUnselected.clear();
            }
            return true;
        }
        mRequestedInstanceIndex = previousInstanceIndex;
        mBlocked = blocked;
        Log.i(TAG, "Failed to tune to service with UID: " + uid);
        return false;
    }

    /**
     * Unpinned service selection would keep this instance (ERRATA0120 no-op).
     * False when a pin is holding DASH while RF is available (ERRATA0900 step 9).
     */
    private boolean isCurrentHighestPriorityInstance(Service current, ServiceInstance currentInst) {
        ServiceInstance preferred = mServiceManager.getHighestPrioritySelectableInstance(current);
        return sameServiceInstance(currentInst, preferred);
    }

    private static boolean sameServiceInstance(ServiceInstance a, ServiceInstance b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        String uriA = a.getUri();
        String uriB = b.getUri();
        if (uriA != null && uriA.equals(uriB)) {
            return true;
        }
        Triplet ta = a.getTriplet();
        Triplet tb = b.getTriplet();
        return ta != null && tb != null && ta.toString().equals(tb.toString());
    }

    private static boolean isAlreadySelectedInstance(Service current, ServiceInstance currentInst,
            int instanceIndex) {
        if (current == null || currentInst == null || instanceIndex < 0) {
            return false;
        }
        List<ServiceInstance> instances = current.getInstances();
        if (instances == null) {
            return false;
        }
        if (instanceIndex == instances.indexOf(currentInst)) {
            return true;
        }
        if (instanceIndex >= instances.size()) {
            return false;
        }
        ServiceInstance requested = instances.get(instanceIndex);
        if (requested == null) {
            return false;
        }
        if (requested == currentInst) {
            return true;
        }
        String requestedUri = requested.getUri();
        String currentUri = currentInst.getUri();
        return requestedUri != null && requestedUri.equals(currentUri);
    }

    /**
     * Same-service tune while the block is still in force and the dialog was dismissed
     * (Back). A dialog that is already up has a pending request and is left alone.
     */
    private void rePromptParentalIfBlocked() {
        if (!mBlocked || mRePromptBlocked == null) {
            return;
        }
        Log.i(TAG, "tune: still blocked on return to this service; showing parental dialog");
        mRePromptBlocked.run();
    }

    /**
     * Pin the current DVB-I instance and synthesise ServiceInstanceChanged plus PRESENTING
     * so video/broadcast can leave CONNECTING. Must not call {@link TunedServiceManager#tune}
     * (that tuneOff()s DASH) or {@link ITvInputCallback#clearParentalAccessOverride()}.
     */
    private void completeInstanceLockWithoutRetune(int instanceIndex, String uid) {
        mServiceManager.pinCurrentInstance();
        final Triplet triplet = getHbbtvChannelStatusTriplet(instanceIndex);
        Log.i(TAG, "tune: already on instance " + instanceIndex + " of " + uid
            + "; pin and complete setChannel (O.5.4 / ERRATA0400)"
            + (triplet != null ? (" ccs=" + triplet) : ""));
        mDvbIView.getContext().getMainExecutor().execute(() -> {
            for (HbbTVCallback handler : mHbbTVCallbacks) {
                handler.onServiceInstanceChange(instanceIndex);
            }
            if (triplet != null) {
                mLastState = PLAYER_STATUS_PLAYING;
                dispatchPlayerStatusChangedEvent(
                    triplet.getOrigNetId(), triplet.getTsId(), triplet.getServiceId(),
                    PLAYER_STATUS_PLAYING);
            }
        });
    }

    public synchronized int getTuneGeneration() {
        return mTuneGeneration;
    }

    /**
     * Drop the DVB-I selection only when {@code generation} is still current.
     * A session teardown can block in broadcast {@code Player.stop} while Live Channels
     * has already tuned on the next session. That newer tune must keep its selection
     * and in-flight XML AIT.
     */
    public synchronized void tuneOffIfCurrent(int generation) {
        if (generation != mTuneGeneration) {
            Log.i(TAG, "tuneOff: ignored stale teardown gen " + generation
                + " current " + mTuneGeneration);
            return;
        }
        tuneOff();
    }

    public synchronized void tuneOff() {
        Log.i(TAG, "tuneOff: drop DVB-I selection (gen " + mTuneGeneration + " -> "
            + (mTuneGeneration + 1) + ")");
        mTuneGeneration++;
        cancelType13RetryRelaunch();
        mPreplayState = PREPLAY_NONE;
        mPreplayServiceUid = null;
        mPreplayAitUrl = null;
        mPreplayAppIdentity = null;
        mPreplayQueryPairs.clear();
        mBlocked = false;
        mOverrideRequestPending = false;
        mLastState = null;
        mPinnedInstanceOutsideWindow = false;
        mRequestedInstanceIndex = -1;
        clearPendingNativePresentation();
        mPendingLinkedAppUrl = null;
        mPendingLinkedAppScheme = null;
        mServiceManager.tuneOff();
        mStreamEventsLookup.clear();
        mTracks.clear();
        mSelectedTracks.clear();
        mIsUnselected.clear();
        mDvbIView.tuneOff();
    }

    /** Type 1.1 native DASH is selected in {@link DvbIView} for compositing (not type 1.2 HTML5).
     * True hides the empty DTVKit plane; it is not a decoded-frame guarantee. */
    public boolean isNativeDashTuned() {
        return mDvbIView != null && mDvbIView.isDashTuned();
    }

    public void setPresentationSuspended(boolean suspend) {
        mPresentationSuspended = suspend;
        mDvbIView.setPresentationSuspended(suspend);
        if (!suspend) {
            startPendingNativePresentation();
            // bindToCurrentChannel after DASH is already presenting (O.5.4):
            // re-issue PLAYING so v/b can leave CONNECTING without treating an
            // empty RF component list as "media available" before the MPD GET.
            if (mPendingDashUri == null && PLAYER_STATUS_PLAYING.equals(mLastState)
                    && !mBlocked) {
                Triplet triplet = getHbbtvChannelStatusTriplet();
                if (triplet != null) {
                    dispatchPlayerStatusChangedEvent(
                            triplet.getOrigNetId(), triplet.getTsId(), triplet.getServiceId(),
                            PLAYER_STATUS_PLAYING);
                    if (mTvInputCallback != null) {
                        mTvInputCallback.notifyVideoAvailable();
                    }
                }
            }
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
        ServiceInstance instance = mServiceManager.getTunedInstance();
        if (instance == null || !"dvb-dash".equals(instance.getDeliveryType())) {
            return;
        }
        Log.i(TAG, "Setting video rectangle to " + x + ", " + y + ", " + width + ", " + height);
        mDvbIView.setVideoRectangle(x, y, width, height);
        for (DvbCallback handler : mDvbCallbacks) {
            handler.onSetVideoRectangle(x, y, width, height);
        }
    }

    public List<DvbChannel> getListOfServices() {
        ArrayList<DvbChannel> ret = new ArrayList<>();
        List<ServiceList> serviceLists = mDbHandler.getServiceLists();
        for (ServiceList serviceList : serviceLists) {
            if (isConsentWithdrawn(serviceList.getUID())) {
                continue;
            }
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
        mDiscoveryEpoch++;
        if (mType41AitTask != null) {
            mType41AitTask.cancel(true);
            mType41AitTask = null;
        }
        if (mType4xAitTask != null) {
            mType4xAitTask.cancel(true);
            mType4xAitTask = null;
        }
        if (mInstallGateActive) {
            Log.w(TAG, "Cancelling in-progress type 4.1 install gate to start a new search");
            cancelType41InstallGate();
        }
        // A Setup install always runs type 4.1 again so it can reset fixtures
        // (APPS0130). Version refresh uses skip-install via updateServiceList.
        clearType41CompletedForUrl(serviceListURL);
        mInstallQueryPairs.clear();
        // A new list install replaces leftover 4.2 / 4.3 from a previous UID.
        invalidateRenewalCycle();
        clearType4xLaunchLocators();
        ServiceListDiscoveryTask previous = mLastDiscoveryTask;
        mLastDiscoveryTask = new ServiceListDiscoveryTask(mDiscoveryEpoch);
        if (previous != null) {
            previous.cancel(true);
        }
        if (serviceListURL == null || serviceListURL.isEmpty()) {
            // Default to HbbTV test harness service list URL for test environment
            serviceListURL = "http://hbbtv1.test/servicelist.xml";
        }
        mCurrentServiceListUrl = serviceListURL;
        Log.d(TAG, "Starting service search at " + serviceListURL);
        mLastDiscoveryTask.execute(serviceListURL);
        return true;
    }

    /**
     * Reload {@code serviceListURL} only when that same URL already has a stored version
     * and the published version is different. A new URL, a missing URL, and a version
     * that cannot be read leave the installed list in place. The check runs off the
     * caller thread. Returns false so the caller can sync the services already stored;
     * a later reload syncs again after it commits.
     */
    public boolean updateServiceList(String serviceListURL) {
        if (serviceListURL == null || serviceListURL.isEmpty()) {
            Log.d(TAG, "No installed DVB-I service list URL, skipping refresh");
            return false;
        }
        if (mInstallGateActive || mLastDiscoveryTask != null) {
            Log.d(TAG, "Service list search already in progress, skipping version refresh");
            return false;
        }
        SharedPreferences prefs = mDvbIView.getContext().getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        String lastKnownVersion = prefs.getString(PREF_DVBI_SERVICE_LIST_VERSION, null);
        String lastKnownUrl = prefs.getString(PREF_DVBI_SERVICE_LIST_URL, null);
        if (lastKnownVersion == null || !serviceListURL.equals(lastKnownUrl)) {
            Log.d(TAG, "No stored version for installed service list, leaving it unchanged");
            return false;
        }
        final String installedVersion = lastKnownVersion;
        mIoExecutor.execute(() -> refreshServiceListIfVersionChanged(serviceListURL, installedVersion));
        return false;
    }

    private void refreshServiceListIfVersionChanged(String serviceListURL, String installedVersion) {
        String currentVersion = getServiceListVersion(serviceListURL);
        if (currentVersion == null) {
            Log.d(TAG, "Could not determine DVB-I service list version, leaving installed list unchanged");
            return;
        }
        if (currentVersion.equals(installedVersion)) {
            Log.d(TAG, String.format("DVB-I service list version unchanged (%s), skipping reload", currentVersion));
            return;
        }
        Log.d(TAG, String.format("DVB-I service list version changed from %s to %s, reloading",
                installedVersion, currentVersion));
        mMainHandler.post(() -> {
            if (mInstallGateActive || mLastDiscoveryTask != null) {
                Log.d(TAG, "Service list search started while version was checked, skipping reload");
                return;
            }
            startServiceSearch(serviceListURL);
        });
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
               new GetXmlAitTask().execute(new XmlAitAttributes(
                   onDemandUrl, LINKED_APP_SCHEME_1_1 + "?lloc=epg", mTuneGeneration));
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
     * Triplet for HbbTV channel-status / CCS for the DVB-I <em>service</em>
     * (ERRATA0710 / ERRATA0720). Tuner lock still uses
     * {@link ServiceInstance#getTriplet()}.
     */
    public Triplet getHbbtvChannelStatusTriplet() {
        return getHbbtvChannelStatusTriplet(-1);
    }

    /**
     * Triplet for HbbTV channel-status / CCS.
     * <p>
     * {@code instanceIndex < 0}: DVB-I Channel identity (ERRATA0710).
     * RF instance lock ({@code instanceIndex >= 0}, non-DASH): RF delivery
     * triplet so {@code setChannel} to that instance Channel can complete CCS
     * (ERRATA0900). DASH instances keep the DVB-I Channel identity (ERRATA0400).
     */
    public Triplet getHbbtvChannelStatusTriplet(int instanceIndex) {
        if (instanceIndex >= 0) {
            ServiceInstance inst = instanceAt(instanceIndex);
            if (inst != null && !"dvb-dash".equals(inst.getDeliveryType())
                    && inst.getTriplet() != null) {
                return inst.getTriplet();
            }
        }
        return hbbtvStatusTriplet(mServiceManager.getTunedService(),
            mServiceManager.getTunedChannel());
    }

    /**
     * RF delivery triplet while a non-DASH instance is selected. Broadcast AIT
     * {@code service_id} matches this, not the DVB-I Channel CCS identity.
     *
     * @return RF triplet, or null for DASH / no instance
     */
    public Triplet getBroadcastAitTriplet() {
        ServiceInstance inst = mServiceManager.getTunedInstance();
        if (inst == null || "dvb-dash".equals(inst.getDeliveryType())) {
            return null;
        }
        return inst.getTriplet();
    }

    private ServiceInstance instanceAt(int instanceIndex) {
        ServiceInstance tuned = mServiceManager.getTunedInstance();
        Service service = mServiceManager.getTunedService();
        if (service == null || service.getInstances() == null
                || instanceIndex < 0 || instanceIndex >= service.getInstances().size()) {
            return tuned;
        }
        ServiceInstance requested = service.getInstances().get(instanceIndex);
        return requested != null ? requested : tuned;
    }

    private static Triplet hbbtvStatusTriplet(Service service, DvbIChannelAdapter channel) {
        if (service != null && service.getTriplet() != null) {
            return service.getTriplet();
        }
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
        private final int mEpoch;

        ServiceListDiscoveryTask(int epoch) {
            mEpoch = epoch;
        }

        @Override
        protected Void doInBackground(String... uris) {
            mPendingServiceList = null;
            mPendingServiceListUri = null;
            mPendingServiceListXml = null;
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

                    // Hold the parsed list until the type 4.1 gate (if any) completes.
                    mPendingServiceList = serviceList;
                    mPendingServiceListUri = uri;
                    mPendingServiceListXml = responseBuilder.toString();
                }
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
            if (mEpoch != mDiscoveryEpoch) {
                discardPendingServiceList();
                return;
            }
            RelatedMaterial type41 = findType41App(mPendingServiceList);
            if (type41 != null && hasCompletedType41Install(mPendingServiceList)) {
                Log.i(TAG, "Type 4.1 already completed for list UID "
                        + mPendingServiceList.getUID() + " — skipping install gate");
                restoreType41QueryPairs(mPendingServiceList);
                mActiveListUid = mPendingServiceList.getUID();
                restoreActiveCredentials(mActiveListUid);
                captureType4xLocators(mPendingServiceList);
                persistActiveCredentials();
                commitPendingServiceList();
                finalizeSearch();
                startRenewUrlPolling(remainingRenewPollDelayMs());
                if (mRenewDeadlineMs > 0 && !mType43Presented && !mType43RenewSucceeded) {
                    scheduleType43Launch(mRenewDeadlineMs);
                }
            } else if (type41 != null) {
                startType41InstallGate(type41);
            } else {
                commitPendingServiceList();
                finalizeSearch();
            }
        }

        @Override
        public void onCancelled(Void ignore) {
            discardPendingServiceList();
            if (mLastDiscoveryTask == this) {
                finalizeSearch();
            }
        }
    }

    private synchronized void finalizeSearch() {
        Log.d(TAG, "finalizeSearch: Service list discovery completed, triggering callbacks");
        mLastDiscoveryTask = null; // Reset so new searches can be started
        mCurrentServiceListUrl = null; // Clear tracked URL
        clearType13SessionState();
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
            if (LINKED_APP_SCHEME_4_1.equals(result.scheme)
                    && (result.discoveryEpoch != mDiscoveryEpoch || !mInstallGateActive)) {
                Log.i(TAG, "Ignoring stale type 4.1 XML AIT after search abort");
                return;
            }
            if ((LINKED_APP_SCHEME_4_2.equals(result.scheme) && !mType42AppRunning)
                    || (LINKED_APP_SCHEME_4_3.equals(result.scheme) && !mType43AppRunning)) {
                Log.i(TAG, "Ignoring stale type 4.x XML AIT scheme=" + result.scheme);
                return;
            }
            // ERRATA0900: a previous instance's XML AIT must not land after setChannel
            // to another instance (re-lock 1.2 → 1.1 applied scheme 1.2 and CCS).
            if (result.generation >= 0 && result.generation != mTuneGeneration) {
                Log.i(TAG, "Ignoring stale XML AIT scheme=" + result.scheme
                    + " gen=" + result.generation + " current=" + mTuneGeneration);
                return;
            }
            if (result.xml == null || result.xml.isEmpty()) {
                Log.e(TAG, "XML AIT fetch failed scheme=" + result.scheme + " url=" + result.url);
                if (LINKED_APP_SCHEME_1_2.equals(result.scheme)) {
                    onLinkedApp12StartFailed("XML AIT fetch failed");
                }
                if (LINKED_APP_SCHEME_4_1.equals(result.scheme) && mInstallGateActive) {
                    Log.e(TAG, "Type 4.1 XML AIT fetch failed — cancelling install gate");
                    discardPendingServiceList();
                    finishType41InstallGate();
                }
                if (LINKED_APP_SCHEME_4_2.equals(result.scheme)) {
                    mType42AppRunning = false;
                }
                if (LINKED_APP_SCHEME_4_3.equals(result.scheme)) {
                    onType43EndedWithoutCompletion();
                }
                return;
            }
            if (LINKED_APP_SCHEME_1_3.equals(result.scheme)
                    || LINKED_APP_SCHEME_1_2.equals(result.scheme)
                    || LINKED_APP_SCHEME_1_1.equals(result.scheme)) {
                String identity = type13AppIdentity(result.xml);
                if (identity != null && (mPreplayAppIdentity == null
                        || LINKED_APP_SCHEME_1_3.equals(result.scheme))) {
                    mPreplayAppIdentity = identity;
                }
                loadPersistedType13Query(result.url);
            }
            result.xml = applyLaunchContextToXmlAit(result.xml, result.scheme);
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
        public int generation;
        public int discoveryEpoch;

        public XmlAitAttributes(String url, String scheme, int generation) {
            this(url, scheme, generation, -1);
        }

        public XmlAitAttributes(String url, String scheme, int generation, int discoveryEpoch) {
            this.url = url;
            this.scheme = scheme;
            this.generation = generation;
            this.discoveryEpoch = discoveryEpoch;
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

    private static final class QueryPair {
        final String key;
        final String value;
        QueryPair(String key, String value) {
            this.key = key;
            this.value = value != null ? value : "";
        }
    }

    private static RelatedMaterial findType41App(ServiceList list) {
        return findLinkedApp(list, LINKED_APP_SCHEME_4_1);
    }

    private static RelatedMaterial findLinkedApp(ServiceList list, String scheme) {
        if (list == null) {
            return null;
        }
        RelatedMaterial found = findLinkedApp(list.getRelatedMaterials(), scheme);
        if (found != null) {
            return found;
        }
        for (Service service : list.getServices()) {
            found = findLinkedApp(service.getRelatedMaterials(), scheme);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static RelatedMaterial findLinkedApp(List<RelatedMaterial> materials, String scheme) {
        if (materials == null) {
            return null;
        }
        for (RelatedMaterial rm : materials) {
            if (scheme.equals(rm.getHowRelatedHref())
                    && rm.getMediaLocatorUri() != null
                    && !rm.getMediaLocatorUri().isEmpty()) {
                return rm;
            }
        }
        return null;
    }

    private void startType41InstallGate(RelatedMaterial type41) {
        mInstallGateActive = true;
        Log.i(TAG, "Type 4.1 install gate: " + type41.getMediaLocatorUri());
        mMainHandler.removeCallbacks(mInstallGateTimeout);
        mMainHandler.postDelayed(mInstallGateTimeout, TYPE_41_INSTALL_TIMEOUT_MS);
        setLinkedAppJsonRpcListener((method, paramsJson) -> {
            if (!mInstallGateActive) {
                return;
            }
            if (LA_SL_INSTALL_SUCCESS.equals(method)) {
                applyInstallSuccessParams(paramsJson);
                persistType41InstallSuccess(mPendingServiceList);
                commitPendingServiceList();
                finishType41InstallGate();
                startRenewUrlPolling(0);
            } else if (LA_SL_INSTALL_FAILURE.equals(method)) {
                Log.i(TAG, "Type 4.1 sl_install_failure — discarding pending service list");
                discardPendingServiceList();
                finishType41InstallGate();
            }
        });
        launchLinkedApp(type41, LINKED_APP_SCHEME_4_1, "install", true);
    }

    private void launchLinkedApp(RelatedMaterial app, String scheme, String lloc, boolean useDiscoveryEpoch) {
        if (app == null) {
            Log.w(TAG, "No linked app to launch scheme=" + scheme);
            return;
        }
        boolean type4x = LINKED_APP_SCHEME_4_1.equals(scheme)
                || LINKED_APP_SCHEME_4_2.equals(scheme)
                || LINKED_APP_SCHEME_4_3.equals(scheme);
        String url = type4x ? app.getType4xLaunchUri() : app.getMediaLocatorUri();
        boolean html = type4x ? app.isType4xHtml() : app.isGenericHtmlContentType();
        launchLinkedApp(url, html, scheme, lloc, useDiscoveryEpoch);
    }

    private boolean launchLinkedApp(String url, boolean html, String scheme, String lloc,
            boolean useDiscoveryEpoch) {
        if (url == null || url.isEmpty()) {
            Log.w(TAG, "Linked app URL missing scheme=" + scheme);
            return false;
        }
        publishHowRelated(scheme);
        if (html || isHtmlLinkedAppUrl(url, html)) {
            if (!hasLinkedAppCallback()) {
                Log.w(TAG, "No linked-app host for scheme=" + scheme);
            }
            String launchUrl = appendQueryComponent(url, "lloc", lloc);
            if (!LINKED_APP_SCHEME_4_1.equals(scheme)) {
                launchUrl = applyInstallQuery(launchUrl);
            }
            launchUrl = applyInstallationToken(launchUrl, scheme);
            boolean presented = false;
            for (Callback cb : mCallbacks) {
                presented |= cb.onLaunchHtmlLinkedApp(launchUrl, scheme);
            }
            return presented;
        } else {
            if (mType4xAitTask != null) {
                mType4xAitTask.cancel(true);
            }
            if (LINKED_APP_SCHEME_4_1.equals(scheme) && mType41AitTask != null) {
                mType41AitTask.cancel(true);
            }
            GetXmlAitTask task = new GetXmlAitTask();
            if (LINKED_APP_SCHEME_4_1.equals(scheme)) {
                mType41AitTask = task;
            } else {
                mType4xAitTask = task;
            }
            int generation = LINKED_APP_SCHEME_4_1.equals(scheme) ? mTuneGeneration : -1;
            int epoch = useDiscoveryEpoch ? mDiscoveryEpoch : -1;
            task.execute(new XmlAitAttributes(url, scheme, generation, epoch));
            return true;
        }
    }

    private static boolean isHtmlLinkedAppUrl(String url, boolean htmlFromContentType) {
        if (htmlFromContentType) {
            return true;
        }
        String path = url.toLowerCase(Locale.US);
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        return path.endsWith(".html") || path.endsWith(".htm")
                || path.endsWith(".xhtml") || path.endsWith(".html5");
    }

    private void publishHowRelated(String href) {
        Runnable publish = () -> {
            for (Callback cb : mCallbacks) {
                cb.onApplicationHowRelatedHrefChanged(href);
            }
        };
        if (Looper.getMainLooper().isCurrentThread()) {
            publish.run();
        } else {
            mMainHandler.post(publish);
        }
    }

    private void onType41InstallTimeout() {
        if (!mInstallGateActive) {
            return;
        }
        Log.w(TAG, "Type 4.1 install gate timed out after " + TYPE_41_INSTALL_TIMEOUT_MS
                + "ms — discarding pending service list");
        discardPendingServiceList();
        finishType41InstallGate();
    }

    /**
     * Drop a hung or superseded 4.1 gate without scan-complete. Does not bump
     * {@link #mDiscoveryEpoch}; Setup abort must call {@link #cancelServiceSearch()}.
     */
    private void cancelType41InstallGate() {
        mMainHandler.removeCallbacks(mInstallGateTimeout);
        mInstallGateActive = false;
        setLinkedAppJsonRpcListener(null);
        discardPendingServiceList();
    }

    private void finishType41InstallGate() {
        mMainHandler.removeCallbacks(mInstallGateTimeout);
        mInstallGateActive = false;
        setLinkedAppJsonRpcListener(null);
        finalizeSearch();
    }

    private void commitPendingServiceList() {
        ServiceList list = mPendingServiceList;
        String uri = mPendingServiceListUri;
        String xml = mPendingServiceListXml;
        mPendingServiceList = null;
        mPendingServiceListUri = null;
        mPendingServiceListXml = null;
        if (list == null) {
            return;
        }
        mDbHandler.updateServiceList(list);
        if (mCurrentServiceListUrl != null && uri != null && uri.equals(mCurrentServiceListUrl) && xml != null) {
            String version = extractVersionFromXml(xml);
            if (version != null) {
                saveServiceListVersion(uri, version);
            }
        }
        mEpgManager.refreshServiceLists();
    }

    private void discardPendingServiceList() {
        mPendingServiceList = null;
        mPendingServiceListUri = null;
        mPendingServiceListXml = null;
        mPendingRenewUrl = null;
        mPendingInstallationToken = null;
    }

    private void applyInstallSuccessParams(String paramsJson) {
        mInstallQueryPairs.clear();
        mPendingRenewUrl = null;
        mPendingInstallationToken = null;
        try {
            JSONObject params = new JSONObject(paramsJson != null ? paramsJson : "{}");
            parseInstallQuery(params);
            JSONObject response = params.optJSONObject("application_response");
            if (response != null) {
                if (response.has("renewurl")) {
                    mPendingRenewUrl = emptyToNull(response.optString("renewurl", null));
                }
                if (response.has("installationtoken")) {
                    mPendingInstallationToken = emptyToNull(response.optString("installationtoken", null));
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "Type 4.1 success params were not JSON");
        }
    }

    private void promotePendingCredentialsToActive() {
        mActiveRenewUrl = mPendingRenewUrl;
        mActiveInstallationToken = mPendingInstallationToken;
    }

    /**
     * §5.2.3.6.2: 4.3 may include a replacement renewurl / token. Omitted fields
     * and a missing query keep the live 4.1 values.
     */
    private void applyRenewSuccessParams(String paramsJson) {
        try {
            JSONObject params = new JSONObject(paramsJson != null ? paramsJson : "{}");
            if (params.has("query") && !params.isNull("query")) {
                mInstallQueryPairs.clear();
                parseInstallQuery(params);
            }
            JSONObject response = params.optJSONObject("application_response");
            if (response != null) {
                if (response.has("renewurl")) {
                    mActiveRenewUrl = emptyToNull(response.optString("renewurl", null));
                }
                if (response.has("installationtoken")) {
                    mActiveInstallationToken = emptyToNull(response.optString("installationtoken", null));
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "Type 4.3 success params were not JSON");
        }
    }

    private void restoreActiveCredentials(String uid) {
        if (uid == null) {
            return;
        }
        SharedPreferences prefs = mDvbIView.getContext().getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        mActiveRenewUrl = prefs.getString(PREF_LA_RENEWURL_PREFIX + uid, null);
        mActiveInstallationToken = prefs.getString(PREF_LA_TOKEN_PREFIX + uid, null);
        mRenewDeadlineMs = prefs.getLong(PREF_LA_DEADLINE_PREFIX + uid, -1L);
        mNextRenewPollAtWallMs = prefs.getLong(PREF_LA_NEXT_POLL_PREFIX + uid, 0L);
        mLastRenewFailed = prefs.getBoolean(PREF_LA_RENEW_FAILED_PREFIX + uid, false);
        mAwaitingRenewTimeDocument = prefs.getBoolean(PREF_LA_AWAITING_TIME_PREFIX + uid, false);
        mType43Presented = prefs.getBoolean(PREF_LA43_PRESENTED_PREFIX + uid, false);
        mType43RenewSucceeded = prefs.getBoolean(PREF_LA43_SUCCEEDED_PREFIX + uid, false);
    }

    private void parseInstallQuery(JSONObject params) {
        if (!params.has("query") || params.isNull("query")) {
            return;
        }
        Object query = params.opt("query");
        if (query instanceof JSONArray) {
            JSONArray arr = (JSONArray) query;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String key = item.optString("key", null);
                if (key == null || key.isEmpty()) {
                    continue;
                }
                mInstallQueryPairs.add(new QueryPair(key, item.optString("value", "")));
            }
        } else if (query instanceof JSONObject) {
            JSONObject obj = (JSONObject) query;
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key == null || key.isEmpty()) {
                    continue;
                }
                mInstallQueryPairs.add(new QueryPair(key, obj.optString(key, "")));
            }
        } else {
            Log.w(TAG, "Type 4.1 query was neither a JSON array nor object");
        }
    }

    private boolean hasCompletedType41Install(ServiceList list) {
        if (list == null || list.getUID() == null) {
            return false;
        }
        SharedPreferences prefs = mDvbIView.getContext().getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        Set<String> done = prefs.getStringSet(PREF_LA41_COMPLETED_UIDS, Collections.emptySet());
        return done != null && done.contains(list.getUID());
    }

    /** Setup re-install of this URL must run type 4.1 again (clears leftover skip). */
    private void clearType41CompletedForUrl(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        SharedPreferences prefs = mDvbIView.getContext().getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        Set<String> done = new HashSet<>(prefs.getStringSet(PREF_LA41_COMPLETED_UIDS, Collections.emptySet()));
        if (!done.remove(url)) {
            return;
        }
        prefs.edit().putStringSet(PREF_LA41_COMPLETED_UIDS, done).apply();
        Log.i(TAG, "Cleared type 4.1 completion for " + url + " — install gate will run");
    }

    private void persistType41InstallSuccess(ServiceList list) {
        if (list == null || list.getUID() == null) {
            return;
        }
        mActiveListUid = list.getUID();
        captureType4xLocators(list);
        // Previous list's 4.3 is no longer this install. Do not keep the
        // "already presenting" flag or that list's renew deadline.
        clearType43PresentationState();
        mLastRenewFailed = false;
        mType43RenewSucceeded = false;
        mType43Presented = false;
        mRenewDeadlineMs = -1L;
        mNextRenewPollAtWallMs = 0L;
        mRenewCacheUntilWallMs = 0L;
        mRenewPollRetry = 0;
        SharedPreferences prefs = mDvbIView.getContext().getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        Set<String> done = new HashSet<>(prefs.getStringSet(PREF_LA41_COMPLETED_UIDS, Collections.emptySet()));
        done.add(list.getUID());
        Set<String> withdrawn = new HashSet<>(prefs.getStringSet(PREF_LA_WITHDRAWN_UIDS, Collections.emptySet()));
        withdrawn.remove(list.getUID());
        SharedPreferences.Editor editor = prefs.edit()
                .putStringSet(PREF_LA41_COMPLETED_UIDS, done)
                .putStringSet(PREF_LA_WITHDRAWN_UIDS, withdrawn)
                .putString(PREF_LA41_QUERY_PREFIX + list.getUID(), queryPairsToJson())
                .putString(PREF_LA_ACTIVE_UID, list.getUID());
        promotePendingCredentialsToActive();
        mRenewEpoch++;
        mAwaitingRenewTimeDocument = hasType43Locator() && mActiveRenewUrl != null
                && !mActiveRenewUrl.isEmpty();
        persistLinkedAppCredentials(editor, list.getUID());
        editor.apply();
        Log.i(TAG, "Persisted type 4.1 install completion for list UID " + list.getUID()
                + " renewurl=" + (mActiveRenewUrl != null));
    }

    private void captureType4xLocators(ServiceList list) {
        RelatedMaterial type42 = findLinkedApp(list, LINKED_APP_SCHEME_4_2);
        RelatedMaterial type43 = findLinkedApp(list, LINKED_APP_SCHEME_4_3);
        mType42Uri = type42 != null ? type42.getType4xLaunchUri() : null;
        mType42ContentType = type42 != null ? type42.getType4xLaunchContentType() : null;
        mType43Uri = type43 != null ? type43.getType4xLaunchUri() : null;
        mType43ContentType = type43 != null ? type43.getType4xLaunchContentType() : null;
        Log.i(TAG, "Type 4.x locators 4.2=" + mType42Uri + " 4.3=" + mType43Uri);
    }

    private void persistLinkedAppCredentials(SharedPreferences.Editor editor, String uid) {
        putPrefOrRemove(editor, PREF_LA_RENEWURL_PREFIX + uid, mActiveRenewUrl);
        putPrefOrRemove(editor, PREF_LA_TOKEN_PREFIX + uid, mActiveInstallationToken);
        putPrefOrRemove(editor, PREF_LA42_URI_PREFIX + uid, mType42Uri);
        putPrefOrRemove(editor, PREF_LA42_CTYPE_PREFIX + uid, mType42ContentType);
        putPrefOrRemove(editor, PREF_LA43_URI_PREFIX + uid, mType43Uri);
        putPrefOrRemove(editor, PREF_LA43_CTYPE_PREFIX + uid, mType43ContentType);
        if (mRenewDeadlineMs > 0) {
            editor.putLong(PREF_LA_DEADLINE_PREFIX + uid, mRenewDeadlineMs);
        } else {
            editor.remove(PREF_LA_DEADLINE_PREFIX + uid);
        }
        if (mNextRenewPollAtWallMs > 0) {
            editor.putLong(PREF_LA_NEXT_POLL_PREFIX + uid, mNextRenewPollAtWallMs);
        } else {
            editor.remove(PREF_LA_NEXT_POLL_PREFIX + uid);
        }
        editor.putBoolean(PREF_LA_RENEW_FAILED_PREFIX + uid, mLastRenewFailed);
        editor.putBoolean(PREF_LA_AWAITING_TIME_PREFIX + uid, mAwaitingRenewTimeDocument);
        editor.putBoolean(PREF_LA43_PRESENTED_PREFIX + uid, mType43Presented);
        editor.putBoolean(PREF_LA43_SUCCEEDED_PREFIX + uid, mType43RenewSucceeded);
    }

    private static void putPrefOrRemove(SharedPreferences.Editor editor, String key, String value) {
        if (value == null || value.isEmpty()) {
            editor.remove(key);
        } else {
            editor.putString(key, value);
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private void restoreType41QueryPairs(ServiceList list) {
        mInstallQueryPairs.clear();
        if (list == null || list.getUID() == null) {
            return;
        }
        SharedPreferences prefs = mDvbIView.getContext().getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        String json = prefs.getString(PREF_LA41_QUERY_PREFIX + list.getUID(), null);
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String key = item.optString("key", null);
                if (key == null || key.isEmpty()) {
                    continue;
                }
                mInstallQueryPairs.add(new QueryPair(key, item.optString("value", "")));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Stored type 4.1 query was not JSON");
        }
    }

    private String queryPairsToJson() {
        JSONArray arr = new JSONArray();
        for (QueryPair pair : mInstallQueryPairs) {
            try {
                JSONObject item = new JSONObject();
                item.put("key", pair.key);
                item.put("value", pair.value);
                arr.put(item);
            } catch (JSONException ignored) {
            }
        }
        return arr.toString();
    }

    /**
     * §5.2.3.6: lloc and success query pairs belong on AIT applicationLocation
     * (and DASH MPD URLs), not on the XML AIT fetch URL.
     * Install pairs are appended before any other application's pairs.
     * Raw {@code &} in the location must be escaped or libxml2 rejects the
     * rewritten AIT (APPS0300+).
     */
    private String applyLaunchContextToXmlAit(String xml, String scheme) {
        if (xml == null) {
            return null;
        }
        String lloc = llocForScheme(scheme);
        boolean installQuery = !mInstallQueryPairs.isEmpty();
        boolean preplayQuery = !mPreplayQueryPairs.isEmpty()
                && (LINKED_APP_SCHEME_1_3.equals(scheme)
                || LINKED_APP_SCHEME_1_2.equals(scheme)
                || LINKED_APP_SCHEME_1_1.equals(scheme));
        if (lloc == null && !installQuery && !preplayQuery) {
            return xml;
        }
        Matcher matcher = APPLICATION_LOCATION.matcher(xml);
        StringBuffer rewritten = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String location = unescapeXmlText(matcher.group(2).trim());
            if (lloc != null && !queryHasComponent(location, "lloc")) {
                location = appendQueryComponent(location, "lloc", lloc);
            }
            if (installQuery) {
                location = applyQueryPairs(location, mInstallQueryPairs);
            }
            if (preplayQuery) {
                location = applyQueryPairs(location, mPreplayQueryPairs);
            }
            location = applyInstallationToken(location, scheme);
            location = escapeXmlText(location);
            matcher.appendReplacement(rewritten,
                    Matcher.quoteReplacement(matcher.group(1) + location + matcher.group(3)));
        }
        if (!found) {
            Log.w(TAG, "XML AIT had no applicationLocation to append query/lloc scheme=" + scheme);
            return xml;
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    private static String escapeXmlText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String unescapeXmlText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    private static String llocForScheme(String scheme) {
        if (LINKED_APP_SCHEME_4_1.equals(scheme)) {
            return "install";
        }
        if (LINKED_APP_SCHEME_4_2.equals(scheme)) {
            return "withdrawal-of-agreement";
        }
        if (LINKED_APP_SCHEME_4_3.equals(scheme)) {
            return "renewal-of-agreement";
        }
        if (LINKED_APP_SCHEME_1_3.equals(scheme)) {
            return "preroll";
        }
        if (LINKED_APP_SCHEME_1_2.equals(scheme)) {
            return "service";
        }
        return null;
    }

    private String applyInstallQuery(String url) {
        String result = applyQueryPairs(url, mInstallQueryPairs);
        return applyQueryPairs(result, mPreplayQueryPairs);
    }

    private static String applyQueryPairs(String url, List<QueryPair> pairs) {
        if (url == null || pairs == null || pairs.isEmpty()) {
            return url;
        }
        String result = url;
        for (QueryPair pair : pairs) {
            result = replaceQueryComponent(result, pair.key, pair.value);
        }
        return result;
    }

    /** §5.2.3.6.4: pass installationtoken to type 4.2 and 4.3 on launch. */
    private String applyInstallationToken(String url, String scheme) {
        if (url == null) {
            return null;
        }
        if (!LINKED_APP_SCHEME_4_2.equals(scheme) && !LINKED_APP_SCHEME_4_3.equals(scheme)) {
            return url;
        }
        if (mActiveInstallationToken == null || mActiveInstallationToken.isEmpty()) {
            return url;
        }
        return appendQueryComponent(url, "installationtoken", mActiveInstallationToken);
    }

    private static boolean queryHasComponent(String url, String key) {
        if (url == null || key == null || key.isEmpty()) {
            return false;
        }
        int q = url.indexOf('?');
        if (q < 0) {
            return false;
        }
        int hash = url.indexOf('#');
        String query = hash >= 0 ? url.substring(q + 1, hash) : url.substring(q + 1);
        String encoded = Uri.encode(key);
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            String name = eq >= 0 ? part.substring(0, eq) : part;
            if (key.equals(name) || encoded.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Replace {@code key} if it is already present, otherwise append it. */
    private static String replaceQueryComponent(String url, String key, String value) {
        if (url == null || key == null || key.isEmpty()) {
            return url;
        }
        String fragment = "";
        int hash = url.indexOf('#');
        if (hash >= 0) {
            fragment = url.substring(hash);
            url = url.substring(0, hash);
        }
        int q = url.indexOf('?');
        if (q < 0) {
            return appendQueryComponent(url + fragment, key, value);
        }
        String base = url.substring(0, q);
        String[] parts = url.substring(q + 1).split("&");
        String encodedKey = Uri.encode(key);
        String encodedValue = Uri.encode(value != null ? value : "");
        StringBuilder query = new StringBuilder();
        boolean replaced = false;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String name = eq >= 0 ? part.substring(0, eq) : part;
            if (!replaced && (key.equals(name) || encodedKey.equals(name))) {
                part = encodedKey + "=" + encodedValue;
                replaced = true;
            }
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(part);
        }
        if (!replaced) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(encodedKey).append('=').append(encodedValue);
        }
        return base + '?' + query + fragment;
    }

    private static String appendQueryComponent(String url, String key, String value) {
        if (url == null || key == null || key.isEmpty()) {
            return url;
        }
        String fragment = "";
        int hash = url.indexOf('#');
        if (hash >= 0) {
            fragment = url.substring(hash);
            url = url.substring(0, hash);
        }
        String sep = url.indexOf('?') >= 0 ? "&" : "?";
        return url + sep + Uri.encode(key) + "=" + Uri.encode(value != null ? value : "") + fragment;
    }

    public boolean hasType42LinkedApp() {
        return mType42Uri != null && !mType42Uri.isEmpty()
                && mActiveListUid != null && !isConsentWithdrawn(mActiveListUid);
    }

    /**
     * Withdraw agreement applies to the active list, and only while it still has
     * a type 4.2 linked application.
     */
    public boolean canWithdrawInstalledList(String uid) {
        return uid != null && uid.equals(mActiveListUid) && hasType42LinkedApp();
    }

    /**
     * True while a type 4.3 launch is outstanding this cycle: waiting for the
     * first renewurl time, waiting for a host, scheduled at a deadline, or
     * presenting. Not true merely because a list still has a renewurl after
     * 4.3 completed.
     */
    public boolean hasPendingType43Renewal() {
        if (!hasType43Locator() || mActiveListUid == null || isConsentWithdrawn(mActiveListUid)) {
            return false;
        }
        return mType43AppRunning || mType43AwaitingHost
                || mRenewDeadlineMs > 0 || mAwaitingRenewTimeDocument;
    }

    public boolean shouldHoldInstallationUi() {
        return hasType42LinkedApp() || hasPendingType43Renewal();
    }

    private boolean hasType43Locator() {
        return mType43Uri != null && !mType43Uri.isEmpty();
    }

    private void clearType43PresentationState() {
        mType43AppRunning = false;
        mType43AwaitingHost = false;
        mMainHandler.removeCallbacks(mLaunchType43Runnable);
        mMainHandler.removeCallbacks(mRenewDeadlineExpired);
    }

    /** Drop 4.2 / 4.3 locators until the new list's 4.1 success captures them. */
    private void clearType4xLaunchLocators() {
        mType42AppRunning = false;
        mType42Uri = null;
        mType42ContentType = null;
        mType43Uri = null;
        mType43ContentType = null;
    }

    /** Drop live 4.3 scheduling and in-flight polls. Persisted deadline is reloaded on skip-install. */
    private void invalidateRenewalCycle() {
        mRenewEpoch++;
        stopRenewUrlPolling();
        clearType43PresentationState();
        mAwaitingRenewTimeDocument = false;
        mRenewPollRetry = 0;
        mRenewDeadlineMs = -1L;
        mNextRenewPollAtWallMs = 0L;
    }

    /**
     * Platform UI for TS 103 770 §5.2.3.6.3: run the type 4.2 linked application
     * with {@code lloc=withdrawal-of-agreement}.
     */
    public boolean requestConsentWithdrawal() {
        if (!hasType42LinkedApp()) {
            Log.w(TAG, "No type 4.2 linked application to withdraw agreement");
            return false;
        }
        if (mType42AppRunning) {
            Log.i(TAG, "Type 4.2 withdrawal app already running");
            return true;
        }
        mType42AppRunning = true;
        Log.i(TAG, "Launching type 4.2 consent withdrawal app");
        boolean html = isHtmlContentType(mType42ContentType) || isHtmlLinkedAppUrl(mType42Uri, false);
        if (!launchLinkedApp(mType42Uri, html, LINKED_APP_SCHEME_4_2, "withdrawal-of-agreement", false)) {
            mType42AppRunning = false;
            Log.w(TAG, "Type 4.2 withdrawal app did not start");
            return false;
        }
        return true;
    }

    /**
     * A stored service list. {@link #getUid()} is the list URL used at install.
     * {@link #getLabel()} is the filename, for the setup screen.
     */
    public static final class InstalledServiceList {
        private final String mUid;
        private final String mLabel;

        InstalledServiceList(String uid, String label) {
            mUid = uid;
            mLabel = label;
        }

        public String getUid() {
            return mUid;
        }

        public String getLabel() {
            return mLabel;
        }

        @Override
        public String toString() {
            return mLabel;
        }
    }

    /** @return true when at least one service list is stored, including a withdrawn list. */
    public boolean hasInstalledServiceLists() {
        return !getInstalledServiceLists().isEmpty();
    }

    /**
     * URL of the list whose version can be refreshed. Prefers the URL saved at install.
     * A single stored list is used when that preference is missing. Several stored lists
     * and no saved URL have no single list to refresh.
     */
    public String getInstalledServiceListUrl() {
        SharedPreferences prefs = mDvbIView.getContext().getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        String url = prefs.getString(PREF_DVBI_SERVICE_LIST_URL, null);
        if (url != null && !url.isEmpty()) {
            return url;
        }
        List<InstalledServiceList> lists = getInstalledServiceLists();
        if (lists.size() == 1) {
            return lists.get(0).getUid();
        }
        return null;
    }

    public List<InstalledServiceList> getInstalledServiceLists() {
        List<InstalledServiceList> installed = new ArrayList<>();
        List<ServiceList> lists = mDbHandler.getServiceLists();
        if (lists == null) {
            return installed;
        }
        for (ServiceList list : lists) {
            String uid = list.getUID();
            if (uid == null || uid.isEmpty()) {
                continue;
            }
            installed.add(new InstalledServiceList(uid, labelForServiceList(uid)));
        }
        return installed;
    }

    /**
     * Delete one stored service list and its services. Does not run the type 4.2
     * withdrawal application. Clears that list's install-completion state so a later
     * scan runs its type 4.1 gate again.
     *
     * @return true when that list was stored and has been removed
     */
    public boolean removeInstalledServiceList(String uid) {
        if (uid == null || uid.isEmpty()) {
            return false;
        }
        boolean found = false;
        List<ServiceList> lists = mDbHandler.getServiceLists();
        if (lists != null) {
            for (ServiceList list : lists) {
                if (uid.equals(list.getUID())) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            return false;
        }
        if (mPendingServiceList != null && uid.equals(mPendingServiceList.getUID())) {
            cancelServiceSearch();
        }
        mDbHandler.deleteServiceList(uid);
        SharedPreferences prefs = mDvbIView.getContext().getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : new HashSet<>(prefs.getAll().keySet())) {
            if (key.endsWith(uid)) {
                editor.remove(key);
            }
        }
        Set<String> completed = new HashSet<>(prefs.getStringSet(PREF_LA41_COMPLETED_UIDS, Collections.emptySet()));
        completed.remove(uid);
        Set<String> withdrawn = new HashSet<>(prefs.getStringSet(PREF_LA_WITHDRAWN_UIDS, Collections.emptySet()));
        withdrawn.remove(uid);
        editor.putStringSet(PREF_LA41_COMPLETED_UIDS, completed);
        editor.putStringSet(PREF_LA_WITHDRAWN_UIDS, withdrawn);
        if (uid.equals(prefs.getString(PREF_LA_ACTIVE_UID, null))) {
            editor.remove(PREF_LA_ACTIVE_UID);
        }
        if (uid.equals(prefs.getString(PREF_DVBI_SERVICE_LIST_URL, null))) {
            editor.remove(PREF_DVBI_SERVICE_LIST_URL);
            editor.remove(PREF_DVBI_SERVICE_LIST_VERSION);
        }
        editor.apply();
        if (uid.equals(mActiveListUid)) {
            stopAllRenewTimers();
            mActiveListUid = null;
            mActiveRenewUrl = null;
            mActiveInstallationToken = null;
            mPendingRenewUrl = null;
            mPendingInstallationToken = null;
            mType42Uri = null;
            mType42ContentType = null;
            mType43Uri = null;
            mType43ContentType = null;
            mRenewDeadlineMs = -1L;
            mNextRenewPollAtWallMs = 0L;
            mRenewCacheUntilWallMs = 0L;
            mType43RenewSucceeded = false;
            mType42AppRunning = false;
            mType43AppRunning = false;
            mLastRenewFailed = false;
        }
        mEpgManager.refreshServiceLists();
        Log.i(TAG, "Removed installed service list " + uid);
        for (DvbCallback handler : mDvbCallbacks) {
            handler.onServiceDeleted();
        }
        return true;
    }

    private static String labelForServiceList(String uid) {
        String path = uid;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < path.length()) {
            path = path.substring(slash + 1);
        }
        String decoded = Uri.decode(path);
        return decoded == null || decoded.isEmpty() ? uid : decoded;
    }

    public boolean isConsentWithdrawn() {
        return isConsentWithdrawn(mActiveListUid);
    }

    private boolean isConsentWithdrawn(String uid) {
        if (uid == null) {
            return false;
        }
        SharedPreferences prefs = mDvbIView.getContext().getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        Set<String> withdrawn = prefs.getStringSet(PREF_LA_WITHDRAWN_UIDS, Collections.emptySet());
        return withdrawn != null && withdrawn.contains(uid);
    }

    private static boolean isHtmlContentType(String contentType) {
        return "text/html".equalsIgnoreCase(contentType)
                || "application/xhtml+xml".equalsIgnoreCase(contentType);
    }

    private void restoreLinkedAppState() {
        SharedPreferences prefs = mDvbIView.getContext().getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        mActiveListUid = prefs.getString(PREF_LA_ACTIVE_UID, null);
        if (mActiveListUid == null) {
            return;
        }
        restoreType41QueryPairs(new ServiceList.Builder().setUID(mActiveListUid).build());
        restoreActiveCredentials(mActiveListUid);
        mType42Uri = prefs.getString(PREF_LA42_URI_PREFIX + mActiveListUid, null);
        mType42ContentType = prefs.getString(PREF_LA42_CTYPE_PREFIX + mActiveListUid, null);
        mType43Uri = prefs.getString(PREF_LA43_URI_PREFIX + mActiveListUid, null);
        mType43ContentType = prefs.getString(PREF_LA43_CTYPE_PREFIX + mActiveListUid, null);
        if (mType42Uri == null || mType43Uri == null) {
            for (ServiceList list : mDbHandler.getServiceLists()) {
                if (mActiveListUid.equals(list.getUID())) {
                    if (mType42Uri == null) {
                        RelatedMaterial type42 = findLinkedApp(list, LINKED_APP_SCHEME_4_2);
                        if (type42 != null) {
                            mType42Uri = type42.getType4xLaunchUri();
                            mType42ContentType = type42.getType4xLaunchContentType();
                        }
                    }
                    if (mType43Uri == null) {
                        RelatedMaterial type43 = findLinkedApp(list, LINKED_APP_SCHEME_4_3);
                        if (type43 != null) {
                            mType43Uri = type43.getType4xLaunchUri();
                            mType43ContentType = type43.getType4xLaunchContentType();
                        }
                    }
                    break;
                }
            }
        }
        if (isConsentWithdrawn(mActiveListUid)) {
            return;
        }
        if (mLastRenewFailed && mRenewDeadlineMs > 0 && System.currentTimeMillis() >= mRenewDeadlineMs) {
            mMainHandler.post(this::withdrawConsent);
            return;
        }
        if (mRenewDeadlineMs > 0) {
            scheduleType43Launch(mRenewDeadlineMs);
        }
        if (mActiveRenewUrl != null) {
            startRenewUrlPolling(remainingRenewPollDelayMs());
        }
    }

    /**
     * Standby freezes {@link Handler#postDelayed} (uptime). Re-arm from wall clock
     * after SCREEN_ON or when Setup / Live TV comes back.
     */
    public void onTerminalResumed() {
        if (mResumeRearmPosted) {
            return;
        }
        mResumeRearmPosted = true;
        mMainHandler.post(() -> {
            mResumeRearmPosted = false;
            rearmRenewalTimersForWallClock();
        });
    }

    private void rearmRenewalTimersForWallClock() {
        if (mActiveRenewUrl != null && !isConsentWithdrawn(mActiveListUid)) {
            startRenewUrlPolling(remainingRenewPollDelayMs());
        }
        if (mType43AppRunning) {
            return;
        }
        if (mType43Presented || mType43RenewSucceeded) {
            return;
        }
        if (mLastRenewFailed) {
            if (mRenewDeadlineMs > 0) {
                armRenewDeadline();
            }
            return;
        }
        if (mType43AwaitingHost) {
            Log.i(TAG, "Rearming deferred type 4.3 launch after resume");
            scheduleType43Launch(System.currentTimeMillis());
            return;
        }
        if (mRenewDeadlineMs > 0) {
            scheduleType43Launch(mRenewDeadlineMs);
            return;
        }
        maybeLaunchDeferredType43();
    }

    private long remainingRenewPollDelayMs() {
        long notBefore = Math.max(mNextRenewPollAtWallMs, mRenewCacheUntilWallMs);
        if (notBefore <= 0) {
            return 0L;
        }
        return Math.max(0L, notBefore - System.currentTimeMillis());
    }

    /**
     * Host reports that a type 4.2/4.3 application is no longer presenting
     * (destroyed, replaced, or Setup torn down) without a JSON-RPC completion.
     */
    public void notifyType4xAppEnded(String scheme) {
        if (LINKED_APP_SCHEME_4_2.equals(scheme)) {
            mType42AppRunning = false;
            return;
        }
        if (LINKED_APP_SCHEME_4_3.equals(scheme)) {
            onType43EndedWithoutCompletion();
        }
    }

    /** 4.3 left without renew_success / renew_failure (host tear-down or AIT fetch fail). */
    private void onType43EndedWithoutCompletion() {
        if (!mType43AppRunning) {
            return;
        }
        mType43AppRunning = false;
        if (mType43Presented) {
            Log.i(TAG, "Type 4.3 host gone after the app was shown — not relaunching");
            return;
        }
        if (!hasLinkedAppCallback()) {
            mType43AwaitingHost = true;
            startRenewUrlPolling(remainingRenewPollDelayMs());
            return;
        }
        if (mRenewDeadlineMs > 0 && System.currentTimeMillis() >= mRenewDeadlineMs) {
            withdrawConsent();
            return;
        }
        startRenewUrlPolling(remainingRenewPollDelayMs());
    }

    /**
     * Arm the next renewurl GET. An already-armed Cache-Control wait is never
     * shortened: APPS0130–0150 fail if the second GET arrives before max-age.
     * 404 back-off (APPS0120) must not be stretched by that wait.
     */
    private void startRenewUrlPolling(long delayMs) {
        startRenewUrlPolling(delayMs, true);
    }

    private void startRenewUrlPolling(long delayMs, boolean honourCache) {
        if (mActiveRenewUrl == null || isConsentWithdrawn(mActiveListUid)) {
            return;
        }
        long waitMs = Math.max(0L, delayMs);
        if (honourCache) {
            long remaining = remainingRenewPollDelayMs();
            if (remaining > waitMs) {
                Log.i(TAG, "renewurl poll deferred " + remaining + "ms to honour Cache-Control");
                waitMs = remaining;
            }
        } else {
            mRenewCacheUntilWallMs = 0L;
        }
        mNextRenewPollAtWallMs = System.currentTimeMillis() + waitMs;
        persistNextRenewPollTime();
        if (mRenewPollInFlight) {
            return;
        }
        mMainHandler.removeCallbacks(mRenewPollRunnable);
        mMainHandler.postDelayed(mRenewPollRunnable, waitMs);
    }

    private void persistNextRenewPollTime() {
        if (mActiveListUid == null) {
            return;
        }
        SharedPreferences prefs = mDvbIView.getContext().getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (mNextRenewPollAtWallMs > 0) {
            editor.putLong(PREF_LA_NEXT_POLL_PREFIX + mActiveListUid, mNextRenewPollAtWallMs);
        } else {
            editor.remove(PREF_LA_NEXT_POLL_PREFIX + mActiveListUid);
        }
        editor.apply();
    }

    private void stopRenewUrlPolling() {
        mMainHandler.removeCallbacks(mRenewPollRunnable);
    }

    private void stopAllRenewTimers() {
        stopRenewUrlPolling();
        mMainHandler.removeCallbacks(mLaunchType43Runnable);
        mMainHandler.removeCallbacks(mRenewDeadlineExpired);
    }

    private void runRenewUrlPoll() {
        if (mActiveRenewUrl == null || isConsentWithdrawn(mActiveListUid) || mRenewPollInFlight) {
            return;
        }
        final String url = mActiveRenewUrl;
        final String token = mActiveInstallationToken;
        final int epoch = mRenewEpoch;
        mRenewPollInFlight = true;
        mIoExecutor.execute(() -> {
            RenewPollResult result = fetchRenewUrl(url, token);
            mMainHandler.post(() -> {
                mRenewPollInFlight = false;
                if (epoch != mRenewEpoch) {
                    Log.i(TAG, "Ignoring stale renewurl poll after search or credential change");
                    return;
                }
                handleRenewPollResult(result);
            });
        });
    }

    private static final class RenewPollResult {
        final int code;
        final boolean connectFailed;
        final String body;
        final String contentType;
        final String cacheControl;
        final String url;
        final String token;
        RenewPollResult(int code, boolean connectFailed, String body, String contentType,
                String cacheControl, String url, String token) {
            this.code = code;
            this.connectFailed = connectFailed;
            this.body = body;
            this.contentType = contentType;
            this.cacheControl = cacheControl;
            this.url = url;
            this.token = token;
        }
    }

    /**
     * TS 103 770 §5.2.3.6.2. Never log the installation token.
     */
    private static RenewPollResult fetchRenewUrl(String renewUrl, String token) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(renewUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            // Official 0130–0150 count server GETs. HttpURLConnection would
            // otherwise reuse the first empty max-age=30s body and never see
            // the later ISO time, so type 4.3 never launches.
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            int code = connection.getResponseCode();
            String contentType = connection.getContentType();
            String cacheControl = connection.getHeaderField("Cache-Control");
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = "";
            if (stream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (content.length() > 0) {
                        content.append('\n');
                    }
                    content.append(line);
                }
                reader.close();
                body = content.toString();
            }
            return new RenewPollResult(code, false, body, contentType, cacheControl, renewUrl, token);
        } catch (IOException e) {
            Log.w(TAG, "renewurl poll failed before HTTP response");
            return new RenewPollResult(-1, true, null, null, null, renewUrl, token);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void handleRenewPollResult(RenewPollResult result) {
        if (mActiveRenewUrl == null || isConsentWithdrawn(mActiveListUid)) {
            return;
        }
        if (!Objects.equals(result.url, mActiveRenewUrl)
                || !Objects.equals(result.token, mActiveInstallationToken)) {
            Log.i(TAG, "Ignoring stale renewurl poll after credential replacement");
            return;
        }
        if (result.connectFailed || result.code == HttpURLConnection.HTTP_NOT_FOUND) {
            mRenewPollRetry++;
            if (mRenewPollRetry > 10) {
                mRenewPollRetry = 10;
            }
            long waitMs = backoffWaitMs(mRenewPollRetry);
            Log.i(TAG, "renewurl 404/connect failure retry=" + mRenewPollRetry + " waitMs=" + waitMs);
            startRenewUrlPolling(waitMs, false);
            return;
        }
        mRenewPollRetry = 0;
        if (result.code != HttpURLConnection.HTTP_OK) {
            Log.w(TAG, "renewurl HTTP " + result.code + " — backing off");
            mRenewPollRetry = 1;
            startRenewUrlPolling(backoffWaitMs(1), false);
            return;
        }
        String body = result.body != null ? result.body.trim() : "";
        if (body.isEmpty()) {
            long waitMs = cacheControlWaitMs(result.cacheControl);
            if (waitMs > 0) {
                mRenewCacheUntilWallMs = System.currentTimeMillis() + waitMs;
            }
            Log.i(TAG, "renewurl empty document; repoll in " + waitMs + "ms");
            startRenewUrlPolling(waitMs);
            return;
        }
        if (isTextPlain(result.contentType) || looksLikeIso8601(body)) {
            Long when = parseIso8601Millis(body);
            if (when != null) {
                onRenewTimeSignalled(when);
                return;
            }
        }
        Log.w(TAG, "renewurl body was not an empty document or ISO 8601 time");
        startRenewUrlPolling(EMPTY_RENEW_DEFAULT_WAIT_MS);
    }

    /**
     * §4.3.3.7: minwait = 4^(retry-1)*100, maxwait = 4^retry*100.
     * APPS0120 measures request-to-request and allows 400ms + 50ms for the first
     * retry, so retry 1 stays near minwait and leaves room for HTTP overhead.
     */
    private static long backoffWaitMs(int currentRetry) {
        int retry = Math.max(1, Math.min(currentRetry, 10));
        long minWait = 100L;
        for (int i = 1; i < retry; i++) {
            minWait *= 4L;
        }
        long maxWait = minWait * 4L;
        if (retry == 1) {
            return ThreadLocalRandom.current().nextLong(minWait, Math.min(maxWait, 151L));
        }
        if (maxWait <= minWait + 1) {
            return minWait;
        }
        return ThreadLocalRandom.current().nextLong(minWait, maxWait);
    }

    private static long cacheControlWaitMs(String cacheControl) {
        if (cacheControl != null) {
            Matcher matcher = CACHE_MAX_AGE.matcher(cacheControl);
            if (matcher.find()) {
                try {
                    long seconds = Long.parseLong(matcher.group(1));
                    return Math.max(0L, seconds * 1000L);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return EMPTY_RENEW_DEFAULT_WAIT_MS;
    }

    private static boolean isTextPlain(String contentType) {
        if (contentType == null) {
            return false;
        }
        String type = contentType.toLowerCase(Locale.US);
        int semi = type.indexOf(';');
        if (semi >= 0) {
            type = type.substring(0, semi).trim();
        }
        return "text/plain".equals(type);
    }

    private static boolean looksLikeIso8601(String body) {
        return body.length() >= 20 && body.charAt(4) == '-' && body.charAt(10) == 'T';
    }

    private static Long parseIso8601Millis(String body) {
        String value = body.trim();
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeParseException e) {
            if (!value.endsWith("Z") && !value.contains("+") && value.length() >= 19) {
                try {
                    return Instant.parse(value + "Z").toEpochMilli();
                } catch (DateTimeParseException ignored) {
                }
            }
            return null;
        }
    }

    private void onRenewTimeSignalled(long whenMs) {
        long now = System.currentTimeMillis();
        if (mLastRenewFailed && whenMs <= now) {
            Log.i(TAG, "renewurl time is in the past after renew_failure — withdrawing consent");
            withdrawConsent();
            return;
        }
        mAwaitingRenewTimeDocument = false;
        // APPS0130–0150 serve now+120s on every poll after the first. That is
        // the same renewal, not a new deadline. Keep the first time and launch
        // before it (TS 103 770 §5.2.3.6.2).
        if (mRenewDeadlineMs <= 0) {
            mRenewDeadlineMs = whenMs;
            persistActiveCredentials();
            if (mType43RenewSucceeded || mType43Presented) {
                Log.i(TAG, "renewurl signalled time " + Instant.ofEpochMilli(whenMs)
                        + " — polling only, 4.3 already ran this install");
            } else if (!mType43AppRunning) {
                Log.i(TAG, "renewurl signalled time " + Instant.ofEpochMilli(whenMs)
                        + " — launching type 4.3");
                scheduleType43Launch(now);
            }
        } else {
            Log.i(TAG, "renewurl time " + Instant.ofEpochMilli(whenMs)
                    + " ignored; keeping first deadline "
                    + Instant.ofEpochMilli(mRenewDeadlineMs));
        }
        // §5.2.3.6.2: keep GET-ing until the 4.3 application exits.
        startRenewUrlPolling(EMPTY_RENEW_DEFAULT_WAIT_MS);
    }

    private void scheduleType43Launch(long whenMs) {
        mMainHandler.removeCallbacks(mLaunchType43Runnable);
        mMainHandler.removeCallbacks(mRenewDeadlineExpired);
        if (isConsentWithdrawn(mActiveListUid)) {
            return;
        }
        if (!hasType43Locator()) {
            Log.w(TAG, "Type 4.3 scheduled but no locator is captured yet");
            return;
        }
        long delayMs = Math.max(0, whenMs - System.currentTimeMillis());
        // Arm the deadline only after a real launch. A deferred (no-host)
        // launch must not share this turn with withdrawConsent().
        mMainHandler.postDelayed(mLaunchType43Runnable, delayMs);
    }

    private void runScheduledType43Launch() {
        if (!launchType43App()) {
            Log.w(TAG, "Type 4.3 launch did not start");
            return;
        }
        // Keep the signalled time as the renew_failure re-run window.
        // Do not arm a same-instant withdraw at launch.
        if (mType43AppRunning && mRenewDeadlineMs > System.currentTimeMillis()) {
            armRenewDeadline();
        }
    }

    private void armRenewDeadline() {
        mMainHandler.removeCallbacks(mRenewDeadlineExpired);
        if (mRenewDeadlineMs <= 0) {
            return;
        }
        long delayMs = Math.max(0, mRenewDeadlineMs - System.currentTimeMillis());
        mMainHandler.postDelayed(mRenewDeadlineExpired, delayMs);
    }

    /**
     * Restore / ISO time can schedule 4.3 before Setup has a host. Launch then,
     * instead of keeping {@link #mType43AppRunning} set with no browser.
     */
    private void maybeLaunchDeferredType43() {
        if (mType43AppRunning || mType43Presented || mType43RenewSucceeded
                || isConsentWithdrawn(mActiveListUid) || !hasType43Locator()) {
            return;
        }
        if (!mType43AwaitingHost && mRenewDeadlineMs <= 0) {
            return;
        }
        if (!hasLinkedAppCallback()) {
            return;
        }
        scheduleType43Launch(mRenewDeadlineMs > 0 ? mRenewDeadlineMs : System.currentTimeMillis());
    }

    private void onRenewDeadlineExpired() {
        if (isConsentWithdrawn(mActiveListUid)) {
            return;
        }
        if (mType43AppRunning || mType43AwaitingHost) {
            // Presenting, or still waiting for Setup/OrbProvider to attach.
            return;
        }
        withdrawConsent();
    }

    /** @return true if a 4.3 app is presenting, was launched, or is waiting for a host */
    private boolean launchType43App() {
        if (isConsentWithdrawn(mActiveListUid)) {
            return false;
        }
        if (mType43Presented || mType43RenewSucceeded) {
            Log.i(TAG, "Type 4.3 already shown this install — not launching again");
            return true;
        }
        if (!hasType43Locator()) {
            Log.i(TAG, "renewurl time received but no type 4.3 app is signalled");
            return false;
        }
        if (mType43AppRunning && hasLinkedAppCallback()) {
            return true;
        }
        if (!hasLinkedAppCallback()) {
            mType43AppRunning = false;
            mType43AwaitingHost = true;
            Log.w(TAG, "Type 4.3 deferred: no linked-app host");
            return true;
        }
        Log.i(TAG, "Launching type 4.3 renewal app");
        boolean html = isHtmlContentType(mType43ContentType) || isHtmlLinkedAppUrl(mType43Uri, false);
        boolean presented = launchLinkedApp(mType43Uri, html, LINKED_APP_SCHEME_4_3,
                "renewal-of-agreement", false);
        if (!presented) {
            mType43AppRunning = false;
            mType43AwaitingHost = true;
            Log.w(TAG, "Type 4.3 deferred: host did not present");
            return true;
        }
        mType43AwaitingHost = false;
        mType43AppRunning = true;
        mType43Presented = true;
        persistActiveCredentials();
        return true;
    }

    private void persistActiveCredentials() {
        if (mActiveListUid == null) {
            return;
        }
        SharedPreferences prefs = mDvbIView.getContext().getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(PREF_LA_ACTIVE_UID, mActiveListUid)
                .putString(PREF_LA41_QUERY_PREFIX + mActiveListUid, queryPairsToJson());
        persistLinkedAppCredentials(editor, mActiveListUid);
        editor.apply();
    }

    private void handleConsentOrRenewCompletion(String method, String paramsJson) {
        if (LA_CONSENT_UNCHANGED.equals(method)) {
            mType42AppRunning = false;
            Log.i(TAG, "Type 4.2 consent_unchanged — service list unchanged");
            return;
        }
        if (LA_CONSENT_WITHDRAWN.equals(method)) {
            mType42AppRunning = false;
            withdrawConsent();
            return;
        }
        if (LA_RENEW_SUCCESS.equals(method)) {
            mType43AppRunning = false;
            mType43AwaitingHost = false;
            mAwaitingRenewTimeDocument = false;
            mLastRenewFailed = false;
            mType43RenewSucceeded = true;
            mRenewDeadlineMs = -1L;
            mMainHandler.removeCallbacks(mLaunchType43Runnable);
            mMainHandler.removeCallbacks(mRenewDeadlineExpired);
            String previousRenewUrl = mActiveRenewUrl;
            applyRenewSuccessParams(paramsJson);
            if (!Objects.equals(previousRenewUrl, mActiveRenewUrl)) {
                mNextRenewPollAtWallMs = 0L;
                mRenewCacheUntilWallMs = 0L;
            }
            persistActiveCredentials();
            Log.i(TAG, "Type 4.3 renew_success — polling replacement renewurl");
            startRenewUrlPolling(remainingRenewPollDelayMs());
            refreshTunedMediaAfterQueryChange();
            return;
        }
        if (LA_RENEW_FAILURE.equals(method)) {
            mType43AppRunning = false;
            mType43AwaitingHost = false;
            mLastRenewFailed = true;
            persistActiveCredentials();
            long now = System.currentTimeMillis();
            if (mRenewDeadlineMs > 0 && now < mRenewDeadlineMs) {
                Log.i(TAG, "Type 4.3 renew_failure before deadline — may re-run later");
                mMainHandler.removeCallbacks(mRenewDeadlineExpired);
                mMainHandler.postDelayed(mRenewDeadlineExpired, mRenewDeadlineMs - now);
            } else {
                withdrawConsent();
            }
        }
    }

    /**
     * APPS0150: 4.3 may replace query pairs. Re-request the last native DASH
     * MPD so the next counted GET carries slkey=renew43. 4.3 itself calls
     * tuneOff, so there is often no current instance at renew_success.
     */
    private void refreshTunedMediaAfterQueryChange() {
        ServiceInstance instance = mServiceManager.getTunedInstance();
        if (instance != null && "dvb-dash".equals(instance.getDeliveryType())) {
            Log.i(TAG, "Refreshing tuned presentation after 4.3 query replacement");
            mServiceManagerCallback.onInstanceChanged(instance, instance);
            return;
        }
        String dashUri = mLastNativeDashUri;
        if (dashUri == null || dashUri.isEmpty()) {
            Log.i(TAG, "4.3 query replacement: no DASH instance to refresh");
            return;
        }
        String uri = applyInstallQuery(dashUri);
        Log.i(TAG, "Refreshing last DASH after 4.3 query replacement " + uri);
        if (mTvInputCallback != null) {
            mTvInputCallback.tuneOffBroadcast();
        }
        mDvbIView.tune(uri, mSubtitlesEnabled);
    }

    private void withdrawConsent() {
        String uid = mActiveListUid;
        if (uid == null) {
            return;
        }
        Log.i(TAG, "Consent withdrawn for list UID " + uid);
        mRenewEpoch++;
        stopAllRenewTimers();
        mType42AppRunning = false;
        mType43AppRunning = false;
        mType43AwaitingHost = false;
        mAwaitingRenewTimeDocument = false;
        mPendingRenewUrl = null;
        mPendingInstallationToken = null;
        mActiveRenewUrl = null;
        mActiveInstallationToken = null;
        mRenewDeadlineMs = -1L;
        mNextRenewPollAtWallMs = 0L;
        mRenewCacheUntilWallMs = 0L;
        mType43RenewSucceeded = false;
        SharedPreferences prefs = mDvbIView.getContext().getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        Set<String> withdrawn = new HashSet<>(prefs.getStringSet(PREF_LA_WITHDRAWN_UIDS, Collections.emptySet()));
        withdrawn.add(uid);
        Set<String> done = new HashSet<>(prefs.getStringSet(PREF_LA41_COMPLETED_UIDS, Collections.emptySet()));
        done.remove(uid);
        SharedPreferences.Editor editor = prefs.edit()
                .putStringSet(PREF_LA_WITHDRAWN_UIDS, withdrawn)
                .putStringSet(PREF_LA41_COMPLETED_UIDS, done);
        editor.remove(PREF_LA_RENEWURL_PREFIX + uid);
        editor.remove(PREF_LA_TOKEN_PREFIX + uid);
        editor.remove(PREF_LA_DEADLINE_PREFIX + uid);
        editor.remove(PREF_LA_NEXT_POLL_PREFIX + uid);
        editor.remove(PREF_LA_RENEW_FAILED_PREFIX + uid);
        editor.remove(PREF_LA_AWAITING_TIME_PREFIX + uid);
        editor.apply();
        mDbHandler.deleteServiceList(uid);
        mEpgManager.refreshServiceLists();
        for (DvbCallback handler : mDvbCallbacks) {
            handler.onServiceDeleted();
        }
        for (Callback cb : mCallbacks) {
            cb.onReturnToInstallationUi();
        }
    }

    private void handlePreplayCompletion(String method, String paramsJson) {
        if (LA_PREPLAY_SUCCESS.equals(method)) {
            mPreplayQueryPairs.clear();
            try {
                JSONObject params = new JSONObject(paramsJson != null ? paramsJson : "{}");
                parseQueryInto(params, mPreplayQueryPairs);
                persistType13Query(mPreplayAppIdentity, mPreplayAitUrl, mPreplayQueryPairs);
                JSONObject response = params.optJSONObject("application_response");
                if (response != null && !response.isNull("persistent")) {
                    ServiceInstance scoped = mPreplaySignalledOnInstance
                            ? mServiceManager.getTunedInstance() : null;
                    Object persistent = response.opt("persistent");
                    if (mPreplayAppIdentity != null && !mPreplayAppIdentity.isEmpty()) {
                        storeType13Persistent(type13PersistKey(mPreplayServiceUid, scoped,
                                mPreplayAppIdentity, null), persistent);
                    }
                    // tune() clears mPreplayAppIdentity. Keep an AIT-URL key so the
                    // next selection of this application still finds the record.
                    if (mPreplayAitUrl != null && !mPreplayAitUrl.isEmpty()) {
                        storeType13Persistent(type13PersistKey(mPreplayServiceUid, scoped,
                                null, mPreplayAitUrl), persistent);
                    }
                }
            } catch (JSONException e) {
                Log.w(TAG, "Type 1.3 preplay_success params were not JSON");
            }
            mPreplayState = PREPLAY_SUCCEEDED;
            cancelType13RetryRelaunch();
            Log.i(TAG, "Type 1.3 preplay_success — chaining to 1.1/1.2 / media");
            ServiceInstance instance = mServiceManager.getTunedInstance();
            if (instance != null) {
                mServiceManagerCallback.onInstanceChanged(instance, instance);
            }
            return;
        }
        if (LA_PREPLAY_FAILURE.equals(method)) {
            mPreplayState = PREPLAY_FAILED;
            Object retry = null;
            try {
                JSONObject params = new JSONObject(paramsJson != null ? paramsJson : "{}");
                JSONObject response = params.optJSONObject("application_response");
                if (response != null && !response.isNull("retry")) {
                    retry = response.opt("retry");
                }
            } catch (JSONException e) {
                Log.w(TAG, "Type 1.3 preplay_failure params were not JSON");
            }
            Service service = mServiceManager.getTunedService();
            ServiceInstance instance = mServiceManager.getTunedInstance();
            storeType13Retry(service, instance, mPreplayAitUrl, mPreplaySignalledOnInstance, retry);
            Log.i(TAG, "Type 1.3 preplay_failure retry=" + retry);
            applyType13RetryHold(service, instance, mPreplayAitUrl);
        }
    }

    private String type13PersistKey(String serviceUid, ServiceInstance instance,
            String appIdentity, String aitUrl) {
        StringBuilder key = new StringBuilder();
        if (serviceUid != null) {
            key.append(serviceUid);
        }
        if (instance != null && instance.getUri() != null) {
            key.append('|').append(instance.getUri());
        }
        if (appIdentity != null && !appIdentity.isEmpty()) {
            key.append("|app:").append(appIdentity);
        } else if (aitUrl != null) {
            key.append('|').append(aitUrl);
        }
        return key.toString();
    }

    private static String type13AppIdentity(String xml) {
        if (xml == null || xml.isEmpty()) {
            return null;
        }
        Matcher org = APPLICATION_ORG_ID.matcher(xml);
        Matcher app = APPLICATION_APP_ID.matcher(xml);
        if (!org.find() || !app.find()) {
            return null;
        }
        return org.group(1) + ":" + app.group(1);
    }

    private static String readType13PersistentValue(SharedPreferences prefs, String persistKey) {
        if (prefs == null || persistKey == null || persistKey.isEmpty()) {
            return null;
        }
        String value = prefs.getString(PREF_LA13_PERSIST_PREFIX + persistKey, null);
        return (value == null || value.isEmpty()) ? null : value;
    }

    private void storeType13Persistent(String persistKey, Object persistent) {
        if (persistKey == null || persistKey.isEmpty() || persistent == null) {
            return;
        }
        String value = String.valueOf(persistent).trim();
        if (value.isEmpty()) {
            return;
        }
        SharedPreferences prefs = mDvbIView.getContext()
                .getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_LA13_PERSIST_PREFIX + persistKey, value).apply();
        Log.i(TAG, "Type 1.3 persistent stored key=" + persistKey + " value=" + value);
    }

    private boolean isType13PersistentSuppressed(Service service, ServiceInstance instance,
            String aitUrl) {
        if (service == null) {
            return false;
        }
        SharedPreferences prefs = mDvbIView.getContext()
                .getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        ServiceInstance scoped = instanceHasLinkedApp(instance, LINKED_APP_SCHEME_1_3)
                ? instance : null;
        String uid = service.getUniqueIdentifier();
        String value = readType13PersistentValue(prefs, type13PersistKey(uid, scoped,
                mPreplayAppIdentity, aitUrl));
        if (value == null && mPreplayAppIdentity != null) {
            value = readType13PersistentValue(prefs, type13PersistKey(uid, scoped,
                    mPreplayAppIdentity, null));
        }
        if (value == null && aitUrl != null && !aitUrl.isEmpty()) {
            value = readType13PersistentValue(prefs, type13PersistKey(uid, scoped, null, aitUrl));
        }
        if (value == null && aitUrl != null && !aitUrl.isEmpty()) {
            value = readType13PersistentValue(prefs, type13PersistKey(uid, null, null, aitUrl));
        }
        if (value == null || value.isEmpty()) {
            return false;
        }
        if ("forever".equalsIgnoreCase(value)) {
            return true;
        }
        Instant until = parseType13PersistentInstant(value);
        if (until == null) {
            Log.w(TAG, "Type 1.3 persistent date was not ISO-8601: " + value);
            return false;
        }
        return Instant.now().isBefore(until);
    }

    /** Instant, or a calendar date (ISO 8601-1) meaning persist through that UTC day. */
    private static Instant parseType13PersistentInstant(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }
        if (!trimmed.endsWith("Z") && !trimmed.contains("+") && trimmed.length() >= 19
                && trimmed.charAt(10) == 'T') {
            try {
                return Instant.parse(trimmed + "Z");
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            LocalDate date = LocalDate.parse(trimmed);
            return date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * Drop leftover type 1.3 persist / retry / query after a list install.
     * Sequential official tests share one DUT: APPS0320 stores persistent=forever,
     * APPS0410/0440 leave a retry hold, and the next test then skips 1.3.
     */
    private void clearType13SessionState() {
        cancelType13RetryRelaunch();
        mPreplayState = PREPLAY_NONE;
        mPreplayServiceUid = null;
        mPreplayAitUrl = null;
        mPreplayAppIdentity = null;
        mPreplayQueryPairs.clear();
        if (mDvbIView == null || mDvbIView.getContext() == null) {
            return;
        }
        SharedPreferences prefs = mDvbIView.getContext()
                .getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        boolean removed = false;
        for (String key : prefs.getAll().keySet()) {
            if (key != null && (key.startsWith(PREF_LA13_PERSIST_PREFIX)
                    || key.startsWith(PREF_LA13_QUERY_PREFIX)
                    || key.startsWith(PREF_LA13_RETRY_UNTIL_PREFIX)
                    || key.startsWith(PREF_LA13_RETRY_SCOPE_PREFIX))) {
                editor.remove(key);
                removed = true;
            }
        }
        if (removed) {
            editor.apply();
            Log.i(TAG, "Cleared leftover type 1.3 persist/retry/query after service-list update");
        }
    }

    private void persistType13Query(String appIdentity, String aitUrl, List<QueryPair> pairs) {
        SharedPreferences prefs = mDvbIView.getContext()
                .getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String json = queryPairsToJson(pairs);
        if (appIdentity != null && !appIdentity.isEmpty()) {
            editor.putString(PREF_LA13_QUERY_PREFIX + "app:" + appIdentity, json);
        }
        if (aitUrl != null && !aitUrl.isEmpty()) {
            editor.putString(PREF_LA13_QUERY_PREFIX + aitUrl, json);
        }
        editor.apply();
    }

    private void loadPersistedType13Query(String aitUrl) {
        if (!mPreplayQueryPairs.isEmpty()) {
            return;
        }
        SharedPreferences prefs = mDvbIView.getContext()
                .getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        String json = null;
        if (mPreplayAppIdentity != null && !mPreplayAppIdentity.isEmpty()) {
            json = prefs.getString(PREF_LA13_QUERY_PREFIX + "app:" + mPreplayAppIdentity, null);
        }
        if ((json == null || json.isEmpty()) && aitUrl != null && !aitUrl.isEmpty()) {
            json = prefs.getString(PREF_LA13_QUERY_PREFIX + aitUrl, null);
        }
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String key = item.optString("key", null);
                if (key == null || key.isEmpty()) {
                    continue;
                }
                mPreplayQueryPairs.add(new QueryPair(key, item.optString("value", "")));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Stored type 1.3 query was not JSON");
        }
    }

    private String type13RetryKey(Service service, ServiceInstance instance, String aitUrl,
            boolean instanceScope) {
        if (instanceScope && instance != null && instance.getUri() != null) {
            return "inst:" + instance.getUri();
        }
        if (service != null) {
            return "svc:" + service.getUniqueIdentifier();
        }
        return aitUrl != null ? "ait:" + aitUrl : null;
    }

    private void storeType13Retry(Service service, ServiceInstance instance, String aitUrl,
            boolean instanceScope, Object retry) {
        String key = type13RetryKey(service, instance, aitUrl, instanceScope);
        if (key == null) {
            return;
        }
        SharedPreferences prefs = mDvbIView.getContext()
                .getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (retry == null || JSONObject.NULL.equals(retry)) {
            editor.remove(PREF_LA13_RETRY_UNTIL_PREFIX + key);
            editor.remove(PREF_LA13_RETRY_SCOPE_PREFIX + key);
            editor.apply();
            return;
        }
        long until;
        if ("infinite".equalsIgnoreCase(String.valueOf(retry))) {
            until = Long.MAX_VALUE;
        } else {
            try {
                until = System.currentTimeMillis() + (long) (Double.parseDouble(
                        String.valueOf(retry)) * 1000L);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Type 1.3 retry was not a number or infinite: " + retry);
                return;
            }
        }
        editor.putLong(PREF_LA13_RETRY_UNTIL_PREFIX + key, until);
        editor.putString(PREF_LA13_RETRY_SCOPE_PREFIX + key, instanceScope ? "instance" : "service");
        editor.apply();
    }

    private boolean isType13RetryHeld(Service service, ServiceInstance instance, String aitUrl) {
        String instKey = type13RetryKey(service, instance, aitUrl, true);
        String svcKey = type13RetryKey(service, instance, aitUrl, false);
        return isRetryKeyHeld(instKey) || isRetryKeyHeld(svcKey);
    }

    private boolean isRetryKeyHeld(String key) {
        if (key == null) {
            return false;
        }
        SharedPreferences prefs = mDvbIView.getContext()
                .getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        if (!prefs.contains(PREF_LA13_RETRY_UNTIL_PREFIX + key)) {
            return false;
        }
        long until = prefs.getLong(PREF_LA13_RETRY_UNTIL_PREFIX + key, 0L);
        if (until == Long.MAX_VALUE) {
            return true;
        }
        if (System.currentTimeMillis() >= until) {
            prefs.edit().remove(PREF_LA13_RETRY_UNTIL_PREFIX + key)
                    .remove(PREF_LA13_RETRY_SCOPE_PREFIX + key).apply();
            return false;
        }
        return true;
    }

    private void applyType13RetryHold(Service service, ServiceInstance instance, String aitUrl) {
        scheduleType13RetryRelaunch(service, instance, aitUrl);
        boolean instanceScope = instanceHasLinkedApp(instance, LINKED_APP_SCHEME_1_3);
        if (instanceScope) {
            mServiceManager.discardCurrentInstanceAndReselect("type 1.3 preplay_failure");
            return;
        }
        Log.i(TAG, "Type 1.3 service-level retry hold uid="
                + (service != null ? service.getUniqueIdentifier() : aitUrl));
        mDvbIView.tuneOff();
        mTvInputCallback.tuneOffBroadcast();
    }

    private void cancelType13RetryRelaunch() {
        mMainHandler.removeCallbacks(mType13RetryRunnable);
        mType13RetryUid = null;
    }

    /**
     * APPS0400 scenario (a): the service stays selected after service-level
     * preplay_failure. SharedPreferences expire, but nothing re-enters the
     * 1.3 gate unless we wake the instance after the hold.
     */
    private void scheduleType13RetryRelaunch(Service service, ServiceInstance instance,
            String aitUrl) {
        cancelType13RetryRelaunch();
        long remaining = remainingType13RetryMs(service, instance, aitUrl);
        if (remaining < 0L) {
            return;
        }
        if (remaining == 0L) {
            remaining = 1L; // spec: do not retry immediately, even for retry=0
        }
        mType13RetryUid = service != null ? service.getUniqueIdentifier() : null;
        Log.i(TAG, "Type 1.3 retry relaunch in " + remaining + "ms uid=" + mType13RetryUid);
        mMainHandler.postDelayed(mType13RetryRunnable, remaining);
    }

    /**
     * @return remaining milliseconds, or {@code -1} if there is no finite hold
     */
    private long remainingType13RetryMs(Service service, ServiceInstance instance, String aitUrl) {
        long inst = remainingRetryKeyMs(type13RetryKey(service, instance, aitUrl, true));
        long svc = remainingRetryKeyMs(type13RetryKey(service, instance, aitUrl, false));
        if (inst == Long.MAX_VALUE || svc == Long.MAX_VALUE) {
            return -1L;
        }
        if (inst < 0L && svc < 0L) {
            return -1L;
        }
        return Math.max(Math.max(inst, 0L), Math.max(svc, 0L));
    }

    /** @return remaining ms, {@link Long#MAX_VALUE} if infinite, or -1 if no hold */
    private long remainingRetryKeyMs(String key) {
        if (key == null || mDvbIView == null || mDvbIView.getContext() == null) {
            return -1L;
        }
        SharedPreferences prefs = mDvbIView.getContext()
                .getSharedPreferences("DvbIClient", Context.MODE_PRIVATE);
        if (!prefs.contains(PREF_LA13_RETRY_UNTIL_PREFIX + key)) {
            return -1L;
        }
        long until = prefs.getLong(PREF_LA13_RETRY_UNTIL_PREFIX + key, 0L);
        if (until == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, until - System.currentTimeMillis());
    }

    private synchronized void onType13RetryElapsed() {
        Service service = mServiceManager.getTunedService();
        if (service == null || mType13RetryUid == null
                || !mType13RetryUid.equals(service.getUniqueIdentifier())) {
            Log.i(TAG, "Type 1.3 retry elapsed; service no longer selected");
            return;
        }
        ServiceInstance instance = mServiceManager.getTunedInstance();
        if (isType13RetryHeld(service, instance, mPreplayAitUrl)) {
            Log.i(TAG, "Type 1.3 retry elapsed but hold still active; rescheduling");
            scheduleType13RetryRelaunch(service, instance, mPreplayAitUrl);
            return;
        }
        Log.i(TAG, "Type 1.3 retry elapsed; reselecting uid=" + mType13RetryUid);
        mPreplayState = PREPLAY_NONE;
        mType13RetryUid = null;
        mServiceManager.reselectAfterType13RetryHold();
    }

    private String queryPairsToJson(List<QueryPair> pairs) {
        JSONArray arr = new JSONArray();
        if (pairs == null) {
            return arr.toString();
        }
        for (QueryPair pair : pairs) {
            try {
                JSONObject item = new JSONObject();
                item.put("key", pair.key);
                item.put("value", pair.value);
                arr.put(item);
            } catch (JSONException ignored) {
            }
        }
        return arr.toString();
    }

    private static void parseQueryInto(JSONObject params, List<QueryPair> dest) {
        if (params == null || dest == null || !params.has("query") || params.isNull("query")) {
            return;
        }
        Object query = params.opt("query");
        if (query instanceof JSONArray) {
            JSONArray arr = (JSONArray) query;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String key = item.optString("key", null);
                if (key == null || key.isEmpty()) {
                    continue;
                }
                dest.add(new QueryPair(key, item.optString("value", "")));
            }
        } else if (query instanceof JSONObject) {
            JSONObject obj = (JSONObject) query;
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key == null || key.isEmpty()) {
                    continue;
                }
                dest.add(new QueryPair(key, obj.optString(key, "")));
            }
        }
    }

    /**
     * Atomic snapshot of a type 4.x JSON-RPC completion (method + params together).
     */
    public static final class LinkedAppCompletion {
        public final String method;
        public final String paramsJson;

        public LinkedAppCompletion(String method, String paramsJson) {
            this.method = method;
            this.paramsJson = paramsJson != null ? paramsJson : "{}";
        }
    }

    public interface LinkedAppJsonRpcListener {
        void onLinkedAppJsonRpc(String method, String paramsJson);
    }

    /**
     * TS 103 770 §5.2.3.7 / §5.2.3.8 completion from a type 4.x linked application.
     * Called from tvinput OrbProvider.OrbSessionCallback.onLinkedAppJsonRpc.
     * paramsJson may include installationtoken — do not log it.
     */
    public void handleLinkedAppJsonRpc(String method, String paramsJson) {
        Runnable deliver = () -> {
            Log.i(TAG, "linked-app JSON-RPC method=" + method);
            LinkedAppCompletion completion = new LinkedAppCompletion(method, paramsJson);
            mLastLinkedAppCompletion = completion;
            LinkedAppJsonRpcListener listener = mLinkedAppJsonRpcListener;
            if (listener != null) {
                listener.onLinkedAppJsonRpc(completion.method, completion.paramsJson);
            }
            handleConsentOrRenewCompletion(completion.method, completion.paramsJson);
            handlePreplayCompletion(completion.method, completion.paramsJson);
        };
        if (Looper.getMainLooper().isCurrentThread()) {
            deliver.run();
        } else {
            mMainHandler.post(deliver);
        }
    }

    public void setLinkedAppJsonRpcListener(LinkedAppJsonRpcListener listener) {
        mLinkedAppJsonRpcListener = listener;
    }

    public LinkedAppCompletion getLastLinkedAppCompletion() {
        return mLastLinkedAppCompletion;
    }

    public String getLastLinkedAppMethod() {
        LinkedAppCompletion last = mLastLinkedAppCompletion;
        return last != null ? last.method : null;
    }

    public String getLastLinkedAppParamsJson() {
        LinkedAppCompletion last = mLastLinkedAppCompletion;
        return last != null ? last.paramsJson : null;
    }

    public static class Callback {
        /** Override when this host can launch HTML / XML AIT linked applications. */
        protected boolean canLaunchLinkedApp() { return false; }
        protected void onProcessXmlAit(String xmlAit, String scheme) { }
        /** @return true if this host actually presented the application */
        protected boolean onLaunchHtmlLinkedApp(String url, String scheme) { return false; }
        protected void onApplicationHowRelatedHrefChanged(String href) { }
        /** Consent withdrawn or renewal failed after the deadline: return to Setup. */
        protected void onReturnToInstallationUi() { }
        protected void onDashStreamEvent(int listenId, String name, String status, String Id,
                                         double startTime, double duration, String data, String contentEncoding) { }
        protected void onTracksUpdated() { }
        protected void onTrackChanged(int type, String id) { }
    }
}
