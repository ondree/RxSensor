/*
 * Copyright (C) 2015 Nathan VanBenschoten.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nvanbenschoten.rxsensor;

import android.annotation.TargetApi;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.disposables.Disposables;
import io.reactivex.functions.Action;


/**
 * A lightweight wrapper around {@link SensorManager} which allows for continuous observations of
 * {@link SensorEvent} data and single observations of {@link TriggerEvent} occurrences.
 */
public final class RxSensorManager {

    private final Handler mSensorListenerHandler;
    private final SensorManager mSensorManager;

    public RxSensorManager(@NonNull SensorManager sensorManager) {
        mSensorListenerHandler = new Handler(Looper.getMainLooper());
        mSensorManager = sensorManager;
    }

    /**
     * Create an observable which will notify subscribers with a {@link SensorEvent}. Sensor
     * data will continue to be polled until the Observable's subscription is unsubscribed from.
     *
     * @see SensorManager#registerListener(SensorEventListener, Sensor, int)
     */
    @CheckResult
    public Flowable<SensorEvent> observeSensor(final int sensorType,
                                               final int samplingPeriodUs) {
        return observeSensor(sensorType, samplingPeriodUs, 0);
    }

    /**
     * Create an observable which will notify subscribers with a {@link SensorEvent}. Sensor
     * data will continue to be polled until the Observable's subscription is unsubscribed from.
     *
     * @see SensorManager#registerListener(SensorEventListener, Sensor, int, int)
     */
    @CheckResult
    public Flowable<SensorEvent> observeSensor(final int sensorType,
                                               final int samplingPeriodUs,
                                               final int maxReportLatencyUs) {


        final FlowableOnSubscribe<SensorEvent> subscribe = new FlowableOnSubscribe<SensorEvent>() {

            @TargetApi(VERSION_CODES.KITKAT)
            @Override
            public void subscribe(final FlowableEmitter<SensorEvent> emitter) throws Exception {
                // Determine Sensor to use
                final Sensor sensor = mSensorManager.getDefaultSensor(sensorType);
                if (sensor == null) {
                    emitter.onError(new SensorException());
                    return;
                }

                // Create SensorEventListener that publishes onSensorChanged events to subscriber
                final SensorEventListener listener = new SensorEventListener() {
                    @Override
                    public void onSensorChanged(SensorEvent event) {
                        emitter.onNext(event);
                    }

                    @Override
                    public void onAccuracyChanged(Sensor sensor, int accuracy) {
                    }
                };

                // Attempt to register listener to SensorManager
                boolean success;
                if (Build.VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
                    success = mSensorManager.registerListener(listener, sensor, samplingPeriodUs,
                            maxReportLatencyUs, mSensorListenerHandler);
                } else {
                    success = mSensorManager.registerListener(listener, sensor, samplingPeriodUs,
                            mSensorListenerHandler);
                }
                if (!success) {
                    emitter.onError(new SensorException(sensor));
                    return;
                }

                // Set action on un-subscribe
                emitter.setDisposable(Disposables.fromAction(new Action() {
                    @Override
                    public void run() {
                        mSensorManager.unregisterListener(listener, sensor);
                    }
                }));
            }

        };
        return Flowable.create(subscribe, BackpressureStrategy.LATEST);
    }

    /**
     * Create an observable which will notify subscribers with a {@link TriggerEvent} once and
     * then complete.
     *
     * @see SensorManager#requestTriggerSensor(TriggerEventListener, Sensor)
     */
    @CheckResult
    @TargetApi(VERSION_CODES.JELLY_BEAN_MR2)
    public Observable<TriggerEvent> observeTrigger(final int sensorType) {
        final ObservableOnSubscribe<TriggerEvent> subscribe = new ObservableOnSubscribe<TriggerEvent>() {
            @Override
            public void subscribe(final ObservableEmitter<TriggerEvent> emitter) throws Exception {
                // Determine Sensor to use
                final Sensor sensor = mSensorManager.getDefaultSensor(sensorType);
                if (sensor == null) {
                    emitter.onError(new SensorException());
                    return;
                }

                // Create TriggerEventListener that publishes a single onTrigger events to
                // subscriber and completes
                final TriggerEventListener listener = new TriggerEventListener() {
                    @Override
                    public void onTrigger(TriggerEvent event) {
                        emitter.onNext(event);
                        emitter.onComplete();
                    }
                };

                // Attempt to request a trigger sensor from SensorManager
                boolean success = mSensorManager.requestTriggerSensor(listener, sensor);
                if (!success) {
                    emitter.onError(new SensorException(sensor));
                    return;
                }

                // Set action on un-subscribe
                emitter.setDisposable(Disposables.fromAction(new Action() {
                    @Override
                    public void run() throws Exception {
                        mSensorManager.cancelTriggerSensor(listener, sensor);
                    }

                }));
            }

        };
        return Observable.create(subscribe);
    }

}
