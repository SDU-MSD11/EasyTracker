package com.sdumsd11.easytracker.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sdumsd11.easytracker.core.RequestStatus
import com.sdumsd11.easytracker.domain.model.Group
import com.sdumsd11.easytracker.ui.components.EasyCard
import com.sdumsd11.easytracker.ui.components.MemberChip
import com.sdumsd11.easytracker.ui.theme.EasyTrackerTheme

/**
 * Create group. Creation runs in the VM owner ([onCreate]); [createResult] success
 * fires [onCreated] with the real id (mirrors the LoginScreen result convention).
 */
@Composable
fun CreateGroupScreen(
    modifier: Modifier = Modifier,
    onCreated: (String) -> Unit,
    onCancel: () -> Unit,
    createResult: RequestStatus<Group>? = null,
    onCreate: (String, String, List<String>) -> Unit = { _, _, _ -> },
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    val invited = remember { mutableStateListOf<String>() }
    val emailValid = email.contains("@")
    val isSaving = createResult is RequestStatus.Pending
    val error = (createResult as? RequestStatus.Failed)?.message
    LaunchedEffect(createResult) {
        if (createResult is RequestStatus.Success) onCreated(createResult.data.id)
    }
    ScreenScaffold(title = "Create group", modifier = modifier, onBack = onCancel) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Group name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        EasyCard(modifier = Modifier.fillMaxWidth()) {
            Text("Members", style = MaterialTheme.typography.titleMedium)
            invited.forEach { MemberChip(name = it, modifier = Modifier.fillMaxWidth()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Member email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { invited.add(email.trim()); email = "" },
                    enabled = emailValid,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Add") }
            }
        }
        NavButton(
            label = if (isSaving) "Creating…" else "Create group",
            onClick = { onCreate(name.trim(), description, invited.toList()) },
            enabled = name.isNotBlank() && !isSaving,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SecondaryButton(label = "Cancel", onClick = onCancel)
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateGroupPreview() {
    EasyTrackerTheme { CreateGroupScreen(onCreated = {}, onCancel = {}) }
}
