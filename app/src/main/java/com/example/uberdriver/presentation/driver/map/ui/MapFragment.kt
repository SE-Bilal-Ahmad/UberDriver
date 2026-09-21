package com.example.uberdriver.presentation.driver.map.ui

import android.Manifest
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.uberdriver.R
import com.example.uberdriver.core.common.BitmapCreator
import com.example.uberdriver.core.common.ButtonAnimator
import com.example.uberdriver.core.common.Constants_Api
import com.example.uberdriver.core.common.FetchLocation
import com.example.uberdriver.core.common.HRMarkerAnimation
import com.example.uberdriver.core.common.Helper
import com.example.uberdriver.core.common.PermissionManagers
import com.example.uberdriver.core.services.LocationTrackerService
import com.example.uberdriver.databinding.FragmentMapBinding
import com.example.uberdriver.domain.remote.socket.location.model.UpdateLocation
import com.example.uberdriver.presentation.auth.register.viewmodels.VehicleViewModel
import com.example.uberdriver.presentation.driver.map.services.AppLaunchService
import com.example.uberdriver.presentation.driver.map.services.RouteNavigationService
import com.example.uberdriver.presentation.driver.map.utilities.RouteCreationHelper
import com.example.uberdriver.presentation.driver.map.viewmodel.DriverViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.GoogleViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.LocationViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.MapAndCardSharedViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.RideViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.SocketViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.TripViewModel
import com.example.uberdriver.presentation.splash.viewmodel.DriverRoomViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference


@AndroidEntryPoint
class MapFragment : Fragment(), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private var driverMarker: Marker? = null
    private var oldLocation: Location? = null
    private var mLastLocation: Location? = null
    private var binding: FragmentMapBinding? = null
    private val socketViewModel: SocketViewModel by viewModels<SocketViewModel>()
    private val rideViewModel: RideViewModel by activityViewModels<RideViewModel>()
    private val locationViewModel: LocationViewModel by activityViewModels<LocationViewModel>()
    private val driverRoomViewModel: DriverRoomViewModel by activityViewModels<DriverRoomViewModel>()
    private val driverViewModel: DriverViewModel by activityViewModels<DriverViewModel>()
    private val vehicleViewModel: VehicleViewModel by activityViewModels<VehicleViewModel>()
    private val googleViewModel: GoogleViewModel by viewModels<GoogleViewModel>()
    private val mapAndCardSharedViewModel: MapAndCardSharedViewModel by activityViewModels<MapAndCardSharedViewModel>()
    private val tripViewModel: TripViewModel by activityViewModels<TripViewModel>()
    private var isGoButtonClicked: Boolean = false
    private var rideRequestCardService: RideRequestCardService? = null
    private var routeCreationHelper: RouteCreationHelper? = null
    private var routeNavigationService: RouteNavigationService? = null
    private var locationManager: com.example.uberdriver.core.services.LocationManager? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestLocationPermission()
        initialCalls()
        setUpGoogleMap()
        startRippleAnimation()
        onGoButtonClickListener()
        connectToSocket()
        fetchContinuousLocation()
        initializeCardService()
        addObservers()
        navigateBtnClickListener()
        locationManager = com.example.uberdriver.core.services.LocationManager(requireContext())
    }

    private fun addObservers() {
        observeRideRequests()
        observeAcceptRideBtnClicked()
        observeRideBtnClicked()
        observePickUpLocationReached()
        observeStartRideBtnClicked()
        observeRideStarted()
        observeNavigateButtonVisibilityStatus()
    }

    private fun initializeCardService() {
        rideRequestCardService = RideRequestCardService(
            locationViewModel,
            WeakReference(binding),
            this,
            rideViewModel,
            tripViewModel,
            requireContext(),
            mapAndCardSharedViewModel,
            driverViewModel
        )
    }

    private fun initialCalls() {
        viewLifecycleOwner.lifecycleScope.launch {
            val job = driverRoomViewModel.getDriver()
            job.join()
            getVehicleDetails()
        }
    }

    private fun getVehicleDetails() {
        vehicleViewModel.getVehicleDetails(driverRoomViewModel.driver.value!!.driverId)
    }

    private fun getCurrentMapStyle(): Int =
        if (Helper.isDarkMode(requireContext())) R.raw.night_map else R.raw.day_map

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
        googleMap.setMapStyle(
            MapStyleOptions.loadRawResourceStyle(
                requireContext(),
                getCurrentMapStyle()
            )
        )
        updateDriverMarker()
        initializeRouteCreationHelper()
        initializeRouteNavigationService(googleMap)
        fetchCurrentLocation()
    }

    private fun initializeRouteNavigationService(googleMap: GoogleMap) {
        routeNavigationService = RouteNavigationService(
            WeakReference(googleMap),
            tripViewModel,
            mapAndCardSharedViewModel,
            locationViewModel,
            googleViewModel,
            driverViewModel,
            this,
            WeakReference(context),
            rideViewModel
        )


    }

    private fun fetchCurrentLocation() {
        checkLocationPermission {
            FetchLocation.getCurrentLocation(requireContext()) { location ->
                animateCameraToCurrentLocation(location)
            }
        }
    }

    private fun requestLocationPermission() {
        checkLocationPermission {
            fetchContinuousLocation()
        }
    }

    private fun checkLocationPermission(onGranted: () -> Unit) {
        PermissionManagers.requestPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) {
            if (it) {
                onGranted.invoke()
            }
        }
    }

    private fun setUpGoogleMap() {
        val mapFragment =
            childFragmentManager.findFragmentById(R.id.google_map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun fetchContinuousLocation() {
        checkLocationPermission {
            viewLifecycleOwner.lifecycleScope.launch {
                FetchLocation.getLocationUpdates(requireContext()).collect {
                    locationViewModel.setDriverLocation(LatLng(it.latitude, it.longitude))
                    if(mapAndCardSharedViewModel.showingRideRequestCard.value)
                        updateLocation(it,false)
                    else
                        updateLocation(it)
                    driverRoomViewModel.driver.value.let { dri ->
                        with(socketViewModel) {
                            if (socketConnected.value && isGoButtonClicked) {
                                locationViewModel.sendDriverLocation(
                                    UpdateLocation(
                                        dri?.driverId!!,
                                        it.longitude,
                                        it.latitude,
                                        vehicleViewModel.vehicleDetails.value!!.data!!.type
                                    )
                                )
                            }
                        }

                    }
                }
            }
        }
    }


    private fun animateCameraToCurrentLocation(lastKnownLocation: Location?) {
        if (googleMap != null) {
            val userLatLng = lastKnownLocation?.let { LatLng(it.latitude, it.longitude) }
            viewLifecycleOwner.lifecycleScope.launch {
                googleViewModel.cameraZoomLevel.value?.let {
                    googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng!!, it))
                }
            }
        }
    }

    private fun animateMarker(value:Boolean = true) {
        HRMarkerAnimation(
            googleMap, 1000
        ) { updatedLocation -> oldLocation = updatedLocation }.animateMarker(
            mLastLocation,
            oldLocation,
            driverMarker,
            value
        )
    }

    private fun updateLocation(newLocation: Location?, animateCameraPos:Boolean = true) {
        if (newLocation != null) {
            mLastLocation = newLocation
            animateMarker(animateCameraPos)
        }
    }

    private fun updateDriverMarker() {

        if (driverMarker == null) {
            driverMarker = googleMap?.addMarker(
                MarkerOptions().position(LatLng(33.591293, 73.122300))
                    .icon(bitmapDescriptorFromVector(R.drawable.driver_arrow1))
            )
        }
    }

    private fun bitmapDescriptorFromVector(vectorResId: Int): BitmapDescriptor? {
        return BitmapCreator.bitmapDescriptorFromVector(vectorResId, requireContext())
    }

    private fun startRippleAnimation() {
        ButtonAnimator.startRippleAnimation(binding?.rippleView!!)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        googleMap = null
        oldLocation = null
        driverMarker = null
        mLastLocation = null
        rideRequestCardService = null
        stopLocationService()
    }

    private fun onGoButtonClickListener() {
        binding?.goButton?.setOnClickListener {
            startLocationService()
            isGoButtonClicked = true
            binding?.frameLayout?.visibility = View.GONE
            viewLifecycleOwner.lifecycleScope.launch {
                mapAndCardSharedViewModel.setGoBtnClicked(true)
            }

        }
    }

    private fun startLocationService() {
        Intent(
            requireContext(), LocationTrackerService::class.java
        ).also {
            it.action = LocationTrackerService.Action.START.name
            requireContext().startService(it)
        }
    }

    private fun stopLocationService() {
        Intent(
            requireContext(), LocationTrackerService::class.java
        ).also {
            it.action = LocationTrackerService.Action.STOP.name
            requireContext().startService(it)
        }
    }


    private fun connectToSocket() {
        socketViewModel.connectSocket(Constants_Api.LOCATION_SOCKET_API + "?riderId=${driverViewModel.driverId}")
        socketViewModel.observeConnectedToSocket()
    }

    private fun observeRideRequests() {
        rideViewModel.apply {
            viewLifecycleOwner.lifecycleScope.launch {
                rideRequests.collectLatest { it ->
                    it?.let {
                        rideViewModel.setRideOnScreenStatus(true)
                        rideRequestCardService?.showCard(it)
                        locationViewModel.location.value?.let { loc ->
                            routeCreationHelper?.createRoute(
                                LatLng(loc.latitude, loc.longitude),
                                LatLng(it.pickupLatitude, it.pickupLongitude),
                                LatLng(it.dropOffLatitude, it.dropOffLongitude)
                            )
                            hideCardAndRoute()
                        }
                    }
                }
            }
        }
    }


    private fun initializeRouteCreationHelper() {
        routeCreationHelper = RouteCreationHelper(
            googleViewModel,
            this,
            WeakReference(googleMap),
            WeakReference(requireContext())
        )
    }

    private var job: Job? = null
    private fun hideCardAndRoute() {
        job = viewLifecycleOwner.lifecycleScope.launch {
            delay(10000)
            mapAndCardSharedViewModel.setShowingRideRequests(false)
            rideRequestCardService?.hideCard()
            routeCreationHelper?.deleteEverythingOnMap()
            rideViewModel.rideRequests.emit(null)
            rideViewModel.setRideOnScreenStatus(false)
        }
    }

    private fun observeAcceptRideBtnClicked() {
        viewLifecycleOwner.lifecycleScope.launch {
            mapAndCardSharedViewModel.acceptRideBtnClick.collectLatest {
                it?.let { t ->
                    if (t) {
                        hideCardAndOtherStuff()
                        rideViewModel.setRideAccepted(true)
                        rideViewModel.rideRequests.value?.let { a ->
                            routeNavigationService?.createRoute(
                                LatLng(
                                    a.pickupLatitude,
                                    a.pickupLongitude
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private var tripJob: Job? = null


    private fun cancelTripUpdates() {
        tripJob?.cancel(null)
    }

    private fun hideCardAndOtherStuff() {
        binding?.goButton?.visibility = View.GONE
        routeCreationHelper?.deleteEverythingOnMap()
        rideRequestCardService?.hideCard()
        job?.cancel(null)
        viewLifecycleOwner.lifecycleScope.launch {
            mapAndCardSharedViewModel.setShowingRideRequests(false)
        }
    }


    private fun observeRideBtnClicked() {
        viewLifecycleOwner.lifecycleScope.launch {
            mapAndCardSharedViewModel.apply {
                startRideClick.collectLatest {
                    if (it) {
                        rideViewModel.rideRequests.value?.let { a ->
                            routeNavigationService?.createRoute(
                                LatLng(
                                    a.dropOffLatitude,
                                    a.dropOffLongitude
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun observePickUpLocationReached() {
        viewLifecycleOwner.lifecycleScope.launch {
            tripViewModel.apply {
                reachedPickUpLocation.collectLatest {
                    if (it) {
                        binding?.bottomSheet?.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun observeStartRideBtnClicked() {
        viewLifecycleOwner.lifecycleScope.launch {
            mapAndCardSharedViewModel.apply {
                startUberBtnClicked.collectLatest {
                    rideViewModel.rideRequests.value?.let { a ->
                        routeNavigationService?.cleanMap()
                        routeNavigationService?.createRoute(
                            LatLng(
                                a.dropOffLatitude,
                                a.dropOffLongitude
                            )
                        )
                    }
                }
            }
        }
    }

    private fun navigateBtnClickListener() {
        binding?.navigate?.setOnClickListener {
            rideViewModel.rideRequests.value?.let { a ->
                if (tripViewModel.tripStatus.first) {
                    launchGoogleMap(LatLng(a.pickupLatitude, a.pickupLongitude))
                } else if (tripViewModel.tripStatus.second) {
                    launchGoogleMap(LatLng(a.dropOffLatitude, a.dropOffLongitude))
                }
            }
        }
    }

    private fun launchGoogleMap(destination: LatLng) {
        val gmmIntentUri =
            Uri.parse("google.navigation:q=${destination.latitude},${destination.longitude}" + "&mode=d")
        val intent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        startActivity(intent)
    }

    private fun observeRideStarted() {
        viewLifecycleOwner.lifecycleScope.launch {
            rideViewModel.rideStarted.collectLatest { t ->
                toggleNavigateButton(t)
            }
        }
    }

    private fun toggleNavigateButton(value: Boolean) {
        binding?.navigate?.visibility = if (value) View.VISIBLE else View.GONE
    }

    private fun observeNavigateButtonVisibilityStatus(){
        viewLifecycleOwner.lifecycleScope.launch {
            mapAndCardSharedViewModel.apply {
                hideNavigateButton.collectLatest {
                    if(rideViewModel.rideStarted.value){
                        toggleNavigateButton(it)
                    }
                }
            }
        }
    }



}