package org.orbtv.dvbiclient;

import android.util.Log;

import org.orbtv.dvbiclient.model.AvailabilityPeriod;
import org.orbtv.dvbiclient.model.Service;
import org.orbtv.dvbiclient.model.ServiceInstance;

import java.util.List;

public class TunedServiceManager {
    private static final String TAG = TunedServiceManager.class.getSimpleName();
    private Callback mCallback;
    private AvailabilityPeriodRunnable mAvailabilityPeriodRunnable = null;
    private Thread mAvailabilityPeriodThread = null;
    private Service mTunedService = null;
    private ServiceInstance mTunedInstance = null;
    private final Object mLock = new Object();

    public TunedServiceManager(Callback callback) {
        mCallback = callback;
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
            mCallback.onInstanceChanged(null, mTunedInstance);
            startInstanceAvailabilityThread(instanceIndex >= 0);
        }
        return true;
    }

    public void tuneOff() {
        stopInstanceAvailabilityThread();
        synchronized (mLock) {
            mTunedService = null;
            mTunedInstance = null;
        }
    }

    public synchronized Service getTunedService() { return mTunedService; }

    public synchronized ServiceInstance getTunedInstance() { return mTunedInstance; }

    public synchronized DvbIChannelAdapter getTunedChannel() {
        return new DvbIChannelAdapter.Builder()
                .setService(mTunedService)
                .setServiceInstance(mTunedInstance)
                .build();
    }

    private void startInstanceAvailabilityThread(boolean forceTunedInstance) {
        synchronized (mLock) {
            if (mAvailabilityPeriodRunnable == null) {
                mAvailabilityPeriodRunnable = new AvailabilityPeriodRunnable(forceTunedInstance);
                mAvailabilityPeriodThread = new Thread(mAvailabilityPeriodRunnable);
                mAvailabilityPeriodThread.start();
            }
        }
    }

    private void stopInstanceAvailabilityThread() {
        synchronized (mLock) {
            if (mAvailabilityPeriodRunnable != null) {
                mAvailabilityPeriodRunnable.stop();
                mAvailabilityPeriodThread = null;
                mAvailabilityPeriodRunnable = null;
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

    private class AvailabilityPeriodRunnable implements Runnable {
        private volatile boolean mIsRunning = false;
        private ServiceInstance mTargetInstance;

        public AvailabilityPeriodRunnable(boolean forceTunedInstance) {
            if (forceTunedInstance) {
                synchronized (mLock) {
                    mTargetInstance = mTunedInstance;
                }
            }
        }

        public void stop() {
            mIsRunning = false;
            Thread.currentThread().interrupt();
            Log.i(TAG, "Stopping AvailabilityPeriodRunnable...");
        }

        @Override
        public void run() {
            ServiceInstance tunedInstance = null;
            mIsRunning = true;
            while (mIsRunning) {
                synchronized (mLock) {
                    tunedInstance = mTunedInstance;
                    if (mTargetInstance == null) {
                        ServiceInstance priorityInstance = getMaxPriorityInstance(mTunedService);
                        if (priorityInstance != tunedInstance) {
                            mTunedInstance = priorityInstance;
                            if (mCallback != null) {
                                mCallback.onInstanceChanged(tunedInstance, priorityInstance);
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
                        if (mCallback != null) {
                            mCallback.onInstanceChanged(tunedInstance, mTunedInstance);
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
            Log.i(TAG, "Stopped AvailabilityPeriodRunnable.");
        }
    }

    public interface Callback {
        void onInstanceChanged(ServiceInstance fromInstance, ServiceInstance toInstance);
    }
}
