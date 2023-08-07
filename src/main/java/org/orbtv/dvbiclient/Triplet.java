package org.orbtv.dvbiclient;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class Triplet {
    private int origNetId = 0;
    private int tsId = 0;
    private int serviceId = 0;

    private Triplet() {
    }

    public Triplet(String uri) throws Exception {
        if (uri != null) {
            String[] triplet = uri.substring(6).split("\\.");
            this.origNetId = Integer.parseInt(triplet[0], 16);
            this.tsId = Integer.parseInt(triplet[1], 16);
            this.serviceId = Integer.parseInt(triplet[2], 16);
        } else {
            throw new Exception("Uri argument must be non null");
        }
    }

    public static Triplet parseFromXML(XmlPullParser xpp) throws Exception {
        Triplet triplet = null;
        int eventType = xpp.getEventType();
        try {
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG &&
                        ("DVBTriplet".equals(xpp.getName()) || "hbbtv-i:DVBTriplet".equals(xpp.getName()))) {
                    triplet = new Triplet();
                    String origNetId = xpp.getAttributeValue(null, "origNetId");
                    String tsId = xpp.getAttributeValue(null, "tsId");
                    String serviceId = xpp.getAttributeValue(null, "serviceId");
                    triplet.origNetId = origNetId != null ? Integer.parseInt(origNetId) : 0;
                    triplet.tsId = tsId != null ? Integer.parseInt(tsId) : 0;
                    triplet.serviceId = serviceId != null ? Integer.parseInt(serviceId) : 0;
                    break;
                }
                eventType = xpp.next();
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            //triplet = new Triplet(); // Return an empty Triplet object in case of an error
        }
        return triplet;
    }

    public int getOrigNetId() {
        return origNetId;
    }

    public int getTsId() {
        return tsId;
    }

    public int getServiceId() {
        return serviceId;
    }

    @Override
    public String toString() {
        return "dvb://" + String.format("%04x", origNetId) + "." + String.format("%04x", tsId) + "." + String.format("%04x", serviceId);
    }
}
