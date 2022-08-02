package com.programmersbox.mangaworld.reader

import android.content.Context
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bumptech.glide.load.model.GlideUrl
import com.github.piasy.biv.BigImageViewer
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.PagerState
import com.google.accompanist.pager.VerticalPager
import com.google.accompanist.pager.rememberPagerState
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.programmersbox.helpfulutils.BuildConfig
import com.programmersbox.helpfulutils.battery
import com.programmersbox.helpfulutils.timeTick
import com.programmersbox.mangaworld.*
import com.programmersbox.mangaworld.R
import com.programmersbox.uiviews.BaseMainActivity
import com.programmersbox.uiviews.utils.*
import com.skydoves.landscapist.glide.GlideImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalPagerApi::class)
@ExperimentalMaterial3Api
@ExperimentalMaterialApi
@ExperimentalComposeUiApi
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
fun ReadView() {

    LifecycleHandle(
        onStop = { BaseMainActivity.showNavBar = true },
        onDestroy = { BaseMainActivity.showNavBar = true },
        onCreate = { BaseMainActivity.showNavBar = false },
        onStart = { BaseMainActivity.showNavBar = false },
        onResume = { BaseMainActivity.showNavBar = false }
    )

    val context = LocalContext.current
    val genericInfo = LocalGenericInfo.current

    val readVm: ReadViewModel = viewModel {
        ReadViewModel(
            handle = createSavedStateHandle(),
            context = context,
            genericInfo = genericInfo
        )
    }

    DisposableEffect(LocalContext.current) {
        val batteryInfo = context.battery {
            readVm.batteryPercent = it.percent
            readVm.batteryInformation.batteryLevel.tryEmit(it.percent)
            readVm.batteryInformation.batteryInfo.tryEmit(it)
        }
        onDispose { context.unregisterReceiver(batteryInfo) }
    }

    val scope = rememberCoroutineScope()
    val swipeState = rememberSwipeRefreshState(isRefreshing = readVm.isLoadingPages.value)

    val pages = readVm.pageList

    LaunchedEffect(readVm.pageList) { BigImageViewer.prefetch(*readVm.pageList.fastMap(Uri::parse).toTypedArray()) }

    val listOrPager by context.listOrPager.collectAsState(initial = true)

    val pagerState = rememberPagerState()
    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { if (listOrPager) listState.firstVisibleItemIndex else pagerState.currentPage } }

    val paddingPage by context.pagePadding.collectAsState(initial = 4)
    var settingsPopup by remember { mutableStateOf(false) }

    if (settingsPopup) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            onDismissRequest = { settingsPopup = false },
            title = { Text(stringResource(R.string.settings)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SliderSetting(
                        scope = scope,
                        settingIcon = Icons.Default.BatteryAlert,
                        settingTitle = R.string.battery_alert_percentage,
                        settingSummary = R.string.battery_default,
                        preference = BATTERY_PERCENT,
                        initialValue = runBlocking { context.dataStore.data.first()[BATTERY_PERCENT] ?: 20 },
                        range = 1f..100f
                    )
                    Divider()
                    SliderSetting(
                        scope = scope,
                        settingIcon = Icons.Default.FormatLineSpacing,
                        settingTitle = R.string.reader_padding_between_pages,
                        settingSummary = R.string.default_padding_summary,
                        preference = PAGE_PADDING,
                        initialValue = runBlocking { context.dataStore.data.first()[PAGE_PADDING] ?: 4 },
                        range = 0f..10f
                    )
                    Divider()
                    val activity = LocalActivity.current
                    SwitchSetting(
                        settingTitle = { Text(stringResource(R.string.list_or_pager_title)) },
                        summaryValue = { Text(stringResource(R.string.list_or_pager_description)) },
                        value = listOrPager,
                        updateValue = { scope.launch { activity.updatePref(LIST_OR_PAGER, it) } },
                        settingIcon = { Icon(if (listOrPager) Icons.Default.List else Icons.Default.Pages, null) }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { settingsPopup = false }) { Text(stringResource(R.string.ok)) } }
        )
    }

    val activity = LocalActivity.current

    fun showToast() {
        activity.runOnUiThread { Toast.makeText(context, R.string.addedChapterItem, Toast.LENGTH_SHORT).show() }
    }

    val listShowItems = (listState.isScrolledToTheEnd() || listState.isScrolledToTheBeginning()) && listOrPager
    val pagerShowItems = (pagerState.currentPage == 0 || pagerState.currentPage >= pages.size) && !listOrPager

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { listState.scrollToItem(it) }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { pagerState.scrollToPage(it) }
    }

    val showItems = readVm.showInfo || listShowItems || pagerShowItems

    val topAppBarScrollState = rememberTopAppBarState()
    val scrollBehavior = remember { TopAppBarDefaults.pinnedScrollBehavior(topAppBarScrollState) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val sheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)

    BackHandler(drawerState.isOpen || sheetState.isVisible) {
        scope.launch {
            when {
                drawerState.isOpen -> drawerState.close()
                sheetState.isVisible -> sheetState.hide()
            }
        }
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetContent = {
            val sheetTopAppBarScrollState = rememberTopAppBarState()
            val sheetScrollBehavior = remember { TopAppBarDefaults.pinnedScrollBehavior(sheetTopAppBarScrollState) }
            Scaffold(
                modifier = Modifier.nestedScroll(sheetScrollBehavior.nestedScrollConnection),
                topBar = {
                    SmallTopAppBar(
                        scrollBehavior = sheetScrollBehavior,
                        title = { Text(readVm.list.getOrNull(readVm.currentChapter)?.name.orEmpty()) },
                        actions = { PageIndicator(Modifier, currentPage + 1, pages.size) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { sheetState.hide() } }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    )
                },
                bottomBar = {
                    if (BuildConfig.BUILD_TYPE == "release") {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            factory = {
                                AdView(it).apply {
                                    setAdSize(AdSize.BANNER)
                                    adUnitId = context.getString(R.string.ad_unit_id)
                                    loadAd(readVm.ad)
                                }
                            }
                        )
                    }
                }
            ) { p ->
                if (sheetState.isVisible) {
                    LazyVerticalGrid(
                        columns = adaptiveGridCell(),
                        contentPadding = p,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(pages) { i, it ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .size(ComposableUtils.IMAGE_WIDTH, ComposableUtils.IMAGE_HEIGHT)
                                    .border(
                                        animateDpAsState(if (currentPage == i) 5.dp else 0.dp).value,
                                        color = animateColorAsState(
                                            if (currentPage == i) MaterialTheme.colorScheme.primary
                                            else Color.Transparent
                                        ).value
                                    )
                                    .clickable {
                                        scope.launch {
                                            if (currentPage == i) sheetState.hide()
                                            if (listOrPager) listState.animateScrollToItem(i) else pagerState.animateScrollToPage(i)
                                        }
                                    }
                            ) {
                                GlideImage(
                                    imageModel = it,
                                    contentScale = ContentScale.Crop,
                                    loading = {
                                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.Center)
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black
                                                ),
                                                startY = 50f
                                            )
                                        )
                                ) {
                                    Text(
                                        (i + 1).toString(),
                                        style = MaterialTheme
                                            .typography
                                            .bodyLarge
                                            .copy(textAlign = TextAlign.Center, color = Color.White),
                                        maxLines = 2,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                val drawerTopAppBarScrollState = rememberTopAppBarState()
                val drawerScrollBehavior = remember { TopAppBarDefaults.pinnedScrollBehavior(drawerTopAppBarScrollState) }
                Scaffold(
                    modifier = Modifier.nestedScroll(drawerScrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            scrollBehavior = drawerScrollBehavior,
                            title = { Text(readVm.title) },
                            actions = { PageIndicator(Modifier, readVm.list.size - readVm.currentChapter, readVm.list.size) }
                        )
                    },
                    bottomBar = {
                        if (BuildConfig.BUILD_TYPE == "release") {
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                factory = {
                                    AdView(it).apply {
                                        setAdSize(AdSize.BANNER)
                                        adUnitId = context.getString(R.string.ad_unit_id)
                                        loadAd(readVm.ad)
                                    }
                                }
                            )
                        }
                    }
                ) { p ->
                    if (drawerState.isOpen) {
                        LazyColumn(
                            state = rememberLazyListState(readVm.currentChapter.coerceIn(0, readVm.list.lastIndex)),
                            contentPadding = p,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(readVm.list) { i, c ->

                                var showChangeChapter by remember { mutableStateOf(false) }

                                if (showChangeChapter) {
                                    AlertDialog(
                                        onDismissRequest = { showChangeChapter = false },
                                        title = { Text(stringResource(R.string.changeToChapter, c.name)) },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    showChangeChapter = false
                                                    readVm.currentChapter = i
                                                    readVm.addChapterToWatched(readVm.currentChapter, ::showToast)
                                                }
                                            ) { Text(stringResource(R.string.yes)) }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showChangeChapter = false }) { Text(stringResource(R.string.no)) }
                                        }
                                    )
                                }

                                WrapHeightNavigationDrawerItem(
                                    modifier = Modifier
                                        .padding(bottom = 4.dp)
                                        .padding(horizontal = 4.dp),
                                    label = { Text(c.name) },
                                    selected = readVm.currentChapter == i,
                                    onClick = { showChangeChapter = true },
                                    shape = RoundedCornerShape(8.0.dp)//MaterialTheme.shapes.medium
                                )

                                if (i < readVm.list.lastIndex) Divider()
                            }
                        }
                    }
                }
            },
            gesturesEnabled = readVm.list.size > 1
        ) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    AnimatedVisibility(
                        visible = showItems,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        TopBar(
                            scrollBehavior = scrollBehavior,
                            pages = pages,
                            currentPage = currentPage,
                            vm = readVm
                        )
                    }
                },
                bottomBar = {
                    AnimatedVisibility(
                        visible = showItems,
                        enter = slideInVertically { it / 2 } + fadeIn(),
                        exit = slideOutVertically { it / 2 } + fadeOut()
                    ) {
                        BottomBar(
                            scrollBehavior = scrollBehavior,
                            onPageSelectClick = { scope.launch { sheetState.show() } },
                            onSettingsClick = { settingsPopup = true },
                            chapterChange = ::showToast,
                            vm = readVm
                        )
                    }
                }
            ) { paddingValues ->
                SwipeRefresh(
                    state = swipeState,
                    onRefresh = { readVm.refresh() },
                    indicatorPadding = paddingValues
                ) {
                    val spacing = LocalContext.current.dpToPx(paddingPage).dp
                    Crossfade(targetState = listOrPager) {
                        if (it) ListView(listState, paddingValues, pages, readVm, spacing) { readVm.showInfo = !readVm.showInfo }
                        else PagerView(pagerState, PaddingValues(0.dp), pages, readVm, spacing) { readVm.showInfo = !readVm.showInfo }

                        //TODO: Add a new type with a recyclerview for listview only

                    }
                }
            }
        }
    }
}

