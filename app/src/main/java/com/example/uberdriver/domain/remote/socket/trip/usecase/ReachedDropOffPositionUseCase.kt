package com.example.uberdriver.domain.remote.socket.trip.usecase

import com.example.uberdriver.data.remote.api.backend.socket.trip.model.ReachedRider
import com.example.uberdriver.data.remote.api.backend.socket.trip.repository.TripRepository
import javax.inject.Inject

class ReachedDropOffPositionUseCase @Inject constructor(private val repository: TripRepository) {
    suspend operator fun invoke(value: ReachedRider) = repository.reachedDropOff(value)
}