package com.alertnotes.domain.model

/**
 * A reusable reminder blueprint: everything about HOW a reminder behaves
 * (title, description, priority, type, acknowledgement, visual theme) minus
 * WHEN it fires — using a template always opens the editor to set the
 * schedule. Built-ins ship with the app and cannot be edited or deleted,
 * only copied; custom templates are captured from the editor's
 * "Save as template" action.
 */
@kotlinx.serialization.Serializable
data class ReminderTemplate(
    val id: String,
    val name: String,
    val title: String,
    val description: String = "",
    val priority: ReminderPriority = ReminderPriority.NORMAL,
    val type: ReminderType = ReminderType.TEXT,
    val acknowledgement: AcknowledgementType = AcknowledgementType.TAP,
    val theme: ReminderTheme = ReminderTheme.PRIMARY_ORANGE,
    val builtIn: Boolean = false,
)
