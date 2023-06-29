package org.orbtv.dvbiclient;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DvbIService {
    private String uniqueIdentifier;
    private String serviceName;
    private String providerName;
    private String serviceType;
    private Triplet triplet;
    private List<RelatedMaterial> relatedMaterials = new ArrayList<>();
    private Map<String, String> additionalParameters = new HashMap<>();
    private List<DvbIServiceInstance> instances = new ArrayList<>();

    public static DvbIService parseFromXML(String xml) throws Exception {
        DvbIService service = new DvbIService();
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();
        xpp.setInput(new StringReader(xml));

        int eventType = xpp.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                switch (xpp.getName()) {
                    case "UniqueIdentifier":
                        service.uniqueIdentifier = xpp.nextText();
                        break;
                    case "ServiceName":
                        service.serviceName = xpp.nextText();
                        break;
                    case "ServiceType":
                        service.serviceType = xpp.nextText();
                        break;
                    case "ProviderName":
                        service.providerName = xpp.nextText();
                        break;
                    case "RelatedMaterial":
                        RelatedMaterial relatedMaterial = RelatedMaterial.parseFromXML(xpp);
                        service.relatedMaterials.add(relatedMaterial);
                        break;
                    case "AdditionalServiceParameters":
                        parseAdditionalParameters(service, xpp);
                        break;
                    case "ServiceInstance":
                        DvbIServiceInstance instance = DvbIServiceInstance.parseFromXML(xpp);
                        service.instances.add(instance);
                        break;
                }
            }
            eventType = xpp.next();
        }
        return service;
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

    public String getServiceName() {
        return serviceName;
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

    public Map<String, String> getAdditionalParameters() {
        return additionalParameters;
    }

    public List<DvbIServiceInstance> getInstances() {
        return instances;
    }

    public Triplet getTriplet() {
        return triplet;
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
                    case "UriBasedLocation":
                        instance.uri = xpp.nextText();
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
                    case "DVBSDeliveryParameters":
                    case "DVBCDeliveryParameters":
                    case "DASHDeliveryParameters":
                    case "DVBTDeliveryParameters":
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
            }
            eventType = xpp.next();
        }

        if (uri != null) {
            instance.deliveryParameters.put(deliveryType, uri);
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
}

class Triplet {
    private String origNetId;
    private String tsId;
    private String serviceId;

    public static Triplet parseFromXML(XmlPullParser xpp) throws Exception {
        Triplet triplet = new Triplet();
        int eventType = xpp.getEventType();
        while (!(eventType == XmlPullParser.END_TAG && "DVBTriplet".equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                switch (xpp.getName()) {
                    case "origNetId":
                        triplet.origNetId = xpp.nextText();
                        break;
                    case "tsId":
                        triplet.tsId = xpp.nextText();
                        break;
                    case "serviceId":
                        triplet.serviceId = xpp.nextText();
                        break;
                }
            }
            eventType = xpp.next();
        }
        return triplet;
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
    private String mediaLocatorUri;

    public static RelatedMaterial parseFromXML(XmlPullParser xpp) throws Exception {
        RelatedMaterial relatedMaterial = new RelatedMaterial();
        int eventType = xpp.getEventType();
        while (!(eventType == XmlPullParser.END_TAG && "RelatedMaterial".equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                switch (xpp.getName()) {
                    case "HowRelated":
                        relatedMaterial.howRelatedHref = xpp.getAttributeValue(null, "href");
                        break;
                    case "MediaLocator":
                        relatedMaterial.mediaLocatorUri = xpp.getAttributeValue(null, "mediaUri");
                        break;
                }
            }
            eventType = xpp.next();
        }
        return relatedMaterial;
    }

    public String getHowRelatedHref() {
        return howRelatedHref;
    }

    public String getMediaLocatorUri() {
        return mediaLocatorUri;
    }
}

class DVBIChannel {
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
    private List<DvbIServiceInstance> serviceInstances = new ArrayList<>();

    public static DVBIChannel createChannel(DvbIService service) {
        DVBIChannel channel = new DVBIChannel();
        channel.channelType = determineChannelType(service);
        channel.idType = determineIdType(service);
        channel.ccid = generateCCID();
        channel.onid = determineOnid(service);
        channel.nid = null; // Undefined
        channel.tsid = determineTsid(service);
        channel.sid = determineSid(service);
        channel.name = determineChannelName(service);
        channel.majorChannel = determineMajorChannel(service);
        channel.dsd = null; // Undefined
        channel.ipBroadcastID = service.getUniqueIdentifier();
        channel.terminalChannel = determineTerminalChannel(service);
        channel.serviceInstances = service.getInstances();
        return channel;
    }

    private static String determineChannelType(DvbIService service) {
        if (service.getServiceType().equals("urn:dvb:metadata:cs:ServiceTypeCS:2019:linear-radio")) {
            return "TYPE_RADIO";
        } else if (service.getServiceType().equals("urn:dvb:metadata:cs:ServiceTypeCS:2019:linear") ||
                service.getServiceType() == null) {
            return "TYPE_TV";
        } else if (service.getServiceType().equals("urn:dvb:metadata:cs:ServiceTypeCS:2019:data")) {
            for (RelatedMaterial relatedMaterial : service.getRelatedMaterials()) {
                if (relatedMaterial.getHowRelatedHref().equals("urn:dvb:metadata:cs:LinkedApplicationCS:2019") &&
                        relatedMaterial.getMediaLocatorUri().contains("application/vnd.dvb.ait+xml")) {
                    return "TYPE_HBBTV_DATA";
                }
            }
        }
        return "TYPE_OTHER";
    }

    private static String determineIdType(DvbIService service) {
        List<DvbIServiceInstance> instances = service.getInstances();
        String deliveryType = null;

        // Check if all instances have the same deliveryType
        boolean sameDeliveryType = true;
        for (DvbIServiceInstance instance : instances) {
            String instanceDeliveryType = instance.getDeliveryType();
            if (deliveryType == null) {
                deliveryType = instanceDeliveryType;
            } else if (!deliveryType.equalsIgnoreCase(instanceDeliveryType)) {
                sameDeliveryType = false;
                break;
            }
        }

        // Determine the appropriate ID type based on the deliveryType
        if (sameDeliveryType) {
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

    private static String determineOnid(DvbIService service) {
        Triplet triplet = service.getTriplet();
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

    private static String determineSid(DvbIService service) {
        Triplet triplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getServiceId();
        }
        return null; // Undefined
    }

    private static String determineChannelName(DvbIService service) {
        //TODO: need to check for languages
        return service.getServiceName();
    }

    private static String determineMajorChannel(DvbIService service) {
        //TODO: need to check LCN tablets
        return null; // Undefined
    }

    private static String determineTerminalChannel(DvbIService service) {
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
}
