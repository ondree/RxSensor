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
package com.example.nvanbenschoten.rxsensor;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.os.Bundle;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jakewharton.rxbinding2.widget.RxCompoundButton;
import com.nvanbenschoten.rxsensor.RxSensorManager;
import com.trello.rxlifecycle2.components.RxActivity;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;


public class MainActivity extends RxActivity {

    @BindView(R.id.sensor_switch) Switch mSensorSwitch;
    @BindView(R.id.sensor_data_0) TextView mSensorData0;
    @BindView(R.id.sensor_data_1) TextView mSensorData1;
    @BindView(R.id.sensor_data_2) TextView mSensorData2;
    @BindView(R.id.trigger_switch) Switch mTriggerSwitch;
    private RxSensorManager mRxSensorManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        // Prepare sensor objects
        setupSensorManager();

        // Set up Rx Chains
        RxCompoundButton.checkedChanges(mSensorSwitch)
                .doOnNext(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean checked) throws Exception {
                        if (checked) {
                            showData();
                        } else {
                            hideData();
                        }
                    }
                })
                .switchMap(new Function<Boolean, ObservableSource<? extends SensorEvent>>() {
                    @Override
                    public ObservableSource<? extends SensorEvent> apply(Boolean checked) throws Exception {
                        if (checked) {
                            return mRxSensorManager.observeSensor(Sensor.TYPE_ACCELEROMETER,
                                    SensorManager.SENSOR_DELAY_NORMAL)
                                    .toObservable();
                        } else {
                            return Observable.empty();
                        }
                    }
                })
                .compose(this.<SensorEvent>bindToLifecycle())
                .subscribe(new Consumer<SensorEvent>() {
                    @Override
                    public void accept(SensorEvent sensorEvent) throws Exception {
                        mSensorData0.setText(Float.toString(sensorEvent.values[0]));
                        mSensorData1.setText(Float.toString(sensorEvent.values[1]));
                        mSensorData2.setText(Float.toString(sensorEvent.values[2]));
                    }

                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Toast.makeText(MainActivity.this, "Error caught observing sensor: " +
                                throwable.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });

        RxCompoundButton.checkedChanges(mTriggerSwitch)
                .doOnNext(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean checked) throws Exception {
                        mTriggerSwitch.setEnabled(!checked);
                    }
                })
                .filter(new Predicate<Boolean>() {
                    @Override
                    public boolean test(Boolean checked) throws Exception {
                        return checked;
                    }
                })
                .switchMap(new Function<Boolean, ObservableSource<? extends TriggerEvent>>() {
                    @Override
                    public ObservableSource<? extends TriggerEvent> apply(Boolean aBoolean) throws Exception {
                        return mRxSensorManager.observeTrigger(Sensor.TYPE_SIGNIFICANT_MOTION);
                    }
                })
                .compose(this.<TriggerEvent>bindToLifecycle())
                .subscribe(new Consumer<TriggerEvent>() {
                    @Override
                    public void accept(TriggerEvent triggerEvent) throws Exception {
                        Toast.makeText(MainActivity.this, "Trigger event at: " +
                                triggerEvent.timestamp, Toast.LENGTH_LONG).show();
                        mTriggerSwitch.setChecked(false);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Toast.makeText(MainActivity.this, "Error caught observing trigger: " +
                                throwable.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setupSensorManager() {
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mRxSensorManager = new RxSensorManager(sensorManager);
    }

    private void showData() {
        mSensorData0.setVisibility(View.VISIBLE);
        mSensorData1.setVisibility(View.VISIBLE);
        mSensorData2.setVisibility(View.VISIBLE);
    }

    private void hideData() {
        mSensorData0.setText(null);
        mSensorData1.setText(null);
        mSensorData2.setText(null);
        mSensorData0.setVisibility(View.GONE);
        mSensorData1.setVisibility(View.GONE);
        mSensorData2.setVisibility(View.GONE);
    }

}
