package com.example.ubloxreceiver;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.library.BuildConfig;
import org.osmdroid.views.MapView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends Activity implements CompassSensorManager.CompassListener {

    private UsbManager usbManager;
    private UsbDeviceConnection connection;
    private UsbEndpoint endpoint;
    private UsbInterface usbInterface;
    private UsbDevice device;

    private TextView dataTextView;
    private TextView errorTextView;
    private ListView deviceListView;

    private PendingIntent permissionIntent;
    private List<UsbDevice> deviceList = new ArrayList<>();
    private UsbBroadcastReceiver usbBroadcastReceiver;

    private static final String ACTION_USB_PERMISSION = "Permission_ok";

    private MapView mapView;
    private CompassSensorManager compassSensorManager;
    private MapManager mapManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置HTTP请求的用户代理字符串（User-Agent）。这个用户代理字符串是用来标识应用程序，以便地图服务知道请求是来自哪个应用程序
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        setContentView(R.layout.activity_main);

        dataTextView = findViewById(R.id.dataTextView);
        errorTextView = findViewById(R.id.errorTextView);
        deviceListView = findViewById(R.id.deviceListView);

        // 初始化USB广播接收器
        usbBroadcastReceiver = new UsbBroadcastReceiver(this);
        // 注册广播接收器，监听USB设备插入和移除
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(usbBroadcastReceiver, filter);
        // PendingIntent用于请求设备权限
        permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        mapView = findViewById(R.id.mapview);
        mapManager = new MapManager(this, mapView);
        mapManager.Initialize();
        // 初始化指南针传感器管理器
        compassSensorManager = new CompassSensorManager(this, this);

        listUsbDevices();
        UsbDevice device = findUsbDevice();
        if (device != null) {
            // 更新主界面（如设置连接和开始读取数据）
            setupUsbConnection(device);
            startReadingData(device);
        }
    }


    public void listUsbDevices() {
        HashMap<String, UsbDevice> deviceMap = usbManager.getDeviceList();
        // 清除之前的设备列表
        deviceList.clear();
        if (deviceMap.isEmpty()) {
            errorTextView.setText("无USB设备\n");
            return;
        }
        errorTextView.setText("找到USB设备\n");
        // 将设备信息转换为字符串显示在列表中
        List<String> deviceNames = new ArrayList<>();
        for (UsbDevice device : deviceMap.values()) {
            deviceList.add(device); // 保存设备列表
            deviceNames.add("Device Name: " + device.getDeviceName() +
                    "\nVendor ID: " + device.getVendorId() +
                    "\nProduct ID: " + device.getProductId());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);
        deviceListView.setAdapter(adapter);
        // 设置监听器，点击时执行，不点击也会执行后面的代码
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // 获取被点击的设备对象
                UsbDevice selectedDevice = deviceList.get(position);
                // 调用方法请求 USB 权限
                requestUsbPermission(selectedDevice);
            }
        });
    }


    // 请求USB设备权限
    private void requestUsbPermission(UsbDevice device) {
        if (usbManager.hasPermission(device)) {
            // 已经有权限
            Toast.makeText(this, "已有权限", Toast.LENGTH_SHORT).show();
        } else {
            // 请求权限
            usbManager.requestPermission(device, permissionIntent);
        }
    }

    // 寻找指定Ublox
    public UsbDevice findUsbDevice() {
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (device.getVendorId() == 0x1546 && device.getProductId() == 0x01A9) {
                errorTextView.append("已找到设备\n");
                return device;
            }
        }
        return null;
    }

    // 建立连接
    public void setupUsbConnection(UsbDevice device) {
        // 设置interface
        usbInterface = device.getInterface(1);
        // 设置endpoint
        endpoint = usbInterface.getEndpoint(1);
        // 建立连接
        connection = usbManager.openDevice(device);
        if (connection == null) {
            errorTextView.append("建立连接失败\n");
            return;
        } else {
            errorTextView.append("建立连接成功\n");
        }
        // 声明独占
        connection.claimInterface(usbInterface, true);
    }

    //  读取数据
    public void startReadingData(UsbDevice device) {
        new Thread(() -> {
            byte[] buffer = new byte[1024]; // 用于接收数据的缓冲区
            int bytesRead = 0;

            while (true) {
                if (!usbManager.hasPermission(device)) {
                    runOnUiThread(() -> errorTextView.setText("无权限访问USB\n"));
                    break;
                }

                bytesRead = connection.bulkTransfer(endpoint, buffer, buffer.length, 5000);

                if (bytesRead > 0) {

                    // 处理接收到的数据
                    parseUBX_NAV_POSLLH(buffer, bytesRead);

                } else {
                    runOnUiThread(() -> errorTextView.setText("接收不到数据\n"));
                }
            }
        }).start();
    }

    private void parseUBX_NAV_POSLLH(byte[] data, int length) {
        // 确保数据符合格式
        if (length >= 28 && data[0] == (byte) 0xb5 && data[1] == (byte) 0x62 &&
                data[2] == (byte) 0x01 && data[3] == (byte) 0x02) {

            // 解析数据
            int iTOW = byteArrayToInt(data, 6, 4) / 1000; // GPS time of week in seconds
            double lon = byteArrayToInt(data, 10, 4) / 1e7; // Longitude in degrees
            double lat = byteArrayToInt(data, 14, 4) / 1e7; // Latitude in degrees
            double height = byteArrayToInt(data, 18, 4) / 1000.0; // Height in meters
            double hMSL = byteArrayToInt(data, 22, 4) / 1000.0; // Height above mean sea level in meters
            int hAcc = byteArrayToInt(data, 26, 4) / 1000; // Horizontal Accuracy in meters
            int vAcc = byteArrayToInt(data, 30, 4) / 1000; // Vertical Accuracy in meters


            // 将数据转为字符串
            String positionData = "iTOW(s): " + iTOW + "\nLongitude(°E): " + lon + "\nLatitude(°N): " + lat + "\nHeight(m): " + hMSL + "\nhAcc(m): " + hAcc + "\nvAcc(m): " + vAcc;

            // 更新 UI和地图
            runOnUiThread(() -> {
                dataTextView.setText(positionData);
                if(lon != 0){
                    mapManager.setMap(lat,lon);
                }
                errorTextView.setText("正在接收...");
            });
        } else {
            runOnUiThread(() -> errorTextView.setText("不合法的数据或头部字节\n"));
        }
    }

    // 辅助方法，将字节数组转换为整数 byte和int都带符号位，所以不用管符号位
    private int byteArrayToInt(byte[] bytes, int offset, int length) {
        int value = 0;
        for (int i = 0; i < length; i++) {
            value |= (bytes[offset + i] & 0xFF) << (i * 8);
        }
        return value;
    }

    // 在 Activity 从 onPause() 状态回到前台时触发，或者在 Activity 启动时
    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        // 开始监听指南针方向
        compassSensorManager.start();
    }

    // onPause() 在 Activity 不再处于前台并且用户无法与之交互时被调用。
    // 它通常在 Activity 被另一个 Activity 或者对话框覆盖时触发，或者在设备进入睡眠模式时
    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        // 停止监听指南针方向
        compassSensorManager.stop();
    }

    // onDestroy() 是 Activity 即将被销毁（从内存中移除）时调用的
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 注销广播接收器，防止内存泄漏
        unregisterReceiver(usbBroadcastReceiver);
    }


    // 提供指南针方向更新时该做什么
    @Override
    public void onDirectionChanged(float direction) {
        // 更新自定义标记的方向
        mapManager.setDirection(direction);
        // 刷新地图以更新显示
        mapView.invalidate();
    }

}
