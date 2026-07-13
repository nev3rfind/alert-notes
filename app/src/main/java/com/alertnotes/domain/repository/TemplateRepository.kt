package com.alertnotes.domain.repository

import com.alertnotes.domain.model.ReminderTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Reminder templates: the built-in set plus the user's own, persisted
 * locally so they work identically offline and online. Built-ins are
 * immutable — they can only be duplicated into custom templates.
 */
interface TemplateRepository {

    /** Built-ins first, then custom templates, live. */
    val templates: Flow<List<ReminderTemplate>>

    /** Inserts or replaces a custom template (built-in ids are refused). */
    suspend fun saveCustom(template: ReminderTemplate)

    /** Copies any template into a new custom one. */
    suspend fun duplicate(template: ReminderTemplate)

    /** Deletes a custom template; built-ins are refused. */
    suspend fun delete(id: String)
}
