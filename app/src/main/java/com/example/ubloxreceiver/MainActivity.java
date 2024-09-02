package com.example.ubloxreceiver;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends Activity {

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

    private static final String ACTION_USB_PERMISSION = "com.example.USB_PERMISSION";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dataTextView = findViewById(R.id.dataTextView);
        errorTextView = findViewById(R.id.errorTextView);
        deviceListView = findViewById(R.id.deviceListView);

        // PendingIntent用于请求设备权限
        permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // 列出所有已连接的USB设备
        listUsbDevices();

        device = findUsbDevice();
        if(device != null) {
        // 建立连接
        setupUsbConnection(device);
        // 传输数据
        startReadingData(device);
        }else {
            errorTextView.append("还未找到设备\n");
        }


    }


    private void listUsbDevices() {

        HashMap<String, UsbDevice> deviceMap;
        deviceMap = usbManager.getDeviceList();
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


    private UsbDevice findUsbDevice() {
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (device.getVendorId() == 0x1546 && device.getProductId() == 0x01A9) {
                errorTextView.append("已找到设备\n");
                return device;
            }
        }
        return null;
    }


    private void setupUsbConnection(UsbDevice device) {
        // 设置interface
        usbInterface = device.getInterface(1);
        // 设置endpoint
        endpoint = usbInterface.getEndpoint(1);
        // 建立连接
        connection = usbManager.openDevice(device);
        if (connection == null) {
            errorTextView.append("建立连接失败\n");
            return;
        }else{
            errorTextView.append("建立连接成功\n");
        }
        // 声明独占
        connection.claimInterface(usbInterface, true);
    }


    private void startReadingData(UsbDevice device) {
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
                    parseUBX_NAV_POSECEF(buffer, bytesRead);

                } else {
                    runOnUiThread(() -> errorTextView.setText("接收不到数据\n"));
                }
            }
        }).start();
    }

    private void parseUBX_NAV_POSECEF(byte[] data, int length) {
        // 确保数据符合格式
        if (length >= 20 && data[0] == (byte) 0xb5 && data[1] == (byte) 0x62 &&
                data[2] == (byte) 0x01 && data[3] == (byte) 0x01) {

            // 解析数据
            int iTOW = byteArrayToInt(data, 6, 4)/1000;
            double ecefX = byteArrayToInt(data, 10, 4)/100.0;
            double ecefY = byteArrayToInt(data, 14, 4)/100.0;
            double ecefZ = byteArrayToInt(data, 18, 4)/100.0;
            int pAcc = byteArrayToInt(data, 22, 4)/100;


            // 将数据转为字符串
            String positionData = "iTOW(s): " + iTOW + "\nECEF X(m): " + ecefX + "\nECEF Y(m): " + ecefY + "\nECEF Z(m): " + ecefZ + "\npAcc(m): " + pAcc;

            // 更新 UI
            runOnUiThread(() -> {
                dataTextView.setText(positionData);
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
}
