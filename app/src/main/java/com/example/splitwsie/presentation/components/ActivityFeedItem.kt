package com.example.splitwsie.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.splitwsie.domain.model.ActivityItem
import com.example.splitwsie.domain.model.ActivityType
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.util.Money
import com.example.splitwsie.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ActivityFeedItem(
    activity: ActivityItem,
    currentUser: User?,
    usersMap: Map<String, User>,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()) }
    
    val actorName = if (activity.userId == currentUser?.id) "You" else usersMap[activity.userId]?.name ?: "Someone"
    val targetName = activity.targetUserId?.let { 
        if (it == currentUser?.id) "you" else usersMap[it]?.name ?: "someone"
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SettleSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val emoji = when (activity.type) {
                ActivityType.EXPENSE_ADDED -> "🧾"
                ActivityType.EXPENSE_EDITED -> "✏️"
                ActivityType.SETTLED -> "💸"
                ActivityType.GROUP_CREATED -> "🎉"
                ActivityType.JOINED_GROUP -> "👋"
            }
            Text(emoji, style = MaterialTheme.typography.titleMedium)
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            val text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SettleTextPrimary)) {
                    append(actorName)
                }
                
                when (activity.type) {
                    ActivityType.EXPENSE_ADDED -> {
                        append(" added ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(activity.title ?: "an expense")
                        }
                        if (activity.amount != null) {
                            append(" ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SettlePrimary)) {
                                append(Money.format(activity.amount))
                            }
                        }
                    }
                    ActivityType.EXPENSE_EDITED -> {
                        append(" edited ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(activity.title ?: "an expense")
                        }
                    }
                    ActivityType.SETTLED -> {
                        append(" settled ")
                        if (activity.amount != null) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SettlePrimary)) {
                                append(Money.format(activity.amount))
                            }
                        }
                        if (targetName != null) {
                            append(" with ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SettleTextPrimary)) {
                                append(targetName)
                            }
                        }
                    }
                    ActivityType.GROUP_CREATED -> {
                        append(" created the group ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(activity.title ?: "")
                        }
                    }
                    ActivityType.JOINED_GROUP -> {
                        append(" joined the group ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(activity.title ?: "")
                        }
                    }
                }
            }
            
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = SettleTextPrimary)
            
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = dateFormat.format(activity.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = SettleTextSecondary
            )
        }
    }
}
