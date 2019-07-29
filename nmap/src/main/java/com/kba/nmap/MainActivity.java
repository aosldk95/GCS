package com.kba.nmap;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.annotation.UiThread;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.NaverMapOptions;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.ArrowheadPathOverlay;
import com.naver.maps.map.overlay.InfoWindow;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.util.FusedLocationSource;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.GimbalApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes;
import com.o3dr.services.android.lib.drone.companion.solo.SoloState;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.mission.item.command.YawCondition;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Home;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, DroneListener, TowerListener, LinkListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private final Handler handler = new Handler();

    private Spinner modeSelector;

    private static final int DEFAULT_UDP_PORT = 14550;
    private static final int DEFAULT_USB_BAUD_RATE = 57600;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;

    private boolean isGPSEnabled = false;
    private boolean registGps = false;
    private NaverMap mMap;


    int number = 0;
    private Attitude droneYow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Start mainActivity");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);



        locationSource =
                new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        this.modeSelector = (Spinner) findViewById(R.id.modeSelect);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onFlightModeSelected(view);
                ((TextView)parent.getChildAt(0)).setTextColor(Color.WHITE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        MapFragment mapFragment = (MapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            getSupportFragmentManager().beginTransaction().add(R.id.map, mapFragment).commit();
        }

        mapFragment.getMapAsync(this);

    }


    protected void updateAltitude() {
        TextView altitudeTextView = (TextView) findViewById(R.id.altitudeValueTextView);
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        altitudeTextView.setText(String.format("%3.1f", droneAltitude.getAltitude()) + "m");
    }

    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Vehicle mode change successful.");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Vehicle mode change failed: " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out.");
            }
        });
    }

    public void onArmButtonTap(View view) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);

        if (vehicleState.isFlying()) {
            // Land
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to land the vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to land the vehicle.");
                }
            });
        } else if (vehicleState.isArmed()) {
            // Take off
            ControlApi.getApi(this.drone).takeoff(2, new AbstractCommandListener() {

                @Override
                public void onSuccess() {
                    alertUser("Taking off...");
                }

                @Override
                public void onError(int i) {
                    alertUser("Unable to take off.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to take off.");
                }
            });
        } else if (!vehicleState.isConnected()) {
            // Connect
            alertUser("Connect to a drone first");
        } else {
            // Connected but not Armed
            VehicleApi.getApi(this.drone).arm(true, false, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to arm vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Arming operation timed out.");
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,  @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            return;
        }
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);
    }


    @UiThread
    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        naverMap.setMapType(NaverMap.MapType.Satellite);
        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);




        mMap = naverMap;
        final Button ctl2 = findViewById(R.id.con2);


        final Button btn1 = findViewById(R.id.map1);
        btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMap.setMapType(NaverMap.MapType.Basic);
                ctl2.setText("일반지도");
            }

        });
        final Button btn2 = findViewById(R.id.map2);
        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMap.setMapType(NaverMap.MapType.Terrain);
                ctl2.setText("지형도");
            }

        });
        final Button btn3 = findViewById(R.id.map3);
        btn3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMap.setMapType(NaverMap.MapType.Satellite);
                ctl2.setText("위성지도");
            }

        });

        final Button cadastral1 = findViewById(R.id.onff1);
        final Button cadastral2 = findViewById(R.id.onff2);
        cadastral1.setOnClickListener(new View.OnClickListener() {
            int number = 1;
            @Override
            public void onClick(View view) {
                if ((number%2)==0) {
                    cadastral2.setVisibility(View.INVISIBLE);

                } else {
                    cadastral2.setVisibility(View.VISIBLE);

                }
                number += 1;
            }

        });



        cadastral2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (cadastral1.getText()=="지적도 on"){
                    cadastral1.setText("지적도 off");
                    cadastral2.setText("지적도 on");
                    mMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
                }else {
                    cadastral1.setText("지적도 on");
                    cadastral2.setText("지적도 off");
                    mMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);
                }

            }

        });
        ctl2.setOnClickListener(new View.OnClickListener() {
            int number = 1;
            @Override
            public void onClick(View view) {
                if ((number%2)==0) {
                    btn1.setVisibility(View.INVISIBLE);
                    btn2.setVisibility(View.INVISIBLE);
                    btn3.setVisibility(View.INVISIBLE);

                } else {
                    btn1.setVisibility(View.VISIBLE);
                    btn2.setVisibility(View.VISIBLE);
                    btn3.setVisibility(View.VISIBLE);

                }
                number += 1;
            }

        });




