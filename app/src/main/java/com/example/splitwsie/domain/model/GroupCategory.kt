package com.example.splitwsie.domain.model

enum class GroupCategory(val displayName: String, val emoji: String) {
    TRIP("Trip", "✈️"),
    HOME("Home", "🏠"),
    FRIENDS("Friends", "🧑‍🤝‍🧑"),
    FAMILY("Family", "👨‍👩‍👧‍👦"),
    COUPLE("Couple", "❤️"),
    OTHER("Other", "📂");

    companion object {
        fun fromName(name: String): GroupCategory {
            return entries.find { it.name.equals(name, ignoreCase = true) || it.displayName.equals(name, ignoreCase = true) } ?: OTHER
        }
    }
}
