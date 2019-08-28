package com.example.geofencing;

import androidx.core.app.NotificationCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static android.webkit.ConsoleMessage.MessageLevel.LOG;

public class Maps1 extends FragmentActivity implements OnMapReadyCallback, GeoQueryEventListener {

    private GoogleMap mMap;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Marker currentUser;
    private DatabaseReference myLocationRef;
    private GeoFire geoFire;
    private List<LatLng> dangerousArea;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps1);

        Dexter.withActivity(this)
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse response) {

                        buildLocationRequest();
                        buildLocationCallback();
                        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(Maps1.this);
                        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
                        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                                .findFragmentById(R.id.map);
                        mapFragment.getMapAsync(Maps1.this);
                        settingGeoFire();
                        initArea();

                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse response) {
                        Toast.makeText(Maps1.this, "You must enable permission", Toast.LENGTH_SHORT).show();

                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {

                    }
                }).check();

    }

    private void initArea() {
        dangerousArea = new ArrayList<>();
        dangerousArea.add(new LatLng(31.780399, 76.996130));
        dangerousArea.add(new LatLng(31.781865, 76.998925));
        dangerousArea.add(new LatLng(31.781015, 76.994496));

    }

    private void settingGeoFire() {
        myLocationRef = FirebaseDatabase.getInstance().getReference("MyLocation");
        geoFire = new GeoFire(myLocationRef);



    }

    private void buildLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (mMap != null) {
                    if (currentUser != null) currentUser.remove();
                    currentUser = mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(locationResult.getLastLocation().getLatitude(),
                                    locationResult.getLastLocation().getLongitude()))
                            .title("You"));
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentUser.getPosition(), 15.0f));

                    geoFire.setLocation("You", new GeoLocation(locationResult.getLastLocation().getLatitude()
                            , locationResult.getLastLocation().getLongitude()), new GeoFire.CompletionListener() {
                        @Override
                        public void onComplete(String key, DatabaseError error) {

                        }
                    });

                }
            }
        };

    }

    private void buildLocationRequest() {

        locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(3000);
        locationRequest.setSmallestDisplacement(10f);
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);

        if (fusedLocationProviderClient != null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                    return;
                }
            }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
        for (LatLng latLng: dangerousArea){
            mMap.addCircle(new CircleOptions().center(latLng)
                    .radius(40)
                    .fillColor(0x220000FF)
                    .strokeWidth(5.0f)
            );

            GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(latLng.latitude,latLng.longitude),0.5f);
            geoQuery.addGeoQueryEventListener(Maps1.this);
        }

    }

    @Override
    protected void onStop() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        super.onStop();
    }

    @Override
    public void onKeyEntered(String key, GeoLocation location) {
        sendNotification("Location",String.format("%s moved in the dangerous area",key));

    }



    @Override
    public void onKeyExited(String key) {
        sendNotification("Location",String.format("%s moved out the dangerous area",key));



    }

    @Override
    public void onKeyMoved(String key, GeoLocation location) {
        sendNotification("Location",String.format("%s are moving within the dangerous area",key));

    }

    @Override
    public void onGeoQueryReady() {

    }

    @Override
    public void onGeoQueryError(DatabaseError error) {
        Toast.makeText(this, "Error:"+error.getCode()+"Nothing", Toast.LENGTH_SHORT).show();
        Log.i("Nothing","World"+error.getDetails());

    }

    private void sendNotification(String title, String content) {
        String NOTIFICATION_CHANNEL_ID = "edm_multiple_location";
        NotificationManager notificationManager =(NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){

            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,"My Notification",
                    NotificationManager.IMPORTANCE_DEFAULT);

           notificationChannel.setDescription("Channel Description");
           notificationChannel.enableLights(true);
           notificationChannel.setLightColor(Color.RED);
           notificationChannel.setVibrationPattern(new long[]{0,1000,500,1000});
           notificationChannel.enableVibration(true);
           notificationManager.createNotificationChannel(notificationChannel);

        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this,NOTIFICATION_CHANNEL_ID);
        builder.setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher));

        Notification notification = builder.build();
        notificationManager.notify(new Random ().nextInt(),notification);
    }

}