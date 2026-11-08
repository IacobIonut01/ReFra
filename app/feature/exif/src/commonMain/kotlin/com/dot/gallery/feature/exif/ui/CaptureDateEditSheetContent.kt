/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.exif.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import com.dot.gallery.core.Position
import com.dot.gallery.core.presentation.components.ExpressiveSheet
import com.dot.gallery.core.presentation.components.SheetDismissConfirmActions
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.presentation.components.SheetOptionRow
import com.dot.gallery.core.presentation.components.SheetRowTrailing
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.domain.date.formatEpochSec
import com.dot.gallery.domain.date.systemDatePattern
import com.dot.gallery.domain.model.CaptureTimeOrigin
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.definedTimestamp
import com.dot.gallery.domain.model.resolvedCaptureTimeOrigin
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.exif.generated.resources.Res
import com.dot.gallery.feature.exif.generated.resources.action_cancel
import com.dot.gallery.feature.exif.generated.resources.action_confirm
import com.dot.gallery.feature.exif.generated.resources.capture_date_date
import com.dot.gallery.feature.exif.generated.resources.capture_date_may_move
import com.dot.gallery.feature.exif.generated.resources.capture_date_time
import com.dot.gallery.feature.exif.generated.resources.create_copy
import com.dot.gallery.feature.exif.generated.resources.dated_copy_created
import com.dot.gallery.feature.exif.generated.resources.dated_copy_created_summary
import com.dot.gallery.feature.exif.generated.resources.dated_copy_loss_summary
import com.dot.gallery.feature.exif.generated.resources.dated_copy_required
import com.dot.gallery.feature.exif.generated.resources.dated_copy_required_summary
import com.dot.gallery.feature.exif.generated.resources.edit_capture_date
import com.dot.gallery.feature.exif.generated.resources.keep_both
import com.dot.gallery.feature.exif.generated.resources.move_original_to_trash
import com.dot.gallery.feature.exif.generated.resources.save_date
import com.dot.gallery.feature.exif.generated.resources.set_capture_date

