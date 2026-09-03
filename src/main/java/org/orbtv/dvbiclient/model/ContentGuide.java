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
 
package org.orbtv.dvbiclient.model;

import android.content.ContentValues;
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

public class ContentGuide {
    public static final String DB_COLUMN_CGSID = "cgsid";
    public static final String DB_COLUMN_SCHEDULE_INFO_URI = "schedule_info_uri";
    public static final String DB_COLUMN_PROGRAM_INFO_URI = "program_info_uri";
    private String mProviderName;
    private String mCGSID;
    private String mScheduleInfoEndpointURI;
    private String mProgramInfoEndpointURI;

    private ContentGuide() { }

    public String getCGSID() { return mCGSID; }

    public String getScheduleInfoEndpointURI() { return mScheduleInfoEndpointURI; }

    public String getProgramInfoEndpointURI() { return mProgramInfoEndpointURI; }

    @Override
    public String toString() {
        return "DvbIContentGuide{" +
                "ProviderName='" + mProviderName + '\'' +
                ", CGSID='" + mCGSID + '\'' +
                ", scheduleInfoEndpointURI='" + mScheduleInfoEndpointURI + '\'' +
                ", ProgramInfoEndpointURI='" + mProgramInfoEndpointURI + '\'' +
                '}';
    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(ContentGuide.DB_COLUMN_CGSID, mCGSID);
        values.put(ContentGuide.DB_COLUMN_SCHEDULE_INFO_URI, mScheduleInfoEndpointURI);
        values.put(ContentGuide.DB_COLUMN_PROGRAM_INFO_URI, mProgramInfoEndpointURI);
        return values;
    }

    public static List<ContentGuide> parseSourceFromXML(XmlPullParser xpp) throws Exception {
        int eventType = xpp.getEventType();
        if("ContentGuideSourceList".equals(xpp.getName())) {
            eventType = xpp.next();
        }
        List<ContentGuide> contentGuides = new ArrayList<>();
        ContentGuide currentContentGuide = null;
        while (!(eventType == XmlPullParser.END_TAG && "ContentGuideSource".equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                if ("ContentGuideSource".equals(xpp.getName())) {
                    currentContentGuide = new Builder().setCGSID(xpp.getAttributeValue(null, "CGSID")).build();
                    contentGuides.add(currentContentGuide);
                } else if (currentContentGuide != null) {
                    switch (xpp.getName()) {
                        case "ProviderName":
                            currentContentGuide.mProviderName = xpp.nextText();
                            break;
                        case "ScheduleInfoEndpoint":
                            if ("application/xml".equals(xpp.getAttributeValue(null, "contentType"))) {
                                while (xpp.next() != XmlPullParser.END_TAG) {
                                    if (xpp.getEventType() == XmlPullParser.START_TAG && "URI".equals(xpp.getName())) {
                                        currentContentGuide.mScheduleInfoEndpointURI = xpp.nextText();
                                        break;
                                    }
                                }
                            }
                            break;
                        case "ProgramInfoEndpoint":
                            if ("application/xml".equals(xpp.getAttributeValue(null, "contentType"))) {
                                while (xpp.next() != XmlPullParser.END_TAG) {
                                    if (xpp.getEventType() == XmlPullParser.START_TAG && "URI".equals(xpp.getName())) {
                                        currentContentGuide.mProgramInfoEndpointURI = xpp.nextText();
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

    public static class Builder {
        private ContentGuide mInstance;
        public Builder() {
            mInstance = new ContentGuide();
        }
        public ContentGuide.Builder setProviderName(String value) {
            mInstance.mProviderName = value;
            return this;
        }
        public ContentGuide.Builder setCGSID(String value) {
            mInstance.mCGSID = value;
            return this;
        }
        public ContentGuide.Builder setScheduleInfoEndpointURI(String value) {
            mInstance.mScheduleInfoEndpointURI = value;
            return this;
        }
        public ContentGuide.Builder setProgramInfoEndpointURI(String value) {
            mInstance.mProgramInfoEndpointURI = value;
            return this;
        }
        public ContentGuide build() {
            ContentGuide instance = new ContentGuide();
            instance.mProviderName = mInstance.mProviderName;
            instance.mCGSID = mInstance.mCGSID;
            instance.mScheduleInfoEndpointURI = mInstance.mScheduleInfoEndpointURI;
            instance.mProgramInfoEndpointURI = mInstance.mProgramInfoEndpointURI;
            return instance;
        }
    }
}