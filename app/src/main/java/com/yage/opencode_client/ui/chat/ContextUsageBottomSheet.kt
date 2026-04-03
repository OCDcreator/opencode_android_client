package com.yage.opencode_client.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yage.opencode_client.R
import com.yage.opencode_client.ui.AppState
import com.yage.opencode_client.ui.theme.LocalUiScale
import com.yage.opencode_client.ui.theme.uiScaled
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import kotlin.math.max

private data class ContextStat(
    val label: String,
    val value: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContextUsageBottomSheet(
    usage: AppState.ContextUsage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val integerFormat = remember { NumberFormat.getIntegerInstance() }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance() }
    val dateTimeFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val unavailable = stringResource(R.string.context_unavailable)
    val stats = listOf(
        ContextStat(stringResource(R.string.context_stats_session), usage.sessionTitle.ifBlank { unavailable }),
        ContextStat(stringResource(R.string.context_stats_provider), usage.providerLabel),
        ContextStat(stringResource(R.string.context_stats_model), usage.modelLabel),
        ContextStat(stringResource(R.string.context_stats_limit), integerFormat.format(usage.contextLimit)),
        ContextStat(stringResource(R.string.context_stats_total_tokens), integerFormat.format(usage.totalTokens)),
        ContextStat(stringResource(R.string.context_stats_usage), "${usage.usagePercent}%"),
        ContextStat(stringResource(R.string.context_stats_remaining_tokens), integerFormat.format(usage.remainingTokens)),
        ContextStat(stringResource(R.string.context_stats_input_tokens), integerFormat.format(usage.inputTokens)),
        ContextStat(stringResource(R.string.context_stats_output_tokens), integerFormat.format(usage.outputTokens)),
        ContextStat(stringResource(R.string.context_stats_reasoning_tokens), integerFormat.format(usage.reasoningTokens)),
        ContextStat(stringResource(R.string.context_stats_cache_read_tokens), integerFormat.format(usage.cacheReadTokens)),
        ContextStat(stringResource(R.string.context_stats_cache_write_tokens), integerFormat.format(usage.cacheWriteTokens)),
        ContextStat(stringResource(R.string.context_stats_total_cost), currencyFormat.format(usage.totalCost)),
        ContextStat(stringResource(R.string.context_stats_messages), integerFormat.format(usage.totalMessages)),
        ContextStat(stringResource(R.string.context_stats_user_messages), integerFormat.format(usage.userMessages)),
        ContextStat(stringResource(R.string.context_stats_assistant_messages), integerFormat.format(usage.assistantMessages)),
        ContextStat(stringResource(R.string.context_stats_session_created), formatTimestamp(dateTimeFormat, usage.sessionCreatedAt, unavailable)),
        ContextStat(stringResource(R.string.context_stats_last_activity), formatTimestamp(dateTimeFormat, usage.lastActivityAt, unavailable))
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            val configuration = LocalConfiguration.current
            val densityPressure = max(configuration.fontScale, LocalUiScale.current.factor)
            val compactLayout = maxWidth / densityPressure < 380.dp
            val statRows = if (compactLayout) stats.map { listOf(it) } else stats.chunked(2)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp.uiScaled(), vertical = 8.dp.uiScaled()),
                verticalArrangement = Arrangement.spacedBy(16.dp.uiScaled())
            ) {
                if (compactLayout) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp.uiScaled())
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ContextUsageRing(
                                usage = usage,
                                outerSize = 56.dp,
                                innerSize = 44.dp,
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp.uiScaled()))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.context_usage_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = usage.sessionTitle.ifBlank { unavailable },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${usage.providerLabel} / ${usage.modelLabel}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = "${integerFormat.format(usage.totalTokens)} / ${integerFormat.format(usage.contextLimit)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${usage.usagePercent}%",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContextUsageRing(
                            usage = usage,
                            outerSize = 56.dp,
                            innerSize = 44.dp,
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp.uiScaled()))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.context_usage_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = usage.sessionTitle.ifBlank { unavailable },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${usage.providerLabel} / ${usage.modelLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${usage.usagePercent}%",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${integerFormat.format(usage.totalTokens)} / ${integerFormat.format(usage.contextLimit)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                statRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp.uiScaled())
                    ) {
                        row.forEach { stat ->
                            ContextStatCard(
                                stat = stat,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (!compactLayout && row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                Spacer(modifier = Modifier.size(12.dp.uiScaled()))
            }
        }
    }
}

@Composable
private fun ContextStatCard(
    stat: ContextStat,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp.uiScaled()),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp.uiScaled(), vertical = 12.dp.uiScaled()),
            verticalArrangement = Arrangement.spacedBy(6.dp.uiScaled())
        ) {
            Text(
                text = stat.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stat.value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatTimestamp(
    formatter: DateFormat,
    timestamp: Long?,
    unavailable: String
): String {
    if (timestamp == null) return unavailable
    return runCatching { formatter.format(Date(timestamp)) }.getOrElse { unavailable }
}
