package com.alertnotes.domain.model

/** The signed-in account, as much of it as the auth provider holds locally. */
data class AuthUser(
    val uid: String,
    val email: String,
    val displayName: String,
)