//        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
//        LatLong Position = droneGps.getPosition();
//        Toast.makeText(getApplicationContext() , "실행",Toast.LENGTH_LONG).show();

//        if (droneGps.isValid())
//        {
//            Toast.makeText(getApplicationContext() , "마커",Toast.LENGTH_LONG).show();
//            Marker marker = new Marker();
//            marker.setPosition(new LatLng(Position.getLatitude(), Position.getLongitude()));
//            marker.setMap(naverMap);
//        }
    }

    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);
        updateVehicleModesForType(this.droneType);
    }

    protected void updateVehicleModesForType(int droneType) {

        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter();
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
            updateConnectedButton(false);
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }

    protected void updateConnectedButton(Boolean isConnected) {
        Button connectButton = (Button) findViewById(R.id.btnConnect);
        if (isConnected) {
            connectButton.setText("Disconnect");
        } else {
            connectButton.setText("Connect");
        }
    }

//    protected void updateAltitude() {
//        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
//    }

    protected void updateSpeed() {
        TextView speedTextView = (TextView) findViewById(R.id.speedValueTextView);
        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
        speedTextView.setText(String.format("%3.1f", droneSpeed.getGroundSpeed()) + "m/s");
    }

    protected void updateYaw() {
        TextView yawTextView = (TextView) findViewById(R.id.yawview);
        Attitude droneYaw = this.drone.getAttribute(AttributeType.ATTITUDE);
        yawTextView.setText(String.format("%3.1f", droneYaw.getYaw()) + "deg");
    }




    protected void updatesatCount() {
        Gps gps = this.drone.getAttribute(AttributeType.GPS);
        TextView countTextView = (TextView) findViewById(R.id.satelliteview);
        countTextView.setText(String.format("%3.1f", gps.getSatellitesCount())+"개" );
    }

    protected void updatevolt() {
        TextView voltTextView = (TextView) findViewById(R.id.voltview);
        Battery dronevolt = (Battery) this.drone.getAttribute(AttributeType.BATTERY);
        voltTextView.setText(String.format("%3.1f", dronevolt.getBatteryVoltage()) + "V");
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {

        Log.d("asdf",event);

        switch (event) {


            case AttributeEvent.GPS_POSITION:
                updateGpsPosition();
                updatesatCount();
//                updatemap();
                break;

            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                checkSoloState();
                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
                updateArmButton();
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();
                    updateVehicleModesForType(this.droneType);
                }
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;

            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();
                break;

            case AttributeEvent.BATTERY_UPDATED:
                updatevolt();
                break;

            case AttributeEvent.ATTITUDE_UPDATED:
                updateYaw();
                break;

//            case AttributeEvent.GPS_COUNT:
//                updatesatCount();
//                break;



            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;

        }
    }

//    private void updatemap() {
//        final Button mapButton = (Button) findViewById(R.id.button);
//
//
//        mapButton.setOnClickListener(new View.OnClickListener() {
//            private Drone drone;
//            int mapnumber = 1;
//            @Override
//            public void onClick(View view) {
//                if((mapnumber%2)==1) {
//                    Gps gps = this.drone.getAttribute(AttributeType.GPS);
//                    LatLong recentLatLng = gps.getPosition();
//                    CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(recentLatLng.getLatitude(), recentLatLng.getLongitude()));
//                    mMap.moveCamera(cameraUpdate);
//                    mapButton.setText("맵이동");
//                }
//                else {
//                    mapButton.setText("맵잠금");
//                    mMap.moveCamera(null);
//                }
//                mapnumber = mapnumber + 1;
//            }
//        });
//
//    }

