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
    private static final long EPG_INTERVAL = 10800;
    private static final int CGS_HTTP_TIMEOUT_MS = 5000;
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

    /**
     * Fetch this service's CGS schedule now and replace stored programmes.
     * Tune-time parental checks must not use a ladder from a previous origin
     * (ERRATA0310 {@code status.php?reset=1}).
     *
     * @return true if a CGS endpoint was contacted
     */
    public boolean fetchAndStoreSchedule(Service service) {
        EpgTaskInfo info = scheduleTaskInfoFor(service);
        if (info == null) {
            return false;
        }
        info.isNowNext = false;
        Log.i(TAG, "Fetching CGS schedule on tune for " + info.getServiceUID()
            + " uri=" + info.getmEndPointUri());
        ArrayList<Programme> programmes = downloadProgrammes(info);
        if (programmes == null) {
            Log.w(TAG, "CGS schedule fetch failed for " + info.getServiceUID()
                + "; using stored EPG");
            return false;
        }
        mDbHandler.updateProgrammesForService(info.getServiceUID(), programmes);
        Log.i(TAG, "CGS schedule stored for " + info.getServiceUID()
            + ", programmeCount=" + programmes.size());
        return true;
    }

    private EpgTaskInfo scheduleTaskInfoFor(Service service) {
        if (service == null) {
            return null;
        }
        ContentGuide guide = service.getContentGuide();
        if (guide == null || guide.getScheduleInfoEndpointURI() == null
                || guide.getScheduleInfoEndpointURI().isEmpty()) {
            return null;
        }
        String uid = service.getUniqueIdentifier();
        String serviceRef = service.getContentGuideServiceRef();
        if (serviceRef == null || serviceRef.isEmpty()) {
            serviceRef = uid;
        }
        if (uid == null || uid.isEmpty() || serviceRef.isEmpty()) {
            return null;
        }
        Uri.Builder builder = Uri.parse(guide.getScheduleInfoEndpointURI()).buildUpon();
        builder.appendQueryParameter("sid", serviceRef);
        Uri programUri = null;
        String programInfo = guide.getProgramInfoEndpointURI();
        if (programInfo != null && !programInfo.isEmpty()) {
            programUri = Uri.parse(programInfo);
        }
        return new EpgTaskInfo(uid, builder.build(), programUri);
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
            if (!findParentalGuidance(node, builder)) {
                applyServiceParentalFallback(builder, service);
            }
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
        List<XmlNode> titles = node.getDescendantsByName("Title");
        XmlNode chosen = null;
        for (XmlNode title : titles) {
            if ("main".equals(title.getAttribute("type"))) {
                chosen = title;
                break;
            }
            if (chosen == null) {
                chosen = title;
            }
        }
        if (chosen != null) {
            builder.setTitle(chosen.getInnerText());
        }
    }

    private void findDescriptions(XmlNode node, Programme.Builder builder) {
        List<XmlNode> descriptions = node.getDescendantsByName("Synopsis");
        for (XmlNode desc : descriptions) {
            String length = desc.getAttribute("length");
            if (length == null) {
                length = "medium";
            }
            switch (length) {
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

    /**
     * @return true if ProgramInformation ParentalGuidance was present (HbbTV O.6.2.2).
     */
    private boolean findParentalGuidance(XmlNode node, Programme.Builder builder) {
        final List<String> minimumAgeNames = Arrays.asList("MinimumAge", "mpeg7:MinimumAge");
        final List<String> parentalRatingNames = Arrays.asList("ParentalRating", "mpeg7:ParentalRating");
        List<XmlNode> parentalGuidanceNodes = node.getDescendantsByName("ParentalGuidance");
        if (parentalGuidanceNodes.isEmpty()) {
            return false;
        }
        String minAge;
        String ratingScheme = null;
        String explanatoryText = null;
        XmlNode n = parentalGuidanceNodes.get(0).getFirstChild();
        if (n == null || !minimumAgeNames.contains(n.getName())) {
            return false;
        }
        minAge = n.getInnerText();
        if (minAge == null) {
            return false;
        }
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
        try {
            builder.setMinimumAge(Integer.parseInt(minAge))
                    .setParentalRatingScheme(ratingScheme)
                    .setParentalRatingDescription(explanatoryText);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    /** HbbTV O.6.2.2: if programme ParentalGuidance is absent, use Service/ParentalRating. */
    private void applyServiceParentalFallback(Programme.Builder builder, Service service) {
        if (service == null || service.getParentalRating() == null) {
            return;
        }
        int minAge = service.getParentalRating();
        builder.setMinimumAge(minAge);
        if (minAge != 255) {
            builder.setParentalRatingScheme("dvb-si");
        }
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

    private XmlNode fetchDataFromUri(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/xml");
            connection.setUseCaches(false);
            connection.setConnectTimeout(CGS_HTTP_TIMEOUT_MS);
            connection.setReadTimeout(CGS_HTTP_TIMEOUT_MS);
            InputStream inputStream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }
            reader.close();
            return XmlNode.parse(responseBuilder.toString());
        } catch (java.io.FileNotFoundException e) {
            Log.d(TAG, "EPG data not available: " + url.toString());
        } catch (Exception e) {
            Log.e(TAG, "error", e);
        } finally {
            connection.disconnect();
        }
        return null;
    }

    private ArrayList<Programme> downloadProgrammes(EpgTaskInfo info) {
        synchronized (info) {
            try {
                Uri.Builder uBuilder = info.getmEndPointUri().buildUpon();
                if (info.isNowNext) {
                    uBuilder.appendQueryParameter("now_next", "true");
                } else {
                    long currentTimestamp = System.currentTimeMillis() / 1000;
                    long startTime = (currentTimestamp - EPG_INTERVAL) - currentTimestamp % EPG_INTERVAL;
                    long endTime = (currentTimestamp + EPG_INTERVAL * 7) - currentTimestamp % EPG_INTERVAL;
                    uBuilder.appendQueryParameter("start", String.valueOf(startTime));
                    uBuilder.appendQueryParameter("end", String.valueOf(endTime));
                }

                XmlNode epgMetadata = fetchDataFromUri(new URL(uBuilder.build().toString()));
                if ((epgMetadata == null || epgMetadata.getChildrenCount() == 0)
                        && !info.triedAlternateQuery) {
                    info.triedAlternateQuery = true;
                    info.isNowNext = !info.isNowNext;
                    return downloadProgrammes(info);
                }
                info.triedAlternateQuery = false;
                return parseProgrammes(epgMetadata, info);
            } catch (IOException e) {
                Log.e(TAG, "IOException", e);
            } catch (Exception e) {
                Log.e(TAG, "Parsing error", e);
            }
            return null;
        }
    }

    private ArrayList<Programme> parseProgrammes(XmlNode epgMetadata, EpgTaskInfo info) {
        ArrayList<Programme> programmes = new ArrayList<>();
        if (epgMetadata == null) {
            return programmes;
        }
        List<XmlNode> scheduleEvents = epgMetadata.getDescendantsByName("ScheduleEvent");
        List<XmlNode> programmesInfo = epgMetadata.getDescendantsByName("ProgramInformation");
        Uri auxEndPointUri = info.getmAuxEndPointUri();
        Service service = mDbHandler.getServiceForUID(info.getServiceUID());

        if (!scheduleEvents.isEmpty()) {
            for (XmlNode event : scheduleEvents) {
                Programme.Builder pBuilder = new Programme.Builder();
                XmlNode programNode = event.getDescendantByName("Program");
                String programId = null;
                if (programNode != null) {
                    programId = programNode.getAttribute("crid");
                    if (programId == null) {
                        programId = programNode.getAttribute("programId");
                    }
                }
                findStartEndTimes(event, pBuilder, System.currentTimeMillis() / 1000, SECONDS_OF_DAY);

                XmlNode programInfo = null;
                if (programId != null) {
                    for (XmlNode pi : programmesInfo) {
                        if (programId.equals(pi.getAttribute("programId"))
                                || programId.equals(pi.getAttribute("crid"))) {
                            programInfo = pi;
                            break;
                        }
                    }
                }
                if (programInfo != null) {
                    if (auxEndPointUri != null && !auxEndPointUri.toString().isEmpty()) {
                        try {
                            Uri.Builder auxBuilder = auxEndPointUri.buildUpon();
                            auxBuilder.appendQueryParameter("pid", programId);
                            XmlNode epgProgramInfoMetadata =
                                    fetchDataFromUri(new URL(auxBuilder.build().toString()));
                            if (epgProgramInfoMetadata != null) {
                                findOnDemandProgram(epgProgramInfoMetadata, event, programId, pBuilder);
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "IOException", e);
                        }
                    }
                    findDescriptions(programInfo, pBuilder);
                    if (!findParentalGuidance(programInfo, pBuilder)) {
                        applyServiceParentalFallback(pBuilder, service);
                    }
                    findTitle(programInfo, pBuilder);
                } else {
                    applyServiceParentalFallback(pBuilder, service);
                }
                programmes.add(pBuilder
                        .setProgramId(programId)
                        .build());
            }
        } else {
            for (XmlNode programInfo : programmesInfo) {
                Programme.Builder pBuilder = new Programme.Builder();
                String programId = programInfo.getAttribute("programId");
                findStartEndTimes(programInfo, pBuilder, System.currentTimeMillis() / 1000, SECONDS_OF_DAY);
                findDescriptions(programInfo, pBuilder);
                if (!findParentalGuidance(programInfo, pBuilder)) {
                    applyServiceParentalFallback(pBuilder, service);
                }
                findTitle(programInfo, pBuilder);
                programmes.add(pBuilder
                        .setProgramId(programId)
                        .build());
            }
        }
        return programmes;
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
                                // TS 103 770 §6.5.3.1: sid = UniqueIdentifier or ContentGuideServiceRef
                                // (latter wins).
                                builder.appendQueryParameter("sid", serviceRef);

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
                                //Log.i(TAG, "Updating EPG for Service " + info.getServiceUID());
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
        private final EpgTaskInfo mTaskInfo;

        public EpgMetadataTask(EpgTaskInfo taskInfo) {
            mTaskInfo = taskInfo;
        }

        @Override
        protected ArrayList<Programme> doInBackground(Void... ignore) {
            ArrayList<Programme> programmes = downloadProgrammes(mTaskInfo);
            if (programmes != null) {
                synchronized (mTaskInfo) {
                    if (mTaskInfo.nextUpdate == null) {
                        mTaskInfo.nextUpdate = System.currentTimeMillis() / 1000 + 300;
                    }
                    mTaskInfo.isNowNext = true;
                }
                mUpdatedServices.add(mTaskInfo.getServiceUID());
            }
            return programmes;
        }

        @Override
        public void onPostExecute(ArrayList<Programme> programmes) {
            long startMs = System.currentTimeMillis();
            Log.i(TAG, "EPG_DEBUG: onPostExecute start thread=" + Thread.currentThread().getName()
                + ", serviceUID=" + mTaskInfo.getServiceUID()
                + ", programmeCount=" + (programmes != null ? programmes.size() : 0)
                + ", pendingTasks=" + mEpgMetadataTasks.size());
            synchronized (mLock) {
                if (programmes != null) {
                    mDbHandler.updateProgrammesForService(mTaskInfo.getServiceUID(), programmes);
                }
                mEpgMetadataTasks.remove(this);
                if (mEpgMetadataTasks.isEmpty() && !mUpdatedServices.isEmpty()) {
                    List<String> updatedServices = new ArrayList<>(mUpdatedServices);
                    Log.i(TAG, "EPG_DEBUG: firing onEpgUpdated for " + updatedServices.size() + " services");
                    for (Callback callback : mCallbacks) {
                        callback.onEpgUpdated(updatedServices);
                    }
                    mUpdatedServices.clear();
                }
            }
            Log.i(TAG, "EPG_DEBUG: onPostExecute done in "
                + (System.currentTimeMillis() - startMs) + "ms, serviceUID=" + mTaskInfo.getServiceUID());
        }
    }

    private static class EpgTaskInfo {
        public Long nextUpdate = null;
        public boolean isNowNext = true;
        public boolean triedAlternateQuery = false;
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
