package com.example.uberdriver.domain.remote.socket.trip.repository

import com.example.uberdriver.core.common.SocketMethods
import com.example.uberdriver.data.remote.api.backend.socket.ride.model.TripLocation
import com.example.uberdriver.data.remote.api.backend.socket.socketBroker.service.SocketBroker
import com.example.uberdriver.data.remote.api.backend.socket.trip.model.ReachedRider
import com.example.uberdriver.data.remote.api.backend.socket.trip.model.TripStarted
import com.example.uberdriver.data.remote.api.backend.socket.trip.repository.TripRepository
import javax.inject.Inject

class TripRepositoryImpl @Inject constructor(private val broker: SocketBroker):TripRepository {
    override fun reachedRider(value: ReachedRider) = broker.send<ReachedRider>(value,SocketMethods.REACHED_RIDER)
    override fun sendTripLocation(trip: TripLocation) = broker.send<TripLocation>(trip,SocketMethods.TRIP_UPDATES)
    override fun reachedDropOff(value: ReachedRider) = broker.send<ReachedRider>(value,SocketMethods.REACHED_DROP_OFF)
    override fun startTrip(value: TripStarted) = broker.send<TripStarted>(value,SocketMethods.START_TRIP)
}