//    protected void updatesatCount() {
//        TextView countTextView = (TextView) findViewById(R.id.satelliteview);
//        Gps dronesatCount = this.drone.getAttribute(AttributeType.GPS);
//        countTextView.setText(String.format("%3.1f", dronesatCount.getSatellitesCount())+"개" );
//    }



    private void checkSoloState() {
        final SoloState soloState = drone.getAttribute(SoloAttributes.SOLO_STATE);
        if (soloState == null){
            alertUser("Unable to retrieve the solo state.");
        }
        else {
            alertUser("Solo state is up to date.");
        }
    }
    Marker marker = new Marker();
    List listA = new ArrayList();
    public void updateGpsPosition() {

        Gps gps = this.drone.getAttribute(AttributeType.GPS);
//        TextView countTextView = (TextView) findViewById(R.id.satelliteview);
//        countTextView.setText(String.format("%3.1f", gps.getSatellitesCount()) );
        LatLong recentLatLng = gps.getPosition();
        LatLng naverRecentLatLng = new LatLng(recentLatLng.getLatitude(), recentLatLng.getLongitude());
        marker.setPosition(naverRecentLatLng);
        marker.setMap(mMap);
        marker.setIcon(OverlayImage.fromResource(R.drawable.icons));
        final CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(recentLatLng.getLatitude(), recentLatLng.getLongitude()));
//        mMap.moveCamera(cameraUpdate);


        listA.add(new LatLng(recentLatLng.getLatitude(), recentLatLng.getLongitude()));
        ArrowheadPathOverlay arrowheadPath = new ArrowheadPathOverlay();
        arrowheadPath.setCoords(listA);
        arrowheadPath.setMap(mMap);


        final Button ctl1 = findViewById(R.id.con1);

        final Button maplack1 = findViewById(R.id.lack1);
        final Button maplack2 = findViewById(R.id.lack2);

        maplack1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ctl1.setText("맵잠금");
            }

        });
        maplack2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ctl1.setText("맵이동");
            }

        });
        if(ctl1.getText()=="맵잠금"){
            mMap.moveCamera(cameraUpdate);
        }

        ctl1.setOnClickListener(new View.OnClickListener() {
            int numb = 1;
            @Override
            public void onClick(View view) {
                if ((numb%2)==0) {
                    maplack1.setVisibility(View.INVISIBLE);
                    maplack2.setVisibility(View.INVISIBLE);
                    numb += 1;

                } else if((numb%2)==1) {
                    maplack1.setVisibility(View.VISIBLE);
                    maplack2.setVisibility(View.VISIBLE);
                    numb += 1;

                }

            }

        });






//        final Button mapButton = (Button) findViewById(R.id.button);
//
//        mapButton.setOnClickListener(new View.OnClickListener() {
//
//
//            @Override
//            public void onClick(View view) {
//                if(mapButton.getText()){
//                    mapButton.setText("맵이동");
//                }
//                else{
//                    mapButton.setText("맵잠금");
//                }
//
//            }
//        });
//        if((){
//            mMap.moveCamera(cameraUpdate);
//        }
//        else{
//            mMap.moveCamera(null);
//        }
//
    }



    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    protected void updateArmButton() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button armButton = (Button) findViewById(R.id.arm);

        if (!this.drone.isConnected()) {
            armButton.setVisibility(View.INVISIBLE);
        } else {
            armButton.setVisibility(View.VISIBLE);
        }

        if (vehicleState.isFlying()) {
            // Land
            armButton.setText("LAND");
        } else if (vehicleState.isArmed()) {
            // Take off
            armButton.setText("TAKE OFF");
        } else if (vehicleState.isConnected()) {
            // Connected but not Armed
            armButton.setText("ARM");
        }
    }


    @Override
    public void onTowerConnected() {
        alertUser("DroneKit-Android Connected");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);

    }

    @Override
    public void onTowerDisconnected() {
        alertUser("DroneKit-Android Interrupted");
    }

    public void onBtnConnectTap(View view) {
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        } else{
            ConnectionParameter params = ConnectionParameter.newUdpConnection(null);
            this.drone.connect(params);
        }

    }




    protected double distanceBetweenPoints(LatLongAlt pointA, LatLongAlt pointB) {
        if (pointA == null || pointB == null) {
            return 0;
        }
        double dx = pointA.getLatitude() - pointB.getLatitude();
        double dy = pointA.getLongitude() - pointB.getLongitude();
        double dz = pointA.getAltitude() - pointB.getAltitude();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public void onLinkStateUpdated(@NonNull LinkConnectionStatus connectionStatus) {
        switch(connectionStatus.getStatusCode()){
            case LinkConnectionStatus.FAILED:
                Bundle extras = connectionStatus.getExtras();
                String msg = null;
                if (extras != null) {
                    msg = extras.getString(LinkConnectionStatus.EXTRA_ERROR_MSG);
                }
                alertUser("Connection Failed:" + msg);
                break;
        }

    }
}
