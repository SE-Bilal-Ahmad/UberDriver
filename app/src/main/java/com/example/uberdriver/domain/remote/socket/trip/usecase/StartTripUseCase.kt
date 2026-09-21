package com.example.uberdriver.domain.remote.socket.trip.usecase

import com.example.uberdriver.data.remote.api.backend.socket.trip.model.TripStarted
import com.example.uberdriver.data.remote.api.backend.socket.trip.repository.TripRepository
import javax.inject.Inject

class StartTripUseCase @Inject constructor(val tripRepository: TripRepository) {
    suspend operator fun invoke(value: TripStarted) = tripRepository.startTrip(value)
}