/**
 * Pure capture-date editor: date/time pickers plus the dated-copy dialogs.
 * Probing edit capability, requesting write/trash permissions and applying the
 * update are the caller's responsibility — the content only reports intents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureDateEditSheetContent(
    state: AppBottomSheetState,
    media: Media,
    busy: Boolean,
    is24Hour: Boolean,
    copyKind: CaptureDateCopyKind?,
    copyCreated: Boolean,
    canTrashOriginal: Boolean,
    onDismiss: () -> Unit,
    onSave: (timestampMillis: Long) -> Unit,
    onCreateCopy: (timestampMillis: Long) -> Unit,
    onDismissCopy: () -> Unit,
    onTrashOriginal: () -> Unit,
    onKeepBoth: () -> Unit,
) {
    val zoneId = remember { TimeZone.currentSystemDefault() }
    val initialTimestamp = media.definedTimestamp * 1000L
    var selectedTimestamp by rememberSaveable(media.id) { mutableLongStateOf(initialTimestamp) }
    val needsEmbeddedCaptureDate = media.resolvedCaptureTimeOrigin != CaptureTimeOrigin.EMBEDDED_IMAGE
    var showDatePicker by rememberSaveable(media.id) { mutableStateOf(false) }
    var showTimePicker by rememberSaveable(media.id) { mutableStateOf(false) }

    val datePattern = remember { systemDatePattern("yMMMd") }
    val timePattern = remember { systemDatePattern("jm") }
    ExpressiveSheet(sheetState = state) {
        CaptureDateEditorBody(
            title = stringResource(
                if (needsEmbeddedCaptureDate) Res.string.set_capture_date else Res.string.edit_capture_date
            ),
            date = (selectedTimestamp / 1000L).formatEpochSec(datePattern),
            time = (selectedTimestamp / 1000L).formatEpochSec(timePattern),
            offset = zoneId.offsetAt(Instant.fromEpochMilliseconds(selectedTimestamp)).toString(),
            canSave = !busy && (selectedTimestamp != initialTimestamp || needsEmbeddedCaptureDate),
            onDate = { showDatePicker = true },
            onTime = { showTimePicker = true },
            onDismiss = onDismiss,
            onSave = { onSave(selectedTimestamp) },
        )
    }

    if (showDatePicker) {
        val selectedDate = Instant.fromEpochMilliseconds(selectedTimestamp)
            .toLocalDateTime(zoneId).date
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDateTime(selectedDate, LocalTime(0, 0))
                .toInstant(TimeZone.UTC)
                .toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { pickerMillis ->
                        val date = Instant.fromEpochMilliseconds(pickerMillis)
                            .toLocalDateTime(TimeZone.UTC).date
                        val time = Instant.fromEpochMilliseconds(selectedTimestamp)
                            .toLocalDateTime(zoneId).time
                        selectedTimestamp = LocalDateTime(date, time)
                            .toInstant(zoneId)
                            .toEpochMilliseconds()
                    }
                    showDatePicker = false
                }) { Text(stringResource(Res.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        val current = Instant.fromEpochMilliseconds(selectedTimestamp).toLocalDateTime(zoneId)
        val timePickerState = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = is24Hour
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(Res.string.capture_date_time)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val date = current.date
                    val time = LocalTime(timePickerState.hour, timePickerState.minute, current.second)
                    selectedTimestamp = LocalDateTime(date, time)
                        .toInstant(zoneId)
                        .toEpochMilliseconds()
                    showTimePicker = false
                }) { Text(stringResource(Res.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }

    copyKind?.let { kind ->
        AlertDialog(
            onDismissRequest = onDismissCopy,
            title = { Text(stringResource(Res.string.dated_copy_required)) },
            text = {
                Text(
                    stringResource(
                        if (kind == CaptureDateCopyKind.SAFE_COPY) {
                            Res.string.dated_copy_required_summary
                        } else {
                            Res.string.dated_copy_loss_summary
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { onCreateCopy(selectedTimestamp) }
                ) { Text(stringResource(Res.string.create_copy)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissCopy) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }

    if (copyCreated) {
        AlertDialog(
            onDismissRequest = onKeepBoth,
            title = { Text(stringResource(Res.string.dated_copy_created)) },
            text = { Text(stringResource(Res.string.dated_copy_created_summary)) },
            confirmButton = {
                if (canTrashOriginal) {
                    TextButton(onClick = onTrashOriginal) {
                        Text(stringResource(Res.string.move_original_to_trash))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onKeepBoth) {
                    Text(stringResource(Res.string.keep_both))
                }
            }
        )
    }
}

/** Stateless body of the capture date sheet: header, date and time rows, zone offset, actions. */
@Composable
fun ColumnScope.CaptureDateEditorBody(
    title: String,
    date: String,
    time: String,
    offset: String,
    canSave: Boolean,
    onDate: () -> Unit,
    onTime: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    SheetHeader(title = title, body = stringResource(Res.string.capture_date_may_move))
    SheetOptionRow(
        title = stringResource(Res.string.capture_date_date),
        summary = date,
        icon = Icons.Outlined.CalendarToday,
        position = Position.Top,
        trailing = SheetRowTrailing.Chevron,
        onClick = onDate,
    )
    SheetOptionRow(
        title = stringResource(Res.string.capture_date_time),
        summary = time,
        icon = Icons.Outlined.Schedule,
        position = Position.Bottom,
        trailing = SheetRowTrailing.Chevron,
        onClick = onTime,
    )
    Text(
        text = offset,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 8.dp)
    )
    SheetDismissConfirmActions(
        dismissText = stringResource(Res.string.action_cancel),
        onDismiss = onDismiss,
        confirmText = stringResource(Res.string.save_date),
        onConfirm = onSave,
        confirmEnabled = canSave,
    )
}
