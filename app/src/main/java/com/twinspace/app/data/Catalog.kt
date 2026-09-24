package com.twinspace.app.data

data class CatalogApp(
    val id: String,
    val name: String,
    val url: String,
    val category: String,
    val hint: String,
)

object Catalog {
    val apps: List<CatalogApp> = listOf(
        CatalogApp("whatsapp", "WhatsApp", "https://web.whatsapp.com", "message", "Second number, work chats"),
        CatalogApp("telegram", "Telegram", "https://web.telegram.org/k/", "message", "Extra account"),
        CatalogApp("messenger", "Messenger", "https://www.messenger.com", "message", "Personal vs work"),
        CatalogApp("instagram", "Instagram", "https://www.instagram.com", "social", "Creator or private"),
        CatalogApp("facebook", "Facebook", "https://www.facebook.com", "social", "Page or profile"),
        CatalogApp("x", "X", "https://x.com", "social", "Second handle"),
        CatalogApp("tiktok", "TikTok", "https://www.tiktok.com", "social", "Creator studio"),
        CatalogApp("reddit", "Reddit", "https://www.reddit.com", "social", "Alt account"),
        CatalogApp("snapchat", "Snapchat", "https://web.snapchat.com", "social", "Web session"),
        CatalogApp("gmail", "Gmail", "https://mail.google.com", "google", "Second inbox"),
        CatalogApp("drive", "Drive", "https://drive.google.com", "google", "Work files"),
        CatalogApp("photos", "Photos", "https://photos.google.com", "google", "Private library"),
        CatalogApp("maps", "Maps", "https://maps.google.com", "google", "Saved places"),
        CatalogApp("youtube", "YouTube", "https://www.youtube.com", "media", "Alt channel"),
        CatalogApp("spotify", "Spotify", "https://open.spotify.com", "media", "Second library"),
        CatalogApp("discord", "Discord", "https://discord.com/app", "media", "Server split"),
        CatalogApp("twitch", "Twitch", "https://www.twitch.tv", "media", "Creator tools"),
        CatalogApp("linkedin", "LinkedIn", "https://www.linkedin.com", "work", "Work identity"),
        CatalogApp("slack", "Slack", "https://app.slack.com", "work", "Second workspace"),
        CatalogApp("outlook", "Outlook", "https://outlook.live.com", "work", "Work mail"),
    )

    fun byId(id: String): CatalogApp? = apps.find { it.id == id }

    val categories = listOf("all", "message", "social", "google", "work", "media")
}
