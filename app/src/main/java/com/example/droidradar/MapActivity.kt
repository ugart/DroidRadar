package com.example.droidradar

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.widget.SeekBar
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.maps.android.SphericalUtil
import kotlinx.android.synthetic.main.activity_maps.*

class MapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener {

    private val initialZoom = 18F
    private val locationPermissionRequestCode = 100

    private val locationUpdateInterval = 2000L
    private val locationUpdateFastestInterval = 1000L

    private var raio: Int? = 1
    private var mMap: GoogleMap? = null
    private var lastKnowLocation: Location? = null
    private lateinit var mapFragment: SupportMapFragment

    private var googleApiClient: GoogleApiClient? = null

    private var locationCallback: LocationCallback? = null
    private var fusedLocationClient: FusedLocationProviderClient? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        MapDatabase.databasePopulating(this, MapDatabase.getDatabaseInstance(this))

        setupMap()

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                raio = p1

                lastKnowLocation?.let { fillMapWithMarkers(mMap, LatLng(it.latitude, it.longitude), raio!!) }

            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

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
                        val currentLocationLatLng = LatLng(currentLocation!!.latitude, currentLocation.longitude)
                        moveCameraAndZoom(currentLocationLatLng, initialZoom)
                        fillMapWithMarkers(mMap, currentLocationLatLng, raio!!)
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

    private fun createMarker(latlng: LatLng, titulo: String, @DrawableRes icone: Int): MarkerOptions {

        fun Drawable.toBitmapDescriptor(): BitmapDescriptor {

            this.setBounds(0, 0, intrinsicWidth, intrinsicHeight)

            val bitmap = createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)

            Canvas(bitmap).apply { draw(this) }

            return BitmapDescriptorFactory.fromBitmap(bitmap)
        }

        return MarkerOptions()
            .position(latlng)
            .title(titulo)
            .icon(ContextCompat.getDrawable(this, icone)!!.toBitmapDescriptor())
    }

    private fun fillMapWithMarkers(googleMap: GoogleMap?, userLocation: LatLng, raio: Int) {
        val instanceDatabase = MapDatabase.getDatabaseInstance(this)

        googleMap?.clear()
        val markers = mutableListOf<MarkerOptions>()

        instanceDatabase.daoMap().listMaps().forEach { map ->

            val mapLatLng = map.getLocation()

            if (userLocation.distanceTo(mapLatLng) <= (raio*1000) ) {

                when {

                    map.radarType.equals("Radar Fixo") -> {

                        when {
                            map.speed.equals("30") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.fixo30
                                )
                            )
                            map.speed.equals("40") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.fixo40
                                )
                            )
                            map.speed.equals("50") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.fixo50
                                )
                            )
                            map.speed.equals("60") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.fixo60
                                )
                            )
                            map.speed.equals("70") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.fixo70
                                )
                            )
                            map.speed.equals("80") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.fixo80
                                )
                            )
                            map.speed.equals("90") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.fixo90
                                )
                            )
                            map.speed.equals("100") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.fixo100
                                )
                            )
                            map.speed.equals("110") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.fixo110
                                )
                            )
                            map.speed.equals("120") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.fixo120
                                )
                            )
                        }

                    }

                    map.radarType.equals("Radar Movel") -> {

                        when {
                            map.speed.equals("30") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.movel30
                                )
                            )
                            map.speed.equals("40") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.movel40
                                )
                            )
                            map.speed.equals("50") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.movel50
                                )
                            )
                            map.speed.equals("60") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.movel60
                                )
                            )
                            map.speed.equals("70") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.movel70
                                )
                            )
                            map.speed.equals("80") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.movel80
                                )
                            )
                            map.speed.equals("90") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.movel90
                                )
                            )
                            map.speed.equals("100") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.movel100
                                )
                            )
                            map.speed.equals("110") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.movel110
                                )
                            )
                            map.speed.equals("120") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.movel120
                                )
                            )
                        }

                    }

                    map.radarType.equals("Semaforo com Radar") -> {

                        when {
                            map.speed.equals("30") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.semaforo30
                                )
                            )
                            map.speed.equals("40") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.semaforo40
                                )
                            )
                            map.speed.equals("50") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.semaforo50
                                )
                            )
                            map.speed.equals("60") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.semaforo60
                                )
                            )
                            map.speed.equals("70") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.semaforo70
                                )
                            )
                            map.speed.equals("80") -> markers.add(
                                createMarker(
                                    mapLatLng,
                                    map.radarType!!,
                                    R.drawable.semaforo80
                                )
                            )
                        }

                    }

                    map.radarType.equals("Semaforo com Camera") -> {
                        markers.add(createMarker(mapLatLng, map.radarType!!, R.drawable.semaforo))
                    }

                    map.radarType.equals("Policia Rodoviaria") -> {
                        markers.add(createMarker(mapLatLng, map.radarType!!, R.drawable.prf))
                    }

                    map.radarType.equals("Pedagio") -> {
                        markers.add(createMarker(mapLatLng, map.radarType!!, R.drawable.pedagio))
                    }

                    map.radarType.equals("Lombada") -> {
                        markers.add(createMarker(mapLatLng, map.radarType!!, R.drawable.lombada))
                    }

                }

            }

        }

        markers.forEach { googleMap?.addMarker(it) }
    }

    private fun LatLng.distanceTo(locale: LatLng): Double {
        return SphericalUtil.computeDistanceBetween(
            this,
            locale
        )
    }

}
