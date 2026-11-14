package com.example.workingapp.presentation.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.workingapp.R
import kotlinx.collections.immutable.ImmutableList

@Composable
fun GoalDialog(
    options: ImmutableList<Int>,
    currentGoal: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_goal_dialog_title)) },
        text = {
            Column {
                options.forEach { goal ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = goal == currentGoal, role = Role.RadioButton, onClick = { onSelect(goal) })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = goal == currentGoal, onClick = null)
                        Text(stringResource(R.string.home_goal_option, goal), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
