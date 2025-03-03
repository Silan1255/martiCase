package com.example.martiproject.enum

enum class PreferenceKey(val key: String) {
    PREF_NAME("route_preferences"),
    NAVIGATION_ACTIVE("navigation_active"),
    TRACKING_ACTIVE("tracking_active"),
    ROUTE_POINTS("route_points"),
    DESTINATION("destination"),
    NAVIGATION_MODE("navigation_mode"),
    ROUTE_DISTANCE("route_distance"),
    ROUTE_DURATION("route_duration")
}