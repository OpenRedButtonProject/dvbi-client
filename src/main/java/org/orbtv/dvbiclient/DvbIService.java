package org.orbtv.dvbiclient;

import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlPullParserException;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

class ServiceList {
    List<DvbIService> services;
    private List<LCNTable> lcnTables = new ArrayList<>();
    //rest of the members

    private static class LCNTable {
        private String targetRegion;
        private List<LCNEntry> lcnEntries;

        private LCNTable(String targetRegion) {
            this.targetRegion = targetRegion;
            this.lcnEntries = new ArrayList<>();
        }
    }

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

        private LCNEntry(String channelNumber, String serviceRef) {
            this(channelNumber, serviceRef, true, true);
        }
    }

    public static ServiceList parseFromXML(String xml, String region) throws Exception {
        ServiceList serviceList = new ServiceList();

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
                    serviceList.lcnTables.add(currentLCNTable);
                } else if (currentLCNTable != null && "LCN".equals(xpp.getName())) {
                    String channelNumber = xpp.getAttributeValue(null, "channelNumber");
                    String serviceRef = xpp.getAttributeValue(null, "serviceRef");
                    boolean selectable = Boolean.parseBoolean(xpp.getAttributeValue(null, "selectable"));
                    boolean visible = Boolean.parseBoolean(xpp.getAttributeValue(null, "visible"));
                    LCNEntry lcnEntry = new LCNEntry(channelNumber, serviceRef, selectable, visible);
                    currentLCNTable.lcnEntries.add(lcnEntry);
                } else if ("Service".equals(xpp.getName())) {
                    serviceList.services = DvbIService.parseFromXML(xpp);
                }
            }
            eventType = xpp.next();
        }
        serviceList.setLCN(region);
        int maxLcn = serviceList.getNextAvailableLcn();
        for (DvbIService service : serviceList.services) {
            if (service.getLCNNumber() == null) {
                service.setLCNNumber(String.valueOf(maxLcn++));
            }
        }
        return serviceList;
    }

    private int getNextAvailableLcn() {
        int lcn = 0;
        for (DvbIService service : services) {
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
            for (DvbIService service : this.services) {
                String uniqueIdentifier = service.getUniqueIdentifier();
                for (LCNEntry lcnEntry : matchingLCNTable.lcnEntries) {
                    if (lcnEntry.serviceRef.equals(uniqueIdentifier)) {
                        service.setLCNNumber(lcnEntry.channelNumber);
                        service.setSelectable(lcnEntry.selectable);
                        service.setVisible(lcnEntry.visible);
                        break;
                    }
                }
            }
        }
    }

    private LCNTable findMatchingLCNTable(String targetRegion) {
        if (lcnTables.size() > 0) {
            if (targetRegion == null || targetRegion.isEmpty()) {
                return lcnTables.get(0);
            } else {
                for (LCNTable lcnTable : lcnTables) {
                    if (lcnTable.targetRegion != null && lcnTable.targetRegion.equals(targetRegion)) {
                        return lcnTable;
                    }
                }
            }
        }
        return null;
    }
}


public class DvbIService {
    private String uniqueIdentifier;
    private String providerName;
    private String serviceType;
    private Triplet triplet;
    private List<RelatedMaterial> relatedMaterials = new ArrayList<>();
    private List<DvbIServiceInstance> instances = new ArrayList<>();
    private Map<String, String> serviceNames = new HashMap<>();
    private String lcnNumber;
    private boolean selectable;
    private boolean visible;

    private DvbIService() { }

    public DvbIService(Map<String, String> names, String provider, String uid, String type, String lcn, Triplet triplet, List<DvbIServiceInstance> instances, List<RelatedMaterial> materials) {
        this.uniqueIdentifier = uid;
        this.serviceNames = names;
        this.serviceType = type;
        this.providerName = provider;
        this.instances = instances;
        this.relatedMaterials = materials;
        this.lcnNumber = lcn;
        this.triplet = triplet;
    }

    public static List<DvbIService> parseFromXML(XmlPullParser xpp) throws Exception {
        List<DvbIService> services = new ArrayList<>();
        int eventType = xpp.getEventType();
        DvbIService currentService = null;
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                if ("Service".equals(xpp.getName())) {
                    currentService = new DvbIService();
                    services.add(currentService);
                } else if (currentService != null) {
                    switch (xpp.getName()) {
                        case "UniqueIdentifier":
                            currentService.uniqueIdentifier = xpp.nextText();
                            break;
                        case "ServiceName":
                            String language = xpp.getAttributeValue(null, "lang");
                            String name = xpp.nextText();
                            currentService.serviceNames.put(name, language);
                            break;
                        case "ServiceType":
                            currentService.serviceType = xpp.getAttributeValue(null, "href");
                            break;
                        case "ProviderName":
                            currentService.providerName = xpp.nextText();
                            break;
                        case "RelatedMaterial":
                            RelatedMaterial relatedMaterial = RelatedMaterial.parseFromXML(xpp);
                            currentService.relatedMaterials.add(relatedMaterial);
                            break;
                        case "AdditionalServiceParameters":
                            parseAdditionalParameters(currentService, xpp);
                            break;
                        case "ServiceInstance":
                            DvbIServiceInstance instance = DvbIServiceInstance.parseFromXML(xpp);
                            currentService.instances.add(instance);
                            break;
                    }
                }
            }
            eventType = xpp.next();
        }
        return services;
    }
    @Override
    public String toString() {
        String ret = "- " + this.getClass().getSimpleName() + " " + uniqueIdentifier + " -"
                + "\nserviceNames: " + this.serviceNames
                + "\nlcn: " + this.lcnNumber
                + "\ntriplet: " + this.triplet;
        for (RelatedMaterial mat : relatedMaterials) {
            ret += "\n" + mat.toString();
        }
        for (DvbIServiceInstance instance : instances) {
            ret += "\n" + instance.toString();
        }
        return ret;
    }

    private static void parseAdditionalParameters(DvbIService service, XmlPullParser xpp) throws Exception {
        String extensionName = xpp.getAttributeValue(null, "extensionName");
        if (extensionName != null && extensionName.equals("urn:hbbtv:dvbi:service:serviceIdentifierTriplet")) {
            service.triplet = Triplet.parseFromXML(xpp);
        }
    }

    public String getUniqueIdentifier() {
        return uniqueIdentifier;
    }
    
    public Map<String, String> getServiceNames() {
        return serviceNames;
    }

    public String getProviderName() {
        return providerName;
    }
 
    public String getServiceType() {
        return serviceType;
    }

    public List<RelatedMaterial> getRelatedMaterials() {
        return relatedMaterials;
    }

    public List<DvbIServiceInstance> getInstances() {
        return instances;
    }

    public Triplet getTriplet() {
        return triplet;
    }

    public void setLCNNumber(String lcnNumber) {
        this.lcnNumber = lcnNumber;
    }

    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public String getLCNNumber() {
        return lcnNumber;
    }
}

