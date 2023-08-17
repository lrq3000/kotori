package dev.melonpan.kotori

data class PositioningData(
    var accuracy: Float = 0.0f,
    var altitude: Double = 0.0,
    var altitudeMeanSeaLevel: Double = 0.0,
    var currentSpeed: Float = 0.0f,
    var distance: Double = 0.0,
    var isFirstTime: Boolean = true,
    var isRunning: Boolean = false,
    var maxSpeed: Float = 0.0f,
    var satellitesUsed: Int = 0,
    var satellites: Int = 0,
    var time: Long = 0,
    var timeStopped: Long = 0
)
