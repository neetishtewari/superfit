package com.superfit.app.data

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Names for meals, workouts and weights in the cloud backup.
 *
 * The phone numbers its entries 1, 2, 3…, so two devices on one account (an Android phone and an
 * iPhone, or an old and a new phone) would both make "meal 120" and overwrite each other in the
 * backup. A random id is unique on every device. Entries saved before this keep their old number
 * as their cloud id (see SuperfitDatabase.MIGRATION_8_9), so their existing backups still match.
 */
object CloudIds {
    /** The cloud id old app versions used for every entry before it had a real number. */
    const val LEGACY_UNNUMBERED = "0"

    @OptIn(ExperimentalUuidApi::class)
    fun newId(): String = Uuid.random().toString()
}