@Composable
fun ListView(
    listState: LazyListState,
    contentPadding: PaddingValues,
    pages: List<String>,
    readVm: ReadViewModel,
    itemSpacing: Dp,
    onClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
        contentPadding = contentPadding
    ) { reader(pages, readVm, onClick) }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun PagerView(pagerState: PagerState, contentPadding: PaddingValues, pages: List<String>, vm: ReadViewModel, itemSpacing: Dp, onClick: () -> Unit) {
    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        count = pages.size + 1,
        itemSpacing = itemSpacing,
        contentPadding = contentPadding,
        key = { it }
    ) { page -> pages.getOrNull(page)?.let { ChapterPage(it, vm.isDownloaded, onClick, vm.headers, ContentScale.Fit) } ?: LastPageReached(vm = vm) }
}

@Composable
private fun LastPageReached(vm: ReadViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(id = R.string.lastPage),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
        )
        if (vm.currentChapter <= 0) {
            Text(
                stringResource(id = R.string.reachedLastChapter),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
            )
        }
        if (BuildConfig.BUILD_TYPE == "release") {
            val context = LocalContext.current
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = {
                    AdView(it).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = context.getString(R.string.ad_unit_id)
                        loadAd(vm.ad)
                    }
                }
            )
        }
    }
}

