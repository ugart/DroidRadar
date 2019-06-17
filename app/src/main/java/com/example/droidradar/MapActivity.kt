package com.example.droidradar

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng

@SuppressLint("ByteOrderMark")
class MapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private val initialZoom = 18F
    private val locationPermissionRequestCode = 100

    private val locationUpdateInterval = 2000L
    private val locationUpdateFastestInterval = 1000L

    private var mMap: GoogleMap? = null
    private var lastKnowLocation: Location? = null
    private lateinit var mapFragment: SupportMapFragment

    private var googleApiClient: GoogleApiClient? = null

    private var locationCallback: LocationCallback? = null
    private var fusedLocationClient: FusedLocationProviderClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        setupMap()

    }

    override fun onStart() {
        super.onStart()
        googleApiClient?.connect()
    }

    override fun onStop() {
        super.onStop()
        googleApiClient?.disconnect()
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        startLocationUpdates()

        if (mMap != null && !mMap?.isMyLocationEnabled!! && isLocationPermissionGranted()) {
            mMap?.isMyLocationEnabled = true
        }

    }

    private fun buildLocationRequest(interval: Long?, fastestInterval: Long?): LocationRequest {
        val lr = LocationRequest()
        lr.interval = interval!!
        lr.fastestInterval = fastestInterval!!
        lr.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        return lr
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionRequestCode) {
            if (isLocationPermissionGranted()) {
                mMap?.isMyLocationEnabled = true
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {

        val locationRequest = buildLocationRequest(locationUpdateInterval, locationUpdateFastestInterval)

        if (isLocationPermissionGranted()) {
            fusedLocationClient?.requestLocationUpdates(locationRequest, locationCallback, null)
        }

    }

    private fun stopLocationUpdates() {
        fusedLocationClient?.removeLocationUpdates(locationCallback)
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {

        mMap = googleMap

        mMap?.uiSettings?.isMapToolbarEnabled = false
        mMap?.uiSettings?.isCompassEnabled = false

        mMap?.mapType = GoogleMap.MAP_TYPE_SATELLITE

        if (isLocationPermissionGranted()) {
            mMap?.isMyLocationEnabled = true
        } else {
            AlertDialog.Builder(this)
                    .setTitle("Precisamos da sua localização")
                    .setMessage("Este aplicativo necessita da sua permissão para acessar sua localização. Por favor, nos forneça esta permissão.")
                    .setPositiveButton("OK") { _, _ ->
                        requestLocationPermission()
                    }
                    .create()
                    .show()
        }

        val locationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        try {
            if (isLocationPermissionGranted()) {

                val location = locationProviderClient.lastLocation
                location.addOnCompleteListener { task ->

                    if (task.isSuccessful) {
                        val currentLocation = task.result
                        moveCameraAndZoom(LatLng(currentLocation!!.latitude, currentLocation.longitude), initialZoom)
                    }

                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

    }

    private fun setupMap() {

        googleApiClient = GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build()

        MapsInitializer.initialize(this)

        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)

                if (locationResult == null || locationResult.lastLocation == null) {
                    return
                }

                lastKnowLocation = locationResult.lastLocation

            }

        }

    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationPermissionRequestCode
        )
    }

    private fun moveCameraAndZoom(latLng: LatLng, zoomLevel: Float) {
        val cameraPosition = CameraPosition.Builder().zoom(zoomLevel).target(latLng).build()
        val center = CameraUpdateFactory.newCameraPosition(cameraPosition)
        mMap?.moveCamera(center)
    }

    override fun onConnected(p0: Bundle?) {
        startLocationUpdates()
    }

    override fun onConnectionSuspended(i: Int) {
        stopLocationUpdates()
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        stopLocationUpdates()
    }

}
