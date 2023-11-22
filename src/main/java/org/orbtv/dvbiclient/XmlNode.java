package org.orbtv.dvbiclient;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XmlNode {
    private List<XmlNode> mChildren;
    private String mName;
    private XmlNode mParentNode = null;
    private String mInnerText = null;
    private Map<String, String> mAttributes;

    private XmlNode () { }
    public String getAttribute(String name) { return mAttributes.get(name); }
    public String getName() { return mName; }
    public XmlNode getParentNode() { return mParentNode; }
    public String getInnerText() { return mInnerText; }
    public int getChildrenCount() { return mChildren.size(); }
    public XmlNode getDescendantByName(String name) {
        for (XmlNode node : mChildren) {
            if (node.mName != null && node.mName.equals(name)) {
                return node;
            }
            XmlNode ret = node.getDescendantByName(name);
            if (ret != null) {
                return ret;
            }
        }
        return null;
    }
    public List<XmlNode> getDescendantsByName(String name) {
        ArrayList<XmlNode> nodes = new ArrayList<>();
        for (XmlNode node : mChildren) {
            if (node.mName != null && node.mName.equals(name)) {
                nodes.add(node);
            }
            nodes.addAll(node.getDescendantsByName(name));
        }
        return nodes;
    }
    public XmlNode getChildAt(int i) {
        if (i >= 0 && i < mChildren.size()) {
            return mChildren.get(i);
        }
        return null;
    }
    public XmlNode getFirstChild() {
        if (!mChildren.isEmpty()) {
            return mChildren.get(0);
        }
        return null;
    }
    public XmlNode getNextSibling() {
        if (mParentNode != null) {
            int index = mParentNode.mChildren.indexOf(this);
            if (index >= 0 && index + 1 < mParentNode.mChildren.size()) {
                return mParentNode.mChildren.get(index + 1);
            }
        }
        return null;
    }
    @Override
    public String toString() {
        return "<" + mName + ">"
                + "\nattributes: " + mAttributes
                + "\ninnerText: " + mInnerText
                + "\nchildren: " + mChildren
                + "\n</" + mName + ">";
    }

    public static XmlNode parse(String xml) throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();
        xpp.setInput(new StringReader(xml));
        int eventType = xpp.getEventType();
        while (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_DOCUMENT) {
            eventType = xpp.next();
        }
        return parseNodes(xpp, null);
    }

    private static XmlNode parseNodes(XmlPullParser xpp, XmlNode parentNode) throws Exception {
        if (xpp.getEventType() == XmlPullParser.START_TAG) {
            XmlNode.Builder builder = new XmlNode.Builder();
            ArrayList<XmlNode> children = new ArrayList<>();
            HashMap<String, String> attributes = new HashMap<>();
            for (int i = 0; i < xpp.getAttributeCount(); ++i) {
                attributes.put(xpp.getAttributeName(i), xpp.getAttributeValue(i));
            }
            builder.setParentNode(parentNode)
                    .setName(xpp.getName())
                    .setAttributes(attributes)
                    .setChildren(children);
            int eventType = xpp.next();
            if (eventType == XmlPullParser.TEXT) {
                builder.setInnerText(xpp.getText());
                eventType = xpp.next();
            }
            XmlNode node = builder.build();
            while (eventType != XmlPullParser.END_TAG && eventType != XmlPullParser.END_DOCUMENT) {
                XmlNode childNode = parseNodes(xpp, node);
                if (childNode != null) {
                    children.add(childNode);
                }
                eventType = xpp.next();
            }
            return builder.build();
        }
        return null;
    }

    private static class Builder {
        private XmlNode mInstance;
        public Builder() {
            mInstance = new XmlNode();
        }
        public XmlNode.Builder setAttributes(Map<String, String> value) {
            mInstance.mAttributes = value;
            return this;
        }
        public XmlNode.Builder setName(String value) {
            mInstance.mName = value;
            return this;
        }
        public XmlNode.Builder setChildren(List<XmlNode> value) {
            mInstance.mChildren = value;
            return this;
        }
        public XmlNode.Builder setInnerText(String value) {
            mInstance.mInnerText = value;
            return this;
        }
        public XmlNode.Builder setParentNode(XmlNode value) {
            mInstance.mParentNode = value;
            return this;
        }
        public XmlNode build() {
            XmlNode instance = new XmlNode();
            instance.mParentNode = mInstance.mParentNode;
            instance.mInnerText = mInstance.mInnerText;
            instance.mAttributes = mInstance.mAttributes;
            instance.mName = mInstance.mName;
            instance.mChildren = mInstance.mChildren;
            return instance;
        }
    }
}