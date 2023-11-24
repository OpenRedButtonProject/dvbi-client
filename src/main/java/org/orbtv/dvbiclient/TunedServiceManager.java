package org.orbtv.dvbiclient;

import android.util.Log;

import org.orbtv.dvbiclient.model.AvailabilityPeriod;
import org.orbtv.dvbiclient.model.Programme;
import org.orbtv.dvbiclient.model.Service;
import org.orbtv.dvbiclient.model.ServiceInstance;

import java.util.ArrayList;
import java.util.List;

public class TunedServiceManager {
    private static final String TAG = TunedServiceManager.class.getSimpleName();
    private ArrayList<Callback> mCallbacks = new ArrayList<>();
    private TunedServiceRunnable mTunedServiceRunnable = null;
    private Thread mTunedServiceThread = null;
    private Service mTunedService = null;
    private ServiceInstance mTunedInstance = null;
    private DatabaseHandler mDbHandler;
    private Programme mNowProgramme = null;
    private final Object mLock = new Object();

    public TunedServiceManager(EpgManager epgManager, DatabaseHandler dbHandler) {
        mDbHandler = dbHandler;
        epgManager.registerCallback(serviceUIDs -> {
            synchronized (mLock) {
                if (mTunedService != null) {
                    String uid = mTunedService.getUniqueIdentifier();
                    if (serviceUIDs.contains(uid)) {
                        updateNowProgramme();
                    }
                }
            }
        });
    }

    public boolean tune(Service service, int instanceIndex) {
        ServiceInstance instance = null;
        tuneOff();
        if (service == null) {
            Log.i(TAG, "Cannot tune to a null service.");
            return false;
        }
        if (instanceIndex < 0) {
            instance = getMaxPriorityInstance(service);
        }
        else if (instanceIndex < service.getInstances().size()) {
            instance = service.getInstances().get(instanceIndex);
        }
        else {
            Log.i(TAG, "No instance with index " + instanceIndex + " found in service.");
            return false;
        }
        Log.i(TAG, "---------- Tuning to service ----------\n" + service + "\n------------------------------------");
        synchronized (mLock) {
            mTunedService = service;
            mTunedInstance = instance;
            updateNowProgramme();
            for (Callback callback : mCallbacks) {
                callback.onInstanceChanged(null, mTunedInstance);
            }
            startTunedServiceThread(instanceIndex >= 0);
        }
        return true;
    }

    public void tuneOff() {
        stopTunedServiceThread();
        synchronized (mLock) {
            mTunedService = null;
            mNowProgramme = null;
            mTunedInstance = null;
        }
    }

    public void registerCallback(Callback handler) {
        synchronized (mLock) {
            if (!mCallbacks.contains(handler)) {
                mCallbacks.add(handler);
            }
        }
    }

    public void unregisterCallback(Callback handler) {
        synchronized (mLock) {
            mCallbacks.remove(handler);
        }
    }

    public synchronized Service getTunedService() { return mTunedService; }

    public Programme getNowProgramme() { return mNowProgramme; }

    public synchronized ServiceInstance getTunedInstance() { return mTunedInstance; }

    public synchronized DvbIChannelAdapter getTunedChannel() {
        return new DvbIChannelAdapter.Builder()
                .setService(mTunedService)
                .setServiceInstance(mTunedInstance)
                .build();
    }

    private void startTunedServiceThread(boolean forceTunedInstance) {
        synchronized (mLock) {
            if (mTunedServiceRunnable == null) {
                mTunedServiceRunnable = new TunedServiceRunnable(forceTunedInstance);
                mTunedServiceThread = new Thread(mTunedServiceRunnable);
                mTunedServiceThread.start();
            }
        }
    }
    
    private void updateNowProgramme() {
        long currentTime = System.currentTimeMillis() / 1000;
        List<Programme> programmes = mDbHandler.getProgrammesForService(mTunedService.getUniqueIdentifier(), currentTime, currentTime, 1);
        Programme programme = null;
        if (!programmes.isEmpty()) {
            programme = programmes.get(0);
        }
        if ((programme != null && !programme.equals(mNowProgramme)) || mNowProgramme != null) {
            mNowProgramme = programme;
            for (Callback callback : mCallbacks) {
                callback.onNowProgrammeUpdated(programme);
            }
        }
    }

