package org.orbtv.dvbiclient;

import android.net.Uri;
import android.util.Log;

import org.orbtv.companionlibrary.utils.AsyncUtils;
import org.orbtv.dvbiclient.model.ContentGuide;
import org.orbtv.dvbiclient.model.Service;
import org.orbtv.dvbiclient.model.ServiceInstance;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class TunedServiceManager {
    private static final String TAG = TunedServiceManager.class.getSimpleName();
    private Callback mCallback;
    private AvailabilityPeriodRunnable mAvailabilityPeriodRunnable = null;
    private Thread mAvailabilityPeriodThread = null;
    private Service mTunedService = null;
    private ServiceInstance mTunedInstance = null;
    private List<EpgMetadata> mEpgMetadata;
    private final Object mLock = new Object();

    public TunedServiceManager(Callback callback) {
        mCallback = callback;
    }

    public boolean tune(Service service, int instanceIndex) {
        ServiceInstance instance = null;
        tuneOff();
        if (service == null) {
            Log.i(TAG, "Cannot tune to a null service.");
            return false;
        }
        if (instanceIndex < 0) {
            instance = getMaxPriorityInstance(service);
        }
        else if (instanceIndex < service.getInstances().size()) {
            instance = service.getInstances().get(instanceIndex);
        }
        else {
            Log.i(TAG, "No instance with index " + instanceIndex + " found in service.");
            return false;
        }
        Log.i(TAG, "---------- Tuning to service ----------\n" + service + "\n------------------------------------");
        synchronized (mLock) {
            mTunedService = service;
            mTunedInstance = instance;
        }
        Callback callback = new Callback() {
            @Override
            public void onInstanceChanged(ServiceInstance fromInstance, ServiceInstance toInstance) { }
            @Override
            public void onEpgEventsUpdated() {
                synchronized (mLock) {
                    mCallback.onEpgEventsUpdated();
                    if (mTunedService == service) {
                        mCallback.onInstanceChanged(null, mTunedInstance);
                        if (instanceIndex >= 0) {
                            if (mTunedInstance.getAvailabilityPeriod() != null && !mTunedInstance.getAvailabilityPeriod().getStartTimes().isEmpty()) {
                                startInstanceAvailabilityThread(true);
                            }
                        } else {
                            startInstanceAvailabilityThread(false);
                        }
                    }
                }
            }
        };

        if (!requestEpgMetadata(true, null, null, callback)) {
            callback.onEpgEventsUpdated();
        }
        return true;
    }

    public void tuneOff() {
        stopInstanceAvailabilityThread();
        synchronized (mLock) {
            mTunedService = null;
            mTunedInstance = null;
            mEpgMetadata = null;
        }
    }

    public synchronized void updateEpgMetadata(String xml) {
        try {
            mEpgMetadata = parseEpgFromXML(xml);
            mCallback.onEpgEventsUpdated();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<EpgMetadata> getEpgMetadata() {
        if (mEpgMetadata == null) {
            return null;
        }
        return new ArrayList<>(mEpgMetadata);
    }

    public synchronized Service getTunedService() { return mTunedService; }

    public synchronized ServiceInstance getTunedInstance() { return mTunedInstance; }

    public synchronized DvbIChannelAdapter getTunedChannel() {
        return new DvbIChannelAdapter.Builder()
                .setService(mTunedService)
                .setServiceInstance(mTunedInstance)
                .build();
    }

    private void startInstanceAvailabilityThread(boolean forceTunedInstance) {
        synchronized (mLock) {
            if (mAvailabilityPeriodRunnable == null) {
                mAvailabilityPeriodRunnable = new AvailabilityPeriodRunnable(forceTunedInstance);
                mAvailabilityPeriodThread = new Thread(mAvailabilityPeriodRunnable);
                mAvailabilityPeriodThread.start();
            }
        }
    }

    private void stopInstanceAvailabilityThread() {
        synchronized (mLock) {
            if (mAvailabilityPeriodRunnable != null) {
                mAvailabilityPeriodRunnable.stop();
                mAvailabilityPeriodThread = null;
                mAvailabilityPeriodRunnable = null;
            }
        }
    }

    private ServiceInstance getMaxPriorityInstance(Service service) {
        ServiceInstance maxInstance = null;
        List<ServiceInstance> instances = service.getInstances();
        for (int i = 0; i < instances.size(); i++) {
            ServiceInstance instance = instances.get(i);
            if (isInstanceAvailable(instance) && (maxInstance == null || maxInstance.getPriority() > instance.getPriority())) {
                maxInstance = instance;
            }
        }
        return maxInstance;
    }

    private boolean isInstanceAvailable(ServiceInstance instance) {
        boolean result = false;
        if (instance.getAvailabilityPeriod() == null || instance.getAvailabilityPeriod().getStartTimes().isEmpty()) {
            result = true;
        }
        else {
            long currentTime = System.currentTimeMillis() % 86400000; // TODO: may need to remove modulo operation
            List<String> startTimes = instance.getAvailabilityPeriod().getStartTimes();
            List<String> endTimes = instance.getAvailabilityPeriod().getEndTimes();
            for (int i = 0; i < startTimes.size(); ++i) {
                long start = convertToMillis(startTimes.get(i));
                long end = convertToMillis(endTimes.get(i));
                if (currentTime >= start && currentTime < end) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    private long convertToMillis(String timeString) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss'Z'");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = format.parse(timeString);
            return date.getTime();
        } catch (ParseException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public void requestEpgMetadata(boolean nowNext, String startTime, String endTime) {
        requestEpgMetadata(nowNext, startTime, endTime, mCallback);
    }

    //TODO: needs extra (optional) request arguments, also polling should be considered if @dynamic service is true
    //TODO: NowNext can be a 'window'
    private synchronized boolean requestEpgMetadata(boolean nowNext, String startTime, String endTime, TunedServiceManager.Callback callback) {
        boolean result = false;
        ContentGuide guide = mTunedService.getContentGuide();
        if (guide != null && guide.getScheduleInfoEndpointURI() != null && !guide.getScheduleInfoEndpointURI().isEmpty()) {
            String serviceRef = mTunedService.getUniqueIdentifier();
            if (mTunedService.getContentGuideServiceRef() != null) {
                serviceRef = mTunedService.getContentGuideServiceRef();
            }
            Uri baseUri = Uri.parse(guide.getScheduleInfoEndpointURI());
            Uri.Builder builder = baseUri.buildUpon();
            if(!serviceRef.isEmpty()) {
                builder.appendQueryParameter("sid", serviceRef);
                if(nowNext) {
                    builder.appendQueryParameter("now_next", "true");
//              else if(nowNext.equals("window")) {
                } else {
                    builder.appendQueryParameter("start_time", startTime);
                    builder.appendQueryParameter("end_time", endTime);
                }
            }
            new EpgMetadataTask(callback).execute(builder.build().toString());
            result = true;

//            if (pid != null && !pid.isEmpty()) {
//                builder.clearQuery();
//                builder.appendQueryParameter("pid", pid);
//                new EpgMetadataTask().execute(builder.build().toString());
//            }
        }
        return result;
    }

    private List<EpgMetadata> parseEpgFromXML(String xml) throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();
        xpp.setInput(new StringReader(xml));
        ArrayList<EpgMetadata> epgMetadata = new ArrayList<>();
        int eventType = xpp.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                String tagName = xpp.getName();
                switch (tagName) {
                    case "Title":
                        if ("main".equals(xpp.getAttributeValue(null, "type"))) {
                            String title = xpp.nextText();
                            System.out.println("Title: " + title);
                        }
                        break;
                    case "Synopsis":
                        if ("medium".equals(xpp.getAttributeValue(null, "length"))) {
                            String synopsis = xpp.nextText();
                            System.out.println("Synopsis: " + synopsis);
                        }
                        break;
                    case "ParentalGuidance":
                        eventType = xpp.next();
                        while (!(eventType == XmlPullParser.END_TAG && "ParentalGuidance".equals(xpp.getName()))) {
                            if (eventType == XmlPullParser.START_TAG && ("MinimumAge".equals(xpp.getName()) || "mpeg7:MinimumAge".equals(xpp.getName()))) {
                                String minAge = xpp.nextText();
                                System.out.println("Minimum Age: " + minAge);
                                epgMetadata.add(new EpgMetadata.Builder()
                                        .setParentalRating(Integer.parseInt(minAge))
                                        .build());
                            } else if (eventType == XmlPullParser.START_TAG && ("ParentalRating".equals(xpp.getName()) || "mpeg7:ParentalRating".equals(xpp.getName()))) {
                                String parentalRating = xpp.getAttributeValue(null, "href");
                                System.out.println("Parental Rating: " + parentalRating);
                            }
                            eventType = xpp.next();
                        }
                        break;
                    case "PublishedStartTime":
                        String startTime = xpp.nextText();
                        System.out.println("Start Time: " + startTime);
                        break;
                    case "PublishedDuration":
                        String duration = xpp.nextText();
                        System.out.println("Duration: " + duration);
                        break;
                }
            }
            eventType = xpp.next();
        }
        return epgMetadata;
    }

    private class AvailabilityPeriodRunnable implements Runnable {
        private volatile boolean mIsRunning = false;
        private ServiceInstance mTargetInstance;

        public AvailabilityPeriodRunnable(boolean forceTunedInstance) {
            if (forceTunedInstance) {
                synchronized (mLock) {
                    mTargetInstance = mTunedInstance;
                }
            }
        }

        public void stop() {
            mIsRunning = false;
            Log.i(TAG, "Stopping AvailabilityPeriodRunnable...");
        }

        @Override
        public void run() {
            ServiceInstance tunedInstance = null;
            mIsRunning = true;
            while (mIsRunning) {
                synchronized (mLock) {
                    tunedInstance = mTunedInstance;
                    if (mTargetInstance == null) {
                        ServiceInstance priorityInstance = getMaxPriorityInstance(mTunedService);
                        if (priorityInstance != tunedInstance) {
                            mTunedInstance = priorityInstance;
                            if (mCallback != null) {
                                mCallback.onInstanceChanged(tunedInstance, priorityInstance);
                            }
                        }
                    }
                    else if ((tunedInstance == null) == isInstanceAvailable(mTargetInstance)){
                        if (tunedInstance != null) {
                            mTunedInstance = null;
                        }
                        else {
                            mTunedInstance = mTargetInstance;
                        }
                        if (mCallback != null) {
                            mCallback.onInstanceChanged(tunedInstance, mTunedInstance);
                        }
                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            Log.i(TAG, "Stopped AvailabilityPeriodRunnable.");
        }
    }

    private class EpgMetadataTask extends AsyncUtils<List<EpgMetadata>, String> {
        private Service mService;
        private TunedServiceManager.Callback mEventsUpdatedCallback;

        public EpgMetadataTask(TunedServiceManager.Callback callback) {
            synchronized (mLock) {
                mEventsUpdatedCallback = callback;
                mService = mTunedService;
            }
        }

        @Override
        protected List<EpgMetadata> doInBackground(String... uris) {
            String uri;
            if (uris.length > 0) {
                uri = uris[0];
                try {
                    Log.d(TAG,"Request EPG Metadata from: " + uri);
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

                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                    reader.close();

                    Log.d(TAG, responseBuilder.toString());
                    return parseEpgFromXML(responseBuilder.toString());
                }
                catch(IOException e) {
                    Log.e(TAG, "Error sending request", e);
                }
                catch(Exception e) {
                    Log.e(TAG, "Error configuring SSL context", e);
                }
            }
            return null;
        }

        @Override
        public void onPostExecute(List<EpgMetadata> epgMetadata) {
            synchronized (mLock) {
                if (mService == mTunedService) {
                    mEpgMetadata = epgMetadata;
                    if (mEventsUpdatedCallback != null) {
                        mEventsUpdatedCallback.onEpgEventsUpdated();
                    }
                }
            }
        }
    }

    public static class EpgMetadata {
        private Integer mParentalRating;

        private EpgMetadata() { }
        public Integer getParentalRating() { return mParentalRating; }

        public static class Builder {
            private EpgMetadata mInstance;
            public Builder() {
                mInstance = new EpgMetadata();
            }
            public EpgMetadata.Builder setParentalRating(Integer value) {
                mInstance.mParentalRating = value;
                return this;
            }
            public EpgMetadata build() {
                EpgMetadata instance = new EpgMetadata();
                instance.mParentalRating = mInstance.mParentalRating;
                return instance;
            }
        }
    }

    public interface Callback {
        void onInstanceChanged(ServiceInstance fromInstance, ServiceInstance toInstance);
        void onEpgEventsUpdated();
    }
}
