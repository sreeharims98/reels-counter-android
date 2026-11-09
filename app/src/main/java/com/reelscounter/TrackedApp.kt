package com.reelscounter

enum class TrackedApp(val packageName: String, val label: String, val shortLabel: String, val feedName: String) {
    INSTAGRAM("com.instagram.android", "Instagram", "IG", "Reels"),
    YOUTUBE("com.google.android.youtube", "YouTube", "YT", "Shorts");

    companion object {
        fun fromPackage(pkg: CharSequence?): TrackedApp? = entries.firstOrNull { it.packageName == pkg?.toString() }
        fun fromName(name: String): TrackedApp? = entries.firstOrNull { it.name == name }
    }
}
