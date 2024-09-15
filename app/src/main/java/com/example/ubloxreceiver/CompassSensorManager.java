package com.example.ubloxreceiver;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class CompassSensorManager {
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private SensorEventListener sensorEventListener;

    private float[] gravity;      // 加速度数据
    private float[] geomagnetic;  // 磁场数据

    private CompassListener compassListener;

    // 定义接口，用于回调指南针方向更新
    public interface CompassListener {
        void onDirectionChanged(float direction);
    }

    // 构造方法
    public CompassSensorManager(Context context, CompassListener listener) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        compassListener = listener;

        sensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    gravity = event.values;
                } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                    geomagnetic = event.values;
                }

                if (gravity != null && geomagnetic != null) {
                    float[] R = new float[9];
                    float[] I = new float[9];
                    boolean success = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic);
                    if (success) {
                        float[] orientation = new float[3];
                        SensorManager.getOrientation(R, orientation);
                        // orientation[0] 是方位角，单位为弧度，转换为角度
                        float azimuthInDegrees = (float) Math.toDegrees(orientation[0]);
                        if (azimuthInDegrees < 0) {
                            azimuthInDegrees += 360;
                        }

                        // 调用接口方法，将方向角传给 MainActivity 或其他类
                        compassListener.onDirectionChanged(azimuthInDegrees);
                    }
                }
            }

            // 重写抽象方法
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                // 可选处理精度变化
            }
        };
    }

    // 开始监听传感器数据
    public void start() {
        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(sensorEventListener, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    // 停止监听传感器数据
    public void stop() {
        sensorManager.unregisterListener(sensorEventListener);
    }
}

