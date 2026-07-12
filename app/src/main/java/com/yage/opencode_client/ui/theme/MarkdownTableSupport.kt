package com.yage.opencode_client.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownDivider
import com.mikepenz.markdown.compose.elements.MarkdownTableBasicText
import com.mikepenz.markdown.compose.components.markdownComponents
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMElementTypes.HEADER
import org.intellij.markdown.flavours.gfm.GFMElementTypes.ROW
import org.intellij.markdown.flavours.gfm.GFMTokenTypes.CELL
import org.intellij.markdown.flavours.gfm.GFMTokenTypes.TABLE_SEPARATOR

/**
 * Markdown [MarkdownComponents] that overrides only the table renderer so wide tables scroll
 * horizontally instead of being compressed into the screen width, and cells wrap their content
 * instead of truncating to a single ellipsized line.
 *
 * The stock mikepenz (0.39.0) table uses `weight(1f)` columns with `maxLines = 1` +
 * `TextOverflow.Ellipsis`, so any cell longer than its share of the screen gets cut off. This
 * variant gives the whole table a fixed width (columns * cellWidth), wraps it in
 * `horizontalScroll`, and lets cells grow vertically (`maxLines = Int.MAX_VALUE`).
 */
@Composable
fun markdownComponentsWithScrollableTable(): MarkdownComponents =
    markdownComponents(table = { model -> ScrollableMarkdownTable(model) })

@Composable
private fun ScrollableMarkdownTable(model: MarkdownComponentModel) {
    val dimens = LocalMarkdownDimens.current
    val tableCellWidth = dimens.tableCellWidth
    val tableCornerSize = dimens.tableCornerSize
    val tableBackground = LocalMarkdownColors.current.tableBackground
    val style: TextStyle = model.typography.table
    val settings = annotatorSettings()

    val columnsCount = model.node.findChildOfType(HEADER)?.children?.count { it.type == CELL } ?: 0
    val rowsCount = model.node.children.count { it.type == ROW } + 1 // header
    val naturalWidth: Dp = tableCellWidth * columnsCount

    // Take the larger of "natural table width" and "available width" so the table always fills
    // its container (no empty gap on the right, matching other message elements) while still
    // scrolling horizontally when the content is genuinely wider than the screen.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = dimens.tableMaxWidth)
            .background(tableBackground, RoundedCornerShape(tableCornerSize))
            .semantics { collectionInfo = CollectionInfo(rowCount = rowsCount, columnCount = columnsCount) }
    ) {
        val tableWidth = maxOf(naturalWidth, maxWidth)
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .requiredWidth(tableWidth)
        ) {
            var rowIndex = 1
            model.node.children.forEach { child ->
                when (child.type) {
                    HEADER -> ScrollableHeaderRow(model.content, child, tableWidth, style, settings)
                    ROW -> {
                        ScrollableBodyRow(model.content, child, tableWidth, style, settings, rowIndex)
                        rowIndex++
                    }
                    TABLE_SEPARATOR -> MarkdownDivider()
                }
            }
        }
    }
}

@Composable
private fun ScrollableHeaderRow(
    content: String,
    header: ASTNode,
    tableWidth: Dp,
    style: TextStyle,
    settings: com.mikepenz.markdown.annotator.AnnotatorSettings
) {
    val tableCellPadding = LocalMarkdownDimens.current.tableCellPadding
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.widthIn(tableWidth).height(IntrinsicSize.Max)
    ) {
        header.children.filter { it.type == CELL }.forEachIndexed { colIndex, cell ->
            Column(
                modifier = Modifier
                    .padding(tableCellPadding)
                    .weight(1f)
                    .semantics {
                        heading()
                        collectionItemInfo = CollectionItemInfo(0, 1, colIndex, 1)
                    }
            ) {
                MarkdownTableBasicText(
                    content = content,
                    cell = cell,
                    style = style.copy(fontWeight = FontWeight.Bold),
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Visible,
                    annotatorSettings = settings
                )
            }
        }
    }
}

@Composable
private fun ScrollableBodyRow(
    content: String,
    header: ASTNode,
    tableWidth: Dp,
    style: TextStyle,
    settings: com.mikepenz.markdown.annotator.AnnotatorSettings,
    rowIndex: Int
) {
    val tableCellPadding = LocalMarkdownDimens.current.tableCellPadding
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.widthIn(tableWidth)
    ) {
        header.children.filter { it.type == CELL }.forEachIndexed { colIndex, cell ->
            Column(
                modifier = Modifier
                    .padding(tableCellPadding)
                    .weight(1f)
                    .semantics {
                        collectionItemInfo = CollectionItemInfo(rowIndex, 1, colIndex, 1)
                    }
            ) {
                MarkdownTableBasicText(
                    content = content,
                    cell = cell,
                    style = style,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Visible,
                    annotatorSettings = settings
                )
            }
        }
    }
}
