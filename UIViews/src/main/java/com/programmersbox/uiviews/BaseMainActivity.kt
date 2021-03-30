package com.programmersbox.uiviews

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.navigation.NavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.programmersbox.uiviews.utils.setupWithNavController
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

abstract class BaseMainActivity : AppCompatActivity() {

    private val disposable = CompositeDisposable()

    private var currentNavController: LiveData<NavController>? = null

    protected abstract fun onCreate()

    abstract fun createGenericInfo(): GenericInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        genericInfo = createGenericInfo()
        setContentView(R.layout.base_main_activity)

        if (savedInstanceState == null) {
            setupBottomNavBar()
        }

        onCreate()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        setupBottomNavBar()
    }

    private fun setupBottomNavBar() {
        val navGraphIds = listOf(R.navigation.recent_nav, R.navigation.all_nav)//, R.navigation.settings_nav)

        val controller = findViewById<BottomNavigationView>(R.id.navLayout2)
            .setupWithNavController(
                navGraphIds = navGraphIds,
                fragmentManager = supportFragmentManager,
                containerId = R.id.mainShows,
                intent = intent
            )

        currentNavController = controller

        /*sourcePublish.onNext(currentSource)

        sourcePublish
            .subscribe { currentSource = it }
            .addTo(disposable)

        downloadOrStreamPublish
            .subscribe { downloadOrStream = it }
            .addTo(disposable)

        updateCheckPublish
            .subscribe { lastUpdateCheck = it }
            .addTo(disposable)*/
    }

    override fun onSupportNavigateUp(): Boolean = currentNavController?.value?.navigateUp() ?: false

    override fun onDestroy() {
        disposable.dispose()
        super.onDestroy()
    }

    companion object {
        var genericInfo by Delegates.notNull<GenericInfo>()
    }

}