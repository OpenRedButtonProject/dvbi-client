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
import java.util.Map;
import java.util.LinkedHashMap;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

class ServiceList {
    List<DvbIService> services;
    private List<LCNTable> lcnTables;
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
                    serviceList.lcnTables = new ArrayList<>();
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
        return serviceList;
    }

    private void setLCN(String targetRegion) {
        LCNTable matchingLCNTable;
        if (targetRegion == null || targetRegion.isEmpty()) {
            matchingLCNTable = lcnTables.get(0);
        } else {
            matchingLCNTable = findMatchingLCNTable(targetRegion);
        }

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
        for (LCNTable lcnTable : lcnTables) {
            if (lcnTable.targetRegion != null && lcnTable.targetRegion.equals(targetRegion)) {
                return lcnTable;
            }
        }
        return null;
    }
}


public class DvbIService {
    private String uniqueIdentifier;
    private String serviceName;
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

<<<<<<< HEAD
    public DvbIService(String name, String provider, String uid, String type, String lcn, List<DvbIServiceInstance> instances, List<RelatedMaterial> materials) {
=======
    public DvbIService(String name, String provider, String uid, String type, List<DvbIServiceInstance> instances, List<RelatedMaterial> materials, String lcn) {
>>>>>>> d588114 (Add LCN field to the DVB-I database.)
        this.uniqueIdentifier = uid;
        this.serviceName = name;
        this.serviceType = type;
        this.providerName = provider;
        this.instances = instances;
        this.relatedMaterials = materials;
        this.lcnNumber = lcn;
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
                            currentService.serviceType = xpp.nextText();
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
        String ret = "- " + this.getClass().getSimpleName() + " " + uniqueIdentifier + " -\n"
                + "serviceName: " + this.serviceName;
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

class DvbIServiceInstance {
    private String displayName;
    private String serviceName; 
    private int priority;
    private List<InstanceAvailabilityPeriod> availabilityPeriods = new ArrayList<>();
    private Triplet triplet;
    private String deliveryType;
    private Map<String, String> deliveryParameters = new HashMap<>();
    private List<RelatedMaterial> relatedMaterials = new ArrayList<>();

    private DvbIServiceInstance() { }

    public DvbIServiceInstance(String displayName, int priority, JSONObject deliveryParams, List<RelatedMaterial> relatedMaterials) {
        Iterator<String> keys = deliveryParams.keys();
        this.displayName = displayName;
        this.priority = priority;
        this.relatedMaterials = relatedMaterials;
        while(keys.hasNext()) {
            String key = keys.next();
            try {
                this.deliveryParameters.put(key, deliveryParams.get(key).toString());
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static DvbIServiceInstance parseFromXML(XmlPullParser xpp) throws Exception {
        DvbIServiceInstance instance = new DvbIServiceInstance();
        int eventType = xpp.getEventType();
        while (!(eventType == XmlPullParser.END_TAG && "ServiceInstance".equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                switch (xpp.getName()) {
                    case "DisplayName":
                        instance.displayName = xpp.nextText();
                        break;
                    case "ServiceInstance":
                        instance.priority = Integer.parseInt(xpp.getAttributeValue(null, "priority"));
                        break;
                    case "Availability":
                        InstanceAvailabilityPeriod period = InstanceAvailabilityPeriod.parseFromXML(xpp);
                        instance.availabilityPeriods.add(period);
                        break;
                    case "RelatedMaterial":
                        RelatedMaterial relatedMaterial = RelatedMaterial.parseFromXML(xpp);
                        instance.relatedMaterials.add(relatedMaterial);
                        break;
                    case "DVBTriplet":
                        instance.triplet = Triplet.parseFromXML(xpp);
                        break;
                    case "ServiceName":
                        instance.serviceName = xpp.nextText();
                        break;
                    case "SourceType":
                        instance.deliveryType = parseServiceType(xpp.nextText());
                        break;
                    case "SATIPDeliveryParameters":
                        instance.deliveryType = "sat-ip";
                        parseDeliveryParameters(instance, xpp);
                        break;
                    case "DVBSDeliveryParameters":
                        instance.deliveryType = "dvb-s";
                        parseDeliveryParameters(instance, xpp);
                        break;
                    case "DVBCDeliveryParameters":
                        instance.deliveryType = "dvb-c";
                        parseDeliveryParameters(instance, xpp);
                        break;
                    case "DASHDeliveryParameters":
                        instance.deliveryType = "dvb-dash";
                        parseDeliveryParameters(instance, xpp);
                        break;
                    case "DVBTDeliveryParameters":
                        instance.deliveryType = "dvb-t";
                        parseDeliveryParameters(instance, xpp);
                        break;
                }
            }
            eventType = xpp.next();
        }
        return instance;
    }

    private static void parseDeliveryParameters(DvbIServiceInstance instance, XmlPullParser xpp) throws Exception {
        String deliveryType = xpp.getName();
        String uri = null;

        int eventType = xpp.next();
        while (!(eventType == XmlPullParser.END_TAG && deliveryType.equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                if (xpp.getName().equals("UriBasedLocation")) {
                    uri = parseUriBasedLocation(xpp);
                }
                if (xpp.getName().equals("DVBTriplet")) {
                    instance.triplet = Triplet.parseFromXML(xpp);
                }
            }
            eventType = xpp.next();
        }

        if (uri != null) {
            instance.deliveryParameters.put("UriBasedLocation", uri);
        }
    }

    private static String parseUriBasedLocation(XmlPullParser xpp) throws Exception {
        int eventType = xpp.next();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && xpp.getName().equals("URI")) {
                return xpp.nextText();
            }
            eventType = xpp.next();
        }
        return null;
    }

    private static String parseServiceType(String uri) {
        if (uri != null) {
            String lastPart = uri.substring(uri.lastIndexOf(":") + 1);
            return lastPart;
        }
        return null;
    }

    @Override
    public String toString() {
        String ret = "- " + this.getClass().getSimpleName() + " -\n"
                + "displayName: " + this.displayName + "\n"
                + "deliveryParams: " + this.deliveryParameters + "\n"
                + "priority: " + this.priority;
        for (RelatedMaterial mat : relatedMaterials) {
            ret += "\n" + mat.toString();
        }
        return ret;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getPriority() {
        return priority;
    }

    public List<InstanceAvailabilityPeriod> getAvailabilityPeriods() {
        return availabilityPeriods;
    }

    public String getUri() {
        return deliveryParameters.get("UriBasedLocation");
    }

    public Triplet getTriplet() {
        return triplet;
    }

    public String getDeliveryType() {
        return deliveryType;
    }

    public Map<String, String> getDeliveryParameters() {
        return deliveryParameters;
    }

    public List<RelatedMaterial> getRelatedMaterials() {
        return relatedMaterials;
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

class Triplet {
    private String origNetId;
    private String tsId;
    private String serviceId;

    private Triplet() { }

    public Triplet(String origNetId, String tsId, String serviceId) {
        this.origNetId = origNetId;
        this.tsId = tsId;
        this.serviceId = serviceId;
    }

    public static Triplet parseFromXML(XmlPullParser xpp) throws Exception {
        Triplet triplet = null;
        int eventType = xpp.getEventType();
        try {
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG &&
                        ("DVBTriplet".equals(xpp.getName()) || "hbbtv-i:DVBTriplet".equals(xpp.getName()))) {
                    triplet = new Triplet();
                    triplet.origNetId = xpp.getAttributeValue(null, "origNetId");
                    triplet.tsId = xpp.getAttributeValue(null, "tsId");
                    triplet.serviceId = xpp.getAttributeValue(null, "serviceId");
                    break;
                }
                eventType = xpp.next();
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            triplet = new Triplet(); // Return an empty Triplet object in case of an error
        }
        return triplet;
    }

    public boolean isEmpty() {
        return origNetId == null && tsId == null && serviceId == null;
    }

    public String getOrigNetId() {
        return origNetId;
    }

    public String getTsId() {
        return tsId;
    }

    public String getServiceId() {
        return serviceId;
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

class DvbIChannel {
    private String channelType;
    private String idType;
    private String onid;
    private String nid;
    private String tsid;
    private String sid;
    private String name;
    private String majorChannel;
    private String dsd;
    private String ipBroadcastID;
    private String terminalChannel;
    private DvbIService parentService;
    private List<DvbIServiceInstance> serviceInstances = new ArrayList<>();

    public static DvbIChannel createChannel(DvbIService service, String preferredLanguage) {
        DvbIChannel channel = new DvbIChannel();
        channel.channelType = determineChannelType(service);
        channel.idType = determineIdType(service);
        channel.onid = determineOnid(service);
        channel.nid = null; // Undefined or will be populated later
        channel.tsid = determineTsid(service);
        channel.sid = determineSid(service);
        channel.name = determineChannelName(service, preferredLanguage);
        channel.majorChannel = determineMajorChannel(service);
        channel.dsd = null; // Undefined
        channel.ipBroadcastID = service.getUniqueIdentifier();
        channel.terminalChannel = determineTerminalChannel(service);
        channel.serviceInstances = service.getInstances();
        channel.parentService = null;
        return channel;
    }

    public static DvbIChannel createChannel(DvbIService parentService, DvbIServiceInstance instance, String preferredLanguage) {
        DvbIChannel channel = new DvbIChannel();
        channel.channelType = determineChannelType(parentService);
        channel.idType = determineIdType(instance);
        channel.onid = determineOnid(instance);
        channel.nid = null; // Undefined
        channel.tsid = determineTsid(instance);
        channel.sid = determineSid(instance);
        channel.name = determineChannelName(parentService, instance, preferredLanguage);
        channel.majorChannel = determineMajorChannel(parentService);
        channel.dsd = null; // Undefined or will be populated later
        channel.ipBroadcastID = instance.getDeliveryParameters().get("UriBasedLocation");
        channel.terminalChannel = determineTerminalChannel(instance);
        channel.serviceInstances = null;
        channel.parentService = parentService;
        return channel;
    }

    private static String determineChannelType(DvbIService service) {
        String serviceType = service.getServiceType();

        if (serviceType == null || "urn:dvb:metadata:cs:ServiceTypeCS:2019:linear".equals(serviceType)) {
            return "TYPE_TV";
        } else if ("urn:dvb:metadata:cs:ServiceTypeCS:2019:linear-radio".equals(serviceType)) {
            return "TYPE_RADIO";
        } else if ("urn:dvb:metadata:cs:ServiceTypeCS:2019:data".equals(serviceType)) {
            List<RelatedMaterial> relatedMaterials = service.getRelatedMaterials();
            for (RelatedMaterial relatedMaterial : relatedMaterials) {
                if (isHbbtvData(relatedMaterial)) {
                    return "TYPE_HBBTV_DATA";
                }
            }
        }
        return "TYPE_OTHER";
    }

    private static boolean isHbbtvData(RelatedMaterial relatedMaterial) {
        String howRelatedHref = relatedMaterial.getHowRelatedHref();
        String mediaLocatorContentType = relatedMaterial.getMediaLocatorContentType();

        return howRelatedHref != null && mediaLocatorContentType != null &&
                howRelatedHref.startsWith("urn:dvb:metadata:cs:LinkedApplicationCS:2019") &&
                relatedMaterial.isXmlAitContentType();
    }

    private static String determineIdType(DvbIService service) {
        List<DvbIServiceInstance> instances = service.getInstances();
        String deliveryType = null;
    
        boolean sameDeliveryType = true;
        for (DvbIServiceInstance instance : instances) {
            String instanceDeliveryType = instance.getDeliveryType();
            if (instanceDeliveryType == null || instanceDeliveryType.isEmpty()) {
                sameDeliveryType = false;
                break;
            }
            if (deliveryType == null) {
                deliveryType = instanceDeliveryType;
            } else if (!deliveryType.equalsIgnoreCase(instanceDeliveryType)) {
                sameDeliveryType = false;
                break;
            }
        }

        // Determine the appropriate ID type based on the deliveryType and the additionalServiceParameters
        // T2/S2 support?
        if (sameDeliveryType && deliveryType != null && service.getTriplet() != null && !service.getTriplet().isEmpty()) {
            String lowerCaseDeliveryType = deliveryType.toLowerCase();
            switch (lowerCaseDeliveryType) {
                case "dvb-c":
                    return "ID_DVB_C";
                case "dvb-t":
                    return "ID_DVB_T";
                case "dvb-s":
                    return "ID_DVB_S";
            }
        }

        return "ID_DVB_I";
    }


    private static String determineIdType(DvbIServiceInstance instance) {
        String deliveryType = instance.getDeliveryType();

        if (deliveryType != null && !deliveryType.isEmpty()) {
            switch (deliveryType.toLowerCase()) {
                case "dvb-c":
                    return "ID_DVB_C";
                case "dvb-t":
                    return "ID_DVB_T";
                case "dvb-s":
                    return "ID_DVB_S";
                case "dvb-dash":
                    return "ID_DVB_DASH";
            }
        }
        return "UNSUPPORTED"; //default value? also support for T2, S2
}


    private static String determineOnid(DvbIService service) {
        Triplet triplet = service.getTriplet();
        if (triplet != null && !triplet.isEmpty()) {
            return triplet.getOrigNetId();
        }
        return null;
    }

    private static String determineOnid(DvbIServiceInstance instance) {
        Triplet triplet = instance.getTriplet();
        if (triplet != null && !triplet.isEmpty()) {
            return triplet.getOrigNetId();
        }
        return null;
    }

    private static String determineTsid(DvbIService service) {
        Triplet triplet = service.getTriplet();
        if (triplet != null && !triplet.isEmpty()) {
            return triplet.getTsId();
        }
        return null;
    }

    private static String determineTsid(DvbIServiceInstance instance) {
        Triplet triplet = instance.getTriplet();
        if (triplet != null && !triplet.isEmpty()) {
            return triplet.getTsId();
        }
        return null;
    }

    private static String determineSid(DvbIService service) {
        Triplet triplet = service.getTriplet();
        if (triplet != null && !triplet.isEmpty()) {
            return triplet.getServiceId();
        }
        return null;
    }

    private static String determineSid(DvbIServiceInstance instance) {
        Triplet triplet = instance.getTriplet();
        if (triplet != null && !triplet.isEmpty()) {
            return triplet.getServiceId();
        }
        return null;
    }

    private static String determineChannelName(DvbIService service, String preferredLanguage) {
        Map<String, String> serviceNames = service.getServiceNames();
        if (serviceNames != null && !serviceNames.isEmpty()) {
            if (preferredLanguage != null && !preferredLanguage.isEmpty()) {
                for (Map.Entry<String, String> entry : serviceNames.entrySet()) {
                    if (entry.getValue().equals(preferredLanguage)) {
                        return entry.getKey();
                    }
                }
            }
    
            // If the preferred language is not found or empty, select the first available name
            for (String name : serviceNames.keySet()) {
                return name;
            }
        }
        return null;
    }

    private static String determineChannelName(DvbIService parentService, DvbIServiceInstance instance,  String preferredLanguage) {
        String displayName = instance.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }

        return determineChannelName(parentService, preferredLanguage);
    }

    private static String determineMajorChannel(DvbIService service) {
        return service.getLCNNumber();
    }

    private static String determineTerminalChannel(DvbIService service) {
        return null; // Undefined - can be determined later
    }

    private static String determineTerminalChannel(DvbIServiceInstance instance) {
        // table O.3 is missing the property completely (error?) 
        return null; // Undefined
    }

    // Getter methods for the channel attributes
    public String getChannelType() {
        return channelType;
    }

    public String getOnid() {
        return onid;
    }

    public String getNid() {
        return nid;
    }

    public String getTsid() {
        return tsid;
    }

    public String getSid() {
        return sid;
    }

    public String getName() {
        return name;
    }

    public String getMajorChannel() {
        return majorChannel;
    }

    public String getDsd() {
        return dsd;
    }

    public String getIpBroadcastID() {
        return ipBroadcastID;
    }

    public String getTerminalChannel() {
        return terminalChannel;
    }

    public List<DvbIServiceInstance> getServiceInstances() {
        return serviceInstances;
    }

    public DvbIService getParentService() {
        return parentService;
    }

    public void printChannelProperties() {
        System.out.println("--------------------------------");
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("Channel Type", channelType);
        properties.put("Id Type", idType);
        properties.put("ONID", onid);
        properties.put("NID", nid);
        properties.put("TSID", tsid);
        properties.put("SID", sid);
        properties.put("Name", name);
        properties.put("Major Channel", majorChannel);
        properties.put("DSD", dsd);
        properties.put("IP Broadcast ID", ipBroadcastID);
        properties.put("Terminal Channel", terminalChannel);

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String propertyName = entry.getKey();
            String propertyValue = entry.getValue();
            if (propertyValue != null && !propertyValue.isEmpty()) {
                System.out.println(propertyName + ": " + propertyValue);
            }
        }

        if (serviceInstances != null) {
            int numInstances = serviceInstances.size();
            System.out.println("Number of Instances: " + numInstances);
        }
    }
}
