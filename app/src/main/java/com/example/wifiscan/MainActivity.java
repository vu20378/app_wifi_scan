package com.example.wifiscan;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.text.method.ScrollingMovementMethod;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static final String LOG_TAG = "Vtwo";
    private static final int MY_REQUEST_CODE = 123;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private static final String FILE_HEADER = "ScanNumber,TimeStamp,SSID,BSSID,Level,gyroX,gyroY,gyroZ,accX,accY,accZ,magneticX,magneticY,magneticZ,Ox,Oy,Oz";
    private static final String NEW_LINE_SEPARATOR = "\n";
    private static final String COMMA_DELIMITER = ",";

    private WifiManager wifiManager;
    private BroadcastReceiver wifiReceiver;
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor magnetometerSensor;
    private Sensor pressureSensor;
    private Sensor gyroSensor;
    private Sensor accSensor;

    private Button buttonScan;
    private Button buttonBack;
    private Button buttonStopScan;
    private EditText editTextCount;
    private EditText editTextX;
    private EditText editTextY;
    private EditText editTextZ;
    private TextView textViewFile;
    private TextView textViewList;
    private TextView textViewRemainingScans;

    private Handler wifiScanHandler = new Handler();
    private long wifiScanInterval = 1500;
    private long remainingScans = 0;
    private List<ScanResult> listwifi;
    private float[] magneticValues = new float[3];
    private float[] gyroValues = new float[3];
    private float[] accValues = new float[3];
    private FileWriter fileWriter = null;
    private File csvFile = null;
    private File dir = null;
    private String ox;
    private String oy;
    private String oz;
    private boolean isstart = false;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        wifiReceiver = new WifiBroadcastReceiver();
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        buttonScan = (Button) findViewById(R.id.button_scan);
        buttonBack = (Button) findViewById(R.id.button_back);
        buttonStopScan = (Button) findViewById(R.id.button_stop_scan);
        editTextX = (EditText) findViewById(R.id.ox);
        editTextY = (EditText) findViewById(R.id.oy);
        editTextZ = (EditText) findViewById(R.id.oz);
        editTextCount = (EditText) findViewById(R.id.count_scans);
        textViewFile = (TextView) findViewById(R.id.fileView);
        textViewList = (TextView) findViewById(R.id.listWifi);
        textViewRemainingScans = (TextView) findViewById(R.id.textViewRemainingScans);
        textViewList.setMovementMethod(new ScrollingMovementMethod());
        textViewList.setText("\n\n\n\n\n\n\n\n\n!!!!!! Quá trình thu thập dữ liệu đang chờ !!!!!!");

        // Tên File quy định là ngày/tháng/năm của ngày lấy dữ liệu
        String ten_file = getIntent().getStringExtra("data_key") + ".csv"; // Lưu tên file để xử lý dữ liệu
        //tạo và đọc dữ liệu có trong file
        createCSVFile(ten_file);
        readAndDisplayCSVFile();
        //check quyền vị trí và wifi để việc quét dễ dàng hơn
        checkPermissions();

        buttonScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkWifiStatus()) {
                    if (checkLocationStatus()) {
                        //xóa tất cả vết thời gian
                        wifiScanHandler.removeCallbacksAndMessages(null);
                        isstart = true;
                        startScanning();
                    } else {
                        Toast.makeText(MainActivity.this, "Hãy bật Vị trí!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Hãy bật Wifi!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        buttonStopScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopScanning();
            }
        });

        buttonBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, file_ui_main.class);
                startActivity(intent);
            }
        });
    }

    private void startScanning() {
        String str_numberOfScans = editTextCount.getText().toString();
        if (isInteger(str_numberOfScans)) {
            int numberOfScans = Integer.parseInt(str_numberOfScans);
            if (numberOfScans > 0) {
                ox = editTextX.getText().toString();
                oy = editTextY.getText().toString();
                oz = editTextZ.getText().toString();
                if (isFloat(ox) && isFloat(oy) && isFloat(oz)) {
                    remainingScans = numberOfScans;
                    updateRemainingScans();
                    wifiScanHandler.post(scanRunnable);
                } else {
                    Toast.makeText(this, "Tọa độ không phù hợp!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Số lần quét không phù hợp!", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Số lần quét không phù hợp!", Toast.LENGTH_SHORT).show();
        }
    }

    private Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            if (remainingScans > 0) {
                wifiManager.startScan();
                wifiScanHandler.postDelayed(this, wifiScanInterval);
            } else {
                Toast.makeText(MainActivity.this, "Thu thập dữ liệu hoàn tất", Toast.LENGTH_SHORT).show();
                isstart = false;
                stopScanning();
            }
        }
    };

    private void updateRemainingScans() {
        textViewRemainingScans.setText("Số lần quét: " + remainingScans);
    }

    private void stopScanning() {
        remainingScans = 0;
        wifiScanHandler.removeCallbacksAndMessages(null);
        updateRemainingScans();
        Toast.makeText(this, "Kết thúc thu thập dữ liệu", Toast.LENGTH_SHORT).show();
    }

    class WifiBroadcastReceiver extends BroadcastReceiver {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean ok = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            //nếu khi nhấn nút start khởi động thì quá trình quét bắt đầu nếu có giá trị sẽ trả về true
            //ngược lại sẽ trả về false
            remainingScans--;
            updateRemainingScans();
            if (ok) {
                List<ScanResult> scanResults = wifiManager.getScanResults();
                listwifi = wifiManager.getScanResults();
                saveToCSV(scanResults);
                displayWifiResults(scanResults);
                Log.d(LOG_TAG, "Thu thập dữ liệu hoàn tất");
            } else {
                textViewList.setText("\n\n\n\n\n\n\n\n\n!!!!!! Thu thập dữ liệu xảy ra lỗi !!!!!!");
            }
        }
    }

    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_MAGNETIC_FIELD:
                magneticValues[0] = event.values[0];
                magneticValues[1] = event.values[1];
                magneticValues[2] = event.values[2];
                Log.d(LOG_TAG, "MagneticX: " + magneticValues[0] + ", MagneticY: " + magneticValues[1] + ", MagneticZ: " + magneticValues[2]);
                break;
            case Sensor.TYPE_GYROSCOPE:
                gyroValues[0] = event.values[0];
                gyroValues[1] = event.values[1];
                gyroValues[2] = event.values[2];
                Log.d(LOG_TAG, "GyroX: " + gyroValues[0] + ", GyroY: " + gyroValues[1] + ", GyroZ: " + gyroValues[2]);
                break;
            case Sensor.TYPE_ACCELEROMETER:
                accValues[0] = event.values[0];
                accValues[1] = event.values[1];
                accValues[2] = event.values[2];
                Log.d(LOG_TAG, "AccX: " + accValues[0] + ", AccY: " + accValues[1] + ", AccZ: " + accValues[2]);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void createCSVFile(String ten_file) {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            textViewFile.setText("File: " + ten_file);
            dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

            if (dir != null) {
                if (!dir.exists()) {
                    boolean dirCreated = dir.mkdirs();
                    if (!dirCreated) {
                        Log.d(LOG_TAG, "Không thể tạo thư mục");
                        return;
                    }
                }
                csvFile = new File(dir, ten_file);
                try {
                    boolean isNewFile = !csvFile.exists();
                    fileWriter = new FileWriter(csvFile, true);
                    if (isNewFile) {
                        fileWriter.append(FILE_HEADER);
                        fileWriter.append(NEW_LINE_SEPARATOR);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Lỗi khi tạo hoặc ghi tệp", Toast.LENGTH_SHORT).show();
                } finally {
                    try {
                        if (fileWriter != null) {
                            fileWriter.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                Log.d(LOG_TAG, "Không thể có được thư mục lưu trữ");
                Toast.makeText(this, "Bộ nhớ ngoại vi không có sẵn để ghi.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Bộ nhớ ngoại vi không có sẵn để ghi.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToCSV(List<ScanResult> scanResults) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String currentTime = dateFormat.format(new Date());
        if (isstart) {
            try {
                if (listwifi != null) {
                    fileWriter = new FileWriter(csvFile, true);
                    for (ScanResult result : scanResults) {
                        String[] data = {
                                String.valueOf(remainingScans + 1),
                                currentTime,
                                result.SSID,
                                result.BSSID,
                                String.valueOf(result.level),
                                String.valueOf(gyroValues[0]),
                                String.valueOf(gyroValues[1]),
                                String.valueOf(gyroValues[2]),
                                String.valueOf(accValues[0]),
                                String.valueOf(accValues[1]),
                                String.valueOf(accValues[2]),
                                String.valueOf(magneticValues[0]),
                                String.valueOf(magneticValues[1]),
                                String.valueOf(magneticValues[2]),
                                ox,
                                oy,
                                oz
                        };
                        fileWriter.append(TextUtils.join(COMMA_DELIMITER, data));
                        fileWriter.append(NEW_LINE_SEPARATOR);
                    }
                    fileWriter.flush();
                    fileWriter.close();
                    Toast.makeText(this, "Tệp CSV đã được ghi thành công!", Toast.LENGTH_SHORT).show();
                    readAndDisplayCSVFile();
                } else {
                    Toast.makeText(this, "Không có dữ liệu Wifi!", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Lỗi khi ghi dữ liệu WiFi vào tệp", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void readAndDisplayCSVFile() {
        try {
            if (csvFile.exists()) {
                FileReader fileReader = new FileReader(csvFile);
                BufferedReader bufferedReader = new BufferedReader(fileReader);
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                bufferedReader.close();
                fileReader.close();
            } else {
                Toast.makeText(this, "Tệp không tồn tại", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Lỗi khi đọc tệp", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }


    private void displayWifiResults(List<ScanResult> scanResults) {
        textViewList.setText("");
        for (ScanResult result : scanResults) {
            textViewList.append("SSID: " + result.SSID + "\n");
            textViewList.append("BSSID: " + result.BSSID + "\n");
            textViewList.append("Level: " + result.level + " dBm\n");
            textViewList.append("GyroX: " + gyroValues[0] + "\n");
            textViewList.append("GyroY: " + gyroValues[1] + "\n");
            textViewList.append("GyroZ: " + gyroValues[2] + "\n");
            textViewList.append("AccX: " + accValues[0] + "\n");
            textViewList.append("AccY: " + accValues[1] + "\n");
            textViewList.append("AccZ: " + accValues[2] + "\n");
            textViewList.append("MagneticX: " + magneticValues[0] + "\n");
            textViewList.append("MagneticY: " + magneticValues[1] + "\n");
            textViewList.append("MagneticZ: " + magneticValues[2] + "\n");
            textViewList.append("\n------------------------------------------------\n");
        }
    }

    private void checkPermissions() {
        int permission1 = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);

        if (permission1 != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.ACCESS_NETWORK_STATE},
                    MY_REQUEST_CODE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE);
        }

        if (magnetometerSensor != null) {
            sensorManager.registerListener( this, magnetometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
            Log.d(LOG_TAG, "Thiết bị hỗ trợ cảm biến từ trường");
        } else {
            Log.d(LOG_TAG, "Thiết bị không hỗ trợ cảm biến từ trường");
        }

        if (gyroSensor != null) {
            sensorManager.registerListener( this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL);
            Log.d(LOG_TAG, "Thiết bị hỗ trợ cảm biến con quay hồi chuyển");
        } else {
            Log.d(LOG_TAG, "Thiết bị không hỗ trợ cảm biến con quay hồi chuyển");
        }

        if (accSensor != null) {
            sensorManager.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_NORMAL);
            Log.d(LOG_TAG, "Thiết bị hỗ trợ cảm biến gia tốc");
        } else {
            Log.d(LOG_TAG, "Thiết bị không hỗ trợ cảm biến gia tốc");
        }
    }

    private boolean checkLocationStatus() {
        boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        return isGPSEnabled;
    }

    private boolean checkWifiStatus() {
        return wifiManager.isWifiEnabled();
    }

    private boolean isInteger(String n) {
        try {
            Integer.parseInt(n);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isFloat(String n) {
        try {
            Float.parseFloat(n);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    protected void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "onResume"); // đã quay lại
    }

    @Override
    protected void onPause() {
        super.onPause(); // tạm dừng
        Log.d(LOG_TAG, "onPause");
    }

    protected void onStop() {
        super.onStop();
        Log.d(LOG_TAG, "onStop"); // có thể đã tạm dừng hoặc rơi vào đa nhiệm
    }

    protected void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        super.onDestroy();
        if (wifiReceiver != null) {
            unregisterReceiver(wifiReceiver);
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
}