package com.example.ubloxreceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.widget.Toast;

public class UsbBroadcastReceiver extends BroadcastReceiver {
    private MainActivity mainActivity;
    private static final String ACTION_USB_PERMISSION = "Permission_ok";

    public UsbBroadcastReceiver(MainActivity activity) {
        mainActivity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            Toast.makeText(context, "USB 设备已插入", Toast.LENGTH_SHORT).show();

            // 刷新设备列表
            mainActivity.listUsbDevices();
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            Toast.makeText(context, "USB 设备已移除", Toast.LENGTH_SHORT).show();

            // 刷新设备列表
            mainActivity.listUsbDevices();
        } else if (ACTION_USB_PERMISSION.equals(action)) {

            synchronized (this) {
                UsbDevice device = mainActivity.findUsbDevice();

                if (device != null) {
                    Toast.makeText(context, "已连接", Toast.LENGTH_SHORT).show();
                    // 更新主界面（如设置连接和开始读取数据）
                    mainActivity.setupUsbConnection(device);
                    mainActivity.startReadingData(device);
                } else {
                    Toast.makeText(context, "连接失败", Toast.LENGTH_SHORT).show();
                }

            }
        }
    }

}
