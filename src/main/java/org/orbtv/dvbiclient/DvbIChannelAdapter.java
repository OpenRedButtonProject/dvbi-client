package org.orbtv.dvbiclient;

import android.media.tv.TvContract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DvbIChannelAdapter {
    private String channelType;
    private String idType;
    private int onid;
    private String nid;
    private int tsid;
    private int sid;
    private String name;
    private String majorChannel;
    private String dsd;
    private String ipBroadcastID;
    private String terminalChannel;
    private DvbIService parentService;
    private List<DvbIServiceInstance> serviceInstances = new ArrayList<>();
    private List<String> appParallelUri;
    private List<String> appControlUri;

    public static DvbIChannelAdapter createChannel(DvbIService service, String preferredLanguage) {
        DvbIChannelAdapter channel = new DvbIChannelAdapter();
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
        channel.appParallelUri = determineApps(service, "1.1");
        channel.appControlUri = determineApps(service, "1.2");
        return channel;
    }

    public static DvbIChannelAdapter createChannel(DvbIService parentService, DvbIServiceInstance instance, String preferredLanguage) {
        DvbIChannelAdapter channel = new DvbIChannelAdapter();
        channel.channelType = determineChannelType(parentService);
        channel.idType = determineIdType(instance);
        channel.onid = determineOnid(instance, parentService);
        channel.nid = null; // Undefined
        channel.tsid = determineTsid(instance, parentService);
        channel.sid = determineSid(instance, parentService);
        channel.name = determineChannelName(parentService, instance, preferredLanguage);
        channel.majorChannel = determineMajorChannel(parentService);
        channel.dsd = null; // Undefined or will be populated later
        channel.ipBroadcastID = instance.getDeliveryParameters().get("UriBasedLocation");
        channel.terminalChannel = determineTerminalChannel(instance);
        channel.serviceInstances = null;
        channel.parentService = parentService;
        channel.appParallelUri = determineApps(instance, parentService, "1.1");
        channel.appControlUri = determineApps(instance, parentService, "1.2");
        return channel;
    }

    private static List<String> determineApps(DvbIService service, String appType) {
        List<RelatedMaterial> relatedMaterials = service.getRelatedMaterials();
        List<String> uris = new ArrayList<>();
        for (RelatedMaterial relatedMaterial : relatedMaterials) {
            String howRelatedHref = relatedMaterial.getHowRelatedHref();
            String mediaLocatorContentType = relatedMaterial.getMediaLocatorContentType();
            String howRelatedTermID = relatedMaterial.getHowRelatedTermID();
            if (howRelatedHref != null && mediaLocatorContentType != null &&
                    howRelatedHref.startsWith("urn:dvb:metadata:cs:LinkedApplicationCS:2019") &&
                    relatedMaterial.isXmlAitContentType() && appType.equals(howRelatedTermID)) {
                String xmlUri = relatedMaterial.getMediaLocatorUri();
                if (xmlUri != null && !xmlUri.isEmpty()) {
                    uris.add(xmlUri);
                }
            }
        }
        return uris;
    }

    private static List<String> determineApps(DvbIServiceInstance instance, DvbIService parentService, String appType) {
        List<RelatedMaterial> relatedMaterials = instance.getRelatedMaterials();
        List<String> uris = new ArrayList<>();
        for (RelatedMaterial relatedMaterial : relatedMaterials) {
            String howRelatedHref = relatedMaterial.getHowRelatedHref();
            String mediaLocatorContentType = relatedMaterial.getMediaLocatorContentType();
            String howRelatedTermID = relatedMaterial.getHowRelatedTermID();
            if (howRelatedHref != null && mediaLocatorContentType != null &&
                    howRelatedHref.startsWith("urn:dvb:metadata:cs:LinkedApplicationCS:2019") &&
                    relatedMaterial.isXmlAitContentType() && appType.equals(howRelatedTermID)) {
                String xmlUri = relatedMaterial.getMediaLocatorUri();
                if (xmlUri != null && !xmlUri.isEmpty()) {
                    uris.add(xmlUri);
                }
            }
        }

        if (uris.isEmpty()) {
            uris = determineApps(parentService, appType);
        }

        return uris;
    }

    private static String determineChannelType(DvbIService service) {
        String serviceType = service.getServiceType();

        if (serviceType == null || "urn:dvb:metadata:cs:ServiceTypeCS:2019:linear".equals(serviceType)) {
            return TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO;
            //return "TYPE_TV";
        } else if ("urn:dvb:metadata:cs:ServiceTypeCS:2019:linear-radio".equals(serviceType)) {
            return TvContract.Channels.SERVICE_TYPE_AUDIO;
            //return "TYPE_RADIO";
        } else if ("urn:dvb:metadata:cs:ServiceTypeCS:2019:data".equals(serviceType)) {
            List<RelatedMaterial> relatedMaterials = service.getRelatedMaterials();
            for (RelatedMaterial relatedMaterial : relatedMaterials) {
                if (isHbbtvData(relatedMaterial)) {
                    //return "TYPE_HBBTV_DATA";
                    return "SERVICE_TYPE_DATA";
                }
            }
        }
        return TvContract.Channels.SERVICE_TYPE_OTHER;
        //return "TYPE_OTHER";
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
        if (sameDeliveryType && deliveryType != null && service.getTriplet() != null) {
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


    private static int determineOnid(DvbIService service) {
        Triplet triplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getOrigNetId();
        }
        return 0;
    }

    private static int determineOnid(DvbIServiceInstance instance, DvbIService service) {
        Triplet triplet = instance.getTriplet();
        Triplet parentTriplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getOrigNetId();
        } else if(parentTriplet != null) {
            return parentTriplet.getOrigNetId();
        }
        return 0;
    }

    private static int determineTsid(DvbIService service) {
        Triplet triplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getTsId();
        }
        return 0;
    }

    private static int determineTsid(DvbIServiceInstance instance, DvbIService service) {
        Triplet triplet = instance.getTriplet();
        Triplet parentTriplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getTsId();
        } else if(parentTriplet != null) {
            return parentTriplet.getTsId();
        }
        return 0;
    }

    private static int determineSid(DvbIService service) {
        Triplet triplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getServiceId();
        }
        return 0;
    }

    private static int determineSid(DvbIServiceInstance instance, DvbIService service) {
        Triplet triplet = instance.getTriplet();
        Triplet parentTriplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getServiceId();
        } else if(parentTriplet != null) {
            return parentTriplet.getServiceId();
        }
        return 0;
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

    private static String determineChannelName(DvbIService parentService, DvbIServiceInstance instance, String preferredLanguage) {
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

    public String getIdType() {
        return idType;
    }

    public int getOnid() {
        return onid;
    }

    public String getNid() {
        return nid;
    }

    public int getTsid() {
        return tsid;
    }

    public int getSid() {
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

    public List<String> getAppParallelUris() {
        return appParallelUri;
    }

    public List<String> getAppControlUris() {
        return appControlUri;
    }

    @Override
    public String toString() {
        String ret = "Channel Type: " + channelType
            + "\nId Type:" + idType
            + "\nONID:" + String.format(Locale.ENGLISH, "%d", onid)
            + "\nNID:" + nid
            + "\nTSID:" + String.format(Locale.ENGLISH, "%d", tsid)
            + "\nSID:" + String.format(Locale.ENGLISH, "%d", sid)
            + "\nName:" + name
            + "\nMajor Channel:" + majorChannel
            + "\nDSD:" + dsd
            + "\nIP Broadcast ID:" + ipBroadcastID
            + "\nTerminal Channel:" + terminalChannel;

        if (serviceInstances != null) {
            int numInstances = serviceInstances.size();
            ret += "\nNumber of Instances: " + numInstances;
        }
        return ret;
    }
}
