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

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class ServiceList {
    public static final String DB_COLUMN_NAME = "name";
    public static final String DB_COLUMN_PROVIDER = "provider";
    public static final String DB_COLUMN_UID = "uid";
    private String mUID;
    private List<Service> mServices = new ArrayList<>();
    private List<LCNTable> mLCNTables = new ArrayList<>();
    private List<ContentGuide> mContentGuideSources = new ArrayList<>();
    //rest of the members

    private ServiceList() {
    }

    public List<Service> getServices() {
        return mServices;
    }

    public String getUID() {
        return mUID;
    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(ServiceList.DB_COLUMN_UID, mUID);
        values.put(ServiceList.DB_COLUMN_NAME, "myname");
        values.put(ServiceList.DB_COLUMN_PROVIDER, "myprovider");
        return values;
    }

    public List<ContentGuide> getContentGuideSources() { return new ArrayList<>(mContentGuideSources); }

    private int getNextAvailableLcn() {
        int lcn = 0;
        for (Service service : mServices) {
            int lcnNumber = service.getLCNNumber() == null ? lcn : Integer.parseInt(service.getLCNNumber());
            if (lcnNumber > lcn) {
                lcn = lcnNumber + 1;
            }
        }
        return lcn;
    }

    private void setLCN(String targetRegion) {
        LCNTable matchingLCNTable = findMatchingLCNTable(targetRegion);

        if (matchingLCNTable != null) {
            for (Service service : this.mServices) {
                String uniqueIdentifier = service.getUniqueIdentifier();
                for (LCNTable.LCNEntry lcnEntry : matchingLCNTable.lcnEntries) {
                    if (lcnEntry.serviceRef.equals(uniqueIdentifier)) {
                        service.setLCNNumber(lcnEntry.channelNumber);
//                        service.setSelectable(lcnEntry.selectable);
//                        service.setVisible(lcnEntry.visible);
                        break;
                    }
                }
            }
        }
    }

    private LCNTable findMatchingLCNTable(String targetRegion) {
        if (mLCNTables.size() > 0) {
            if (targetRegion == null || targetRegion.isEmpty()) {
                return mLCNTables.get(0);
            } else {
                for (LCNTable lcnTable : mLCNTables) {
                    if (lcnTable.targetRegion != null && lcnTable.targetRegion.equals(targetRegion)) {
                        return lcnTable;
                    }
                }
            }
        }
        return null;
    }

    public static ServiceList parseFromXML(String uid, String xml, String region) throws Exception {
        ServiceList serviceList = new ServiceList();
        serviceList.mUID = uid;

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();
        xpp.setInput(new StringReader(xml));

        int eventType = xpp.getEventType();
        LCNTable currentLCNTable = null;
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                if ("LCNTable".equals(xpp.getName())) {
                    String targetRegion = xpp.getAttributeValue(null, "targetRegion");
                    currentLCNTable = new LCNTable(targetRegion);
                    serviceList.mLCNTables.add(currentLCNTable);
                } else if (currentLCNTable != null && "LCN".equals(xpp.getName())) {
                    String channelNumber = xpp.getAttributeValue(null, "channelNumber");
                    String serviceRef = xpp.getAttributeValue(null, "serviceRef");
                    boolean selectable = Boolean.parseBoolean(xpp.getAttributeValue(null, "selectable"));
                    boolean visible = Boolean.parseBoolean(xpp.getAttributeValue(null, "visible"));
                    LCNTable.LCNEntry lcnEntry = new LCNTable.LCNEntry(channelNumber, serviceRef, selectable, visible);
                    currentLCNTable.lcnEntries.add(lcnEntry);
                } else if ("ContentGuideSource".equals(xpp.getName()) || "ContentGuideSourceList".equals(xpp.getName())) {
                    serviceList.mContentGuideSources = ContentGuide.parseSourceFromXML(xpp);
                } else if ("Service".equals(xpp.getName())) {
                    serviceList.mServices = Service.parseFromXML(xpp);
                }
            }
            eventType = xpp.next();
        }
        serviceList.setLCN(region);
        int maxLcn = serviceList.getNextAvailableLcn();
        for (Service service : serviceList.mServices) {
            if (service.getLCNNumber() == null) {
                service.setLCNNumber(String.valueOf(maxLcn++));
            }
        }

        for (Service service : serviceList.mServices) {
            service.updateContentGuide(serviceList.mContentGuideSources);
        }
        return serviceList;
    }

    private static class LCNTable {
        private static class LCNEntry {
            private String channelNumber;
            private String serviceRef;
            private boolean selectable;
            private boolean visible;

            private LCNEntry(String channelNumber, String serviceRef, boolean selectable, boolean visible) {
                this.channelNumber = channelNumber;
                this.serviceRef = serviceRef;
                this.selectable = selectable;
                this.visible = visible;
            }
        }
        private String targetRegion;
        private List<LCNEntry> lcnEntries;

        private LCNTable(String targetRegion) {
            this.targetRegion = targetRegion;
            this.lcnEntries = new ArrayList<>();
        }
    }

    public static class Builder {
        private ServiceList mInstance;
        public Builder() {
            mInstance = new ServiceList();
        }
        public ServiceList.Builder setUID(String value) {
            mInstance.mUID = value;
            return this;
        }
        public ServiceList.Builder setServices(List<Service> value) {
            mInstance.mServices = value;
            return this;
        }
        public ServiceList.Builder setLCNTables(List<LCNTable> value) {
            mInstance.mLCNTables = value;
            return this;
        }
        public ServiceList.Builder setContentGuideSources(List<ContentGuide> value) {
            mInstance.mContentGuideSources = value;
            return this;
        }
        public ServiceList build() {
            ServiceList instance = new ServiceList();
            instance.mUID = mInstance.mUID;
            instance.mServices = mInstance.mServices; // for now, no need to copy the array as it will allocate twice as many services for no reason
            instance.mLCNTables = mInstance.mLCNTables;
            instance.mContentGuideSources = mInstance.mContentGuideSources;
            return instance;
        }
    }
}
