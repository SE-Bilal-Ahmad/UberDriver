package com.example.uberdriver.presentation.driver.map.services

import android.content.Context
import android.graphics.Color
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.uber.data.remote.api.googleMaps.models.directionsResponse.Distance
import com.example.uber.data.remote.api.googleMaps.models.directionsResponse.Duration
import com.example.uberdriver.R
import com.example.uberdriver.core.common.BitmapCreator
import com.example.uberdriver.core.common.Helper
import com.example.uberdriver.core.common.PolyUtilExtension
import com.example.uberdriver.core.common.UiHelper
import com.example.uberdriver.data.remote.api.backend.socket.ride.model.TripLocation
import com.example.uberdriver.data.remote.api.backend.socket.trip.model.ReachedRider
import com.example.uberdriver.presentation.driver.map.viewmodel.DriverViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.GoogleViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.LocationViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.MapAndCardSharedViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.RideViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.TripViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class RouteNavigationService(
    private val googleMap: WeakReference<GoogleMap>,
    private val tripViewModel: TripViewModel,
    private val mapAndCardSharedViewModel: MapAndCardSharedViewModel,
    private val locationViewModel: LocationViewModel,
    private val googleViewModel: GoogleViewModel,
    private val driverViewModel: DriverViewModel,
    private val viewLifecycleOwner: LifecycleOwner,
    private val context: WeakReference<Context>,
    private val rideViewModel: RideViewModel
) {
    private var polylineOptions: PolylineOptions? = null
    private var routePoints: MutableList<LatLng>? = null
    private var polyline: Polyline? = null
    private var location: LatLng? = null
    private var duration: Duration? = null
    private var distance: Distance? = null
    private var riderMarker: Marker? = null
    private var dropOffMarker: Marker? = null

    fun createRoute(
        location: LatLng,
    ) {
        this.location = location
        locationViewModel.location.value?.let {
            tripViewModel.directionsRequest(location, it)
            observeDirectionsResponse()
            observeLocationChanges()
            observeDistanceMatrixResponse()
            registerDistanceHandler()
        }
        if (tripViewModel.tripStatus.first) {
            addRiderMarker()
        } else if (tripViewModel.tripStatus.second) {
            riderDropOffMarker()
        }
    }


    private fun removeTravelledPolyLine(loc: LatLng) {
        routePoints?.let {
            viewLifecycleOwner.lifecycleScope.launch {
                val (index, closestPoint) = PolyUtilExtension.getNearestPointOnRoute(
                    loc, it
                )
                val trimmedPoints = routePoints?.subList(0, index)
                trimmedPoints?.let { b ->
                    getDistanceFromCurrentPointToDestination(trimmedPoints)
                    polyline?.points = b
                }
                trimmedPoints?.let { b ->
                    if (b.size <= 2) {
                        driverReachedToLocation(loc)
                    }
                }

            }

        }
    }

    private fun observeDirectionsResponse() {
        tripViewModel?.run {
            viewLifecycleOwner.lifecycleScope.launch {
                directions.collectLatest {
                    if (it?.data!!.routes.isNotEmpty()) {
                        createRoute(
                            it.data!!.routes[0].overview_polyline!!.points,
                            it.data.routes[0].legs[0].duration,
                            it.data.routes[0].legs[0].distance
                        )
                    }
                }

            }
        }
    }

    private fun createRoute(
        line: String,
        duration: Duration,
        distance: com.example.uber.data.remote.api.googleMaps.models.directionsResponse.Distance
    ) {
        val routePoints: MutableList<LatLng> = PolyUtil.decode(line)
        if (routePoints.size > 1) {
            this.routePoints = routePoints
            polylineOptions = PolylineOptions()
                .width(15f)
                .color(Color.DKGRAY)

            polyline?.remove()
            polylineOptions?.let {
                it.addAll(routePoints)
                polyline = googleMap.get()?.addPolyline(it)
            }
            updateMapZoomLevel()
            UiHelper.animateCameraToFillRoute(routePoints, googleMap.get()!!)
            this.distance = distance
            this.duration = duration
        }
    }

    private fun updateMapZoomLevel() {
        viewLifecycleOwner.lifecycleScope.launch {
            googleViewModel.setCameraZoomLevel(17.0f)
            withContext(Dispatchers.Main) {
                googleMap?.get()?.moveCamera(CameraUpdateFactory.zoomTo(17.0f))
            }

        }
    }

    private fun observeLocationChanges() {
        viewLifecycleOwner.lifecycleScope.launch {
            locationViewModel.location.collectLatest {
                it?.let { a ->
                    checkIfDriverLocationOnRoute(a)
                    removeTravelledPolyLine(a)
                    sendTripLocationUpdates(a)
                }
            }
        }
    }

    private fun sendTripLocationUpdates(value: LatLng) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (distance != null && duration != null) {
                tripViewModel.ride.value?.let { ri ->
                    tripViewModel.sendTripLocation(
                        TripLocation(
                            ri.rideId,
                            driverViewModel.driverId!!,
                            ri.riderId,
                            value.latitude,
                            value.longitude,
                            distance!!.value,
                            duration!!.value
                        )
                    )
                }
            }
        }
    }

    private fun checkIfDriverLocationOnRoute(trip: LatLng) {

        if (routePoints != null && !PolyUtil.isLocationOnPath(
                trip,
                routePoints,
                true,
                50.0
            )
        ) {
            tripViewModel.directionsRequest(
                location!!,
                trip
            )
        }
    }

    private fun driverReachedToLocation(value: LatLng) {
        routePoints?.let {
            checkIfDriverInRiderPickupPoint(value)
            checkIfDriverInRiderDropOffPoint(value)
        }
    }

    private fun checkIfDriverInRiderPickupPoint(
        driverLocation: LatLng
    ) {
        val results = FloatArray(1)
        location?.let {
            var distance = Location.distanceBetween(
                driverLocation.latitude,
                driverLocation.longitude,
                it.latitude,
                it.longitude,
                results
            )

            if (results[0] <= 50) {
                driverViewModel?.driverId?.let { a ->
                    cleanMap()
                    tripViewModel.ride.value?.let { trip ->
                        tripViewModel.reachedRiderPickUpSpot(
                            ReachedRider(
                                trip.riderId,
                                a,
                                trip.rideId,
                                true,
                            )
                        )
                        viewLifecycleOwner.lifecycleScope.launch {
                            tripViewModel.setPickUpLocationReached(true)
                        }
                    }
                }
            }
        }
    }

    private fun checkIfDriverInRiderDropOffPoint(
        driverLocation: LatLng
    ) {
        val results = FloatArray(1)
        location?.let {
            var distance = Location.distanceBetween(
                driverLocation.latitude,
                driverLocation.longitude,
                it.latitude,
                it.longitude,
                results
            )
            if (results[0] <= 50) {
                driverViewModel?.driverId?.let { a ->
                    cleanMap()
                    tripViewModel.ride.value?.let { trip ->
                        tripViewModel.reachedDropOffSpot(
                            ReachedRider(
                                trip.riderId,
                                a,
                                trip.rideId,
                                true,
                            )
                        )
                        viewLifecycleOwner.lifecycleScope.launch {
                            mapAndCardSharedViewModel.setDropOffLocationReached(true)
                        }
                    }
                }
            }
        }

    }

    private fun getDistanceMatrix(destination: LatLng, origin: LatLng) {
        viewLifecycleOwner.lifecycleScope.launch {
            googleViewModel.getDistanceMatrix(destination, origin)
        }
    }

    private fun observeDistanceMatrixResponse() {
        viewLifecycleOwner.lifecycleScope.launch {
            googleViewModel.distanceMatrix.collectLatest { res ->
                res?.data?.let {
                    if (it.rows.isNotEmpty()) {
                        distance = it?.rows?.get(0)?.elements?.get(0)?.distance
                        duration = it.rows?.get(0)?.elements?.get(0)?.duration
                    }
                }

            }
        }
    }


    fun cleanMap() {
        polyline?.remove()
        routePoints?.clear()
        handler?.removeCallbacks(runnable)
        handler = null
        routePoints = null
        distance = null
        duration = null
    }

    var handler: Handler? = Handler(Looper.getMainLooper())
    val runnable = object : Runnable {
        override fun run() {
            getDistanceMatrix(locationViewModel.location.value!!, location!!)
        }
    }

    private fun registerDistanceHandler() {
        handler?.postDelayed(runnable, 10000)
    }

    private fun addRiderMarker() {
        rideViewModel.rideRequests.value?.let {
            riderMarker = googleMap.get()?.addMarker(
                MarkerOptions().position(LatLng(it.pickupLatitude, it.pickupLongitude)).icon(
                    BitmapCreator.bitmapDescriptorFromVector(
                        R.drawable.baseline_account_circle_24,
                        context.get()!!,
                        0.50,
                        0.50
                    )
                )
            )
        }
    }

    private fun riderDropOffMarker() {
        rideViewModel.rideRequests.value?.let {
            if (tripViewModel.tripStatus.second) {
                dropOffMarker = googleMap.get()?.addMarker(
                    MarkerOptions().position(LatLng(it.dropOffLatitude, it.dropOffLongitude)).icon(
                        BitmapCreator.bitmapDescriptorFromVector(
                            R.drawable.baseline_stop_circle_24,
                            context.get()!!,
                        )
                    )
                )
                riderMarker?.remove()
            }
        }
    }

    private fun getDistanceFromCurrentPointToDestination(
        routePoints: MutableList<LatLng>
    ) {
        val distance = Helper.calculatePolylineDistance(routePoints)
        val time = Helper.getUserFetchTime(distance)
        viewLifecycleOwner.lifecycleScope.launch {
            tripViewModel.updateTripTimeAndDistance(time, distance)
        }
    }


}