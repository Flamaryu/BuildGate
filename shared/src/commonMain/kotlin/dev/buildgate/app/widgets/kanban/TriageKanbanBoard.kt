package dev.buildgate.app.widgets.kanban

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.buildgate.app.entities.feedbackitem.model.FeedbackItem
import dev.buildgate.app.entities.feedbackitem.model.FeedbackStatus
import dev.buildgate.app.entities.feedbackitem.ui.FeedbackItemCard

@Composable
fun TriageKanbanBoard(
    items: List<FeedbackItem>,
    onStatusChange: (String, FeedbackStatus) -> Unit,
    onUpvote: (String) -> Unit,
    onItemSelect: (FeedbackItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val openItems = items.filter { it.status == FeedbackStatus.OPEN }
    val inProgressItems = items.filter { it.status == FeedbackStatus.IN_PROGRESS }
    val fixedItems = items.filter { it.status == FeedbackStatus.FIXED }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        KanbanColumn(
            title = "🔴 OPEN (${openItems.size})",
            items = openItems,
            targetStatus = FeedbackStatus.IN_PROGRESS,
            actionLabel = "Move to Progress ➔",
            onStatusChange = onStatusChange,
            onUpvote = onUpvote,
            onItemSelect = onItemSelect,
            modifier = Modifier.weight(1f)
        )

        KanbanColumn(
            title = "🟡 IN PROGRESS (${inProgressItems.size})",
            items = inProgressItems,
            targetStatus = FeedbackStatus.FIXED,
            actionLabel = "Mark Fixed ✅",
            onStatusChange = onStatusChange,
            onUpvote = onUpvote,
            onItemSelect = onItemSelect,
            modifier = Modifier.weight(1f)
        )

        KanbanColumn(
            title = "🟢 FIXED / VERIFIED (${fixedItems.size})",
            items = fixedItems,
            targetStatus = FeedbackStatus.OPEN,
            actionLabel = "Reopen ↺",
            onStatusChange = onStatusChange,
            onUpvote = onUpvote,
            onItemSelect = onItemSelect,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun KanbanColumn(
    title: String,
    items: List<FeedbackItem>,
    targetStatus: FeedbackStatus,
    actionLabel: String,
    onStatusChange: (String, FeedbackStatus) -> Unit,
    onUpvote: (String) -> Unit,
    onItemSelect: (FeedbackItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items) { item ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FeedbackItemCard(
                        item = item,
                        onUpvoteClick = { onUpvote(item.id) },
                        onCardClick = { onItemSelect(item) }
                    )

                    Button(
                        onClick = { onStatusChange(item.id, targetStatus) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(actionLabel, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
