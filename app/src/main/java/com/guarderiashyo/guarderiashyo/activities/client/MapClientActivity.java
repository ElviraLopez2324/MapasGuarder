package com.guarderiashyo.guarderiashyo.activities.client;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQueryDataEventListener;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.maps.android.SphericalUtil;
import com.guarderiashyo.guarderiashyo.R;
import com.guarderiashyo.guarderiashyo.activities.InicioActivity;
import com.guarderiashyo.guarderiashyo.activities.guarderia.MapGuarderiaActivity;
import com.guarderiashyo.guarderiashyo.includes.MyToolbar;
import com.guarderiashyo.guarderiashyo.models.Guarderia;
import com.guarderiashyo.guarderiashyo.providers.AuthProvider;
import com.guarderiashyo.guarderiashyo.providers.GeofireProvider;
import com.guarderiashyo.guarderiashyo.providers.TokenProviders;

import java.lang.reflect.Array;
import java.security.Key;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MapClientActivity extends AppCompatActivity implements OnMapReadyCallback {
    AuthProvider mAuthProvider;

    private GoogleMap mMap;
    private SupportMapFragment mMapFragment;

    private LocationRequest mLocationRequest;
    private FusedLocationProviderClient mFusedLocation;

    private final static int LOCATION_REQUEST_CODE = 1;//actua como bandera para solicitar los permisos de ubicacion
    private final static int SETTINGS_REQUEST_CODE = 2;//actua como bandera para solicitar los permisos de ubicacion

    private Marker mMarker;

    private GeofireProvider mGeofireProvider;
    private TokenProviders mTokenProvider;

    private LatLng mActualLatLng;

    private List<Marker> mGuarderiaMarcadores = new ArrayList<>();
    private boolean mIsFirstTime = true;

    private PlacesClient mPlaces;
    private AutocompleteSupportFragment mAutocomplete;
    private AutocompleteSupportFragment mAutocompleteDestination;

    private String mOrigin;
    private LatLng mOriginLatLng;
    private String mDestination;
    private LatLng mDestinationLatLng;

    private GoogleMap.OnCameraIdleListener mCameraListener;

    private Button mBtnRequestGuarderia;


    LocationCallback mLocationCallback = new LocationCallback() {//si se mueve lo registra
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for (Location location : locationResult.getLocations()) {
                if (getApplicationContext() != null) {
                    mActualLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    //obtener la localizacion del cliente en tiempo real


                    if (mMarker != null) {
                        mMarker.remove();//elimnar marca si ya esta
                    }
                    mMarker = mMap.addMarker(new MarkerOptions().position(
                            new LatLng(location.getLatitude(), location.getLongitude())
                            ).title("Tu posicion actual")
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ubicacion))
                    );


                    mMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                            new CameraPosition.Builder()
                                    .target(new LatLng(location.getLatitude(), location.getLongitude()))
                                    .zoom(16f)
                                    .build()
                    ));
                    if (mIsFirstTime) {//solo se ejecutara una vez
                        mIsFirstTime = false;
                        getActiveGuarderias();
                        limitBuscar();//limitacion solo peru
                    }
                }
            }
        }
    };
    public Object Guarderia;
    private Object GuarderiaMarker;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_client);
        MyToolbar.show(this, "Cliente", false);

        mMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mMapFragment.getMapAsync(this);


        mGeofireProvider = new GeofireProvider("active_guarderias");
        mAuthProvider = new AuthProvider();

        mTokenProvider = new TokenProviders();

        mFusedLocation = LocationServices.getFusedLocationProviderClient(this);//iniciar o detener la ubicacion de user

        mBtnRequestGuarderia = findViewById(R.id.btnRequestGuarderia);

        if (!Places.isInitialized()) {//si no esta inicializado

            Places.initialize(getApplicationContext(), getResources().getString(R.string.google_maps_key));
        }

        mPlaces = Places.createClient(this);
        instaceAutocompleteOrigin();
        instaceAutocompleteDestino();
        onCameraMove();


        mBtnRequestGuarderia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestGuarderia();
            }
        });


        generateToken();
    }

    void requestGuarderia() {
        if (mOriginLatLng != null && mDestinationLatLng != null) {
            Intent i = new Intent(MapClientActivity.this, DetailRequestActivity.class);
            i.putExtra("origin_lat", mOriginLatLng.latitude);
            i.putExtra("origin_lng", mOriginLatLng.longitude);
            i.putExtra("destination_lat", mDestinationLatLng.latitude);
            i.putExtra("destination_lng", mDestinationLatLng.longitude);
            i.putExtra("origin", mOrigin);
            i.putExtra("destino", mDestination);
            startActivity(i);

        } else {
            Toast.makeText(this, "Seleccione el lugar de inicio y destino", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.client_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            logout();
        }

        if (item.getItemId() == R.id.action_update) {
            Intent intent = new Intent(MapClientActivity.this, UpdateProfileActivity.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    private void limitBuscar() {

        LatLng nortnSide = SphericalUtil.computeOffset(mActualLatLng, 5000, 0);//radio de 5km
        LatLng southSide = SphericalUtil.computeOffset(mActualLatLng, 5000, 180);
        mAutocomplete.setCountry("PE");
        mAutocomplete.setLocationBias(RectangularBounds.newInstance(southSide, nortnSide));
        mAutocompleteDestination.setCountry("PE");
        mAutocomplete.setLocationBias(RectangularBounds.newInstance(southSide, nortnSide));

    }

    private void onCameraMove() {//mueve la camara
        mCameraListener = new GoogleMap.OnCameraIdleListener() {
            @Override
            public void onCameraIdle() {

                try {

                    Geocoder geocoder = new Geocoder(MapClientActivity.this);
                    mDestinationLatLng = mMap.getCameraPosition().target;
                    List<Address> addressList = geocoder.getFromLocation(mDestinationLatLng.latitude, mDestinationLatLng.longitude, 1);
                    String ciudad = addressList.get(0).getLocality();
                    String pais = addressList.get(0).getCountryName();
                    String direccion = addressList.get(0).getAddressLine(0);
                    mDestination = direccion + " " + ciudad;
                    mAutocompleteDestination.setText(direccion + " " + ciudad);

                } catch (Exception e) {
                    Log.d("Error: ", "Mensaje error" + e.getMessage());
                }
            }
        };
    }

    private void instaceAutocompleteOrigin() {
        mAutocomplete = (AutocompleteSupportFragment) getSupportFragmentManager().findFragmentById(R.id.placeCompleteOrigin);
        mAutocomplete.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.LAT_LNG, Place.Field.NAME));
        mAutocomplete.setHint("Lugar de inicio");
        mAutocomplete.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(@NonNull Place place) {

                mOrigin = place.getName();
                mOriginLatLng = place.getLatLng();
                Log.d("Place", "Name: " + mOrigin);
                Log.d("Place", "Lat: " + mOriginLatLng.latitude);
                Log.d("Place", "Lng: " + mOriginLatLng.longitude);
            }

            @Override
            public void onError(@NonNull Status status) {

            }
        });
    }

    private void instaceAutocompleteDestino() {
        mAutocompleteDestination = (AutocompleteSupportFragment) getSupportFragmentManager().findFragmentById(R.id.placeAutocompleteDestination);
        mAutocompleteDestination.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.LAT_LNG, Place.Field.NAME));
        mAutocompleteDestination.setHint("Lugar de destino");
        mAutocompleteDestination.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(@NonNull Place place) {

                mDestination = place.getName();
                mDestinationLatLng = place.getLatLng();
                Log.d("Place", "Name: " + mDestination);
                Log.d("Place", "Lat: " + mDestinationLatLng.latitude);
                Log.d("Place", "Lng: " + mDestinationLatLng.longitude);
            }

            @Override
            public void onError(@NonNull Status status) {

            }
        });

    }

    private void getActiveGuarderias() {
        mGeofireProvider.getActiveGuarderias(mActualLatLng, 20).addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                // AÑADIREMOS LOS MARCADORES DE LOS CONDUCTORES QUE SE CONECTEN EN LA APLICACION

                for (Marker marker : mGuarderiaMarcadores) {
                    if (marker.getTag() != null) {
                        if (marker.getTag().equals(key)) {
                            return;
                        }
                    }
                }

                LatLng guarderiaLatLng = new LatLng(location.latitude, location.longitude);
                Marker marker = mMap.addMarker(new MarkerOptions().position(guarderiaLatLng).title("Guarderia disponible").title("SERVICIOS").snippet("Estimulacion temprana, desarrollo de confianza").icon(BitmapDescriptorFactory.fromResource(R.drawable.madre)));
                //Toast.makeText(MapClientActivity.this, "servicios", Toast.LENGTH_SHORT).show();
                marker.setTag(key);
                mGuarderiaMarcadores.add(marker);
            }

            @Override
            public void onKeyExited(String key) {
                for (Marker marker : mGuarderiaMarcadores) {
                    if (marker.getTag() != null) {
                        if (marker.getTag().equals(key)) {
                            marker.remove();
                            mGuarderiaMarcadores.remove(marker);
                            return;
                        }
                    }
                }
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                // ACTUALIZAR LA POSICION DE CADA CONDUCTOR
                for (Marker marker : mGuarderiaMarcadores) {
                    if (marker.getTag() != null) {
                        if (marker.getTag().equals(key)) {
                            marker.setPosition(new LatLng(location.latitude, location.longitude));
                        }
                    }
                }
            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        mMap = googleMap;

        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.getUiSettings().setZoomControlsEnabled(true);

        mMap.setOnCameraIdleListener(mCameraListener);


        //mMap.setMyLocationEnabled(true);

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setSmallestDisplacement(5);

        startLocaction();
    }

    //validacion de ubicacion necesarias
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    if (gpsActivar()) {
                        mFusedLocation.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                        mMap.setMyLocationEnabled(true);
                    } else {
                        mostrarDialogNoGps();
                    }
                } else {
                    checkLocationPermissions();
                }
            } else {
                checkLocationPermissions();
            }

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SETTINGS_REQUEST_CODE && gpsActivar()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            mFusedLocation.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
            mMap.setMyLocationEnabled(true);

        }else if(requestCode == SETTINGS_REQUEST_CODE && !gpsActivar()){
            mostrarDialogNoGps();
        }
    }

    private void mostrarDialogNoGps(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Porfavor activa tu ubicación para continuar")
                .setPositiveButton("Configuraciones", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        startActivityForResult( new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), SETTINGS_REQUEST_CODE);
                    }
                }).create().show();
    }

    private boolean  gpsActivar(){
        boolean isActive = false;
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            isActive = true;
        }
        return isActive;
    }

    private void startLocaction(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if(gpsActivar()){
                    mFusedLocation.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                    mMap.setMyLocationEnabled(true);
                }else{
                    mostrarDialogNoGps();
                }


            }
            else{
                checkLocationPermissions();
            }
        } else {
            if(gpsActivar()){
                mFusedLocation.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                mMap.setMyLocationEnabled(true);

            }
            else{
                mostrarDialogNoGps();
            }
        }
    }

    private  void checkLocationPermissions(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                new AlertDialog.Builder(this)
                        .setTitle("Proporciona los permisos para continuar")
                        .setMessage("Esta aplicacion requiere de los permisos de ubicacion para poder utilizarse")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ActivityCompat.requestPermissions(MapClientActivity.this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
                            }
                        })
                        .create()
                        .show();
            }
            else{
                ActivityCompat.requestPermissions(MapClientActivity.this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
            }
        }
    }

    void logout(){
        mAuthProvider.logout();
        Intent i = new Intent(MapClientActivity.this, InicioActivity.class);
        startActivity(i);
        finish();

    }

    void generateToken(){

        mTokenProvider.create(mAuthProvider.getId());
    }
}