private fun LazyListScope.reader(pages: List<String>, vm: ReadViewModel, onClick: () -> Unit) {

    /*items(pages) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val state = rememberTransformableState { zoomChange, offsetChange, _ ->
            scale = (scale * zoomChange).coerceAtLeast(1f)
            offset += offsetChange
        }

        Box(
            modifier = Modifier
                // add transformable to listen to multitouch transformation events
                // after offset
                .transformable(state = state)
                //TODO: For some reason this is causing the weird performance issue
                // it is a known issue: https://issuetracker.google.com/issues/204328131
                // when that gets resolved, look into adding back the nestedScrollConnection by scrollBehavior
                .combinedClickable(
                    onClick = {
                        showInfo = !showInfo
                        if (!showInfo) {
                            toolbarOffsetHeightPx.value = -toolbarHeightPx
                            topBarOffsetHeightPx.value = -topBarHeightPx
                        }
                    },
                    onDoubleClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    onLongClick = {},
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
        ) {
            Text(
                stringResource(R.string.doubleTapToReset),
                modifier = Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center
            )
            val scaleAnim = animateFloatAsState(scale).value
            val (x, y) = animateOffsetAsState(targetValue = offset).value

            *//*GlideImage(
                    imageModel = it,
                    contentScale = ContentScale.FillWidth,
                    loading = {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = M3MaterialTheme.colorScheme.primary
                        )
                    },
                    success = {
                        val byteArray = remember {
                            val stream = ByteArrayOutputStream()
                            it.drawable?.toBitmap()?.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            val b = stream.toByteArray()
                            ByteArrayInputStream(b)
                        }
                        SubSampledImage(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = ComposableUtils.IMAGE_HEIGHT)
                                .align(Alignment.Center)
                                .clipToBounds(),
                            imageSource = rememberInputStreamImageSource(inputStream = byteArray)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ComposableUtils.IMAGE_HEIGHT)
                        .align(Alignment.Center)
                        .clipToBounds()
                )*//*

                GlideImage(
                    imageModel = it,
                    contentScale = ContentScale.FillWidth,
                    loading = {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = M3MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ComposableUtils.IMAGE_HEIGHT)
                        .align(Alignment.Center)
                        .clipToBounds()
                        .graphicsLayer(
                            scaleX = scaleAnim,
                            scaleY = scaleAnim,
                            translationX = x,
                            translationY = y
                        )
                )
            }
        }*/

    items(pages, key = { it }, contentType = { it }) { ChapterPage(it, vm.isDownloaded, onClick, vm.headers, ContentScale.FillWidth) }
    item { LastPageReached(vm = vm) }
}

