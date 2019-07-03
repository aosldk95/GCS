package com.kba.nmap;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v7.app.AppCompatActivity;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.ArrowheadPathOverlay;
import com.naver.maps.map.overlay.InfoWindow;
import com.naver.maps.map.overlay.Marker;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MapFragment mapFragment = (MapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            getSupportFragmentManager().beginTransaction().add(R.id.map, mapFragment).commit();
        }

        mapFragment.getMapAsync(this);


    }
    @UiThread
    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        LatLng location = new LatLng(35.94528, 126.682167);
        CameraPosition cameraPosition = new CameraPosition(location, 8);
        naverMap.setCameraPosition(cameraPosition);
        Marker marker = new Marker();
        marker.setPosition(new LatLng(35.94528, 126.682167));
        marker.setMap(naverMap);
        Marker marker1 = new Marker();
        marker1.setPosition(new LatLng(35.969389, 126.957333));
        marker1.setMap(naverMap);
        Marker marker2 = new Marker();
        marker2.setPosition(new LatLng(35.983, 126.717));
        marker2.setMap(naverMap);
        Marker marker3 = new Marker();
        marker3.setPosition(new LatLng(35.846833, 127.129361));
        marker3.setMap(naverMap);
        ArrowheadPathOverlay arrowheadPath = new ArrowheadPathOverlay();
        arrowheadPath.setCoords(Arrays.asList(
                new LatLng(35.94528, 126.682167),
                new LatLng(35.983, 126.717),
                new LatLng(35.969389, 126.957333),
                new LatLng(35.846833, 127.129361)
        ));
        Context context = this;
        arrowheadPath.setMap(naverMap);
        InfoWindow infoWindow = new InfoWindow();
        infoWindow.setAdapter(new InfoWindow.DefaultTextAdapter(context) {
            @NonNull
            @Override
            public CharSequence getText(@NonNull InfoWindow infoWindow) {
                return "군산대";
            }
        });
        InfoWindow infoWindow1 = new InfoWindow();
        infoWindow1.setAdapter(new InfoWindow.DefaultTextAdapter(context) {
            @NonNull
            @Override
            public CharSequence getText(@NonNull InfoWindow infoWindow) {
                return "원광대";
            }
        });
        InfoWindow infoWindow2 = new InfoWindow();
        infoWindow2.setAdapter(new InfoWindow.DefaultTextAdapter(context) {
            @NonNull
            @Override
            public CharSequence getText(@NonNull InfoWindow infoWindow) {
                return "군산시청";
            }
        });
        InfoWindow infoWindow3 = new InfoWindow();
        infoWindow3.setAdapter(new InfoWindow.DefaultTextAdapter(context) {
            @NonNull
            @Override
            public CharSequence getText(@NonNull InfoWindow infoWindow) {
                return "전북대";
            }
        });
        infoWindow.open(marker);
        infoWindow1.open(marker1);
        infoWindow2.open(marker2);
        infoWindow3.open(marker3);


    }

}
