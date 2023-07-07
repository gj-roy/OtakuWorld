package com.programmersbox.uiviews.history

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberDismissState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.google.accompanist.placeholder.material.placeholder
import com.programmersbox.favoritesdatabase.HistoryDao
import com.programmersbox.favoritesdatabase.RecentModel
import com.programmersbox.sharedutils.MainLogo
import com.programmersbox.uiviews.R
import com.programmersbox.uiviews.utils.BackButton
import com.programmersbox.uiviews.utils.ComposableUtils
import com.programmersbox.uiviews.utils.InsetMediumTopAppBar
import com.programmersbox.uiviews.utils.LightAndDarkPreviews
import com.programmersbox.uiviews.utils.LoadingDialog
import com.programmersbox.uiviews.utils.LocalGenericInfo
import com.programmersbox.uiviews.utils.LocalHistoryDao
import com.programmersbox.uiviews.utils.LocalNavController
import com.programmersbox.uiviews.utils.LocalSystemDateTimeFormat
import com.programmersbox.uiviews.utils.MockAppIcon
import com.programmersbox.uiviews.utils.OtakuScaffold
import com.programmersbox.uiviews.utils.PreviewTheme
import com.programmersbox.uiviews.utils.components.GradientImage
import com.programmersbox.uiviews.utils.dispatchIo
import com.programmersbox.uiviews.utils.navigateToDetails
import com.programmersbox.uiviews.utils.showErrorToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@ExperimentalMaterial3Api
@Composable
fun HistoryUi(
    logo: MainLogo,
    dao: HistoryDao = LocalHistoryDao.current,
    hm: HistoryViewModel = viewModel { HistoryViewModel(dao) },
) {
    val recentItems = hm.historyItems.collectAsLazyPagingItems()
    val recentSize by hm.historyCount.collectAsState(initial = 0)
    val scope = rememberCoroutineScope()

    var clearAllDialog by remember { mutableStateOf(false) }

    if (clearAllDialog) {
        val onDismissRequest = { clearAllDialog = false }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(R.string.clear_all_history)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) { println("Deleted " + dao.deleteAllRecentHistory() + " rows") }
                        onDismissRequest()
                    }
                ) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.no)) } }
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    OtakuScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            InsetMediumTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() },
                title = { Text(stringResource(R.string.history)) },
                actions = {
                    Text("$recentSize")
                    IconButton(onClick = { clearAllDialog = true }) { Icon(Icons.Default.DeleteForever, null) }
                }
            )
        }
    ) { p ->

        /*AnimatedLazyColumn(
            contentPadding = p,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 4.dp),
            items = recentItems.itemSnapshotList.fastMap { item ->
                AnimatedLazyListItem(key = item!!.url, value = item) { HistoryItem(item, scope) }
            }
        )*/

        LazyColumn(
            contentPadding = p,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                count = recentItems.itemCount,
                key = recentItems.itemKey { it.url },
                contentType = recentItems.itemContentType { it }
            ) {
                val item = recentItems[it]
                if (item != null) {
                    HistoryItem(item, dao, logo, scope)
                } else {
                    HistoryItemPlaceholder()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryItem(item: RecentModel, dao: HistoryDao, logo: MainLogo, scope: CoroutineScope) {
    var showPopup by remember { mutableStateOf(false) }

    if (showPopup) {
        val onDismiss = { showPopup = false }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.removeNoti, item.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { dao.deleteRecent(item) }
                        onDismiss()
                    }
                ) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.no)) } }
        )
    }

    val dismissState = rememberDismissState(
        confirmValueChange = {
            if (it == DismissValue.DismissedToEnd || it == DismissValue.DismissedToStart) {
                showPopup = true
            }
            false
        }
    )

    SwipeToDismiss(
        state = dismissState,
        background = {
            val direction = dismissState.dismissDirection ?: return@SwipeToDismiss
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    DismissValue.Default -> Color.Transparent
                    DismissValue.DismissedToEnd -> Color.Red
                    DismissValue.DismissedToStart -> Color.Red
                }, label = ""
            )
            val alignment = when (direction) {
                DismissDirection.StartToEnd -> Alignment.CenterStart
                DismissDirection.EndToStart -> Alignment.CenterEnd
            }
            val icon = when (direction) {
                DismissDirection.StartToEnd -> Icons.Default.Delete
                DismissDirection.EndToStart -> Icons.Default.Delete
            }
            val scale by animateFloatAsState(if (dismissState.targetValue == DismissValue.Default) 0.75f else 1f, label = "")

            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.scale(scale)
                )
            }
        },
        dismissContent = {
            var showLoadingDialog by remember { mutableStateOf(false) }

            LoadingDialog(
                showLoadingDialog = showLoadingDialog,
                onDismissRequest = { showLoadingDialog = false }
            )

            val context = LocalContext.current
            val logoDrawable = remember { AppCompatResources.getDrawable(context, logo.logoId) }

            val info = LocalGenericInfo.current
            val navController = LocalNavController.current

            Surface(
                tonalElevation = 4.dp,
                shape = MaterialTheme.shapes.medium,
                onClick = {
                    scope.launch {
                        info.toSource(item.source)
                            ?.getSourceByUrlFlow(item.url)
                            ?.dispatchIo()
                            ?.onStart { showLoadingDialog = true }
                            ?.catch {
                                showLoadingDialog = false
                                context.showErrorToast()
                            }
                            ?.onEach { m ->
                                showLoadingDialog = false
                                navController.navigateToDetails(m)
                            }
                            ?.collect()
                    }
                }
            ) {
                ListItem(
                    headlineContent = { Text(item.title) },
                    overlineContent = { Text(item.source) },
                    supportingContent = { Text(LocalSystemDateTimeFormat.current.format(item.timestamp)) },
                    leadingContent = {
                        GradientImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.imageUrl)
                                .lifecycle(LocalLifecycleOwner.current)
                                .crossfade(true)
                                .size(ComposableUtils.IMAGE_WIDTH_PX, ComposableUtils.IMAGE_HEIGHT_PX)
                                .build(),
                            placeholder = rememberDrawablePainter(logoDrawable),
                            error = rememberDrawablePainter(logoDrawable),
                            contentDescription = item.title,
                            modifier = Modifier
                                .size(ComposableUtils.IMAGE_WIDTH, ComposableUtils.IMAGE_HEIGHT)
                                .clip(MaterialTheme.shapes.medium)
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showPopup = true }) { Icon(imageVector = Icons.Default.Delete, contentDescription = null) }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        info.toSource(item.source)
                                            ?.getSourceByUrlFlow(item.url)
                                            ?.dispatchIo()
                                            ?.onStart { showLoadingDialog = true }
                                            ?.catch {
                                                showLoadingDialog = false
                                                context.showErrorToast()
                                            }
                                            ?.onEach { m ->
                                                showLoadingDialog = false
                                                navController.navigateToDetails(m)
                                            }
                                            ?.collect()
                                    }
                                }
                            ) { Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null) }
                        }
                    }
                )
            }
        }
    )
}