@Composable
private fun ChapterPage(
    chapterLink: String,
    isDownloaded: Boolean,
    openCloseOverlay: () -> Unit,
    headers: Map<String, String>,
    contentScale: ContentScale
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .requiredHeightIn(min = 100.dp),
        contentAlignment = Alignment.Center
    ) {
        ZoomableImage(
            modifier = Modifier.fillMaxWidth(),
            painter = chapterLink,
            onClick = openCloseOverlay,
            headers = headers,
            contentScale = contentScale,
            isDownloaded = isDownloaded
        )
    }
}

@Composable
private fun ZoomableImage(
    modifier: Modifier = Modifier,
    painter: String,
    isDownloaded: Boolean,
    contentScale: ContentScale = ContentScale.Fit,
    headers: Map<String, String>,
    onClick: () -> Unit = {}
) {
    var centerPoint by remember { mutableStateOf(Offset.Zero) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val scaleAnim by animateFloatAsState(
        targetValue = scale
    ) {
        if (scale == 1f) offset = Offset.Zero
    }

    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale *= zoomChange
        scale = scale.coerceIn(1f, 5f)

        offset += offsetChange
        offset = clampOffset(centerPoint, offset, scale)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .onGloballyPositioned { coordinates ->
                val size = coordinates.size.toSize() / 2.0f
                centerPoint = Offset(size.width, size.height)
            }
            .transformable(state)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = {
                        when {
                            scale > 2f -> {
                                scale = 1f
                            }
                            else -> {
                                scale = 3f

                                offset = (centerPoint - it) * (scale - 1)
                                offset = clampOffset(centerPoint, offset, scale)
                            }
                        }

                    }
                )
            }
    ) {
        val scope = rememberCoroutineScope()
        var showTheThing by remember { mutableStateOf(true) }

        if (showTheThing) {
            GlideImage(
                imageModel = if (isDownloaded) painter else remember(painter) { GlideUrl(painter) { headers } },
                contentScale = contentScale,
                loading = { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) },
                failure = {
                    Text(
                        stringResource(R.string.pressToRefresh),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clickable {
                                scope.launch {
                                    showTheThing = false
                                    delay(1000)
                                    showTheThing = true
                                }
                            }
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .heightIn(min = ComposableUtils.IMAGE_HEIGHT)
                    .align(Alignment.Center)
                    .clipToBounds()
                    .graphicsLayer {
                        translationX = offset.x
                        translationY = offset.y

                        scaleX = scaleAnim
                        scaleY = scaleAnim
                    }
            )
        }
    }
}

