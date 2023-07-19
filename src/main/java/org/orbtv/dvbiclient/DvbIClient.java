package org.orbtv.dvbiclient;

import android.content.Context;
import android.util.Log;
import android.view.View;

import org.json.JSONException;
import org.orbtv.companionlibrary.callbacks.DvbCallback;
import org.orbtv.companionlibrary.callbacks.HbbTVCallback;
import org.orbtv.companionlibrary.model.DvbChannel;
import org.orbtv.companionlibrary.model.InternalProviderData;
import org.orbtv.companionlibrary.utils.AsyncUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class DvbIClient {
    public static final String TYPE_DVB_I = "TYPE_DVB_I";
    public static final String KEY_DVBI_UID = "DVBI_UID";
    private static DvbIClient mSingleton;
    private static final String TAG = DvbIClient.class.getSimpleName();
    private DvbIView mDvbIView;
    private ServiceListDiscoveryTask mLastDiscoveryTask = null;
    private DvbIDatabaseHandler mDbHandler;
    private final ArrayList<DvbCallback> mDvbCallbacks = new ArrayList<>();
    private final ArrayList<HbbTVCallback> mHbbTVCallbacks = new ArrayList<>();
    
    private DvbIClient(Context context) {
        mDbHandler = new DvbIDatabaseHandler(context);
        mDvbIView = new DvbIView(context, mHbbTVCallbacks);
    }

    public static void instantiate(Context context) {
        if (mSingleton == null) {
            mSingleton = new DvbIClient(context);
        }
    }

    public static DvbIClient getInstance() {
        return mSingleton;
    }

    public synchronized void addDvbCallback(DvbCallback handler) {
        if (!mDvbCallbacks.contains(handler)) {
            mDvbCallbacks.add(handler);
        }
    }

    public synchronized void removeDvbCallback(DvbCallback handler) {
        mDvbCallbacks.remove(handler);
    }

    public synchronized void addHbbTVCallback(HbbTVCallback handler) {
      if (!mHbbTVCallbacks.contains(handler)) {
         mHbbTVCallbacks.add(handler);
      }
    }

    public synchronized void removeHbbTVCallback(HbbTVCallback handler) {
       mHbbTVCallbacks.remove(handler);
    }

    public View getView() {
        return mDvbIView;
    }

    public boolean tune(String uid) {
        DvbIService service = mDbHandler.getServiceForUID(uid);
        if (service != null) {
            Log.i(TAG, "---------- Tuning to service ----------\n" + service + "\n------------------------------------");
            List<DvbIServiceInstance> instances = service.getInstances();
            for (DvbIServiceInstance instance : instances) {
                String uri = instance.getUri();
                if (uri != null) {
                    for (DvbCallback handler : mDvbCallbacks) {
                        handler.onPlayerStatusChanged(null);
                    }
                    return mDvbIView.tune(uri);
                }
            }
        }
        Log.i(TAG, "Failed to tune to service.");
        return false;
    }

    public void tuneOff() {
        mDvbIView.tuneOff();
    }

    public List<DvbChannel> getListOfServices() {
        ArrayList<DvbChannel> ret = new ArrayList<>();
        List<DvbIService> services = mDbHandler.getServices();
        for (DvbIService service : services) {
            try {
                ret.add(createChannel(service));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        Log.i(TAG, "Number of services: " + services.size());
        return ret;
    }

    public boolean startServiceSearch() {
        boolean ret = false;
        if (mLastDiscoveryTask == null) {
            mLastDiscoveryTask = new ServiceListDiscoveryTask();
          //  mLastDiscoveryTask.execute("http://stage.sofiadigital.fi/dvb/dvb-i-reference-application/backend/servicelists/example.xml?ts=1689243059951");
            //mLastDiscoveryTask.execute("http://192.168.1.145/config.xml");
            mLastDiscoveryTask.execute("http://192.168.1.145/servicelist.xml");
            ret = true;
        }
        return ret;
    }

    private DvbChannel createChannel(DvbIService service) throws JSONException {
        DvbIChannel channel = DvbIChannel.createChannel(service, "");
        InternalProviderData data = new InternalProviderData();
        data.put("DVB_URI", "");
        data.put("NET_ID", channel.getNid() == null ? "0" : channel.getNid());
        data.put("LOCKED", false);
        data.put("ORIG_LCN", service.getLCNNumber());
        data.put(KEY_DVBI_UID, service.getUniqueIdentifier());
        int onid = channel.getNid() == null ? 0 : Integer.parseInt(channel.getNid());
        int tsid = channel.getTsid() == null ? 0 : Integer.parseInt(channel.getTsid());
        int sid = channel.getSid() == null ? 0 : Integer.parseInt(channel.getSid());
        return new DvbChannel.Builder()
            .setDisplayName(channel.getName())
            .setDisplayNumber(service.getLCNNumber())
            .setType(TYPE_DVB_I)
            .setServiceType("SERVICE_TYPE_DVBI")
            .setOriginalNetworkId(onid)
            .setTransportStreamId(tsid)
            .setServiceId(sid)
            .setBrowsable(true)
            .setSearchable(true)
            .setInternalProviderData(data)
            .build();
    }

    public class ServiceListDiscoveryTask extends AsyncUtils<Void, String> {
        @Override
        protected Void doInBackground(String... uris) {
            try {
                for (String uri : uris) {
                    URL url = new URL(uri);
                    boolean useHttps = false;
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//                if (useHttps) {
//                    // Configure the SSL context for HTTPS connections
//                    SSLContext sslContext = SSLContext.getInstance("TLS");
//                    sslContext.init(null, new TrustManager[]{new TrustAllManager()}, null);
//                    ((HttpsURLConnection) connection).setSSLSocketFactory(sslContext.getSocketFactory());
//                    ((HttpsURLConnection) connection).setHostnameVerifier((hostname, session) -> true);
//                }

                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Content-Type", "application/xml");

                    int responseCode = connection.getResponseCode();
                    Log.i(TAG, "Response Code: " + responseCode);

                    // Read the response from the input stream
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                    reader.close();

                    // Return the response as a string
                    Log.i(TAG, responseBuilder.toString());

                    String targetRegion = "";
                    ServiceList serviceList = ServiceList.parseFromXML(responseBuilder.toString(), targetRegion);

                    for (DvbIService service : serviceList.services) {
                        DvbIChannel.createChannel(service,"").printChannelProperties();
                        List<DvbIServiceInstance> instances = service.getInstances();
                        for (DvbIServiceInstance instance : instances) {
                            DvbIChannel.createChannel(service, instance, "").printChannelProperties();
                        }
                     //   mServices.add(service);
                    }
                    mDbHandler.updateServices(serviceList.services);

                }
            }
            catch(IOException e) {
                Log.e(TAG, "Error sending request", e);
            }
            catch(Exception e) {
                Log.e(TAG, "Error configuring SSL context", e);
            }
            return null;
        }

        @Override
        public void onPostExecute(Void success) {
            finalizeSearch();
        }

        @Override
        public void onCancelled(Void ignore) {
            finalizeSearch();
        }

        private synchronized void finalizeSearch() {
            for (DvbCallback handler : mDvbCallbacks) {
                handler.onDvbtStatusChanged(100);
            }
            mLastDiscoveryTask = null;
        }
    }
}
