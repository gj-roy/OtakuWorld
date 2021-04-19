package com.programmersbox.animeworld

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.obsez.android.lib.filechooser.ChooserDialog
import com.programmersbox.anime_sources.Sources
import com.programmersbox.anime_sources.anime.Movies
import com.programmersbox.anime_sources.anime.Torrents
import com.programmersbox.anime_sources.anime.Yts
import com.programmersbox.animeworld.ytsdatabase.Torrent
import com.programmersbox.gsonutils.fromJson
import com.programmersbox.helpfulutils.requestPermissions
import com.programmersbox.models.ApiService
import com.programmersbox.models.ChapterModel
import com.programmersbox.models.sourcePublish
import com.programmersbox.uiviews.BaseListFragment
import com.programmersbox.uiviews.BaseMainActivity
import com.programmersbox.uiviews.ItemListAdapter
import com.programmersbox.uiviews.SettingsDsl
import com.programmersbox.uiviews.utils.currentService
import com.tonyodev.fetch2.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : BaseMainActivity() {

    companion object {
        private const val VIEW_DOWNLOADS = "animeworld://view_downloads"
        private const val VIEW_VIDEOS = "animeworld://view_videos"
        private lateinit var activity: MainActivity
    }

    override fun onCreate() {

        activity = this

        Notifications.setup(this)

        when (intent.data) {
            Uri.parse(VIEW_DOWNLOADS) -> openDownloads()
            Uri.parse(VIEW_VIDEOS) -> openVideos()
        }

        if (currentService == null) {
            sourcePublish.onNext(Sources.GOGOANIME)
            currentService = Sources.GOGOANIME.serviceName
        }

    }

    override val showMiddleChapterButton: Boolean
        get() = true

    override val apkString: String
        get() = "animeworld-debug.apk"

    override fun createAdapter(context: Context, baseListFragment: BaseListFragment): ItemListAdapter<RecyclerView.ViewHolder> =
        (AnimeAdapter(context, baseListFragment) as ItemListAdapter<RecyclerView.ViewHolder>)

    override fun createLayoutManager(context: Context): RecyclerView.LayoutManager = LinearLayoutManager(context)

    override fun downloadChapter(chapterModel: ChapterModel, title: String) {
        GlobalScope.launch {
            val link = chapterModel.getChapterInfo().blockingGet().firstOrNull()?.link
            runOnUiThread {
                startActivity(
                    Intent(this@MainActivity, VideoPlayerActivity::class.java).apply {
                        putExtra("showPath", link)
                        putExtra("showName", chapterModel.name)
                        putExtra("downloadOrStream", true)
                    }
                )
            }
        }
    }

    private val fetch = Fetch.getDefaultInstance()

    override fun chapterOnClick(model: ChapterModel, allChapters: List<ChapterModel>, context: Context) {
        requestPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE) {
            if (it.isGranted) {
                Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show()

                when (model.source) {
                    Yts -> {

                        val f = model.extras["torrents"].toString().fromJson<Torrents>()?.let {
                            val m = model.extras["info"].toString().fromJson<Movies>()
                            Torrent(
                                title = m?.title.orEmpty(),
                                banner_url = m?.background_image.orEmpty(),
                                url = it.url.orEmpty(),
                                hash = it.hash.orEmpty(),
                                quality = it.quality.orEmpty(),
                                type = it.type.orEmpty(),
                                seeds = it.seeds?.toInt() ?: 0,
                                peers = it.peers?.toInt() ?: 0,
                                size_pretty = it.size.orEmpty(),
                                size = it.size_bytes?.toLong() ?: 0L,
                                date_uploaded = it.date_uploaded.orEmpty(),
                                date_uploaded_unix = it.date_uploaded_unix.toString(),
                                movieId = m?.id?.toInt() ?: 0,
                                imdbCode = m?.imdb_code.orEmpty(),
                            )
                        }

                        val serviceIntent = Intent(this, DownloadService::class.java)
                        serviceIntent.putExtra(DownloadService.TORRENT_JOB, f)
                        startService(serviceIntent)
                    }
                    else -> {
                        GlobalScope.launch { fetchIt(model) }
                    }
                }
            }
        }
    }

    private fun fetchIt(ep: ChapterModel) {

        fetch.setGlobalNetworkType(NetworkType.ALL)

        fun getNameFromUrl(url: String): String {
            return Uri.parse(url).lastPathSegment?.let { if (it.isNotEmpty()) it else ep.name } ?: ep.name
        }

        val requestList = arrayListOf<Request>()
        val url = ep.getChapterInfo().blockingGet()
        for (i in url) {

            val filePath = folderLocation + getNameFromUrl(i.link!!) + "${ep.name}.mp4"
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
            request.addHeader("Referer", "http://thewebsite.com")
            request.addHeader("Connection", "keep-alive")

            requestList.add(request)

        }
        fetch.enqueue(requestList) {}
    }

    override fun sourceList(): List<ApiService> = Sources.values().toList()

    override fun toSource(s: String): ApiService? = try {
        Sources.valueOf(s)
    } catch (e: IllegalArgumentException) {
        null
    }

    override fun customPreferences(preferenceScreen: SettingsDsl) {

        preferenceScreen.viewSettings {
            it.addPreference(
                Preference(it.context).apply {
                    title = "Videos"
                    icon = ContextCompat.getDrawable(it.context, R.drawable.ic_baseline_video_library_24)
                    setOnPreferenceClickListener {
                        openVideos()
                        true
                    }
                }
            )

            it.addPreference(
                Preference(it.context).apply {
                    title = "Downloads"
                    icon = ContextCompat.getDrawable(it.context, R.drawable.ic_baseline_download_24)
                    setOnPreferenceClickListener {
                        openDownloads()
                        true
                    }
                }
            )
        }

        preferenceScreen.generalSettings {
            it.addPreference(
                Preference(it.context).apply {
                    title = "Folder Location"
                    summary = it.context.folderLocation
                    icon = ContextCompat.getDrawable(it.context, R.drawable.ic_baseline_folder_24)
                    setOnPreferenceClickListener {
                        requestPermissions(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ) {
                            if (it.isGranted) {
                                ChooserDialog(this@MainActivity)
                                    .withIcon(R.mipmap.ic_launcher)
                                    .withStringResources("Choose a Directory", "CHOOSE", "CANCEL")
                                    .withFilter(true, false)
                                    .withStartFile(folderLocation)
                                    .enableOptions(true)
                                    .withChosenListener { dir, _ ->
                                        folderLocation = "$dir/"
                                        println(dir)
                                        summary = folderLocation
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

    }

    private fun openDownloads() {
        DownloadViewerFragment().show(activity.supportFragmentManager, "downloadViewer")
    }

    private fun openVideos() {
        ViewVideosFragment().show(activity.supportFragmentManager, "videoViewer")
    }

}