private fun clampOffset(centerPoint: Offset, offset: Offset, scale: Float): Offset {
    val maxPosition = centerPoint * (scale - 1)

    return offset.copy(
        x = offset.x.coerceIn(-maxPosition.x, maxPosition.x),
        y = offset.y.coerceIn(-maxPosition.y, maxPosition.y)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalAnimationApi
@Composable
private fun TopBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior,
    pages: List<String>,
    currentPage: Int,
    vm: ReadViewModel
) {
    CenterAlignedTopAppBar(
        scrollBehavior = scrollBehavior,
        modifier = modifier,
        navigationIcon = {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    vm.batteryIcon.composeIcon,
                    contentDescription = null,
                    tint = animateColorAsState(
                        if (vm.batteryColor == Color.White) MaterialTheme.colorScheme.onSurface
                        else vm.batteryColor
                    ).value
                )
                Text(
                    "${vm.batteryPercent.toInt()}%",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        },
        title = {
            var time by remember { mutableStateOf(System.currentTimeMillis()) }

            val activity = LocalActivity.current

            DisposableEffect(LocalContext.current) {
                val timeReceiver = activity.timeTick { _, _ -> time = System.currentTimeMillis() }
                onDispose { activity.unregisterReceiver(timeReceiver) }
            }

            Text(
                DateFormat.getTimeFormat(LocalContext.current).format(time).toString(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(4.dp)
            )
        },
        actions = {
            PageIndicator(
                Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically),
                currentPage + 1,
                pages.size
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomBar(
    modifier: Modifier = Modifier,
    vm: ReadViewModel,
    scrollBehavior: TopAppBarScrollBehavior,
    onPageSelectClick: () -> Unit,
    onSettingsClick: () -> Unit,
    chapterChange: () -> Unit
) {
    BottomAppBar(
        modifier = modifier,
        containerColor = TopAppBarDefaults.centerAlignedTopAppBarColors()
            .containerColor(scrollBehavior.state.collapsedFraction).value,
        contentColor = TopAppBarDefaults.centerAlignedTopAppBarColors()
            .titleContentColor(scrollBehavior.state.collapsedFraction).value
    ) {
        val prevShown = vm.currentChapter < vm.list.lastIndex
        val nextShown = vm.currentChapter > 0

        AnimatedVisibility(
            visible = prevShown && vm.list.size > 1,
            enter = expandHorizontally(expandFrom = Alignment.Start),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start)
        ) {
            PreviousButton(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .weight(
                        when {
                            prevShown && nextShown -> 8f / 3f
                            prevShown -> 4f
                            else -> 4f
                        }
                    ),
                previousChapter = chapterChange,
                vm = vm
            )
        }

        GoBackButton(
            modifier = Modifier
                .weight(
                    animateFloatAsState(
                        when {
                            prevShown && nextShown -> 8f / 3f
                            prevShown || nextShown -> 4f
                            else -> 8f
                        }
                    ).value
                )
        )

        AnimatedVisibility(
            visible = nextShown && vm.list.size > 1,
            enter = expandHorizontally(),
            exit = shrinkHorizontally()
        ) {
            NextButton(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .weight(
                        when {
                            prevShown && nextShown -> 8f / 3f
                            nextShown -> 4f
                            else -> 4f
                        }
                    ),
                nextChapter = chapterChange,
                vm = vm
            )
        }
        //The three buttons above will equal 8f
        //So these two need to add up to 2f
        IconButton(
            onClick = onPageSelectClick,
            modifier = Modifier.weight(1f)
        ) { Icon(Icons.Default.GridOn, null) }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.weight(1f)
        ) { Icon(Icons.Default.Settings, null) }
    }
}

@Composable
@ExperimentalMaterial3Api
private fun WrapHeightNavigationDrawerItem(
    label: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
    shape: Shape = CircleShape,
    colors: NavigationDrawerItemColors = NavigationDrawerItemDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    Surface(
        shape = shape,
        color = colors.containerColor(selected).value,
        modifier = modifier
            .heightIn(min = 56.dp)
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                interactionSource = interactionSource,
                role = Role.Tab,
                indication = null
            )
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                val iconColor = colors.iconColor(selected).value
                CompositionLocalProvider(LocalContentColor provides iconColor, content = icon)
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.weight(1f)) {
                val labelColor = colors.textColor(selected).value
                CompositionLocalProvider(LocalContentColor provides labelColor, content = label)
            }
            if (badge != null) {
                Spacer(Modifier.width(12.dp))
                val badgeColor = colors.badgeColor(selected).value
                CompositionLocalProvider(LocalContentColor provides badgeColor, content = badge)
            }
        }
    }
}

