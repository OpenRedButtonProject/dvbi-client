package org.orbtv.dvbiclient;

import android.util.Log;

import org.orbtv.dvbiclient.model.AvailabilityPeriod;
import org.orbtv.dvbiclient.model.Programme;
import org.orbtv.dvbiclient.model.Service;
import org.orbtv.dvbiclient.model.ServiceInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
            Log.i(TAG, "Tune now/next size=" + mNowNextProgrammes.size()
                    + ", now=" + getNowProgramme());
            mTunedInstance = instance;
            notifyNowProgrammeUpdated(getNowProgramme());
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
    
    /** Reload now/next from the DB. Does not notify; the poller reports now-programme changes. */
    private void updateNowNextProgrammes() {
        if (mTunedService == null) {
            return;
        }
        List<Programme> programmes = fetchNowNextProgrammes(mTunedService.getUniqueIdentifier());
        applyNowNextProgrammes(programmes);
    }

    private List<Programme> fetchNowNextProgrammes(String serviceUid) {
        long currentTime = System.currentTimeMillis() / 1000;
        List<Programme> programmes = mDbHandler.getProgrammesForService(
                serviceUid, currentTime, currentTime + SECONDS_OF_DAY, 5);
        Log.i(TAG, "Fetched " + programmes.size() + " programmes for " + serviceUid);
        return programmes;
    }

    private void applyNowNextProgrammes(List<Programme> programmes) {
        if (programmes != null && !programmes.equals(mNowNextProgrammes)) {
            mNowNextProgrammes = programmes;
            Log.i(TAG, "Now/next list size=" + programmes.size()
                    + ", now=" + getNowProgramme());
        }
    }

    private static boolean isSameNowProgramme(Programme a, Programme b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.getProgramId(), b.getProgramId())
                && a.getStartTime() == b.getStartTime()
                && a.getMinimumAge() == b.getMinimumAge();
    }

    private void notifyNowProgrammeUpdated(Programme programme) {
        for (Callback callback : mCallbacks) {
            callback.onNowProgrammeUpdated(programme);
        }
    }

    private void stopTunedServiceThread() {
        Thread thread;
        TunedServiceRunnable runnable;
        synchronized (mLock) {
            runnable = mTunedServiceRunnable;
            thread = mTunedServiceThread;
            mTunedServiceRunnable = null;
            mTunedServiceThread = null;
            if (runnable != null) {
                runnable.stop();
            }
        }
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Interrupted while waiting for tune poller to stop");
            }
        }
    }

    private ServiceInstance getMaxPriorityInstance(Service service) {
        if (service == null) {
            Log.w(TAG, "getMaxPriorityInstance called with null service");
            return null;
        }
        ServiceInstance maxInstance = null;
        List<ServiceInstance> instances = service.getInstances();
        if (instances == null) {
            Log.w(TAG, "Service.getInstances() returned null");
            return null;
        }
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
        private volatile boolean mIsRunning = true;
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
            Log.i(TAG, "Stopping TunedServiceRunnable...");
        }

        @Override
        public void run() {
            ServiceInstance tunedInstance = null;
            Programme nowProgramme = getNowProgramme();
            while (mIsRunning) {
                String uid;
                synchronized (mLock) {
                    uid = mTunedService != null ? mTunedService.getUniqueIdentifier() : null;
                }
                List<Programme> refreshed = uid != null ? fetchNowNextProgrammes(uid) : null;

                synchronized (mLock) {
                    if (!mIsRunning || mTunedServiceRunnable != this) {
                        break;
                    }
                    if (uid != null && mTunedService != null
                            && uid.equals(mTunedService.getUniqueIdentifier())) {
                        applyNowNextProgrammes(refreshed);
                        Programme programme = getNowProgramme();
                        if (!isSameNowProgramme(nowProgramme, programme)) {
                            nowProgramme = programme;
                            notifyNowProgrammeUpdated(programme);
                        }

                        tunedInstance = mTunedInstance;
                        if (mTargetInstance == null && mTunedService != null) {
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
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
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
