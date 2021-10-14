package com.programmersbox.animeworld

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rxjava2.subscribeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.app.MediaRouteDialogFactory
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.material.placeholder
import com.google.accompanist.placeholder.material.shimmer
import com.google.android.gms.cast.framework.CastContext
import com.obsez.android.lib.filechooser.ChooserDialog
import com.programmersbox.anime_sources.ShowApi
import com.programmersbox.anime_sources.Sources
import com.programmersbox.anime_sources.anime.WcoStream
import com.programmersbox.anime_sources.utilities.Qualities
import com.programmersbox.anime_sources.utilities.getQualityFromName
import com.programmersbox.animeworld.cast.ExpandedControlsActivity
import com.programmersbox.favoritesdatabase.DbModel
import com.programmersbox.helpfulutils.requestPermissions
import com.programmersbox.helpfulutils.runOnUIThread
import com.programmersbox.helpfulutils.sharedPrefNotNullDelegate
import com.programmersbox.models.*
import com.programmersbox.sharedutils.AppUpdate
import com.programmersbox.sharedutils.MainLogo
import com.programmersbox.uiviews.GenericInfo
import com.programmersbox.uiviews.SettingsDsl
import com.programmersbox.uiviews.utils.*
import com.tonyodev.fetch2.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.koin.dsl.module
import kotlin.collections.set


val appModule = module {
    single<GenericInfo> { GenericAnime(get()) }
    single { MainLogo(R.mipmap.ic_launcher) }
    single { NotificationLogo(R.mipmap.ic_launcher_foreground) }
}

class GenericAnime(val context: Context) : GenericInfo {

    private var Context.wcoRecent by sharedPrefNotNullDelegate(true)

    private val disposable = CompositeDisposable()

    override val apkString: AppUpdate.AppUpdates.() -> String? get() = { anime_file }

    override fun chapterOnClick(model: ChapterModel, allChapters: List<ChapterModel>, infoModel: InfoModel, context: Context) {
        if ((model.source as? ShowApi)?.canPlay == false) {
            Toast.makeText(context, context.getString(R.string.source_no_stream, model.source.serviceName), Toast.LENGTH_SHORT).show()
            return
        }
        getEpisodes(
            R.string.source_no_stream,
            model,
            context
        ) {
            if (MainActivity.cast.isCastActive()) {
                MainActivity.cast.loadUrl(
                    it.link,
                    infoModel.title,
                    model.name,
                    infoModel.imageUrl,
                    it.headers
                )
            } else {
                MainActivity.activity.startActivity(
                    Intent(context, VideoPlayerActivity::class.java).apply {
                        putExtra("showPath", it.link)
                        putExtra("showName", model.name)
                        putExtra("referer", it.headers["referer"])
                        putExtra("downloadOrStream", false)
                    }
                )
            }
        }
    }

    private val fetch = Fetch.getDefaultInstance()