@Composable
private fun HistoryItemPlaceholder() {
    val placeholderColor = contentColorFor(backgroundColor = MaterialTheme.colorScheme.surface)
        .copy(0.1f)
        .compositeOver(MaterialTheme.colorScheme.surface)

    Surface(
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.placeholder(true, color = placeholderColor)
    ) {
        ListItem(
            modifier = Modifier.placeholder(true, color = placeholderColor),
            headlineContent = { Text("Otaku") },
            overlineContent = { Text("Otaku") },
            supportingContent = { Text("Otaku") },
            leadingContent = {
                Surface(shape = MaterialTheme.shapes.medium) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(ComposableUtils.IMAGE_WIDTH, ComposableUtils.IMAGE_HEIGHT)
                    )
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@LightAndDarkPreviews
@Composable
private fun HistoryScreenPreview() {
    PreviewTheme {
        HistoryUi(logo = MockAppIcon)
    }
}

@LightAndDarkPreviews
@Composable
private fun HistoryItemPreview() {
    PreviewTheme {
        HistoryItem(
            item = RecentModel(
                title = "Title",
                description = "Description",
                url = "url",
                imageUrl = "imageUrl",
                source = "MANGA_READ"
            ),
            dao = LocalHistoryDao.current,
            logo = MockAppIcon,
            scope = rememberCoroutineScope()
        )
    }
}

@LightAndDarkPreviews
@Composable
private fun HistoryPlaceholderItemPreview() {
    PreviewTheme {
        HistoryItemPlaceholder()
    }
}