    private void stopTunedServiceThread() {
        synchronized (mLock) {
            if (mTunedServiceRunnable != null) {
                mTunedServiceRunnable.stop();
                mTunedServiceThread = null;
                mTunedServiceRunnable = null;
            }
        }
    }

    private ServiceInstance getMaxPriorityInstance(Service service) {
        ServiceInstance maxInstance = null;
        List<ServiceInstance> instances = service.getInstances();
        for (int i = 0; i < instances.size(); i++) {
            ServiceInstance instance = instances.get(i);
            if (isInstanceAvailable(instance) && (maxInstance == null || maxInstance.getPriority() > instance.getPriority())) {
                maxInstance = instance;
            }
        }
        return maxInstance;
    }

    private boolean isInstanceAvailable(ServiceInstance instance) {
        List<AvailabilityPeriod> periods = instance.getAvailabilityPeriods();
        if (periods == null || periods.isEmpty()) {
            return true;
        }
        long currentTimestamp = System.currentTimeMillis() / 1000;
        List<AvailabilityPeriod.Interval> intervals;
        for (AvailabilityPeriod period : periods) {
            if (period.getValidTo() == null ||
                    (currentTimestamp >= period.getValidFrom() && currentTimestamp < period.getValidTo())) {
                intervals = period.getIntervals();
                if (intervals == null || intervals.isEmpty()) {
                    return true;
                }
                else {
                    long secondsOfDay = currentTimestamp % 86400;
                    String dayOfWeek = String.valueOf((currentTimestamp / 86400 + 3) % 7 + 1); // +3 as Epoch was Thursday and +1 as Monday should be 1
                    for (AvailabilityPeriod.Interval interval : intervals) {
                        if ((interval.getDays() == null || interval.getDays().contains(dayOfWeek)) &&
                                (interval.getStartTime() == null || secondsOfDay >= interval.getStartTime() && secondsOfDay < interval.getEndTime())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private class TunedServiceRunnable implements Runnable {
        private volatile boolean mIsRunning = false;
        private ServiceInstance mTargetInstance;

        public TunedServiceRunnable(boolean forceTunedInstance) {
            if (forceTunedInstance) {
                synchronized (mLock) {
                    mTargetInstance = mTunedInstance;
                }
            }
        }

        public void stop() {
            mIsRunning = false;
            Thread.currentThread().interrupt();
            Log.i(TAG, "Stopping TunedServiceRunnable...");
        }

        @Override
        public void run() {
            ServiceInstance tunedInstance = null;
            mIsRunning = true;
            while (mIsRunning) {
                synchronized (mLock) {
                    if (mNowProgramme != null) {
                        long currentTime = System.currentTimeMillis() / 1000;
                        if (mNowProgramme.getEndTime() < currentTime) {
                            updateNowProgramme();
                        }
                    }

                    tunedInstance = mTunedInstance;
                    if (mTargetInstance == null) {
                        ServiceInstance priorityInstance = getMaxPriorityInstance(mTunedService);
                        if (priorityInstance != tunedInstance) {
                            mTunedInstance = priorityInstance;
                            for (Callback callback : mCallbacks) {
                                callback.onInstanceChanged(tunedInstance, priorityInstance);
                            }
                        }
                    }
                    else if ((tunedInstance == null) == isInstanceAvailable(mTargetInstance)){
                        if (tunedInstance != null) {
                            mTunedInstance = null;
                        }
                        else {
                            mTunedInstance = mTargetInstance;
                        }
                        for (Callback callback : mCallbacks) {
                            callback.onInstanceChanged(tunedInstance, mTunedInstance);
                        }
                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    stop();
                }
            }
            Log.i(TAG, "Stopped TunedServiceRunnable.");
        }
    }

    public interface Callback {
        void onInstanceChanged(ServiceInstance fromInstance, ServiceInstance toInstance);
        void onNowProgrammeUpdated(Programme programme);
    }
}