    override fun downloadChapter(model: ChapterModel, allChapters: List<ChapterModel>, infoModel: InfoModel, context: Context) {
        if ((model.source as? ShowApi)?.canDownload == false) {
            Toast.makeText(
                context,
                context.getString(R.string.source_no_download, model.source.serviceName),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        MainActivity.activity.requestPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE) { p ->
            if (p.isGranted) {
                Toast.makeText(context, R.string.downloading_dots_no_percent, Toast.LENGTH_SHORT).show()
                getEpisodes(
                    R.string.source_no_download,
                    model,
                    context
                ) { fetchIt(it, model) }
            }
        }
    }

    private fun fetchIt(i: Storage, ep: ChapterModel) {

        try {

            fetch.setGlobalNetworkType(NetworkType.ALL)

            fun getNameFromUrl(url: String): String {
                return Uri.parse(url).lastPathSegment?.let { if (it.isNotEmpty()) it else ep.name } ?: ep.name
            }

            val requestList = arrayListOf<Request>()

            val filePath = context.folderLocation + getNameFromUrl(i.link!!) + "${ep.name}.mp4"
            val request = Request(i.link!!, filePath)
            request.priority = Priority.HIGH
            request.networkType = NetworkType.ALL
            request.enqueueAction = EnqueueAction.REPLACE_EXISTING
            request.extras.map.toProperties()["URL_INTENT"] = ep.url
            request.extras.map.toProperties()["NAME_INTENT"] = ep.name

            request.addHeader("Accept-Language", "en-US,en;q=0.5")
            request.addHeader("User-Agent", "\"Mozilla/5.0 (Windows NT 10.0; WOW64; rv:40.0) Gecko/20100101 Firefox/40.0\"")
            request.addHeader("Accept", "text/html,video/mp4,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            request.addHeader("Access-Control-Allow-Origin", "*")
            request.addHeader("Referer", i.headers["referer"] ?: "http://thewebsite.com")
            request.addHeader("Connection", "keep-alive")

            i.headers.entries.forEach { request.headers[it.key] = it.value }

            requestList.add(request)

            fetch.enqueue(requestList) {}
        } catch (e: Exception) {
            MainActivity.activity.runOnUiThread { Toast.makeText(context, R.string.something_went_wrong, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun getEpisodes(
        errorId: Int,
        model: ChapterModel,
        context: Context,
        onAction: (Storage) -> Unit
    ) {
        model.getChapterInfo()
            .doOnError { runOnUIThread { Toast.makeText(context, R.string.something_went_wrong, Toast.LENGTH_SHORT).show() } }
            .onErrorReturnItem(emptyList())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy { c ->
                when {
                    c.size == 1 -> {
                        when (model.source) {
                            else -> c.firstOrNull()?.let { onAction(it) }
                        }
                    }
                    c.isNotEmpty() -> {
                        ListBottomSheet(
                            title = context.getString(R.string.choose_quality_for, model.name),
                            list = c,
                            onClick = { onAction(it) }
                        ) {
                            ListBottomSheetItemModel(
                                primaryText = it.quality.orEmpty(),
                                icon = when (getQualityFromName(it.quality.orEmpty())) {
                                    Qualities.Unknown -> Icons.Default.DeviceUnknown
                                    Qualities.P360 -> Icons.Default._360
                                    Qualities.P480 -> Icons.Default._4mp
                                    Qualities.P720 -> Icons.Default._7mp
                                    Qualities.P1080 -> Icons.Default._10mp
                                    Qualities.P1440 -> Icons.Default._1k
                                    Qualities.P2160 -> Icons.Default._4k
                                    else -> Icons.Default.DeviceUnknown
                                }
                            )
                        }.show(MainActivity.activity.supportFragmentManager, "qualityChooser")
                    }
                    else -> {
                        Toast.makeText(context, context.getString(errorId, model.source.serviceName), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addTo(disposable)
    }

    override fun sourceList(): List<ApiService> = Sources.values().toList()

    override fun searchList(): List<ApiService> = Sources.searchSources

    override fun toSource(s: String): ApiService? = try {
        Sources.valueOf(s)
    } catch (e: IllegalArgumentException) {
        null
    }

    override fun customPreferences(preferenceScreen: SettingsDsl) {

        preferenceScreen.viewSettings { s, it ->
            it.addPreference(
                Preference(it.context).apply {
                    title = context.getString(R.string.video_menu_title)
                    icon = ContextCompat.getDrawable(it.context, R.drawable.ic_baseline_video_library_24)
                    setOnPreferenceClickListener {
                        s.findNavController()
                            .navigate(ViewVideosFragment::class.java.hashCode(), null, SettingsDsl.customAnimationOptions)
                        true
                    }
                }
            )

            val casting = Preference(it.context).apply {
                title = context.getString(R.string.cast_menu_title)
                icon = ContextCompat.getDrawable(it.context, R.drawable.ic_baseline_cast_24)
                setOnPreferenceClickListener {
                    if (MainActivity.cast.isCastActive()) {
                        context.startActivity(Intent(context, ExpandedControlsActivity::class.java))
                    } else {
                        MediaRouteDialogFactory.getDefault().onCreateChooserDialogFragment()
                            .also { it.routeSelector = CastContext.getSharedInstance(context).mergedSelector }
                            .show(MainActivity.activity.supportFragmentManager, "media_chooser")
                    }
                    true
                }
            }

            MainActivity.cast.sessionConnected()
                .subscribe(casting::setVisible)
                .addTo(disposable)

            MainActivity.cast.sessionStatus()
                .map { if (it) R.drawable.ic_baseline_cast_connected_24 else R.drawable.ic_baseline_cast_24 }
                .subscribe(casting::setIcon)
                .addTo(disposable)

            it.addPreference(casting)

            it.addPreference(
                Preference(it.context).apply {
                    title = context.getString(R.string.downloads_menu_title)
                    icon = ContextCompat.getDrawable(it.context, R.drawable.ic_baseline_download_24)
                    setOnPreferenceClickListener {
                        s.findNavController()
                            .navigate(DownloadViewerFragment::class.java.hashCode(), null, SettingsDsl.customAnimationOptions)
                        true
                    }
                }
            )
        }

        preferenceScreen.generalSettings { _, it ->
            it.addPreference(
                SwitchPreference(it.context).apply {
                    title = context.getString(R.string.wco_recent_title)
                    summary = context.getString(R.string.wco_recent_info)
                    key = "wco_recent"
                    isChecked = context.wcoRecent
                    setOnPreferenceChangeListener { _, newValue ->
                        WcoStream.RECENT_TYPE = newValue as Boolean
                        context.wcoRecent = newValue
                        true
                    }
                    icon = ContextCompat.getDrawable(it.context, R.drawable.ic_baseline_article_24)
                    sourcePublish.subscribe { api ->
                        isVisible = api == Sources.WCO_CARTOON || api == Sources.WCO_DUBBED ||
                                api == Sources.WCO_MOVIES || api == Sources.WCO_SUBBED || api == Sources.WCO_OVA
                    }
                        .addTo(disposable)
                }
            )

            it.addPreference(
                Preference(it.context).apply {
                    title = context.getString(R.string.folder_location)
                    summary = it.context.folderLocation
                    icon = ContextCompat.getDrawable(it.context, R.drawable.ic_baseline_folder_24)
                    setOnPreferenceClickListener {
                        MainActivity.activity.requestPermissions(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ) {
                            if (it.isGranted) {
                                ChooserDialog(context)
                                    .withIcon(R.mipmap.ic_launcher)
                                    .withResources(R.string.choose_a_directory, R.string.chooseText, R.string.cancelText)
                                    .withFilter(true, false)
                                    .withStartFile(context.folderLocation)
                                    .enableOptions(true)
                                    .withChosenListener { dir, _ ->
                                        context.folderLocation = "$dir/"
                                        println(dir)
                                        summary = context.folderLocation
                                    }
                                    .build()
                                    .show()
                            }
                        }
                        true
                    }
                }
            )
        }

        preferenceScreen.navigationSetup {
            it.findNavController()
                .graph
                .addDestination(
                    FragmentNavigator(it.requireContext(), it.childFragmentManager, R.id.setting_nav).createDestination().apply {
                        id = DownloadViewerFragment::class.java.hashCode()
                        className = DownloadViewerFragment::class.java.name
                    }
                )

            it.findNavController()
                .graph
                .addDestination(
                    FragmentNavigator(it.requireContext(), it.childFragmentManager, R.id.setting_nav).createDestination().apply {
                        id = ViewVideosFragment::class.java.hashCode()
                        className = ViewVideosFragment::class.java.name
                    }
                )
        }

    }

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    override fun DetailActions(infoModel: InfoModel, tint: Color) {
        val showCast by MainActivity.cast.sessionConnected()
            .subscribeAsState(initial = true)

        AnimatedVisibility(
            visible = showCast,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            AndroidView(
                modifier = Modifier.animateContentSize(),
                factory = { context ->
                    MediaRouteButton(context).apply {
                        MainActivity.cast.showIntroductoryOverlay(this)
                        MainActivity.cast.setMediaRouteMenu(context, this)
                    }
                }
            )
        }
    }

    @ExperimentalMaterialApi
    @Composable
    override fun ComposeShimmerItem() {
        LazyColumn {
            items(10) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .placeholder(true, highlight = PlaceholderHighlight.shimmer())
                    ) {
                        Icon(
                            Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )

                        Text(
                            "",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(5.dp)
                        )
                    }
                }
            }
        }
    }

    @ExperimentalAnimationApi
    @ExperimentalMaterialApi
    @ExperimentalFoundationApi
    @Composable
    override fun ItemListView(
        list: List<ItemModel>,
        favorites: List<DbModel>,
        listState: LazyListState,
        onClick: (ItemModel) -> Unit
    ) {
        val animated by updateAnimatedItemsState(newList = list)
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            animatedItems(
                animated,
                enterTransition = fadeIn(),
                exitTransition = fadeOut()
            ) {
                Card(
                    onClick = { onClick(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 5.dp),
                    elevation = 5.dp
                ) {
                    ListItem(
                        icon = {
                            Icon(
                                if (favorites.fastAny { f -> f.url == it.url }) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                            )
                        },
                        text = { Text(it.title) },
                        overlineText = { Text(it.source.serviceName) },
                        secondaryText = if (it.description.isNotEmpty()) {
                            { Text(it.description) }
                        } else null
                    )
                }
            }
        }
    }
}
