package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyCardElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun UsersScreen(
    users: List<UserProfile>,
    currentUserId: String,
    isSubmitting: Boolean,
    onUpdateUser: (targetUserId: String, role: UserRole, active: Boolean, name: String, username: String) -> Unit,
    onUpdateCredentials: (targetUserId: String, username: String, newPassword: String?, onSuccess: () -> Unit) -> Unit,
    onCreateStaff: (name: String, username: String, password: String, role: UserRole, active: Boolean, onSuccess: () -> Unit) -> Unit
) {
    var editingCredentialsUser by remember { mutableStateOf<UserProfile?>(null) }
    var showCreateStaffDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(title = "Staff Access & Multi-Phone Sync")
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Add a Labour or Owner username & password here, or approve staff who sign in from another phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Button(
                        onClick = { showCreateStaffDialog = true },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("add_staff_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Staff Login")
                    }
                }
            }
        }

        if (users.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Group,
                    title = "No Staff Users Found",
                    description = "Staff members will appear here automatically once they sign in with Google."
                )
            }
        } else {
            items(users, key = { it.userId.ifBlank { it.id } }) { user ->
                val targetUid = user.userId.ifBlank { user.id }
                val isSelf = targetUid == currentUserId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_card_$targetUid"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${user.name}${if (isSelf) " (You)" else ""}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${user.email.ifBlank { "@${user.username}" }} • @${user.username}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            StatusBadge(label = if (user.active) "Active" else "Pending")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = user.userRole == UserRole.OWNER,
                                    onClick = {
                                        if (!isSelf && !isSubmitting) {
                                            onUpdateUser(targetUid, UserRole.OWNER, user.active, user.name, user.username)
                                        }
                                    },
                                    enabled = !isSelf && !isSubmitting,
                                    label = { Text("Owner") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentPurple,
                                        selectedLabelColor = TextPrimary
                                    )
                                )
                                FilterChip(
                                    selected = user.userRole == UserRole.LABOUR,
                                    onClick = {
                                        if (!isSelf && !isSubmitting) {
                                            onUpdateUser(targetUid, UserRole.LABOUR, user.active, user.name, user.username)
                                        }
                                    },
                                    enabled = !isSelf && !isSubmitting,
                                    label = { Text("Labour") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentBlue,
                                        selectedLabelColor = TextPrimary
                                    )
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Active", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Switch(
                                    checked = user.active,
                                    onCheckedChange = { checked ->
                                        if (!isSelf && !isSubmitting) {
                                            onUpdateUser(targetUid, user.userRole, checked, user.name, user.username)
                                        }
                                    },
                                    enabled = !isSelf && !isSubmitting
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { editingCredentialsUser = user },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("edit_credentials_$targetUid"),
                            border = BorderStroke(1.dp, NavyBorder)
                        ) {
                            Icon(Icons.Rounded.Key, contentDescription = null, tint = AccentBlue)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (user.passcodeHash.isBlank()) "Set Username & Password" else "Update Username & Password",
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    editingCredentialsUser?.let { target ->
        var nextUsername by remember(target.id) { mutableStateOf(target.username) }
        var nextPassword by remember(target.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editingCredentialsUser = null },
            containerColor = NavyCardElevated,
            title = { Text("Staff Credentials (${target.name})", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nextUsername,
                        onValueChange = { nextUsername = it },
                        label = { Text("Username (2–50 chars)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nextPassword,
                        onValueChange = { nextPassword = it },
                        label = { Text("New Password (leave blank to keep current)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateCredentials(
                            target.userId.ifBlank { target.id },
                            nextUsername,
                            nextPassword.takeIf { it.isNotBlank() }
                        ) {
                            editingCredentialsUser = null
                        }
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCredentialsUser = null }) { Text("Cancel") }
            }
        )
    }

    if (showCreateStaffDialog) {
        var staffName by remember { mutableStateOf("") }
        var staffUsername by remember { mutableStateOf("") }
        var staffPassword by remember { mutableStateOf("") }
        var staffRole by remember { mutableStateOf(UserRole.LABOUR) }
        AlertDialog(
            onDismissRequest = { showCreateStaffDialog = false },
            containerColor = NavyCardElevated,
            title = { Text("Add Staff Login", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = staffName,
                        onValueChange = { staffName = it },
                        label = { Text("Staff Name") },
                        placeholder = { Text("e.g. Shop Labour") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = staffUsername,
                        onValueChange = { staffUsername = it },
                        label = { Text("Username") },
                        placeholder = { Text("e.g. labour") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = staffPassword,
                        onValueChange = { staffPassword = it },
                        label = { Text("Password (4+ chars)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = staffRole == UserRole.LABOUR,
                            onClick = { staffRole = UserRole.LABOUR },
                            label = { Text("Labour") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentBlue,
                                selectedLabelColor = TextPrimary
                            )
                        )
                        FilterChip(
                            selected = staffRole == UserRole.OWNER,
                            onClick = { staffRole = UserRole.OWNER },
                            label = { Text("Owner") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentPurple,
                                selectedLabelColor = TextPrimary
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateStaff(
                            staffName.ifBlank { staffUsername },
                            staffUsername,
                            staffPassword,
                            staffRole,
                            true
                        ) {
                            showCreateStaffDialog = false
                        }
                    },
                    enabled = !isSubmitting && staffUsername.trim().length >= 2 && staffPassword.length >= 4,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateStaffDialog = false }) { Text("Cancel") }
            }
        )
    }
}
