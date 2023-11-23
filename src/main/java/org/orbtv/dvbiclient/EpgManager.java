package org.orbtv.dvbiclient;

import static org.orbtv.dvbiclient.Utils.getDurationFromString;
import static org.orbtv.dvbiclient.Utils.getSecondsFromDate;

import android.net.Uri;
import android.util.Log;

import org.orbtv.companionlibrary.utils.AsyncUtils;
import org.orbtv.dvbiclient.model.ContentGuide;
import org.orbtv.dvbiclient.model.Programme;
import org.orbtv.dvbiclient.model.Service;
import org.orbtv.dvbiclient.model.ServiceList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EpgManager {
    private static final String TAG = EpgManager.class.getSimpleName();
    private final DatabaseHandler mDbHandler;
    private final Object mLock = new Object();
    private final EpgRunnable mEpgRunnable;
    private final Thread mEpgThread;
    private final ArrayList<Callback> mCallbacks = new ArrayList<>();
    private final ArrayList<EpgMetadataTask> mEpgMetadataTasks = new ArrayList<>();
    private ArrayList<String> mUpdatedServices = new ArrayList<>();

    public EpgManager(DatabaseHandler dbHandler) {
        mDbHandler = dbHandler;
        mEpgRunnable = new EpgRunnable();
        mEpgThread = new Thread(mEpgRunnable);
        mEpgThread.start();
    }

    public void refreshServiceLists() {
        mEpgRunnable.refreshServiceLists();
    }

    public void requestUpdateFromContentGuideSourceXML(Service service, String xml) {
        try {
            Log.i(TAG, "requesting epg update for service with UID " + service.getUniqueIdentifier() + " from ContentGuideSourceList...");
            XmlNode contentGuideSourceList = XmlNode.parse(xml);
            List<XmlNode> contentGuideSources = contentGuideSourceList.getDescendantsByName("ContentGuideSource");
            for (XmlNode cgs : contentGuideSources) {
                if (service.getContentGuide() != null && service.getContentGuide().getCGSID().equals(cgs.getAttribute("CGSID"))) {
                    List<XmlNode> uris = cgs.getDescendantsByName("URI");
                    for (XmlNode uri : uris) {
                        if ("ScheduleInfoEndpoint".equals(uri.getParentNode().getName())) {
                            new EpgMetadataTask(new EpgTaskInfo(service.getUniqueIdentifier(), Uri.parse(uri.getInnerText()))).execute();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void registerCallback(Callback handler) {
        synchronized (mLock) {
            if (!mCallbacks.contains(handler)) {
                mCallbacks.add(handler);
            }
        }
    }

    public void unregisterCallback(Callback handler) {
        synchronized (mLock) {
            mCallbacks.remove(handler);
        }
    }

    private class EpgRunnable implements Runnable {
        private final ArrayList<EpgTaskInfo> mScheduleInfos = new ArrayList<>();

        public EpgRunnable() {
            refreshServiceLists();
        }

        public void refreshServiceLists() {
            List<ServiceList> serviceLists = mDbHandler.getServiceLists();
            synchronized (mLock) {
                mScheduleInfos.clear();
                long currentTime = System.currentTimeMillis() / 1000;
                for (ServiceList list : serviceLists) {
                    for (Service service : list.getServices()) {
                        ContentGuide guide = service.getContentGuide();
                        if (guide != null && guide.getScheduleInfoEndpointURI() != null && !guide.getScheduleInfoEndpointURI().isEmpty()) {
                            String uid = service.getUniqueIdentifier();
                            String serviceRef = service.getContentGuideServiceRef();
                            if (serviceRef == null) {
                                serviceRef = uid;
                            }
                            if (!serviceRef.isEmpty()) {
                                Uri baseUri = Uri.parse(guide.getScheduleInfoEndpointURI());
                                Uri.Builder builder = baseUri.buildUpon();
                                builder.appendQueryParameter("sid", uid);
                                mScheduleInfos.add(new EpgTaskInfo(uid, builder.build()));
                                mScheduleInfos.get(mScheduleInfos.size() - 1).nextUpdate = currentTime;
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void run() {
            while (true) {
                synchronized (mLock) {
                    long currentTimestamp = System.currentTimeMillis() / 1000;
                    for (EpgTaskInfo info : mScheduleInfos) {
                        if (info.nextUpdate != null && info.nextUpdate <= currentTimestamp) {
                            Log.i(TAG, "Updating EPG for Service " + info.getServiceUID());
                            synchronized (info) {
                                info.nextUpdate = null;
                                EpgMetadataTask task = new EpgMetadataTask(info);
                                mEpgMetadataTasks.add(task);
                                task.execute();
                            }
                        }
                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class EpgMetadataTask extends AsyncUtils<XmlNode, Void> {
        private static final long EPG_INTERVAL = 10800;
        private final EpgTaskInfo mTaskInfo;

        public EpgMetadataTask(EpgTaskInfo taskInfo) {
            mTaskInfo = taskInfo;
        }

        @Override
        protected XmlNode doInBackground(Void... ignore) {
            synchronized (mTaskInfo) {
                try {
                    Log.d(TAG, "Request EPG Metadata for: " + mTaskInfo.getServiceUID());
                    Uri.Builder builder = mTaskInfo.getmEndPointUri().buildUpon();
                    if (mTaskInfo.isNowNext) {
                        builder.appendQueryParameter("now_next", "true");
                    } else {
                        long currentTimestamp = System.currentTimeMillis() / 1000;
                        long startTime = (currentTimestamp - EPG_INTERVAL) - currentTimestamp % EPG_INTERVAL;
                        long endTime = (currentTimestamp + EPG_INTERVAL * 7) - currentTimestamp % EPG_INTERVAL;
                        builder.appendQueryParameter("start", String.valueOf(startTime));
                        builder.appendQueryParameter("end", String.valueOf(endTime));
                    }
                    URL url = new URL(builder.build().toString());
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
                    Log.i(TAG, mTaskInfo.getServiceUID() + " response Code: " + responseCode);
                    Log.i(TAG, "Cache control: " + connection.getHeaderField("Cache-Control"));

                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                    reader.close();

                    Log.d(TAG, responseBuilder.toString());

                    if (!mTaskInfo.isNowNext && responseBuilder.length() <= 0) {
                        mTaskInfo.isNowNext = true;
                        return doInBackground();
                    }
                    mTaskInfo.isNowNext = false;

                    if (mTaskInfo.nextUpdate != null) {
                        // TODO: update request time
                        // mTimestamp.set(connection.getHeaderField("Cache-Control"));
                        mTaskInfo.nextUpdate = System.currentTimeMillis() / 1000 + 300;
                    }
                    return XmlNode.parse(responseBuilder.toString());
                } catch (IOException e) {
                    Log.e(TAG, "Error sending request", e);
                } catch (Exception e) {
                    Log.e(TAG, "Error configuring SSL context", e);
                }
            }
            return null;
        }

        @Override
        public void onPostExecute(XmlNode epgMetadata) {
            synchronized (mLock) {
                if (epgMetadata != null) {
                    final List<String> minimumAgeNames = Arrays.asList("MinimumAge", "mpeg7:MinimumAge");
                    final List<String> parentalRatingNames = Arrays.asList("ParentalRating", "mpeg7:ParentalRating");
                    List<XmlNode> scheduleEvents = epgMetadata.getDescendantsByName("ScheduleEvent");
                    List<XmlNode> programmesInfo = epgMetadata.getDescendantsByName("ProgramInformation");
                    ArrayList<Programme> programmes = new ArrayList<>();
                    mUpdatedServices.add(mTaskInfo.getServiceUID());
                    for (XmlNode info : programmesInfo) {
                        try {
                            Programme.Builder builder = new Programme.Builder();
                            String programId = info.getAttribute("programId");
                            if (programId != null) {
                                for (XmlNode event : scheduleEvents) {
                                    XmlNode programNode = event.getDescendantByName("Program");
                                    if (programNode != null && programId.equals(programNode.getAttribute("crid"))) {
                                        try {
                                            long startTime = getSecondsFromDate(event.getDescendantByName("PublishedStartTime").getInnerText());
                                            Duration duration = getDurationFromString(event.getDescendantByName("PublishedDuration").getInnerText());
                                            builder.setStartTime(startTime);
                                            builder.setEndTime(startTime + duration.getSeconds());
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        break;
                                    }
                                }
                            }
                            List<XmlNode> descriptions = info.getDescendantsByName("Synopsis");
                            for (XmlNode desc : descriptions) {
                                switch (desc.getAttribute("length")) {
                                    case "short":
                                        builder.setShortDescription(desc.getInnerText());
                                        break;
                                    case "long":
                                        builder.setLongDescription(desc.getInnerText());
                                        break;
                                    default:
                                        builder.setMediumDescription(desc.getInnerText());
                                        break;
                                }
                            }
                            String minAge = "0";
                            String ratingScheme = null;
                            String explanatoryText = null;
                            List<XmlNode> parentalGuidanceNodes = info.getDescendantsByName("ParentalGuidance");
                            if (!parentalGuidanceNodes.isEmpty()) {
                                XmlNode node = parentalGuidanceNodes.get(0).getFirstChild();
                                if (node != null && minimumAgeNames.contains(node.getName())) {
                                    minAge = node.getInnerText();
                                    if (parentalGuidanceNodes.size() > 1) {
                                        node = parentalGuidanceNodes.get(1).getFirstChild();
                                        if (node != null && parentalRatingNames.contains(node.getName())) {
                                            ratingScheme = node.getAttribute("href");
                                            node = node.getNextSibling();
                                            if (node != null) {
                                                explanatoryText = node.getInnerText();
                                            }
                                        }
                                    }
                                }
                            }

                            programmes.add(builder
                                    .setTitle(info.getDescendantByName("Title").getInnerText())
                                    .setProgramId(programId)
                                    .setMinimumAge(Integer.parseInt(minAge))
                                    .setParentalRatingScheme(ratingScheme)
                                    .setParentalRatingDescription(explanatoryText)
                                    .build());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    mDbHandler.updateProgrammesForService(mTaskInfo.getServiceUID(), programmes);
                }
                mEpgMetadataTasks.remove(this);
                if (mEpgMetadataTasks.isEmpty() && !mUpdatedServices.isEmpty()) {
                    List<String> updatedServices = new ArrayList<>(mUpdatedServices);
                    for (Callback callback : mCallbacks) {
                        callback.onEpgUpdated(updatedServices);
                    }
                    mUpdatedServices.clear();
                }
            }
        }
    }

    private static class EpgTaskInfo {
        public Long nextUpdate = null;
        public boolean isNowNext = false;
        public Uri mEndPointUri;
        private String mServiceUID;
        public EpgTaskInfo(String serviceUID, Uri endPointUri) {
            mServiceUID = serviceUID;
            mEndPointUri = endPointUri;
        }
        public String getServiceUID() { return mServiceUID; }
        public Uri getmEndPointUri() { return mEndPointUri; }
    }

    public interface Callback {
        void onEpgUpdated(List<String> serviceUIDs);
    }
}