@ExperimentalAnimationApi
@Composable
private fun PageIndicator(modifier: Modifier = Modifier, currentPage: Int, pageCount: Int) {
    Text(
        "$currentPage/$pageCount",
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
    )
}

private fun Context.dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

private fun LazyListState.isScrolledToTheEnd() = layoutInfo.visibleItemsInfo.lastOrNull()?.index == layoutInfo.totalItemsCount - 1
private fun LazyListState.isScrolledToTheBeginning() = layoutInfo.visibleItemsInfo.firstOrNull()?.index == 0

@Composable
private fun GoBackButton(modifier: Modifier = Modifier) {
    val navController = LocalNavController.current
    OutlinedButton(
        onClick = { navController.popBackStack() },
        modifier = modifier,
        border = BorderStroke(ButtonDefaults.outlinedButtonBorder.width, MaterialTheme.colorScheme.primary)
    ) { Text(stringResource(id = R.string.goBack), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
}

@Composable
private fun NextButton(modifier: Modifier = Modifier, vm: ReadViewModel, nextChapter: () -> Unit) {
    Button(
        onClick = { vm.addChapterToWatched(--vm.currentChapter, nextChapter) },
        modifier = modifier
    ) { Text(stringResource(id = R.string.loadNextChapter)) }
}

@Composable
private fun PreviousButton(modifier: Modifier = Modifier, vm: ReadViewModel, previousChapter: () -> Unit) {
    TextButton(
        onClick = { vm.addChapterToWatched(++vm.currentChapter, previousChapter) },
        modifier = modifier
    ) { Text(stringResource(id = R.string.loadPreviousChapter)) }
}

@Composable
private fun SliderSetting(
    scope: CoroutineScope,
    settingIcon: ImageVector,
    @StringRes settingTitle: Int,
    @StringRes settingSummary: Int,
    preference: Preferences.Key<Int>,
    initialValue: Int,
    range: ClosedFloatingPointRange<Float>
) {
    var sliderValue by remember { mutableStateOf(initialValue.toFloat()) }

    val activity = LocalActivity.current

    SliderSetting(
        sliderValue = sliderValue,
        settingTitle = { Text(stringResource(id = settingTitle)) },
        settingSummary = { Text(stringResource(id = settingSummary)) },
        range = range,
        updateValue = {
            sliderValue = it
            scope.launch { activity.updatePref(preference, sliderValue.toInt()) }
        },
        settingIcon = { Icon(settingIcon, null) }
    )
}