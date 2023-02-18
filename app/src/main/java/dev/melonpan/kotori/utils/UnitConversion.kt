package fly.speedmeter.grub.utils

class UnitConversion {
    
    companion object {

        val FEET_IN_METER = 3.28084f
        val METERS_PER_SECOND = 3.6f // m/s in km/h
        val MILES_PER_HOUR = 0.62137119f // mph in km/h
        val MILES_IN_METER = 0.0006213712f
    
        fun metersToFeet(m: Float): Float = m * FEET_IN_METER
        
        fun metersPerSecondToKmPerHour(ms: Float): Float = ms * METERS_PER_SECOND
        
        fun kmPerHourToMph(kmh: Float): Float = kmh * MILES_PER_HOUR
        
        fun metersToMiles(m: Float): Float = m * MILES_IN_METER
    }
}