class InstanceAvailabilityPeriod {
    private String startTime;
    private String endTime;
    private String days;

    public static InstanceAvailabilityPeriod parseFromXML(XmlPullParser xpp) throws Exception {
        InstanceAvailabilityPeriod period = new InstanceAvailabilityPeriod();
        int eventType = xpp.getEventType();
        while (!(eventType == XmlPullParser.END_TAG && "Period".equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                switch (xpp.getName()) {
                    case "startTime":
                        period.startTime = xpp.nextText();
                        break;
                    case "endTime":
                        period.endTime = xpp.nextText();
                        break;
                    case "days":
                        period.days = xpp.nextText();
                        break;
                }
            }
            eventType = xpp.next();
        }
        return period;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public String getDays() {
        return days;
    }

    public long duration() {
        if (startTime == null || endTime == null || startTime.isEmpty() || endTime.isEmpty()) {
            return 0;
        }

        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            Date startDate = format.parse(startTime);
            Date endDate = format.parse(endTime);
            long durationInMillis = endDate.getTime() - startDate.getTime();

            return TimeUnit.MILLISECONDS.toMinutes(durationInMillis);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }
}

class RelatedMaterial {
    private String howRelatedHref;
    private String howRelatedTermID;
    private String mediaLocatorUri;
    private String mediaLocatorContentType;

    private RelatedMaterial() { }

    public RelatedMaterial(String howRelatedHref, String howRelatedTermID,
                           String mediaLocatorUri, String mediaLocatorContentType) {
        this.howRelatedHref = howRelatedHref;
        this.howRelatedTermID = howRelatedTermID;
        this.mediaLocatorUri = mediaLocatorUri;
        this.mediaLocatorContentType = mediaLocatorContentType;
    }

    public static RelatedMaterial parseFromXML(XmlPullParser xpp) throws Exception {
        RelatedMaterial relatedMaterial = new RelatedMaterial();
        int eventType = xpp.getEventType();
        while (!(eventType == XmlPullParser.END_TAG && "RelatedMaterial".equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                switch (xpp.getName()) {
                    case "HowRelated":
                        relatedMaterial.howRelatedHref = xpp.getAttributeValue(null, "href");
                        relatedMaterial.howRelatedTermID = extractTermID(xpp.getAttributeValue(null, "href"));
                        break;
                    case "MediaLocator":
                        parseMediaLocator(xpp, relatedMaterial);
                        break;
                    case "MediaUri":
                        relatedMaterial.mediaLocatorContentType = xpp.getAttributeValue(null, "contentType");
                        break;
                }
            }
            eventType = xpp.next();
        }
        return relatedMaterial;
    }

    private static void parseMediaLocator(XmlPullParser xpp, RelatedMaterial relatedMaterial) throws Exception {
        int eventType = xpp.next();
        while (!(eventType == XmlPullParser.END_TAG && "MediaLocator".equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG && "MediaUri".equals(xpp.getName())) {
                relatedMaterial.mediaLocatorContentType = xpp.getAttributeValue(null, "contentType");
                relatedMaterial.mediaLocatorUri = xpp.nextText();
            }
            eventType = xpp.next();
        }
    }

    private static String extractTermID(String href) {
        if (href != null) {
            int index = href.lastIndexOf(':');
            if (index != -1) {
                return href.substring(index + 1);
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "- " + this.getClass().getSimpleName() + " -\n"
                + "howRelatedHref: " + this.howRelatedHref + "\n"
                + "howRelatedTermID: " + this.howRelatedTermID + "\n"
                + "mediaLocatorContentType: " + this.mediaLocatorContentType + "\n"
                + "mediaLocatorUri: " + this.mediaLocatorUri;
    }

    public String getHowRelatedHref() {
        return howRelatedHref;
    }

    public String getHowRelatedTermID() {
        return howRelatedTermID;
    }

    public String getMediaLocatorUri() {
        return mediaLocatorUri;
    }

    public String getMediaLocatorContentType() {
        return mediaLocatorContentType;
    }

    public boolean isXmlAitContentType() {
        return "application/vnd.dvb.ait+xml".equals(mediaLocatorContentType);
    }
}

