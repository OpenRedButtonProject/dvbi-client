package org.orbtv.dvbiclient.model;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceInstance implements IService {
    private Map<String, String> mDisplayNames = new HashMap<>();
    private String mServiceName;
    private int mPriority;
    private List<AvailabilityPeriod> mAvailabilityPeriods = new ArrayList<>();
    private Triplet mTriplet;
    private String mDeliveryType;
    private Map<String, String> mDeliveryParameters = new HashMap<>();
    private List<RelatedMaterial> mRelatedMaterials = new ArrayList<>();

    private ServiceInstance() { }

    public Map<String, String> getDisplayNames() {
        return mDisplayNames;
    }

    public String getServiceName() {
        return mServiceName;
    }

    public int getPriority() {
        return mPriority;
    }

    public List<AvailabilityPeriod> getAvailabilityPeriods() {
        return mAvailabilityPeriods;
    }

    public String getUri() {
        return mDeliveryParameters.get("UriBasedLocation");
    }

    public Triplet getTriplet() {
        return mTriplet;
    }

    public String getDeliveryType() {
        return mDeliveryType;
    }

    public Map<String, String> getDeliveryParameters() {
        return mDeliveryParameters;
    }

    public List<RelatedMaterial> getRelatedMaterials() {
        return mRelatedMaterials;
    }

    @Override
    public String toString() {
        String ret = "- " + this.getClass().getSimpleName() + " -"
                + "\ndisplayName: " + this.mDisplayNames
                + "\ndeliveryParams: " + this.mDeliveryParameters
                + "\npriority: " + this.mPriority
                + "\ntriplet: " + this.mTriplet
                + "\ndelivery type: " + this.mDeliveryType;
        for (RelatedMaterial mat : mRelatedMaterials) {
            ret += "\n" + mat.toString();
        }
        return ret;
    }

    public static ServiceInstance parseFromXML(XmlPullParser xpp) throws Exception {
        ServiceInstance instance = new ServiceInstance();
        int eventType = xpp.getEventType();
        while (!(eventType == XmlPullParser.END_TAG && "ServiceInstance".equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                switch (xpp.getName()) {
                    case "DisplayName":
                        String language = xpp.getAttributeValue(null, "lang");
                        String name = xpp.nextText();
                        instance.mDisplayNames.put(language, name);
                        break;
                    case "ServiceInstance":
                        instance.mPriority = Integer.parseInt(xpp.getAttributeValue(null, "priority"));
                        break;
                    case "Availability":
                        instance.mAvailabilityPeriods = AvailabilityPeriod.parseFromXML(xpp);
                        break;
                    case "RelatedMaterial":
                        instance.mRelatedMaterials.add(RelatedMaterial.parseFromXML(xpp));
                        break;
                    case "DVBTriplet":
                        instance.mTriplet = Triplet.parseFromXML(xpp);
                        break;
                    case "ServiceName":
                        instance.mServiceName = xpp.nextText();
                        break;
                    case "SourceType":
                        instance.mDeliveryType = parseServiceType(xpp.nextText());
                        break;
                    case "SATIPDeliveryParameters":
                        instance.mDeliveryType = "sat-ip";
                        parseDeliveryParameters(instance, xpp);
                        break;
                    case "DVBSDeliveryParameters":
                        instance.mDeliveryType = "dvb-s";
                        parseDeliveryParameters(instance, xpp);
                        break;
                    case "DVBCDeliveryParameters":
                        instance.mDeliveryType = "dvb-c";
                        parseDeliveryParameters(instance, xpp);
                        break;
                    case "DASHDeliveryParameters":
                        instance.mDeliveryType = "dvb-dash";
                        parseDeliveryParameters(instance, xpp);
                        break;
                    case "DVBTDeliveryParameters":
                        instance.mDeliveryType = "dvb-t";
                        parseDeliveryParameters(instance, xpp);
                        break;
                }
            }
            eventType = xpp.next();
        }
        return instance;
    }

    private static void parseDeliveryParameters(ServiceInstance instance, XmlPullParser xpp) throws Exception {
        String deliveryType = xpp.getName();
        int eventType = xpp.next();
        while (!(eventType == XmlPullParser.END_TAG && deliveryType.equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                switch (xpp.getName()) {
                    case "UriBasedLocation":
                        instance.mDeliveryParameters.put("UriBasedLocation", parseUriBasedLocation(xpp));
                        break;
                    case "DVBTriplet":
                        instance.mTriplet = Triplet.parseFromXML(xpp);
                        if (instance.mTriplet != null) {
                            instance.mDeliveryParameters.put("DVBTriplet", instance.mTriplet.toString());
                        }
                        break;
                    default:
                        break;
                }
            }
            eventType = xpp.next();
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

    public static class Builder {
        private ServiceInstance mInstance;
        public Builder() {
            mInstance = new ServiceInstance();
        }
        public ServiceInstance.Builder setDisplayNames(Map<String, String> value) {
            mInstance.mDisplayNames = value;
            return this;
        }
        public ServiceInstance.Builder setServiceName(String value) {
            mInstance.mServiceName = value;
            return this;
        }
        public ServiceInstance.Builder setPriority(int value) {
            mInstance.mPriority = value;
            return this;
        }
        public ServiceInstance.Builder setAvailabilityPeriods(List<AvailabilityPeriod> value) {
            mInstance.mAvailabilityPeriods = value;
            return this;
        }
        public ServiceInstance.Builder setTriplet(Triplet value) {
            mInstance.mTriplet = value;
            return this;
        }
        public ServiceInstance.Builder setDeliveryType(String value) {
            mInstance.mDeliveryType = value;
            return this;
        }
        public ServiceInstance.Builder setDeliveryParameters(Map<String, String> value) {
            mInstance.mDeliveryParameters = value;
            return this;
        }
        public ServiceInstance.Builder setRelatedMaterials(List<RelatedMaterial> value) {
            mInstance.mRelatedMaterials = value;
            return this;
        }
        public ServiceInstance build() {
            ServiceInstance instance = new ServiceInstance();
            instance.mDisplayNames = mInstance.mDisplayNames;
            instance.mServiceName = mInstance.mServiceName;
            instance.mPriority = mInstance.mPriority;
            instance.mAvailabilityPeriods = mInstance.mAvailabilityPeriods;
            instance.mTriplet = mInstance.mTriplet;
            instance.mDeliveryType = mInstance.mDeliveryType;
            instance.mDeliveryParameters = mInstance.mDeliveryParameters;
            instance.mRelatedMaterials = mInstance.mRelatedMaterials;
            return instance;
        }
    }
}
