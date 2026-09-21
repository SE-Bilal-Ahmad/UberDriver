package com.example.uberdriver.presentation.bottomsheet

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.uber.data.remote.api.googleMaps.models.directionsResponse.DirectionsResponse
import com.example.uberdriver.R
import com.example.uberdriver.core.common.ButtonAnimator
import com.example.uberdriver.core.common.Helper
import com.example.uberdriver.data.remote.api.backend.rider.model.RiderDetails
import com.example.uberdriver.data.remote.api.backend.socket.trip.model.TripStarted
import com.example.uberdriver.databinding.FragmentRiderNotifiedSheetBinding
import com.example.uberdriver.presentation.driver.map.viewmodel.MapAndCardSharedViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.RideViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.RiderViewModel
import com.example.uberdriver.presentation.driver.map.viewmodel.TripViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.ncorti.slidetoact.SlideToActView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class RiderNotifiedSheet : Fragment(R.layout.fragment_rider_notified_sheet) {

    private var bottomSheet: LinearLayout? = null
    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    private var binding: FragmentRiderNotifiedSheetBinding? = null
    private val mapAndCardSharedViewModel: MapAndCardSharedViewModel by activityViewModels<MapAndCardSharedViewModel>()
    private val tripViewModel: TripViewModel by activityViewModels<TripViewModel>()
    private val riderViewModel: RiderViewModel by viewModels<RiderViewModel>()
    private val rideViewModel: RideViewModel by activityViewModels<RideViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRiderNotifiedSheetBinding.inflate(inflater, container, false)
        return binding?.root
    }

    private fun animateLineAndChangeText() {
        viewLifecycleOwner.lifecycleScope.launch {
            mapAndCardSharedViewModel.goBtnClicked.collectLatest {
                if (it) {
                    binding?.tvOffline?.text = "Going Online"
                    ButtonAnimator.startHorizontalAnimation(
                        binding!!.linearLine,
                        requireContext()
                    )
                    hideLineView()
                }
            }
        }
    }

    private fun hideLineView() {
        lifecycleScope.launch {
            delay(2000)
            withContext(Dispatchers.Main) {
                binding?.tvOffline?.text = "You are Online"
                ButtonAnimator.stopAnimation()
                binding?.linearLine?.visibility = View.GONE
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setBottomSheetStyle()
        observeStartRideBtnClickListener()
        animateLineAndChangeText()
        driverReachedPickUpSpot()
        observeRideAcceptedBtnClicked()
        observeRideRequests()
        observeRideRequestAccepted()
        observeRideDetails()
        observeUpdatedTripDistanceAndTime()
        observeDropOffDestinationStarted()
    }

    private fun setBottomSheetStyle() {
        bottomSheet = requireActivity().findViewById<LinearLayout>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet!!)

        bottomSheet?.layoutParams?.height =
            (requireContext().resources.displayMetrics.heightPixels * 0.35).toInt()
        bottomSheetBehavior?.peekHeight =
            (requireContext().resources.displayMetrics.heightPixels * 0.10).toInt()
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior?.isHideable = false
        bottomSheetBehavior?.isDraggable = false
        registerBottomSheetBehaviourCallback()
    }

    private fun observeStartRideBtnClickListener() {
        binding?.startRide?.onSlideCompleteListener =
            object : SlideToActView.OnSlideCompleteListener {
                override fun onSlideComplete(view: SlideToActView) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        tripViewModel.setTripStatus(Pair(false, true))
                        mapAndCardSharedViewModel.setStartRideBtnClicked(true)
                        tripViewModel.setDropOffDestinationTripStarted(true)
                        sendTripStartedStatus()
                    }
                }
            }
    }

    private fun sendTripStartedStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            tripViewModel.ride.value?.let {
                tripViewModel.startTrip(TripStarted(it.rideId, it.riderId, it.driverId))
            }
        }
    }


    private fun observeRideAcceptedBtnClicked() {
        viewLifecycleOwner.lifecycleScope.launch {
            mapAndCardSharedViewModel.acceptRideBtnClick.collectLatest {
                updateSheetContent()
                updateSheetBehaviour()
            }
        }
    }

    private fun updateSheetContent() {
        binding?.mcSheet?.visibility = View.GONE
        binding?.llRiderNotified?.visibility = View.VISIBLE
    }

    private fun updateSheetBehaviour() {
        bottomSheetBehavior?.isDraggable = true
    }


    private fun driverReachedPickUpSpot() {
        viewLifecycleOwner.lifecycleScope.launch {
            tripViewModel.reachedPickUpLocation.collectLatest {
                if (it) {
                    bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
                    bottomSheetBehavior?.isDraggable = true
                    startCountDownTimer()
                    toggleRiderNotifiedTime(true)
                    toggleStartUberBtnVisibility(true)
                    toggleRiderStatus(false)
                }
            }
        }
    }

    private fun getRiderDetails() {
        viewLifecycleOwner.lifecycleScope.launch {
            tripViewModel.ride.collectLatest {
                if (it != null)
                    riderViewModel.getRiderDetails(it.riderId)
            }
        }
    }

    private fun observeRideDetails() {
        viewLifecycleOwner.lifecycleScope.launch {
            riderViewModel.apply {
                riderDetailsState.collectLatest {
                    if (it != null) {
                        it.data?.let { a ->
                            updateSheetWithRiderName(a)
                        }
                    }
                }
            }
        }
    }


    private fun observeRideRequests() {
        viewLifecycleOwner.lifecycleScope.launch {
            rideViewModel.apply {
                rideRequestOnScreen.collectLatest { a ->
                    if (a) {
                        hideSheetWhenRideRequestAppears()
                    } else {
                        showSheetWhenRideRequestDisappears()
                    }
                }
            }
        }
    }

    private fun hideSheetWhenRideRequestAppears() {
        bottomSheetBehavior?.isHideable = true
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior?.isDraggable = true
    }

    private fun showSheetWhenRideRequestDisappears() {
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior?.isHideable = false
        bottomSheetBehavior?.isDraggable = false
    }

    private fun showSheetInCollapsed() {
        bottomSheetBehavior?.isHideable = false
        bottomSheetBehavior?.isDraggable = true
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun observeRideRequestAccepted() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(rideViewModel.rideAccepted, tripViewModel.directions) { accepted, directions ->
                accepted to directions
            }.collect { (accepted, directions) ->
                if (accepted && directions != null) {
                    showSheetWhenRideRequestDisappears()
                    hideSheetContent()
                    getRiderDetails()
                    directions.data?.let { a ->
                        updateTimeAndDistance(a)
                    }
                }
            }
        }
    }


    private fun updateSheetWithRiderName(resource: RiderDetails) {
        binding?.tvRiderName?.text = resource.firstName
    }

    private fun updateTimeAndDistance(directions: DirectionsResponse) {
        val miles: Double = Helper.covertMetersToMiles(directions.routes[0].legs[0].distance.value)
        val time: Int = Helper.convertSecondsToMinutes(directions.routes[0].legs[0].duration.value)
        updateTimeAndDistanceText(time, miles)
    }

    private fun observeUpdatedTripDistanceAndTime() {
        viewLifecycleOwner.lifecycleScope.launch {
            tripViewModel.timeAndDistance.collectLatest {
                updateTimeAndDistanceText(it.first, it.second)
            }
        }
    }

    private fun updateTimeAndDistanceText(time: Int, distance: Double) {
        binding?.tvDistanceToReach?.text = distance.toString() + " mi"
        binding?.tvTimeToReach?.text = time.toString() + " min"
    }

    private fun hideSheetContent() {
        binding?.llCurrentRiderStatus?.visibility = View.VISIBLE
        binding?.mcSheet?.visibility = View.GONE
        showSheetInCollapsed()
    }

    private fun registerBottomSheetBehaviourCallback() {
        bottomSheetBehavior?.addBottomSheetCallback(bottomSheetBehaviourCallback)
    }

    private val bottomSheetBehaviourCallback =
        object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {

            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (slideOffset >= 0.2f) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        mapAndCardSharedViewModel.setNavigateButtonStatus(false)
                    }
                } else if (slideOffset <= 0.1f) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        mapAndCardSharedViewModel.setNavigateButtonStatus(true)
                    }
                }
            }

        }

    private fun startCountDownTimer() {
        object : CountDownTimer(5 * 60 * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                binding?.tvRiderNotifiedTime?.text = "$minutes : $seconds"
            }

            override fun onFinish() {
            }

        }.start()
    }

    private fun toggleRiderNotifiedTime(value: Boolean) {
        binding?.llTimeForRider?.visibility = if (value) View.VISIBLE else View.GONE
    }

    private fun observeDropOffDestinationStarted() {
        viewLifecycleOwner.lifecycleScope.launch {
            tripViewModel.dropOffDestinationTripStarted.collectLatest {
                if (it) {
                    toggleRiderNotifiedTime(false)
                    toggleStartUberBtnVisibility(false)
                }
            }
        }
    }

    private fun toggleStartUberBtnVisibility(value: Boolean) {
        binding?.startRide?.visibility = if (value) View.VISIBLE else View.GONE
    }

    private fun toggleRiderStatus(value: Boolean) {
        binding?.llCurrentRiderStatus?.visibility = if (value) View.VISIBLE else View.GONE
    }

}