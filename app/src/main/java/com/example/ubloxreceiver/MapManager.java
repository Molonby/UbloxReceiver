package com.example.ubloxreceiver;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;


public class MapManager {

    private Context context;
    private MapView mapView;
    private CustomMarkerOverlay markerOverlay;
    private GeoPoint nowGeoPoint;

    public MapManager(Context context, MapView mapView){
        this.context = context;
        this.mapView = mapView;
    }

    public void Initialize() {

        // MapView 是 osmdroid 提供的地图视图组件，用于显示地图
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);

        // 设置默认缩放级别
        mapView.getController().setZoom(18.8);

        // 设置默认地图中心
        nowGeoPoint = new GeoPoint(1.3404167, 103.680129);
        mapView.getController().setCenter(nowGeoPoint);

        // 显示用户当前位置的覆盖层
        MyLocationNewOverlay locationOverlay = new MyLocationNewOverlay(mapView);
        mapView.getOverlays().add(locationOverlay);

        // 指南针覆盖层
        CompassOverlay compassOverlay = new CompassOverlay(context, mapView);
        mapView.getOverlays().add(compassOverlay);
        // 通过传感器获取手机指南针朝向
        compassOverlay.enableCompass();

        // 添加自定义当前位置标记
        markerOverlay = new CustomMarkerOverlay();
        mapView.getOverlays().add(markerOverlay);
        markerOverlay.setPosition(nowGeoPoint);
        markerOverlay.setDirection(90); // 示例方向：45度

    }

    public void setDirection(float direction){
        markerOverlay.direction = direction;
    }

    public void setMap(double latitude, double longitude){
        nowGeoPoint.setCoords(latitude,longitude);
        mapView.getController().setCenter(nowGeoPoint);
        markerOverlay.setPosition(nowGeoPoint);
    }


    // 自定义标记覆盖层
    private class CustomMarkerOverlay extends Overlay {
        private GeoPoint position;
        private float direction;
        // 设置位置
        public void setPosition(GeoPoint position) {
            this.position = position;
        }
        // 设置方向
        public void setDirection(float direction) {
            this.direction = direction;
        }
        // 自定义图标
        @Override
        public void draw(Canvas canvas, MapView mapView, boolean shadow) {
            super.draw(canvas, mapView, shadow);
            if (position != null) {
                // 转换地理坐标到屏幕坐标
                Point screenPoint = new Point();
                mapView.getProjection().toPixels(position, screenPoint);
                // 绘制标记
                Paint paint = new Paint();
                paint.setAntiAlias(true);
                paint.setColor(Color.GREEN);
                paint.setStyle(Paint.Style.FILL);
                // 绘制小圆点
                float radius = 30;
                canvas.drawCircle(screenPoint.x, screenPoint.y, radius, paint);
                // 绘制箭头
                Path path = new Path();
                path.moveTo(screenPoint.x, screenPoint.y - radius);
                path.lineTo(screenPoint.x - radius / 2, screenPoint.y + radius / 2);
                path.lineTo(screenPoint.x + radius / 2, screenPoint.y + radius / 2);
                path.close();
                paint.setColor(android.graphics.Color.BLACK);
                canvas.save();
                canvas.rotate(direction, screenPoint.x, screenPoint.y);
                canvas.drawPath(path, paint);
                canvas.restore();
            }
        }
    }


}


