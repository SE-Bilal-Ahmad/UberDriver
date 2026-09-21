package com.example.uberdriver.presentation.driver.map.viewmodel

import com.example.uber.data.remote.api.googleMaps.models.directionsResponse.DirectionsResponse
import com.example.uberdriver.core.common.BaseViewModel
import com.example.uberdriver.core.common.Resource
import com.example.uberdriver.core.dispatcher.IDispatchers
import com.example.uberdriver.data.remote.api.backend.socket.ride.model.TripLocation
import com.example.uberdriver.data.remote.api.backend.socket.trip.model.ReachedRider
import com.example.uberdriver.data.remote.api.backend.socket.trip.model.TripStarted
import com.example.uberdriver.domain.remote.google.usecase.GetDirectionsResponseNoWaypoints
import com.example.uberdriver.domain.remote.socket.ride.model.AcceptRideRequest
import com.example.uberdriver.domain.remote.socket.ride.usecase.TripLocationUseCase
import com.example.uberdriver.domain.remote.socket.trip.usecase.ReachedDropOffPositionUseCase
import com.example.uberdriver.domain.remote.socket.trip.usecase.ReachedRiderUseCase
import com.example.uberdriver.domain.remote.socket.trip.usecase.StartTripUseCase
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class TripViewModel @Inject constructor(
    dispatcher: IDispatchers,
    private val directionsResponseNoWaypoints: GetDirectionsResponseNoWaypoints,
    private val reachedRiderUseCase: ReachedRiderUseCase,
    private val tripLocationUseCase: TripLocationUseCase,
    private val reachedDropOffPositionUseCase: ReachedDropOffPositionUseCase,
    private val startTripUseCase: StartTripUseCase
) : BaseViewModel(dispatcher) {
    private val _directions = MutableSharedFlow<Resource<DirectionsResponse>?>()
    val directions get() = _directions.asSharedFlow()

    private val _ride = MutableStateFlow<AcceptRideRequest?>(null)
    val ride get() = _ride.asStateFlow()

    private var _tripStatus: Pair<Boolean, Boolean> = Pair(false, false)
    val tripStatus get() = _tripStatus

    private var _timeAndDistance = MutableStateFlow<Pair<Int, Double>>(Pair(0, 0.0))
    val timeAndDistance get() = _timeAndDistance.asStateFlow()

    private val _dropOffDestinationTripStarted = MutableStateFlow<Boolean>(false)
    val dropOffDestinationTripStarted get() = _dropOffDestinationTripStarted.asStateFlow()

    private val _reachedPickUpLocation = MutableStateFlow<Boolean>(false)
    val reachedPickUpLocation get() = _reachedPickUpLocation.asStateFlow()

    private fun <T> handleResponse(response: Response<T>): Resource<T>? {
        if (response.isSuccessful) {
            return response.body()?.let {
                Resource.Success(it)
            }
        }

        return Resource.Error("Error ${response.code()}: ${response.message()}")
    }

    fun directionsRequest(origin: LatLng, destination: LatLng) {
        launchOnBack {
            var response = directionsResponseNoWaypoints(origin, destination)
            var response1 = handleResponse<DirectionsResponse>(response)
            _directions.emit(response1)
        }
    }

    fun reachedRiderPickUpSpot(value: ReachedRider) {
        launchOnBack {
            reachedRiderUseCase(value)
        }
    }

    fun reachedDropOffSpot(value: ReachedRider) {
        launchOnBack {
            reachedDropOffPositionUseCase(value)
        }
    }

    suspend fun setDropOffDestinationTripStarted(value: Boolean) {
        _dropOffDestinationTripStarted.emit(value)
    }

    fun sendTripLocation(trip: TripLocation) {
        launchOnBack {
            tripLocationUseCase(trip)
        }
    }

    suspend fun setRide(value: AcceptRideRequest) {
        _ride.emit(value)
    }

    suspend fun setTripStatus(tripStatus: Pair<Boolean, Boolean>) {
        this._tripStatus = tripStatus
    }

    suspend fun updateTripTimeAndDistance(time: Int, distance: Double) {
        _timeAndDistance.emit(Pair(time, distance))
    }

    suspend fun setPickUpLocationReached(value: Boolean) {
        _reachedPickUpLocation.emit(value)
    }
    suspend fun startTrip(value: TripStarted){
       startTripUseCase(value)
    }

}