package com.yage.opencode_client.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.yage.opencode_client.R
import com.yage.opencode_client.data.model.TodoItem
import com.yage.opencode_client.ui.theme.uiScaled

@Composable
fun TodoListPanel(
    todos: List<TodoItem>,
    modifier: Modifier = Modifier
) {
    if (todos.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp.uiScaled())
            ) {
                Icon(
                    Icons.Default.Checklist,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp.uiScaled()),
                    tint = MaterialTheme.colorScheme.outline
                )
                Text(
                    stringResource(R.string.chat_todo_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        return
    }

    val completed = todos.count { it.isCompleted }
    val total = todos.size

    LazyColumn(modifier = modifier.padding(horizontal = 8.dp.uiScaled())) {
        item {
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { completed.toFloat() / total.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp.uiScaled()),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    stringResource(R.string.chat_todo_progress, completed, total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp.uiScaled())
                )
            }
        }

        items(todos, key = { it.id }) { todo ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp.uiScaled()),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = if (todo.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp.uiScaled())
                        .padding(top = 2.dp.uiScaled()),
                    tint = if (todo.isCompleted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(8.dp.uiScaled()))
                Text(
                    text = todo.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null
                    ),
                    color = if (todo.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
