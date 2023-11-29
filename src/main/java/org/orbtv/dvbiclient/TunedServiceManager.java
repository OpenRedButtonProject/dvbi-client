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
    private static final long SECONDS_OF_DAY = 86400;
    private ArrayList<Callback> mCallbacks = new ArrayList<>();
    private TunedServiceRunnable mTunedServiceRunnable = null;
    private Thread mTunedServiceThread = null;
    private Service mTunedService = null;
    private ServiceInstance mTunedInstance = null;
    private DatabaseHandler mDbHandler;
    private List<Programme> mNowNextProgrammes = new ArrayList<>();
    private final Object mLock = new Object();

    public TunedServiceManager(EpgManager epgManager, DatabaseHandler dbHandler) {
        mDbHandler = dbHandler;
        epgManager.registerCallback(serviceUIDs -> {
            synchronized (mLock) {
                if (mTunedService != null) {
                    String uid = mTunedService.getUniqueIdentifier();
                    if (serviceUIDs.contains(uid)) {
                        updateNowNextProgrammes();
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
            updateNowNextProgrammes();
            mTunedInstance = instance;
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
            mNowNextProgrammes.clear();
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

    public Programme getNowProgramme() {
        long currentTime = System.currentTimeMillis() / 1000;
        synchronized (mLock) {
            for (Programme p : mNowNextProgrammes) {
                if (p.getStartTime() <= currentTime && p.getEndTime() > currentTime) {
                    return p;
                }
            }
        }
        return null;
    }

    public Programme getNextProgramme() {
        synchronized (mLock) {
            if (getNowProgramme() == null && !mNowNextProgrammes.isEmpty()) {
                return mNowNextProgrammes.get(0);
            }
            if (mNowNextProgrammes.size() >= 2) {
                return mNowNextProgrammes.get(1);
            }
        }
        return null;
    }

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
    
    private void updateNowNextProgrammes() {
        long currentTime = System.currentTimeMillis() / 1000;
        List<Programme> programmes = mDbHandler.getProgrammesForService(
                mTunedService.getUniqueIdentifier(),
                currentTime, currentTime + SECONDS_OF_DAY, 2);
        if (!programmes.equals(mNowNextProgrammes)) {
            mNowNextProgrammes = programmes;
            for (Callback callback : mCallbacks) {
                callback.onNowProgrammeUpdated(getNowProgramme());
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
                    long secondsOfDay = currentTimestamp % SECONDS_OF_DAY;
                    String dayOfWeek = String.valueOf((currentTimestamp / SECONDS_OF_DAY + 3) % 7 + 1); // +3 as Epoch was Thursday and +1 as Monday should be 1
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
            Programme nowProgramme = getNowProgramme();
            mIsRunning = true;
            while (mIsRunning) {
                synchronized (mLock) {
                    Programme programme = getNowProgramme();
                    if (nowProgramme != programme) {
                        if (nowProgramme == null) {
                            nowProgramme = programme;
                            // no need to call updateNowNextProgrammes() as it will return the same
                            // now and next programmes and the callback will not be called
                            for (Callback callback : mCallbacks) {
                                callback.onNowProgrammeUpdated(programme);
                            }
                        }
                        else {
                            // first update, then get a reference to the now Programme
                            updateNowNextProgrammes();
                            nowProgramme = getNowProgramme();
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
