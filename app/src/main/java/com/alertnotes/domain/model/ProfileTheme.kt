package com.alertnotes.domain.model

/**
 * Built-in banner/accent themes for the profile. The selection is public —
 * friends will eventually see it — and persisted as the enum name in
 * `public/data.bannerTheme`. Colors live in the UI layer; the domain only
 * knows the identity, mirroring how reminder themes work.
 */
enum class ProfileTheme {
    PRIMARY_ORANGE,
    FOREST_GREEN,
    OCEAN_BLUE,
    MIDNIGHT_PURPLE,
    CRIMSON,
    WARM_SAND,
    SLATE_GREY,
    SUNSET_GRADIENT,
    AURORA_GRADIENT,
    EMERALD_GRADIENT,
}
