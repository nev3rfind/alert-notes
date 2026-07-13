package com.alertnotes.data.repository

import android.content.Context
import com.alertnotes.core.util.AppLogger
import com.alertnotes.domain.model.AcknowledgementType
import com.alertnotes.domain.model.ReminderPriority
import com.alertnotes.domain.model.ReminderTemplate
import com.alertnotes.domain.model.ReminderTheme
import com.alertnotes.domain.model.ReminderType
import com.alertnotes.domain.repository.TemplateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * File-backed template storage: custom templates live as one JSON document
 * in app-private storage (no schema migration, fully offline), loaded once
 * and held in a StateFlow. Built-ins are code — always present, always
 * up to date with the app, structurally impossible to corrupt.
 */
@Singleton
class TemplateRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: AppLogger,
) : TemplateRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val serializer = ListSerializer(ReminderTemplate.serializer())

    private val custom = MutableStateFlow(loadCustom())

    override val templates: Flow<List<ReminderTemplate>> =
        custom.map { BUILT_INS + it.sortedBy { template -> template.name.lowercase() } }

    override suspend fun saveCustom(template: ReminderTemplate) {
        if (template.builtIn || BUILT_INS.any { it.id == template.id }) return
        mutate { list -> list.filterNot { it.id == template.id } + template.copy(builtIn = false) }
    }

    override suspend fun duplicate(template: ReminderTemplate) {
        mutate { list ->
            list + template.copy(
                id = UUID.randomUUID().toString(),
                name = "${template.name} (copy)",
                builtIn = false,
            )
        }
    }

    override suspend fun delete(id: String) {
        if (BUILT_INS.any { it.id == id }) return
        mutate { list -> list.filterNot { it.id == id } }
    }

    private suspend fun mutate(transform: (List<ReminderTemplate>) -> List<ReminderTemplate>) {
        mutex.withLock {
            val updated = transform(custom.value)
            custom.value = updated
            withContext(Dispatchers.IO) {
                runCatching {
                    storageFile().writeText(json.encodeToString(serializer, updated))
                }.onFailure { logger.w(TAG, "Template persistence failed", it) }
            }
        }
    }

    private fun loadCustom(): List<ReminderTemplate> = runCatching {
        val file = storageFile()
        if (!file.exists()) return emptyList()
        json.decodeFromString(serializer, file.readText())
    }.getOrDefault(emptyList())

    private fun storageFile(): File = File(context.filesDir, FILE_NAME)

    private companion object {
        const val TAG = "TemplateRepository"
        const val FILE_NAME = "reminder_templates.json"

        /** The shipped set — copyable, never editable, never deletable. */
        val BUILT_INS = listOf(
            ReminderTemplate(
                id = "builtin_medication",
                name = "Take Medication",
                title = "Take medication",
                description = "Time for your medication.",
                priority = ReminderPriority.CRITICAL,
                acknowledgement = AcknowledgementType.TAP,
                theme = ReminderTheme.PRIMARY_ORANGE,
                builtIn = true,
            ),
            ReminderTemplate(
                id = "builtin_school",
                name = "Leave for School",
                title = "Leave for school",
                description = "Bag packed? Time to go.",
                priority = ReminderPriority.HIGH,
                builtIn = true,
            ),
            ReminderTemplate(
                id = "builtin_work",
                name = "Leave for Work",
                title = "Leave for work",
                description = "Beat the traffic — head out now.",
                priority = ReminderPriority.HIGH,
                builtIn = true,
            ),
            ReminderTemplate(
                id = "builtin_groceries",
                name = "Buy Groceries",
                title = "Buy groceries",
                description = "Milk, bread, and whatever's missing.",
                type = ReminderType.CHECKLIST,
                builtIn = true,
            ),
            ReminderTemplate(
                id = "builtin_water",
                name = "Drink Water",
                title = "Drink a glass of water",
                priority = ReminderPriority.LOW,
                acknowledgement = AcknowledgementType.NONE,
                builtIn = true,
            ),
            ReminderTemplate(
                id = "builtin_exercise",
                name = "Exercise",
                title = "Time to exercise",
                description = "Thirty minutes — future you says thanks.",
                builtIn = true,
            ),
            ReminderTemplate(
                id = "builtin_appointment",
                name = "Attend Appointment",
                title = "Appointment",
                description = "Don't forget your appointment.",
                priority = ReminderPriority.HIGH,
                acknowledgement = AcknowledgementType.SWIPE,
                builtIn = true,
            ),
            ReminderTemplate(
                id = "builtin_dog",
                name = "Walk the Dog",
                title = "Walk the dog",
                priority = ReminderPriority.LOW,
                builtIn = true,
            ),
            ReminderTemplate(
                id = "builtin_parents",
                name = "Call Parents",
                title = "Call your parents",
                description = "They’d love to hear from you.",
                builtIn = true,
            ),
            ReminderTemplate(
                id = "builtin_bills",
                name = "Pay Bills",
                title = "Pay the bills",
                description = "Rent, utilities, subscriptions.",
                priority = ReminderPriority.HIGH,
                acknowledgement = AcknowledgementType.SIGNATURE,
                builtIn = true,
            ),
        )
    }
}
