package org.orbtv.dvbiclient;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlPullParserException;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

class ServiceList {
    private List<DvbIService> services;
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

    public void setServices(List<DvbIService> services) {
        this.services = services;
    }

    public static ServiceList parseFromXML(String xml) throws Exception {
        ServiceList serviceList = new ServiceList();
        serviceList.lcnTables = new ArrayList<>();

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
                }
            }
            eventType = xpp.next();
        }

        return serviceList;
    }

    public void setLCN(String targetRegion) {
        LCNTable matchingLCNTable;
        if (targetRegion == null || targetRegion.isEmpty()) {
            matchingLCNTable = lcnTables.get(0);
        } else {
            matchingLCNTable = findMatchingLCNTable(targetRegion);
        }

        if (matchingLCNTable != null) {
            for (DvbIService service : services) {
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

    public static List<DvbIService> parseFromXML(String xml) throws Exception {
        List<DvbIService> services = new ArrayList<>();
    
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();
        xpp.setInput(new StringReader(xml));
    
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


        // Move the log statements here, outside the loop
        for (DvbIService service : services) {
            System.out.println("--");
            System.out.println("service UniqueIdentifier: " + service.getUniqueIdentifier());

            for (DvbIServiceInstance instance : service.getInstances()) {
                if (instance.getTriplet() != null && !instance.getTriplet().isEmpty()) {
                    System.out.println("  instance # uri: " + instance.getUri() + " serviceID: " + instance.getTriplet().getServiceId());
                } else {
                    System.out.println("  instance # uri: " + instance.getUri());
                }
            }
        }


        return services;
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
    private String uri;
    private Triplet triplet;
    private String deliveryType;
    private Map<String, String> deliveryParameters = new HashMap<>();
    private List<RelatedMaterial> relatedMaterials = new ArrayList<>();

    public static DvbIServiceInstance parseFromXML(XmlPullParser xpp) throws Exception {
        DvbIServiceInstance instance = new DvbIServiceInstance();
        int eventType = xpp.getEventType();
        while (!(eventType == XmlPullParser.END_TAG && "ServiceInstance".equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                switch (xpp.getName()) {
                    case "DisplayName":
                        instance.displayName = xpp.nextText();
                        break;
                    case "priority":
                        instance.priority = Integer.parseInt(xpp.nextText());
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
        return uri;
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
                        relatedMaterial.mediaLocatorUri = xpp.getAttributeValue(null, "mediaUri");
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

    private static String extractTermID(String href) {
        if (href != null) {
            int index = href.lastIndexOf(':');
            if (index != -1) {
                return href.substring(index + 1);
            }
        }
        return null;
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
    private String ccid;
    private String onid;
    private String nid;
    private String tsid;
    private String sid;
    private String name;
    private String majorChannel;
    private String dsd;
    private String ipBroadcastID;
    private String terminalChannel;
    private String uri;
    private List<DvbIServiceInstance> serviceInstances = new ArrayList<>();

    public static DvbIChannel createChannel(DvbIService service, String preferredLanguage) {
        DvbIChannel channel = new DvbIChannel();
        channel.channelType = determineChannelType(service);
        channel.idType = determineIdType(service);
        channel.ccid = generateCCID();
        channel.onid = determineOnid(service);
        channel.nid = null; // Undefined
        channel.tsid = determineTsid(service);
        channel.sid = determineSid(service);
        channel.name = determineChannelName(service, preferredLanguage);
        channel.majorChannel = determineMajorChannel(service);
        channel.dsd = null; // Undefined
        channel.ipBroadcastID = service.getUniqueIdentifier();
        channel.terminalChannel = determineTerminalChannel(service);
        channel.serviceInstances = service.getInstances();
        channel.uri = null;
        return channel;
    }

    public static DvbIChannel createChannel(DvbIServiceInstance instance) {
        DvbIChannel channel = new DvbIChannel();
        channel.channelType = determineChannelType(instance);
        channel.idType = determineIdType(instance);
        channel.ccid = generateCCID();
        channel.onid = determineOnid(instance);
        channel.nid = null; // Undefined
        channel.tsid = determineTsid(instance);
        channel.sid = determineSid(instance);
        channel.name = determineChannelName(instance);
        channel.majorChannel = determineMajorChannel(instance);
        channel.dsd = null; // Undefined
        channel.ipBroadcastID = null; // Undefined
        channel.terminalChannel = determineTerminalChannel(instance);
        channel.serviceInstances = null;
        channel.uri = instance.getDeliveryParameters().get("UriBasedLocation");
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

    private static String determineChannelType(DvbIServiceInstance instance) {
        return "TYPE_OTHER";
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
        if (sameDeliveryType && deliveryType != null && service.getTriplet() != null && !service.getTriplet().isEmpty()) {
            String lowerCaseDeliveryType = deliveryType.toLowerCase();
            if (lowerCaseDeliveryType.contains("dvb-c")) {
                return "ID_DVB_C";
            } else if (lowerCaseDeliveryType.contains("dvb-t")) {
                return "ID_DVB_T";
            } else if (lowerCaseDeliveryType.contains("dvb-s")) {
                return "ID_DVB_S";
            }
        }

        return "ID_DVB_I";
    }


    private static String determineIdType(DvbIServiceInstance instance) {
        return "ID_DVB_DASH";
    }

    private static String determineOnid(DvbIService service) {
        Triplet triplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getOrigNetId();
        }
        return null; // Undefined
    }

    private static String determineOnid(DvbIServiceInstance instance) {
        Triplet triplet = instance.getTriplet();
        if (triplet != null) {
            return triplet.getOrigNetId();
        }
        return null; // Undefined
    }

    private static String determineTsid(DvbIService service) {
        Triplet triplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getTsId();
        }
        return null; // Undefined
    }

    private static String determineTsid(DvbIServiceInstance instance) {
        Triplet triplet = instance.getTriplet();
        if (triplet != null) {
            return triplet.getTsId();
        }
        return null; // Undefined
    }

    private static String determineSid(DvbIService service) {
        Triplet triplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getServiceId();
        }
        return null; // Undefined
    }

    private static String determineSid(DvbIServiceInstance instance) {
        Triplet triplet = instance.getTriplet();
        if (triplet != null) {
            return triplet.getServiceId();
        }
        return null; // Undefined
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
        return null; // Undefined
    }

    private static String determineChannelName(DvbIServiceInstance instance) {
        //TODO: need to check for languages
        return instance.getServiceName();
    }

    private static String determineMajorChannel(DvbIService service) {
        return service.getLCNNumber();
    }

    private static String determineMajorChannel(DvbIServiceInstance instance) {
        //TODO: need to check LCN tablets
        return null; // Undefined
    }

    private static String determineTerminalChannel(DvbIService service) {
        // Logic to determine the terminalChannel
        return null; // Undefined
    }

    private static String determineTerminalChannel(DvbIServiceInstance instance) {
        // Logic to determine the terminalChannel
        return null; // Undefined
    }

    private static String generateCCID() {
        // Logic to generate a unique CCID
        return null; // Undefined
    }

    private static class Configuration {
        public static String preferredUILanguage;
    }

    // Getter methods for the channel attributes
    public String getChannelType() {
        return channelType;
    }

    public String getCcid() {
        return ccid;
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

    public void printChannelProperties() {
        System.out.println("--------------------------------");
        System.out.println("Channel Type: " + channelType);
        System.out.println("Id Type: " + idType);
        System.out.println("CCID: " + ccid);
        System.out.println("ONID: " + onid);
        System.out.println("NID: " + nid);
        System.out.println("TSID: " + tsid);
        System.out.println("SID: " + sid);
        System.out.println("Name: " + name);
        System.out.println("Major Channel: " + majorChannel);
        System.out.println("DSD: " + dsd);
        System.out.println("IP Broadcast ID: " + ipBroadcastID);
        System.out.println("Terminal Channel: " + terminalChannel);
        System.out.println("Uri: " + uri);
    }
}
