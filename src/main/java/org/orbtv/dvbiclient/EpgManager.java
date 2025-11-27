package org.orbtv.dvbiclient;

import static org.orbtv.dvbiclient.Utils.getDurationFromString;
import static org.orbtv.dvbiclient.Utils.getSecondsFromDate;

import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;
import org.orbtv.companionlibrary.model.Program;
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
    private static final long SECONDS_OF_DAY = 86400;
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

    public void requestUpdateFromEventStream(Service service, JSONObject data) {
        try {
            Log.i(TAG, "requesting epg update for service with UID " + service.getUniqueIdentifier() + " from ContentGuideSourceList...");
            XmlNode baseNode = XmlNode.parse(data.getString("messageData"));
            if (baseNode != null) {
                findScheduleInfoEndpoints(service, baseNode);
                findProgramInfo(service, baseNode,
                        data.has("duration") ? data.getInt("duration") : SECONDS_OF_DAY);
                //TODO find ProgramInfoEndpoint too in events (if any)
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

    private void findScheduleInfoEndpoints(Service service, XmlNode baseNode) {
        List<XmlNode> contentGuideSources = baseNode.getDescendantsByName("ContentGuideSource");
        for (XmlNode cgs : contentGuideSources) {
            if (service.getContentGuide() != null && service.getContentGuide().getCGSID().equals(cgs.getAttribute("CGSID"))) {
                List<XmlNode> uris = cgs.getDescendantsByName("URI");
                Uri programInfoEndpoint = null;
                Uri scheduleInfoEndpoint = null;
                for (XmlNode uri : uris) {
                    if ("ProgramInfoEndpoint".equals(uri.getParentNode().getName())) {
                        programInfoEndpoint = Uri.parse(uri.getInnerText());
                    }
                    if ("ScheduleInfoEndpoint".equals(uri.getParentNode().getName())) {
                        scheduleInfoEndpoint = Uri.parse(uri.getInnerText());
                    }
                }
                new EpgMetadataTask(new EpgTaskInfo(service.getUniqueIdentifier(), scheduleInfoEndpoint, programInfoEndpoint)).execute();
            }
        }
    }

    private void findProgramInfo(Service service, XmlNode baseNode, long duration) {
        XmlNode node = baseNode.getDescendantByName("InstanceDescription");
        if (node != null) {
            Programme.Builder builder = new Programme.Builder();
            findDescriptions(node, builder);
            findParentalGuidance(node, builder);
            findTitle(node, builder);
            findStartEndTimes(node, builder, System.currentTimeMillis() / 1000, duration);
            findProgramId(baseNode, builder, "crid://" + service.getUniqueIdentifier());
            mDbHandler.updateProgrammesForService(service.getUniqueIdentifier(), Arrays.asList(builder.build()));
            synchronized (mLock) {
                for (Callback callback : mCallbacks) {
                    callback.onEpgUpdated(Arrays.asList(service.getUniqueIdentifier()));
                }
            }
        }
    }

    private void findProgramId(XmlNode node, Programme.Builder builder, String fallback) {
        node = node.getDescendantByName("Program");
        String programId = fallback;
        if (node != null && node.getAttribute("crid") != null) {
            programId = node.getAttribute("crid");
        }
        builder.setProgramId(programId);
    }

    private void findTitle(XmlNode node, Programme.Builder builder) {
        node = node.getDescendantByName("Title");
        if (node != null) {
            builder.setTitle(node.getInnerText());
        }
    }

    private void findDescriptions(XmlNode node, Programme.Builder builder) {
        List<XmlNode> descriptions = node.getDescendantsByName("Synopsis");
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
    }

    private void findParentalGuidance(XmlNode node, Programme.Builder builder) {
        final List<String> minimumAgeNames = Arrays.asList("MinimumAge", "mpeg7:MinimumAge");
        final List<String> parentalRatingNames = Arrays.asList("ParentalRating", "mpeg7:ParentalRating");
        String minAge = "0";
        String ratingScheme = null;
        String explanatoryText = null;
        List<XmlNode> parentalGuidanceNodes = node.getDescendantsByName("ParentalGuidance");
        if (!parentalGuidanceNodes.isEmpty()) {
            XmlNode n = parentalGuidanceNodes.get(0).getFirstChild();
            if (n != null && minimumAgeNames.contains(n.getName())) {
                minAge = n.getInnerText();
                if (parentalGuidanceNodes.size() > 1) {
                    n = parentalGuidanceNodes.get(1).getFirstChild();
                    if (n != null && parentalRatingNames.contains(n.getName())) {
                        ratingScheme = n.getAttribute("href");
                        n = n.getNextSibling();
                        if (n != null) {
                            explanatoryText = n.getInnerText();
                        }
                    }
                }
            }
        }
        builder.setMinimumAge(Integer.parseInt(minAge))
                .setParentalRatingScheme(ratingScheme)
                .setParentalRatingDescription(explanatoryText);
    }

    private void findStartEndTimes(XmlNode node, Programme.Builder builder, long fallbackStart, long fallbackDuration) {
        long startTime = fallbackStart;
        long endTime = startTime + fallbackDuration;
        try {
            startTime = getSecondsFromDate(node.getDescendantByName("PublishedStartTime").getInnerText());
        } catch (Exception e) {
        }
        try {
            Duration duration = getDurationFromString(node.getDescendantByName("PublishedDuration").getInnerText());
            endTime = startTime + duration.getSeconds();
        } catch (Exception e) {
        }
        builder.setStartTime(startTime)
                .setEndTime(endTime);
    }

    private void findOnDemandProgram(XmlNode programDescriptionNode, XmlNode scheduleNode, String programId, Programme.Builder builder) {
        long startTimeFromScheduleNode = 0;
        long startTimeFromProgramLocationNode = 0;
        List <XmlNode> onDemandMetadata = programDescriptionNode.getDescendantsByName("OnDemandProgram");
        for (XmlNode OnDemandnode : onDemandMetadata) {
            XmlNode programURLNode = OnDemandnode.getDescendantByName("ProgramURL");
            XmlNode programNode = OnDemandnode.getDescendantByName("Program");
            if (programURLNode != null && programNode != null && programId.equals(programNode.getAttribute("crid"))) {
                if (programURLNode.getAttribute("contentType").equals("application/vnd.dvb.ait+xml")) {
                    builder.setOnDemandURL(programURLNode.getInnerText());
                }
                //TODO: handle Start(end)OfAvailability and duration time for ondemand hbbtv apps
                break;
            }
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
                                Uri baseUriSchedule = Uri.parse(guide.getScheduleInfoEndpointURI());
                                Uri.Builder builder = baseUriSchedule.buildUpon();
                                builder.appendQueryParameter("sid", uid);

                                Uri baseUriProgram = null;
                                String ProgramInfoEndpointURI =  guide.getProgramInfoEndpointURI();
                                if (ProgramInfoEndpointURI != null && !ProgramInfoEndpointURI.isEmpty()) {
                                    baseUriProgram = Uri.parse(ProgramInfoEndpointURI);
                                }

                                mScheduleInfos.add(new EpgTaskInfo(uid, builder.build(), baseUriProgram));
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
                        synchronized (info) {
                            if (info.nextUpdate != null && info.nextUpdate <= currentTimestamp) {
                                Log.i(TAG, "Updating EPG for Service " + info.getServiceUID());
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

    private class EpgMetadataTask extends AsyncUtils<ArrayList<Programme>, Void> {
        private static final long EPG_INTERVAL = 10800;
        private final EpgTaskInfo mTaskInfo;

        public EpgMetadataTask(EpgTaskInfo taskInfo) {
            mTaskInfo = taskInfo;
        }

        private XmlNode fetchDataFromUri(URL url) throws IOException {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            boolean useHttps = false;
            try {
//                if (useHttps) {
//                    // Configure the SSL context for HTTPS connections
//                    SSLContext sslContext = SSLContext.getInstance("TLS");
//                    sslContext.init(null, new TrustManager[]{new TrustAllManager()}, null);
//                    ((HttpsURLConnection) connection).setSSLSocketFactory(sslContext.getSocketFactory());
//                    ((HttpsURLConnection) connection).setHostnameVerifier((hostname, session) -> true);
//                }
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Content-Type", "application/xml");
                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                reader.close();

                //Log.d(TAG, "fetchDataFromUri response: " + responseBuilder.toString());
                return XmlNode.parse(responseBuilder.toString());
            } catch (java.io.FileNotFoundException e) {
                    // FileNotFoundException is expected when EPG URLs are not available
                    Log.d(TAG, "EPG data not available: " + url.toString());
            } catch (Exception e) {
                    Log.e(TAG, "error", e);
            } finally {
                connection.disconnect();
            }
            return null;
        }

        @Override
        protected ArrayList<Programme> doInBackground(Void... ignore) {
            synchronized (mTaskInfo) {
                try {
                    Log.d(TAG, "Request EPG Metadata for: " + mTaskInfo.getServiceUID());
                    Uri.Builder uBuilder = mTaskInfo.getmEndPointUri().buildUpon();
                    if (mTaskInfo.isNowNext) {
                        uBuilder.appendQueryParameter("now_next", "true");
                    } else {
                        long currentTimestamp = System.currentTimeMillis() / 1000;
                        long startTime = (currentTimestamp - EPG_INTERVAL) - currentTimestamp % EPG_INTERVAL;
                        long endTime = (currentTimestamp + EPG_INTERVAL * 7) - currentTimestamp % EPG_INTERVAL;
                        uBuilder.appendQueryParameter("start", String.valueOf(startTime));
                        uBuilder.appendQueryParameter("end", String.valueOf(endTime));
                    }

                    XmlNode epgMetadata = fetchDataFromUri(new URL(uBuilder.build().toString()));
                    XmlNode epgProgramInfoMetadata = null;

                    if (!mTaskInfo.isNowNext && (epgMetadata == null || epgMetadata.getChildrenCount() == 0)) {
                        mTaskInfo.isNowNext = true;
                        return doInBackground();
                    }
                    mTaskInfo.isNowNext = false;

                    if (mTaskInfo.nextUpdate == null) {
                        // TODO: update request time
                        // mTimestamp.set(connection.getHeaderField("Cache-Control"));
                        mTaskInfo.nextUpdate = System.currentTimeMillis() / 1000 + 300;
                    }

                    ArrayList<Programme> programmes = new ArrayList<>();
                    if (epgMetadata != null) {
                        List<XmlNode> scheduleEvents = epgMetadata.getDescendantsByName("ScheduleEvent");
                        List<XmlNode> programmesInfo = epgMetadata.getDescendantsByName("ProgramInformation");
                        Uri auxEndPointUri = mTaskInfo.getmAuxEndPointUri();
                        mUpdatedServices.add(mTaskInfo.getServiceUID());

                        for (XmlNode info : programmesInfo) {
                            Programme.Builder pBuilder = new Programme.Builder();
                            String programId = info.getAttribute("programId");
                            if (programId != null) {
                                for (XmlNode event : scheduleEvents) {
                                    XmlNode programNode = event.getDescendantByName("Program");
                                    if (programNode != null && programId.equals(programNode.getAttribute("crid"))) {
                                        findStartEndTimes(event, pBuilder, System.currentTimeMillis() / 1000, SECONDS_OF_DAY);
                                        if (auxEndPointUri != null && !auxEndPointUri.toString().isEmpty()) {
                                            Uri.Builder auxBuilder = auxEndPointUri.buildUpon();
                                            auxBuilder.appendQueryParameter("pid", programId);
                                            epgProgramInfoMetadata = fetchDataFromUri(new URL(auxBuilder.build().toString()));
                                            if (epgProgramInfoMetadata != null) {
                                                findOnDemandProgram(epgProgramInfoMetadata, event, programId, pBuilder);
                                            }
                                        }
                                        break;
                                    }
                                }
                            }
                            findDescriptions(info, pBuilder);
                            findParentalGuidance(info, pBuilder);
                            findTitle(info, pBuilder);

                            programmes.add(pBuilder
                                    .setProgramId(programId)
                                    .build());
                        }
                    }
                    return programmes;
                } catch (IOException e) {
                    Log.e(TAG, "IOException", e);
                } catch (Exception e) {
                    Log.e(TAG, "Parsing error", e);
                }
            }
            return null;
        }

        @Override
        public void onPostExecute(ArrayList<Programme> programmes) {
            synchronized (mLock) {
                if (programmes != null) {
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
        public Uri mAuxEndPointUri;
        private String mServiceUID;
        public EpgTaskInfo(String serviceUID, Uri endPointUri, Uri auxEndPointUri) {
            mServiceUID = serviceUID;
            mEndPointUri = endPointUri;
            mAuxEndPointUri = auxEndPointUri;
        }
        public String getServiceUID() { return mServiceUID; }
        public Uri getmEndPointUri() { return mEndPointUri; }
        public Uri getmAuxEndPointUri() { return mAuxEndPointUri; }
    }

    public interface Callback {
        void onEpgUpdated(List<String> serviceUIDs);
    }
}
