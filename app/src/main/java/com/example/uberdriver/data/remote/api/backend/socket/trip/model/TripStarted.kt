package com.example.uberdriver.data.remote.api.backend.socket.trip.model

import java.util.UUID

data class TripStarted(val rideId: UUID, val riderId: UUID, val driverId: UUID)
