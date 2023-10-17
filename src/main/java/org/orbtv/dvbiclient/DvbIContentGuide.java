package org.orbtv.dvbiclient;

import android.net.Uri;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.orbtv.companionlibrary.utils.AsyncUtils;

import java.util.ArrayList;
import java.util.List;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.StringReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;

public class DvbIContentGuide {
    private static final String TAG = DvbIContentGuide.class.getSimpleName();
    private String ProviderName;
    private String CGSID;
    private String scheduleInfoEndpointURI;
    private String ProgramInfoEndpointURI;
    private List<Callback> callbacks = new ArrayList<>();

    public DvbIContentGuide(String cgsid, String scheduleInfoEndpointURI) {
        this.CGSID = cgsid;
        this.scheduleInfoEndpointURI = scheduleInfoEndpointURI;
    }

    public interface Callback {
        void EPGEventsUpdated(List<EPGMetadata> EPGMetadata);
    }

    private DvbIContentGuide() { }

    public static List<DvbIContentGuide> parseSourceFromXML(XmlPullParser xpp) throws Exception {
        int eventType = xpp.getEventType();
        if("ContentGuideSourceList".equals(xpp.getName())) {
            eventType = xpp.next();
        }
        List<DvbIContentGuide> contentGuides = new ArrayList<>();
        DvbIContentGuide currentContentGuide = null;
        while (!(eventType == XmlPullParser.END_TAG && "ContentGuideSource".equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                if ("ContentGuideSource".equals(xpp.getName())) {
                    currentContentGuide = new DvbIContentGuide();
                    currentContentGuide.CGSID = xpp.getAttributeValue(null, "CGSID");
                    contentGuides.add(currentContentGuide);
                } else if (currentContentGuide != null) {
                    switch (xpp.getName()) {
                        case "ProviderName":
                            currentContentGuide.ProviderName = xpp.nextText();
                            break;
                        case "ScheduleInfoEndpoint":
                            if ("application/xml".equals(xpp.getAttributeValue(null, "contentType"))) {
                                while (xpp.next() != XmlPullParser.END_TAG) {
                                    if (xpp.getEventType() == XmlPullParser.START_TAG && "URI".equals(xpp.getName())) {
                                        currentContentGuide.scheduleInfoEndpointURI = xpp.nextText();
                                        break;
                                    }
                                }
                            }
                            break;
                        case "ProgramInfoEndpoint":
                            if ("application/xml".equals(xpp.getAttributeValue(null, "contentType"))) {
                                while (xpp.next() != XmlPullParser.END_TAG) {
                                    if (xpp.getEventType() == XmlPullParser.START_TAG && "URI".equals(xpp.getName())) {
                                        currentContentGuide.ProgramInfoEndpointURI = xpp.nextText();
                                        break;
                                    }
                                }
                            }
                            break;
                    }
                }
            }
            eventType = xpp.next();
        }

        return contentGuides;
    }

    public synchronized void registerCallback(Callback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    public synchronized void unregisterCallback(Callback callback) {
        callbacks.remove(callback);
    }

    //TODO: needs extra (optional) request arguments, also polling should be considered if @dynamic service is true
    //TODO: NowNext can be a 'window'
    public void updateEpgMetadata(String serviceUID, String pid, String startTime, String endTime, boolean nowNext) {
        if (scheduleInfoEndpointURI != null && !scheduleInfoEndpointURI.isEmpty()) {
            Uri baseUri = Uri.parse(scheduleInfoEndpointURI);
            Uri.Builder builder = baseUri.buildUpon();
            if(!serviceUID.isEmpty()) {
                builder.appendQueryParameter("sid", serviceUID);
                if(nowNext) {
                    builder.appendQueryParameter("now_next", "true");
//              else if(nowNext.equals("window")) {
                } else {
                    builder.appendQueryParameter("start_time", startTime);
                    builder.appendQueryParameter("end_time", endTime);
                }
            }
            new GetEpgMetadataTask().execute(builder.build().toString());

            if (pid != null && !pid.isEmpty()) {
                builder.clearQuery();
                builder.appendQueryParameter("pid", pid);
                new GetEpgMetadataTask().execute(builder.build().toString());
            }
        }
    }

    public static List<EPGMetadata> parseEpgFromXML(String xml) throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();
        xpp.setInput(new StringReader(xml));
        ArrayList<EPGMetadata> EPGMetadata = new ArrayList<>();
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
                                EPGMetadata.add(new EPGMetadata(){{
                                    parentalRating = Integer.parseInt(minAge);
                                }});
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
        return EPGMetadata;
    }

    public static class EPGMetadata {
        public Integer parentalRating;
    }

    private class GetEpgMetadataTask extends AsyncUtils<List<EPGMetadata>, String> {
        @Override
        protected List<EPGMetadata> doInBackground(String... uris) {
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
        public synchronized void onPostExecute(List<EPGMetadata> EPGMetadata) {
            for (Callback callback : callbacks) {
                callback.EPGEventsUpdated(EPGMetadata);
            }
        }
    }

    @Override
    public String toString() {
        return "DvbIContentGuide{" +
               "ProviderName='" + ProviderName + '\'' +
               ", CGSID='" + CGSID + '\'' +
               ", scheduleInfoEndpointURI='" + scheduleInfoEndpointURI + '\'' +
               ", ProgramInfoEndpointURI='" + ProgramInfoEndpointURI + '\'' +
               '}';
    }

    public String getCGSID() { return CGSID; }
    public String getScheduleInfoEndpointURI() { return scheduleInfoEndpointURI; }
}