package org.orbtv.dvbiclient.model;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class Triplet {
    private int mOrigNetId = 0;
    private int mTsId = 0;
    private int mServiceId = 0;

    private Triplet() { }

    public int getOrigNetId() {
        return mOrigNetId;
    }

    public int getTsId() {
        return mTsId;
    }

    public int getServiceId() {
        return mServiceId;
    }

    @Override
    public String toString() {
        return "dvb://" + String.format("%04x", mOrigNetId) + "." + String.format("%04x", mTsId) + "." + String.format("%04x", mServiceId);
    }

    public static Triplet parseFromXML(XmlPullParser xpp) throws Exception {
        Triplet triplet = null;
        int eventType = xpp.getEventType();
        try {
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG &&
                        ("DVBTriplet".equals(xpp.getName()) || "hbbtv-i:DVBTriplet".equals(xpp.getName()))) {
                    String origNetId = xpp.getAttributeValue(null, "origNetId");
                    String tsId = xpp.getAttributeValue(null, "tsId");
                    String serviceId = xpp.getAttributeValue(null, "serviceId");
                    triplet = new Builder()
                            .setOrigNetId(origNetId != null ? Integer.parseInt(origNetId) : 0)
                            .setTsId(tsId != null ? Integer.parseInt(tsId) : 0)
                            .setServiceId(serviceId != null ? Integer.parseInt(serviceId) : 0)
                            .build();
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

    public static Triplet parseFromURI(String uri) throws Exception {
        if (uri != null) {
            String[] triplet = uri.substring(6).split("\\.");
            return new Builder()
                    .setOrigNetId(Integer.parseInt(triplet[0], 16))
                    .setTsId(Integer.parseInt(triplet[1], 16))
                    .setServiceId(Integer.parseInt(triplet[2], 16))
                    .build();
        } else {
            throw new Exception("Uri argument must be non null");
        }
    }

    public static class Builder {
        private Triplet mInstance;
        public Builder() {
            mInstance = new Triplet();
        }
        public Builder setOrigNetId(int value) {
            mInstance.mOrigNetId = value;
            return this;
        }
        public Builder setTsId(int value) {
            mInstance.mTsId = value;
            return this;
        }
        public Builder setServiceId(int value) {
            mInstance.mServiceId = value;
            return this;
        }
        public Triplet build() {
            Triplet instance = new Triplet();
            instance.mOrigNetId = mInstance.mOrigNetId;
            instance.mTsId = mInstance.mTsId;
            instance.mServiceId = mInstance.mServiceId;
            return instance;
        }
    }
}
