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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EpgManager {
    private static final String TAG = EpgManager.class.getSimpleName();
    private final DatabaseHandler mDbHandler;
    private final Object mLock = new Object();
    private final EpgRunnable epgRunnable = new EpgRunnable();
    private final ArrayList<Callback> mCallbacks = new ArrayList<>();

    public EpgManager(DatabaseHandler dbHandler) {
        mDbHandler = dbHandler;
        epgRunnable.run();
    }

    public void updateServiceLists() {
        epgRunnable.updateServiceLists();
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
                            new EpgMetadataTask(service.getUniqueIdentifier(), null).execute(uri.getInnerText());
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

    private static class MutableLong {
        private Long mValue;
        public MutableLong(Long value) { set(value); }
        public void set(Long value) { mValue = value; };
        public Long get() { return mValue; }
    }

    private class EpgRunnable implements Runnable {
        private static final long EPG_INTERVAL = 10800;
        private final HashMap<String, MutableLong> mRequestTimes = new HashMap<>();
        private final HashMap<String, Uri> mScheduleUris = new HashMap<>();

        public EpgRunnable() {
            updateServiceLists();
        }

        public void updateServiceLists() {
            List<ServiceList> serviceLists = mDbHandler.getServiceLists();
            synchronized (mLock) {
                mScheduleUris.clear();
                mRequestTimes.clear();
                for (ServiceList list : serviceLists) {
                    for (Service service : list.getServices()) {
                        ContentGuide guide = service.getContentGuide();
                        if (guide != null && guide.getScheduleInfoEndpointURI() != null && !guide.getScheduleInfoEndpointURI().isEmpty()) {
                            String uid = service.getUniqueIdentifier();
                            String serviceRef = service.getContentGuideServiceRef();
                            if (serviceRef == null) {
                                serviceRef = uid;
                            }
                            Uri baseUri = Uri.parse(guide.getScheduleInfoEndpointURI());
                            Uri.Builder builder = baseUri.buildUpon();
                            if (!serviceRef.isEmpty()) {
                                builder.appendQueryParameter("sid", serviceRef);
                                mScheduleUris.put(uid, builder.build());
                                mRequestTimes.put(uid, new MutableLong(System.currentTimeMillis() / 1000));
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
                    for (Map.Entry<String, MutableLong> entry : mRequestTimes.entrySet()) {
                        if (entry.getValue().get() != null && entry.getValue().get() <= currentTimestamp) {
                            Log.i(TAG, "Updating EPG...");
                            entry.getValue().set(null);
                            Uri.Builder builder = mScheduleUris.get(entry.getKey()).buildUpon();
                            long startTime = (currentTimestamp - EPG_INTERVAL) - currentTimestamp % EPG_INTERVAL;
                            long endTime = (currentTimestamp + EPG_INTERVAL * 7) - currentTimestamp % EPG_INTERVAL;
                            builder.appendQueryParameter("start", String.valueOf(startTime));
                            builder.appendQueryParameter("end", String.valueOf(endTime));
                            new EpgMetadataTask(entry.getKey(), entry.getValue()).execute(builder.build().toString());
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

    private class EpgMetadataTask extends AsyncUtils<XmlNode, String> {
        private final String mServiceUID;
        private final MutableLong mTimestamp;

        public EpgMetadataTask(String serviceUID, MutableLong timestamp) {
            mServiceUID = serviceUID;
            mTimestamp = timestamp;
        }

        @Override
        protected XmlNode doInBackground(String... uris) {
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

                    synchronized (mLock) {
                        if (mTimestamp != null) {
                            // TODO: update request time
                            // mTimestamp.set(connection.getHeaderField("Cache-Control"));
                        }
                    }
                    return XmlNode.parse(responseBuilder.toString());
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
        public void onPostExecute(XmlNode epgMetadata) {
            List<XmlNode> scheduleEvents = epgMetadata.getDescendantsByName("ScheduleEvent");
            List<XmlNode> programmesInfo = epgMetadata.getDescendantsByName("ProgramInformation");
            ArrayList<Programme> programmes = new ArrayList<>();
            for (XmlNode info : programmesInfo) {
                try {
                    Programme.Builder builder = new Programme.Builder();
                    String programId = info.getAttribute("programId");
                    if (programId != null) {
                        for (XmlNode event : scheduleEvents) {
                            XmlNode programNode = event.getDescendantByName("Program");
                            if (programNode != null && programId.equals(programNode.getAttribute("crid"))) {
                                long startTime = getSecondsFromDate(event.getDescendantByName("PublishedStartTime").getInnerText());
                                Duration duration = getDurationFromString(event.getDescendantByName("PublishedDuration").getInnerText());
                                builder.setStartTime(startTime);
                                builder.setEndTime(startTime + duration.getSeconds());
                                break;
                            }
                        }
                    }
                    List<XmlNode> descriptions = info.getDescendantsByName("Synopsis");
                    for (XmlNode desc : descriptions) {
                        switch (desc.getAttribute("length")) {
                            case "medium":
                                builder.setMediumDescription(desc.getInnerText());
                                break;
                            case "long":
                                builder.setLongDescription(desc.getInnerText());
                                break;
                            default:
                                builder.setShortDescription(desc.getInnerText());
                                break;
                        }
                    }

                    programmes.add(builder
                            .setTitle(info.getDescendantByName("Title").getInnerText())
                            .setParentalRating(Integer.parseInt(info.getDescendantByName("MinimumAge").getInnerText()))
                            .build());
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            mDbHandler.updateProgrammesForService(mServiceUID, programmes);
            synchronized (mLock) {
                for (Callback callback : mCallbacks) {
                    callback.onEpgUpdated(mServiceUID);
                }
            }
        }
    }

    public interface Callback {
        void onEpgUpdated(String serviceUID);
    }
}
