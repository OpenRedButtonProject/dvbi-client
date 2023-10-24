package org.orbtv.dvbiclient;

import android.media.tv.TvContract;

import org.orbtv.dvbiclient.model.IService;
import org.orbtv.dvbiclient.model.RelatedMaterial;
import org.orbtv.dvbiclient.model.Service;
import org.orbtv.dvbiclient.model.ServiceInstance;
import org.orbtv.dvbiclient.model.Triplet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

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
    private List<String> mAppParallelUri;
    private List<String> mAppControlUri;
    private List<String> mAppInactiveUri;
    private List<String> mOutOfServiceImage;

    private DvbIChannelAdapter(Service service) {
        this.mChannelType = determineChannelType(service);
        this.mIdType = determineIdType(service);
        this.mOnid = determineOnid(service);
        this.mNid = null; // Undefined or will be populated later
        this.mTsid = determineTsid(service);
        this.mSid = determineSid(service);
        this.mName = determineChannelName(service.getServiceNames(), PreferredUILanguage);
        this.mMajorChannel = determineMajorChannel(service);
        this.mDsd = null; // Undefined
        this.mIpBroadcastID = service.getUniqueIdentifier();
        this.mTerminalChannel = determineTerminalChannel(service);
        this.mAppParallelUri = determineApps(service, null, DvbIClient.LINKED_APP_SCHEME_1_1);
        this.mAppControlUri = determineApps(service, null, DvbIClient.LINKED_APP_SCHEME_1_2);
        this.mAppInactiveUri = determineApps(service, null, DvbIClient.LINKED_APP_SCHEME_2);
        this.mOutOfServiceImage = determineApps(service, null, DvbIClient.LINKED_APP_SCHEME_1000_1);
    }

    private DvbIChannelAdapter(Service parentService, ServiceInstance instance) {
        this.mChannelType = determineChannelType(parentService);
        this.mIdType = determineIdType(instance);
        this.mOnid = determineOnid(instance, parentService);
        this.mNid = null; // Undefined
        this.mTsid = determineTsid(instance, parentService);
        this.mSid = determineSid(instance, parentService);
        this.mName = determineChannelName(parentService, instance, PreferredUILanguage);
        this.mMajorChannel = determineMajorChannel(parentService);
        this.mDsd = null; // Undefined or will be populated later
        this.mIpBroadcastID = instance.getDeliveryParameters().get("UriBasedLocation");
        this.mTerminalChannel = determineTerminalChannel(instance);
        this.mAppParallelUri = determineApps(instance, parentService, DvbIClient.LINKED_APP_SCHEME_1_1);
        this.mAppControlUri = determineApps(instance, parentService, DvbIClient.LINKED_APP_SCHEME_1_2);
        this.mAppInactiveUri = determineApps(instance, parentService, DvbIClient.LINKED_APP_SCHEME_2);
        this.mOutOfServiceImage = determineApps(instance, parentService, DvbIClient.LINKED_APP_SCHEME_1000_1);
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
    public List<String> getAppParallelUris() { return mAppParallelUri; }
    public List<String> getAppControlUris() { return mAppControlUri; }
    public List<String> getAppInactiveUris() { return mAppInactiveUri; }
    public List<String> getOutOfServiceImages() { return mOutOfServiceImage; }

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

    private List<String> determineApps(IService service, IService fallbackService, String appType) {
        List<RelatedMaterial> relatedMaterials = service.getRelatedMaterials();
        List<String> uris = new ArrayList<>();
        for (RelatedMaterial relatedMaterial : relatedMaterials) {
            String howRelatedHref = relatedMaterial.getHowRelatedHref();
            String mediaLocatorContentType = relatedMaterial.getMediaLocatorContentType();
            if (howRelatedHref != null && mediaLocatorContentType != null &&
                    (relatedMaterial.isXmlAitContentType() || DvbIClient.LINKED_APP_SCHEME_1000_1.equals(howRelatedHref)) &&
                    appType.equals(howRelatedHref)) {
                String xmlUri = relatedMaterial.getMediaLocatorUri();
                if (xmlUri != null && !xmlUri.isEmpty()) {
                    uris.add(xmlUri);
                }
            }
        }
        if (uris.isEmpty() && fallbackService != null) {
            uris = determineApps(fallbackService, null, appType);
        }
        return uris;
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
            }
        }
        //return "UNSUPPORTED"; //default value? also support for T2, S2
        return -1;
    }


    private int determineOnid(Service service) {
        Triplet triplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getOrigNetId();
        }
        return 0;
    }

    private int determineOnid(ServiceInstance instance, Service service) {
        Triplet triplet = instance.getTriplet();
        Triplet parentTriplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getOrigNetId();
        } else if(parentTriplet != null) {
            return parentTriplet.getOrigNetId();
        }
        return 0;
    }

    private int determineTsid(Service service) {
        Triplet triplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getTsId();
        }
        return 0;
    }

    private int determineTsid(ServiceInstance instance, Service service) {
        Triplet triplet = instance.getTriplet();
        Triplet parentTriplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getTsId();
        } else if(parentTriplet != null) {
            return parentTriplet.getTsId();
        }
        return 0;
    }

    private int determineSid(Service service) {
        Triplet triplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getServiceId();
        }
        return 0;
    }

    private int determineSid(ServiceInstance instance, Service service) {
        Triplet triplet = instance.getTriplet();
        Triplet parentTriplet = service.getTriplet();
        if (triplet != null) {
            return triplet.getServiceId();
        } else if(parentTriplet != null) {
            return parentTriplet.getServiceId();
        }
        return 0;
    }

    private String determineChannelName(Map<String, String> names, String preferredLanguage) {
        if (names != null && !names.isEmpty()) {
            if (preferredLanguage != null && !preferredLanguage.isEmpty()) {
                String name = names.get(convertThreeToTwoLetterCode(preferredLanguage));
                if (name != null) {
                    return name;
                }
            }

            // If the preferred language is not found or empty, select the first available name
            for (String value : names.values()) {
                return value;
            }
        }
        return null;
    }

    private String determineChannelName(Service parentService, ServiceInstance instance, String preferredLanguage) {
        String displayName = determineChannelName(instance.getDisplayNames(), preferredLanguage);
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }

        return determineChannelName(parentService.getServiceNames(), preferredLanguage);
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

    private String determineTerminalChannel(Service service) {
        return null; // Undefined - can be determined later
    }

    private String determineTerminalChannel(ServiceInstance instance) {
        // table O.3 is missing the property completely (error?)
        return null; // Undefined
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
