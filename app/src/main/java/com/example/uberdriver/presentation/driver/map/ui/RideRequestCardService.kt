package com.example.uberdriver.presentation.driver.map.ui

import android.content.Context
import android.location.Location
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.uberdriver.R
import com.example.uberdriver.core.common.FetchLocation
import com.example.uberdriver.core.common.Helper
import com.example.uberdriver.core.common.SoundHelper
import com.example.uberdriver.data.remote.api.backend.socket.ride.model.NearbyRideRequests
import com.example.uberdriver.databinding.FragmentMapBinding
import com.example.uberdriver.domain.remote.socket.ride.model.AcceptRideRequest
import com.example.uberdriver.presentation.driver.map.viewmodel.DriverViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.LocationViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.MapAndCardSharedViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.RideViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.TripViewModel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Locale

class RideRequestCardService(
    private val locationViewModel: LocationViewModel,
    private val binding: WeakReference<FragmentMapBinding>,
    private val viewLifecycleOwner: LifecycleOwner,
    private val rideViewModel: RideViewModel,
    private val tripViewModel: TripViewModel,
    private val context: Context,
    private val mapAndCardSharedModel: MapAndCardSharedViewModel,
    private val driverViewModel: DriverViewModel
) {


    init {
        startObservingNearbyRideRequests()
        setRideAcceptListener()
    }

    private fun startObservingNearbyRideRequests() {
        rideViewModel.startObservingNearbyRideRequests()
        rideViewModel.observeRideRequests()
    }


    suspend fun showCard(it: NearbyRideRequests) {
        if (it != null) {
            mapAndCardSharedModel.setShowingRideRequests(true);
            binding.get()?.cardView?.visibility = View.VISIBLE
            binding.get()?.pickupLocationName?.text =
                getLocationName(it.pickupLatitude, it.pickupLongitude)
            val pickUpDistance = getUserDistance(it.pickupLatitude, it.pickupLongitude)
            binding.get()?.pickupTime?.text =
                Helper.getUserFetchTime(pickUpDistance.toDouble()).toString() + " mins "
            binding.get()?.pickpDistance?.text = "(${pickUpDistance} mil) away"
            val dropOffDistance =
                getUserDistance(it.dropOffLatitude, it.dropOffLongitude)
            binding.get()?.dropOffLocationName?.text =
                getLocationName(it.dropOffLatitude, it.dropOffLongitude)
            binding.get()?.dropOffTime?.text =
                Helper.getUserFetchTime(dropOffDistance.toDouble())
                    .toString() + " mins "
            binding.get()?.dropOffDistance?.text = "(${dropOffDistance} mil) trip"
            SoundHelper.startSound(context, R.raw.uberx_request_tone)
            binding.get()?.content?.startRippleAnimation()
        }
    }

    private suspend fun getLocationName(latitude: Double, longitude: Double): String {
        return FetchLocation.getLocation(
            latitude = latitude,
            longitude = longitude, context
        )
    }


    private fun getUserDistance(lat: Double, lng: Double): String {
        return String.format(Locale.US, "%.1f", FetchLocation.getDistance(Location("").apply {
            latitude = lat
            longitude = lng
        }, Location("").apply {
            locationViewModel.location.value?.let {a->
                latitude = a.latitude
                longitude = a.longitude
            }

        }))
    }



    fun hideCard() {
        binding.get()?.cardView?.visibility = View.GONE
        SoundHelper.destroySoundInstance()
        binding.get()?.content?.stopRippleAnimation()
    }

    private fun setRideAcceptListener() {
        binding.get()?.acceptRide?.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                tripViewModel.setTripStatus(Pair(true,false))
                mapAndCardSharedModel.setRideBtnClicked(true)
                acceptRide()
            }
        }
    }


    private suspend fun acceptRide() {
        rideViewModel.rideRequests.value?.let { ride ->
            locationViewModel.location.value?.let { loc ->
                rideViewModel.rideStarted(true)
                rideViewModel.setRideAccepted(true)
                rideViewModel.acceptRideRequest(
                    AcceptRideRequest(
                        ride.rideId,
                        driverViewModel.driverId!!,
                        loc.latitude,
                        loc.longitude,
                        ride.riderId
                    )
                )
                tripViewModel.setRide(
                    AcceptRideRequest(
                        ride.rideId,
                        driverViewModel.driverId!!,
                        loc.latitude,
                        loc.longitude,
                        ride.riderId
                    )
                )
            }
        }
    }

}