package org.orbtv.dvbiclient;

import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DvbIServiceInstance {
    private String displayName;
    private String serviceName;
    private int priority;
    private InstanceAvailabilityPeriod availabilityPeriods;
    private Triplet triplet;
    private String deliveryType;
    private Map<String, String> deliveryParameters = new HashMap<>();
    private List<RelatedMaterial> relatedMaterials = new ArrayList<>();

    private DvbIServiceInstance() {
    }

    public DvbIServiceInstance(String displayName, int priority, String deliveryType, JSONObject deliveryParams, 
                                    List<RelatedMaterial> relatedMaterials, InstanceAvailabilityPeriod availabilityPeriods) {
        Iterator<String> keys = deliveryParams.keys();
        this.displayName = displayName;
        this.priority = priority;
        this.relatedMaterials = relatedMaterials;
        this.deliveryType = deliveryType;
        this.availabilityPeriods = availabilityPeriods;
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                this.deliveryParameters.put(key, deliveryParams.get(key).toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        try {
            this.triplet = new Triplet(deliveryParams.getString("DVBTriplet"));
        } catch (Exception e) {
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
                        System.out.println("GEP found availability1 !!!!!!");
                        instance.availabilityPeriods = InstanceAvailabilityPeriod.parseFromXML(xpp);
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

        int eventType = xpp.next();
        while (!(eventType == XmlPullParser.END_TAG && deliveryType.equals(xpp.getName()))) {
            if (eventType == XmlPullParser.START_TAG) {
                switch (xpp.getName()) {
                    case "UriBasedLocation":
                        instance.deliveryParameters.put("UriBasedLocation", parseUriBasedLocation(xpp));
                        break;
                    case "DVBTriplet":
                        instance.triplet = Triplet.parseFromXML(xpp);
                        if (instance.triplet != null) {
                            instance.deliveryParameters.put("DVBTriplet", instance.triplet.toString());
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

    @Override
    public String toString() {
        String ret = "- " + this.getClass().getSimpleName() + " -"
                + "\ndisplayName: " + this.displayName
                + "\ndeliveryParams: " + this.deliveryParameters
                + "\npriority: " + this.priority
                + "\ntriplet: " + this.triplet
                + "\ndelivery type: " + this.deliveryType;
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

    public InstanceAvailabilityPeriod getAvailabilityPeriod() {
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
