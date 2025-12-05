package org.orbtv.dvbiclient;

import android.media.tv.TvContract;

import org.orbtv.dvbiclient.model.IService;
import org.orbtv.dvbiclient.model.RelatedMaterial;
import org.orbtv.dvbiclient.model.Service;
import org.orbtv.dvbiclient.model.ServiceInstance;
import org.orbtv.dvbiclient.model.Triplet;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DvbIChannelAdapter {
    private static String PreferredUILanguage = "";
    private String mChannelType;
    private int mIdType;
    private int mOnid;
    private String mNid;
    private int mTsid;
    private int mSid;
    private String mName;
    private String mMajorChannel;
    private String mDsd;
    private String mIpBroadcastID;
    private String mTerminalChannel;
    private HashMap<String, String> mLinkedAppUris = new HashMap<>();

    private DvbIChannelAdapter(Service service) {
        this.mChannelType = determineChannelType(service);
        this.mIdType = determineIdType(service);
        this.mOnid = determineOnid(service, null);
        this.mNid = null; // Undefined or will be populated later
        this.mTsid = determineTsid(service, null);
        this.mSid = determineSid(service, null);
        this.mName = determineChannelName(service, null);
        this.mMajorChannel = determineMajorChannel(service);
        this.mDsd = null; // Undefined
        this.mIpBroadcastID = service.getUniqueIdentifier();
        this.mTerminalChannel = determineTerminalChannel(service);
        determineApps(service, null);
    }

    private DvbIChannelAdapter(Service parentService, ServiceInstance instance) {
        this.mChannelType = determineChannelType(parentService);
        this.mIdType = determineIdType(instance);
        this.mOnid = determineOnid(instance, parentService);
        this.mNid = null; // Undefined
        this.mTsid = determineTsid(instance, parentService);
        this.mSid = determineSid(instance, parentService);
        this.mName = determineChannelName(instance, parentService);
        this.mMajorChannel = determineMajorChannel(parentService);
        this.mDsd = null; // Undefined or will be populated later
        String uriBasedLocation = instance.getDeliveryParameters().get("UriBasedLocation");
        // If UriBasedLocation is not available, check RelatedMaterial for MediaUri
        if (uriBasedLocation == null || uriBasedLocation.isEmpty()) {
            List<RelatedMaterial> relatedMaterials = instance.getRelatedMaterials();
            for (RelatedMaterial relatedMaterial : relatedMaterials) {
                String mediaUri = relatedMaterial.getMediaLocatorUri();
                if (mediaUri != null && !mediaUri.isEmpty()) {
                    uriBasedLocation = mediaUri;
                    break;
                }
            }
        }
        // Fallback to parent service's unique identifier if UriBasedLocation is still not available
        this.mIpBroadcastID = (uriBasedLocation != null && !uriBasedLocation.isEmpty()) 
                ? uriBasedLocation 
                : (parentService != null ? parentService.getUniqueIdentifier() : null);
        this.mTerminalChannel = determineTerminalChannel(instance);
        determineApps(instance, parentService);
    }

    // Getter methods for the channel attributes
    public String getChannelType() { return mChannelType; }
    public int getIdType() { return mIdType; }
    public int getOnid() { return mOnid; }
    public String getNid() { return mNid; }
    public int getTsid() { return mTsid; }
    public int getSid() { return mSid; }
    public String getName() { return mName; }
    public String getMajorChannel() { return mMajorChannel; }
    public String getDsd() { return mDsd; }
    public String getIpBroadcastID() { return mIpBroadcastID; }
    public String getTerminalChannel() { return mTerminalChannel; }
    public String getLinkedAppUri(String scheme) { return mLinkedAppUris.get(scheme); }

    @Override
    public String toString() {
        String ret = "Channel Type: " + mChannelType
                + "\nId Type:" + mIdType
                + "\nONID:" + String.format(Locale.ENGLISH, "%d", mOnid)
                + "\nNID:" + mNid
                + "\nTSID:" + String.format(Locale.ENGLISH, "%d", mTsid)
                + "\nSID:" + String.format(Locale.ENGLISH, "%d", mSid)
                + "\nName:" + mName
                + "\nMajor Channel:" + mMajorChannel
                + "\nDSD:" + mDsd
                + "\nIP Broadcast ID:" + mIpBroadcastID
                + "\nTerminal Channel:" + mTerminalChannel;
        return ret;
    }

    private void determineApps(IService service, IService fallback) {
        List<RelatedMaterial> relatedMaterials = service.getRelatedMaterials();
        for (RelatedMaterial relatedMaterial : relatedMaterials) {
            String howRelatedHref = relatedMaterial.getHowRelatedHref();
            String mediaLocatorContentType = relatedMaterial.getMediaLocatorContentType();
            if (howRelatedHref != null && !mLinkedAppUris.containsKey(howRelatedHref) && mediaLocatorContentType != null &&
                    (relatedMaterial.isXmlAitContentType() || DvbIClient.LINKED_APP_SCHEME_1000_1.equals(howRelatedHref))) {
                String xmlUri = relatedMaterial.getMediaLocatorUri();
                if (xmlUri != null && !xmlUri.isEmpty()) {
                    mLinkedAppUris.put(howRelatedHref, xmlUri);
                }
            }
        }
        if (fallback != null) {
            determineApps(fallback, null);
        }
    }

    private String determineChannelType(Service service) {
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

    private boolean isHbbtvData(RelatedMaterial relatedMaterial) {
        String howRelatedHref = relatedMaterial.getHowRelatedHref();
        String mediaLocatorContentType = relatedMaterial.getMediaLocatorContentType();

        return howRelatedHref != null && mediaLocatorContentType != null &&
                howRelatedHref.startsWith("urn:dvb:metadata:cs:LinkedApplicationCS:2019") &&
                relatedMaterial.isXmlAitContentType();
    }

    private int determineIdType(Service service) {
        List<ServiceInstance> instances = service.getInstances();
        String deliveryType = null;

        boolean sameDeliveryType = true;
        for (ServiceInstance instance : instances) {
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
                    //return "ID_DVB_C";
                    return 10;
                case "dvb-t":
                    //return "ID_DVB_T";
                    return 12;
                case "dvb-s":
                    //return "ID_DVB_S";
                    return 11;
            }
        }

        return 50;
    }


    private int determineIdType(ServiceInstance instance) {
        String deliveryType = instance.getDeliveryType();

        if (deliveryType != null && !deliveryType.isEmpty()) {
            switch (deliveryType.toLowerCase()) {
                case "dvb-c":
                    //return "ID_DVB_C";
                    return 10;
                case "dvb-t":
                    //return "ID_DVB_T";
                    return 12;
                case "dvb-s":
                    //return "ID_DVB_S";
                    return 11;
                case "dvb-dash":
                    //return "ID_DVB_DASH";
                    return 51;
                default:
                    // For delivery types not supported by the emulator (e.g., non-broadcast types
                    // that are not dvb-dash), return ID_OTHER to indicate they are detected but
                    // not fully supported
                    //return "ID_OTHER";
                    return 52; // BridgeTypes.Channel.ID_OTHER
            }
        }
        // If deliveryType is null or empty, also return ID_OTHER
        //return "ID_OTHER";
        return 52; // BridgeTypes.Channel.ID_OTHER
    }

    private int determineOnid(IService service, IService fallback) {
        Triplet triplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getOrigNetId();
        } else if(fallback != null) {
            return determineOnid(fallback, null);
        }
        return 0;
    }

    private int determineTsid(IService service, IService fallback) {
        Triplet triplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getTsId();
        } else if(fallback != null) {
            return determineTsid(fallback, null);
        }
        return 0;
    }

    private int determineSid(IService service, IService fallback) {
        Triplet triplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getServiceId();
        } else if(fallback != null) {
            return determineSid(fallback, null);
        }
        return 0;
    }

    private String determineChannelName(IService service, IService fallback) {
        Map<String, String> names = service.getDisplayNames();
        if (names != null && !names.isEmpty()) {
            if (PreferredUILanguage != null && !PreferredUILanguage.isEmpty()) {
                String name = names.get(convertThreeToTwoLetterCode(PreferredUILanguage));
                if (name != null) {
                    return name;
                }
            }

            // If the preferred language is not found or empty, select the first available name
            for (String value : names.values()) {
                return value;
            }
        }
        if (fallback != null) {
            return determineChannelName(fallback, null);
        }
        return null;
    }

    public String convertThreeToTwoLetterCode(String threeLetterCode) {
        for (Locale locale : Locale.getAvailableLocales()) {
            if (threeLetterCode.equals(locale.getISO3Language())) {
                return locale.getLanguage();
            }
        }
        return null;  // or throw an exception if appropriate
    }

    private String determineMajorChannel(Service service) {
        return service.getLCNNumber();
    }

    private String determineTerminalChannel(IService service) {
        return null; // Undefined - can be determined later
    }

    public static void setPreferredUILanguage(String lang) {
        PreferredUILanguage = lang;
    }

    public static class Builder {
        private Service mService = null;
        private ServiceInstance mServiceInstance = null;

        public DvbIChannelAdapter.Builder setService(Service value) {
            mService = value;
            return this;
        }
        public DvbIChannelAdapter.Builder setServiceInstance(ServiceInstance value) {
            mServiceInstance = value;
            return this;
        }
        public DvbIChannelAdapter build() {
            if (mService != null) {
                if (mServiceInstance != null) {
                    return new DvbIChannelAdapter(mService, mServiceInstance);
                }
                return new DvbIChannelAdapter(mService);
            }
            return null;
        }
    }
}
