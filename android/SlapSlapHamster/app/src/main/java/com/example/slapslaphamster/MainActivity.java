package com.example.slapslaphamster;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.icu.text.DecimalFormat;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import cz.msebera.android.httpclient.Header;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager senSensorManager;
    private Sensor senAccelerometer;
    private MediaPlayer player;

    private SoundMeter mSensor;

    private PowerManager.WakeLock wakeLock;

    private AsyncHttpClient client = new AsyncHttpClient();

    private long lastUpdate = 0;
    private float lastX, lastY, lastZ;

    private int currentHealth;

    private static final int HIT_SHAKE_THRESHOLD = 250;
    private static final int HIT_SOUND_THRESHOLD = 99;
    private static final int STEP_LEVEL = 134;

    private static final int MAX_HEALTH = 5000;

    private static final int MAX_TIMEOUT = 3000;

    private static final DecimalFormat df = new DecimalFormat("0000.0000");

    private static final String APP_NAME = "slapslaphamster";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        senSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        senSensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        player = new MediaPlayer();

        mSensor = new SoundMeter();

        acquireWakeLock();

        currentHealth = MAX_HEALTH;

        try {
            mSensor.start();
            Toast.makeText(getApplicationContext(), "Sound sensor initiated.", Toast.LENGTH_LONG);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // senSensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // senSensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor mySensor = sensorEvent.sensor;

        if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = sensorEvent.values[0];
            float y = sensorEvent.values[1];
            float z = sensorEvent.values[2];

            // interval to get the shake gesture
            long currentTime = System.currentTimeMillis();

            if ((currentTime - lastUpdate) > 100) {
                long diffTime = (currentTime - lastUpdate);
                lastUpdate = currentTime;

                double finalX = Math.pow(x - lastX, 2);
                double finalY = Math.pow(y - lastY, 2);
                double finalZ = Math.pow(z - lastZ, 2);
                double speed = Math.sqrt(finalX + finalY + finalZ) / diffTime * 10000;

                double volume = 100 * mSensor.getTheAmplitude() / 32768;
                int volumeToSend = (int) volume;

                int soundToPlay = (int) Math.min(Math.ceil((speed - HIT_SHAKE_THRESHOLD) / STEP_LEVEL), 13);

                if (speed >= HIT_SHAKE_THRESHOLD && volumeToSend == HIT_SOUND_THRESHOLD) {
                    Log.i(APP_NAME, df.format(speed) + "," + volumeToSend);

                    currentHealth -= speed;

                    if (currentHealth < 0) {
                        sendSpeed(-1);
                        playSound("frog14.mp3");

                        Log.i(APP_NAME, "You are dead");

                        currentHealth = MAX_HEALTH;
                        lastUpdate += MAX_TIMEOUT;
                    } else {
                        sendSpeed(speed);
                        playSound("frog" + soundToPlay + ".mp3");

                        Log.i(APP_NAME, "Current life: " + currentHealth);
                    }
                }

                lastX = x;
                lastY = y;
                lastZ = z;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void playSound(String name) {
        try {
            if (player.isPlaying()) {
                player.stop();
                player.release();
                player = new MediaPlayer();
            }

            player.reset();

            AssetFileDescriptor descriptor = getAssets().openFd(name);
            player.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
            descriptor.close();

            player.prepare();
            player.setVolume(1f, 1f);
            player.setLooping(false);
            player.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void acquireWakeLock() {
        final PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        releaseWakeLock();
        // acquire new wake lock
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PARTIAL_WAKE_LOCK");
        wakeLock.acquire();
    }

    public void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    public void sendSpeed(double speed) {
        client.get("http://192.168.43.200:3000/update_score?score=" + speed, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                Log.i(APP_NAME, "score updated");
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                Log.i(APP_NAME, "score updated failed");
            }
        });
    }
}

