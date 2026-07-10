package com.alertnotes.services

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which conversation is on screen right now — the client half of smart
 * delivery. The messaging service suppresses a chat push whose sender the
 * user is already looking at (Firestore's realtime listener renders it
 * first); the server half reads the mirrored `activeChatId` in the private
 * profile section and skips the send entirely.
 */
@Singleton
class ChatSessionTracker @Inject constructor() {

    private val _activePartnerUid = MutableStateFlow<String?>(null)
    val activePartnerUid: StateFlow<String?> = _activePartnerUid.asStateFlow()

    fun onConversationVisible(otherUid: String) {
        _activePartnerUid.value = otherUid
    }

    fun onConversationHidden(otherUid: String) {
        // Guarded: a new conversation may have claimed the slot already.
        if (_activePartnerUid.value == otherUid) _activePartnerUid.value = null
    }
}
