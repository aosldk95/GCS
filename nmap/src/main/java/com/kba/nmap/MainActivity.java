package com.kba.nmap;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.ArrowheadPathOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.util.FusedLocationSource;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
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
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;
import com.o3dr.services.android.lib.util.MathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, DroneListener, TowerListener, LinkListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private Drone drone ;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;

    private Spinner modeSelector;

    private static final int DEFAULT_UDP_PORT = 14550;
    private static final int DEFAULT_USB_BAUD_RATE = 57600;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;

    private boolean isGPSEnabled = false;
    private boolean registGps = false;
    private NaverMap mMap;

    private ArrayList<String>list;
    private ArrayList<LatLong>listLat;
    private SimpleTextAdapter adapter;
    private RecyclerView recyclerView;
    int altit = 2;
    int inte = 5;
    int dista =50;

    public Handler handler = new Handler();

//    private Handler mHandler;
//    private CustomRunnable mCustomRunnable;
//    private TextView mTvTimer;
//    private String mCurrentTime;

    private TimerTask second;

    int count = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listLat = new ArrayList<>();
        list = new ArrayList<>();
        recyclerView = findViewById(R.id.recycler1);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SimpleTextAdapter(list);
        recyclerView.setAdapter(adapter);

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
        testStart();
    }

    public void testStart() {

        second = new TimerTask() {

            @Override
            public void run() {
                Update();
            }
        };
        Timer timer = new Timer();
        timer.schedule(second, 5000, 5000);
    }

    protected void Update() {
        Runnable updater = new Runnable() {
            public void run() {
                if (list.size() > 0) {
                    list.remove(0) ;
                    adapter.notifyDataSetChanged();
                }
            }
        };
        handler.post(updater);
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
                list.add("비행모드 변환.");
                adapter.notifyDataSetChanged();
                recyclerView.scrollToPosition(list.size()-1);
            }

            @Override
            public void onError(int executionError) {
                list.add("비행모드변환 실패: " + executionError);
                adapter.notifyDataSetChanged();
                recyclerView.scrollToPosition(list.size()-1);
            }

            @Override
            public void onTimeout() {
                list.add("비행모드 변환 시간 초과.");
                adapter.notifyDataSetChanged();
                recyclerView.scrollToPosition(list.size()-1);
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
                    list.add("착륙불가.");
                    adapter.notifyDataSetChanged();
                    recyclerView.scrollToPosition(list.size()-1);
                }

                @Override
                public void onTimeout() {
                    list.add("착륙불가.");
                    adapter.notifyDataSetChanged();
                    recyclerView.scrollToPosition(list.size()-1);
                }
            });
        } else if (vehicleState.isArmed()) {
            // Take off
            ControlApi.getApi(this.drone).takeoff(altit, new AbstractCommandListener() {

                @Override
                public void onSuccess() {
                    list.add("이륙");
                    adapter.notifyDataSetChanged();
                    recyclerView.scrollToPosition(list.size()-1);
                }

                @Override
                public void onError(int i) {
                    list.add("이륙불가.");
                    adapter.notifyDataSetChanged();
                    recyclerView.scrollToPosition(list.size()-1);
                }

                @Override
                public void onTimeout() {
                    list.add("이륙불가.");
                    adapter.notifyDataSetChanged();
                    recyclerView.scrollToPosition(list.size()-1);
                }
            });
        } else if (!vehicleState.isConnected()) {
            // Connect
            list.add("먼저 드론을 연결");
            adapter.notifyDataSetChanged();
            recyclerView.scrollToPosition(list.size()-1);
        } else {

            AlertDialog.Builder alert_confirm = new AlertDialog.Builder(MainActivity.this);
            alert_confirm.setMessage("시동을 걸면 프로펠러가 고속으로 회전합니다.").setCancelable(false).setPositiveButton("확인",
                    new DialogInterface.OnClickListener() {

                        @Override

                        public void onClick(DialogInterface dialog, int which) {

                            VehicleApi.getApi(drone).arm(true, false, new SimpleCommandListener() {
                                @Override
                                public void onError(int executionError) {
                                    list.add("arm 불가.");
                                    adapter.notifyDataSetChanged();
                                    recyclerView.scrollToPosition(list.size()-1);
                                }

                                @Override
                                public void onTimeout() {
                                    list.add("아밍시간 초과.");
                                    adapter.notifyDataSetChanged();
                                    recyclerView.scrollToPosition(list.size()-1);
                                }
                            });
                            // 'YES'
                        }
                    }).setNegativeButton("취소",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // 'No'
                            return;
                        }
                    });
            AlertDialog alert = alert_confirm.create();
            alert.show();
            // Connected but not Armed
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

    Marker marker2 = new Marker();
    Marker marker3 = new Marker();

    @UiThread
    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        final State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        naverMap.setMapType(NaverMap.MapType.Satellite);
        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
        UiSettings uiSettings = naverMap.getUiSettings();

        mMap = naverMap;
        final Button ctl2 = findViewById(R.id.con2);

        final Button alt = findViewById(R.id.atitu);
        final Button missio = findViewById(R.id.mission);
        final Button inter = findViewById(R.id.interval);
        final Button distan = findViewById(R.id.distant);
        final Button interup = findViewById(R.id.intervalup);
        final Button interdown = findViewById(R.id.intervaldown);
        final Button distanup = findViewById(R.id.distantup);
        final Button distandown = findViewById(R.id.distantdown);


        final Button upper = findViewById(R.id.up);
        final Button downer = findViewById(R.id.down);

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

        alt.setOnClickListener(new View.OnClickListener() {
            int number = 1;
            @Override
            public void onClick(View view) {
                if ((number%2)==0) {
                    upper.setVisibility(View.INVISIBLE);
                    downer.setVisibility(View.INVISIBLE);
                } else {
                    upper.setVisibility(View.VISIBLE);
                    downer.setVisibility(View.VISIBLE);
                }
                number += 1;
            }
        });

        upper.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                altit += 1;
                alt.setText("이륙고도:" + altit + "m");
            }
        });

        downer.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                altit -= 1;
                alt.setText("이륙고도:" + altit + "m");
            }
        });

        inter.setOnClickListener(new View.OnClickListener() {
            int number = 1;
            @Override
            public void onClick(View view) {
                if ((number%2)==0) {
                    interup.setVisibility(View.INVISIBLE);
                    interdown.setVisibility(View.INVISIBLE);
                } else {
                    interup.setVisibility(View.VISIBLE);
                    interdown.setVisibility(View.VISIBLE);
                }
                number += 1;
            }
        });

        interup.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                inte += 1;
                inter.setText("간격" + inte + "m");
            }
        });

        interdown.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                inte -= 1;
                inter.setText("간격:" + inte + "m");
            }
        });

        distan.setOnClickListener(new View.OnClickListener() {
            int number = 1;
            @Override
            public void onClick(View view) {
                if ((number%2)==0) {
                    distanup.setVisibility(View.INVISIBLE);
                    distandown.setVisibility(View.INVISIBLE);
                } else {
                    distanup.setVisibility(View.VISIBLE);
                    distandown.setVisibility(View.VISIBLE);
                }
                number += 1;
            }
        });

        distanup.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                dista += 1;
                distan.setText("거리:" + dista + "m");
            }
        });

        distandown.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                dista -= 1;
                distan.setText("거리:" + dista + "m");
            }
        });

        cadastral2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (cadastral1.getText()=="지적도 on"){
                    cadastral1.setText("지적도 off");
                    cadastral2.setText("지적도 on");
                    cadastral2.setVisibility(View.INVISIBLE);
                    mMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
                }else {
                    cadastral1.setText("지적도 on");
                    cadastral2.setText("지적도 off");
                    cadastral2.setVisibility(View.INVISIBLE);
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

        missio.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                vehicleState.setVehicleMode(VehicleMode.COPTER_AUTO);
                VehicleMode vehicleMode = vehicleState.getVehicleMode();
                ArrayAdapter arrayAdapter = (ArrayAdapter) modeSelector.getAdapter();
                modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
            }
        });

        uiSettings.setZoomControlEnabled(false);
        mMap.setOnMapClickListener(new NaverMap.OnMapClickListener() {
            @Override
            public void onMapClick(@NonNull PointF point, @NonNull final LatLng coord) {
                AlertDialog.Builder alert_confirm = new AlertDialog.Builder(MainActivity.this);
                alert_confirm.setMessage("해당 좌표로 사각형의 한점을 선택하시겠습니까?").setCancelable(false).setPositiveButton("확인",
                        new DialogInterface.OnClickListener() {


                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                Marker marker4 = new Marker();
                                Marker marker5 = new Marker();

                                // 'YES'
                                if (count == 1) {
                                    marker3.setPosition(new LatLng(coord.latitude, coord.longitude));
                                    marker3.setMap(mMap);
                                    marker3.setIcon(OverlayImage.fromResource(R.drawable.iconsa));
                                    count += 1;

                                } else if(count == 2){

                                    marker2.setPosition(new LatLng(coord.latitude, coord.longitude));
                                    marker2.setMap(mMap);
                                    marker2.setIcon(OverlayImage.fromResource(R.drawable.iconsb));
                                    count -= 1;

                                }

                                LatLng position = marker3.getPosition();
                                LatLng position1 = marker2.getPosition();
                                LatLong posi = new LatLong(position.latitude, position.longitude);
                                LatLong posi1 = new LatLong(position1.latitude, position1.longitude);
                                listLat.add(posi);
                                listLat.add(posi1);

                                double math = MathUtils.getDistance2D(posi,posi1);
                                double angle = MathUtils.getHeadingFromCoordinates(posi,posi1);
                                double xline = 300 * Math.cos(angle+90);
                                double yline = 300 * Math.sin(angle+90);

                                if((listLat.size()/4)==1){
                                    LatLong adddis = MathUtils.addDistance(posi, xline, yline);
                                    LatLng addis = new LatLng(adddis.getLatitude(),adddis.getLongitude());
                                    marker4.setPosition(addis);
                                    marker4.setMap(mMap);

                                    LatLong adddis1 = MathUtils.addDistance(posi1, xline, yline);
                                    LatLng addis1 = new LatLng(adddis1.getLatitude(),adddis1.getLongitude());
                                    marker5.setPosition(addis1);
                                    marker5.setMap(mMap);

                                }

                                list.add(Double.toString(math));
                                list.add(Double.toString(angle));
                                adapter.notifyDataSetChanged();
                                recyclerView.scrollToPosition(list.size()-1);

                            }
                        }).setNegativeButton("취소",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // 'No'
                                return;
                            }
                        });
                AlertDialog alert = alert_confirm.create();
                alert.show();
            }
        });


        mMap.setOnMapLongClickListener(new NaverMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(@NonNull PointF point, @NonNull final LatLng coord) {

                AlertDialog.Builder alert_confirm = new AlertDialog.Builder(MainActivity.this);
                alert_confirm.setMessage("해당 위치로 이동하시겠습니까?").setCancelable(false).setPositiveButton("확인",
                        new DialogInterface.OnClickListener() {


                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // 'YES'
                                Marker marker1 = new Marker();
                                marker1.setPosition(new LatLng(coord.latitude, coord.longitude));
                                marker1.setMap(mMap);

                                vehicleState.setVehicleMode(VehicleMode.COPTER_GUIDED);
                                VehicleMode vehicleMode = vehicleState.getVehicleMode();
                                ArrayAdapter arrayAdapter = (ArrayAdapter) modeSelector.getAdapter();
                                modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));

                                ControlApi.getApi(drone).goTo(new LatLong(coord.latitude, coord.longitude), true, new AbstractCommandListener(){
                                    @Override
                                    public void onSuccess() {
                                        list.add("포인트로");
                                        adapter.notifyDataSetChanged();
                                        recyclerView.scrollToPosition(list.size()-1);
                                    }
                                    @Override
                                    public void onError(int i) {
                                        list.add("에러");
                                        adapter.notifyDataSetChanged();
                                        recyclerView.scrollToPosition(list.size()-1);
                                    }
                                    @Override
                                    public void onTimeout() {
                                        list.add("시간초과");
                                        adapter.notifyDataSetChanged();
                                        recyclerView.scrollToPosition(list.size()-1);
                                    }
                                } );

                            }
                        }).setNegativeButton("취소",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // 'No'
                                return;
                            }
                        });
                AlertDialog alert = alert_confirm.create();
                alert.show();
            }
        });
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

    protected void updateConnectedButton(Boolean isConnected) {
        Button connectButton = (Button) findViewById(R.id.btnconnect);
        if (isConnected) {
            connectButton.setText("Disconnect");
        } else {
            connectButton.setText("Connect");
        }
    }

    protected void updateSpeed() {
        TextView speedTextView = (TextView) findViewById(R.id.speedValueTextView);
        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
        speedTextView.setText(String.format("%3.1f", droneSpeed.getGroundSpeed()) + "m/s");
    }

    protected void updatevolt() {
        TextView voltTextView = (TextView) findViewById(R.id.voltview);
        Battery dronevolt = (Battery) this.drone.getAttribute(AttributeType.BATTERY);
        voltTextView.setText(String.format("%3.1f", dronevolt.getBatteryVoltage()) + "V");
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {

        switch (event) {

            case AttributeEvent.GPS_POSITION:
                updateGpsPosition();
                break;

            case AttributeEvent.STATE_CONNECTED:
                list.add("드론연결");
                adapter.notifyDataSetChanged();
                recyclerView.scrollToPosition(list.size()-1);
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                checkSoloState();
                break;

            case AttributeEvent.STATE_DISCONNECTED:
                list.add("드론 연결해제");
                adapter.notifyDataSetChanged();
                recyclerView.scrollToPosition(list.size()-1);
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

            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    private void checkSoloState() {
        final SoloState soloState = drone.getAttribute(SoloAttributes.SOLO_STATE);
        if (soloState == null){
            list.add("상태검색불가.");
            adapter.notifyDataSetChanged();
            recyclerView.scrollToPosition(list.size()-1);
        }
        else {
            list.add("상태 업데이트.");
            adapter.notifyDataSetChanged();
            recyclerView.scrollToPosition(list.size()-1);
        }
    }

    Marker marker = new Marker();
    List listA = new ArrayList();
    final ArrowheadPathOverlay arrowheadPath = new ArrowheadPathOverlay();
    public void updateGpsPosition() {

        Gps gps = this.drone.getAttribute(AttributeType.GPS);
        TextView countTextView = (TextView) findViewById(R.id.satelliteview);
        countTextView.setText(String.valueOf(gps.getSatellitesCount()));
        LatLong recentLatLng = gps.getPosition();
        LatLng naverRecentLatLng = new LatLng(recentLatLng.getLatitude(), recentLatLng.getLongitude());
        marker.setPosition(naverRecentLatLng);
        marker.setMap(mMap);
        marker.setIcon(OverlayImage.fromResource(R.drawable.grup));
        final CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(recentLatLng.getLatitude(), recentLatLng.getLongitude()));

        listA.add(new LatLng(recentLatLng.getLatitude(), recentLatLng.getLongitude()));
        arrowheadPath.setCoords(listA);
        arrowheadPath.setMap(mMap);
        Button clea = findViewById(R.id.clr);
        clea.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listA.clear();
                arrowheadPath.setMap(null);
            }

        });

        final Button ctl1 = findViewById(R.id.con1);

        final Button maplack1 = findViewById(R.id.lack1);
        final Button maplack2 = findViewById(R.id.lack2);

        maplack1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ctl1.setText("맵잠금");
                maplack1.setVisibility(View.INVISIBLE);
                maplack2.setVisibility(View.INVISIBLE);
            }

        });
        maplack2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ctl1.setText("맵이동");
                maplack1.setVisibility(View.INVISIBLE);
                maplack2.setVisibility(View.INVISIBLE);
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
    }

    protected void updateYaw() {
        TextView yawTextView = (TextView) findViewById(R.id.yawview);
        Attitude droneYaw = this.drone.getAttribute(AttributeType.ATTITUDE);
        float yaw=(float)droneYaw.getYaw();
        if (yaw<0){
            yaw = 360 + yaw;
        }
        yawTextView.setText(String.format("%3.1f", yaw) + "deg");
        marker.setAngle(yaw);
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {
    }

    protected void updateArmButton() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        final Button armButton = (Button) findViewById(R.id.arm);

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
        list.add("드론과 안드로이드 연결");
        adapter.notifyDataSetChanged();
        recyclerView.scrollToPosition(list.size()-1);
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);

    }

    @Override
    public void onTowerDisconnected() {
        list.add("드론과 안드로이드 연결 해제 ");
        adapter.notifyDataSetChanged();
        recyclerView.scrollToPosition(list.size()-1);
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
                list.add("연결실패:" + msg);
                adapter.notifyDataSetChanged();
                recyclerView.scrollToPosition(list.size()-1);
                break;
        }
    }
}
