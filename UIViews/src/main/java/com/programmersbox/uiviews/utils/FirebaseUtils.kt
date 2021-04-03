package com.programmersbox.uiviews.utils

import android.annotation.SuppressLint
import android.app.Activity
import com.firebase.ui.auth.AuthUI
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.toObjects
import com.programmersbox.favoritesdatabase.ChapterWatched
import com.programmersbox.favoritesdatabase.DbModel
import com.programmersbox.rxutils.toLatestFlowable
import com.programmersbox.uiviews.R
import io.reactivex.Completable
import io.reactivex.subjects.PublishSubject

object FirebaseAuthentication {

    private const val RC_SIGN_IN = 32

    val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun signIn(activity: Activity) {
        //val signInIntent = googleSignInClient!!.signInIntent
        //activity.startActivityForResult(signInIntent, RC_SIGN_IN)
        // Choose authentication providers
        val providers = arrayListOf(
            AuthUI.IdpConfig.GoogleBuilder().build()
        )

        // Create and launch sign-in intent
        activity.startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setTheme(R.style.Theme_OtakuWorld)
                //.setLogo(R.mipmap.big_logo)
                .setAvailableProviders(providers)
                .build(),
            RC_SIGN_IN
        )
    }

    fun signOut() {
        auth.signOut()
        //currentUser = null
    }

    val currentUser: FirebaseUser? get() = FirebaseAuth.getInstance().currentUser

}

object FirebaseDb {

    var DOCUMENT_ID = ""
    var CHAPTERS_ID = ""
    var COLLECTION_ID = ""
    var CHAPTER_ID = ""
    var ITEM_ID = ""
    var READ_OR_WATCHED_ID = ""

    @SuppressLint("StaticFieldLeak")
    private val db = FirebaseFirestore.getInstance().apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            /*.setHost("10.0.2.2:8080")
            .setSslEnabled(false)
            .setPersistenceEnabled(false)*/
            //.setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            //.setCacheSizeBytes()
            .build()
    }

    private fun <TResult> Task<TResult>.await(): TResult = Tasks.await(this)

    private val showDoc2 get() = FirebaseAuthentication.currentUser?.let { db.collection(COLLECTION_ID).document(DOCUMENT_ID).collection(it.uid) }
    private val episodeDoc2 get() = FirebaseAuthentication.currentUser?.let { db.collection(COLLECTION_ID).document(CHAPTERS_ID).collection(it.uid) }

    private data class FirebaseAllShows(val first: String = DOCUMENT_ID, val second: List<FirebaseDbModel> = emptyList())

    private data class FirebaseDbModel(
        val title: String? = null,
        val description: String? = null,
        val showUrl: String? = null,
        val mangaUrl: String? = null,
        val imageUrl: String? = null,
        val source: String? = null,
        var numEpisodes: Int = 0,
    )

    private data class FirebaseChapterWatched(
        val url: String? = null,
        val name: String? = null,
        val showUrl: String? = null,
    )

    private fun FirebaseDbModel.toDbModel() = DbModel(
        title.orEmpty(),
        description.orEmpty(),
        (showUrl ?: mangaUrl).orEmpty(),
        imageUrl.orEmpty(),
        source.orEmpty(),
        numEpisodes,
    )

    private fun DbModel.toFirebaseDbModel() = FirebaseDbModel(
        title,
        description,
        url,
        url,
        imageUrl,
        source,
        numChapters,
    )

    private fun FirebaseChapterWatched.toChapterWatchedModel() = ChapterWatched(
        url.orEmpty().pathToUrl(),
        name.orEmpty(),
        showUrl.orEmpty().pathToUrl(),
    )

    private fun ChapterWatched.toFirebaseChapterWatched() = FirebaseChapterWatched(
        url,
        name,
        this.favoriteUrl,
    )

    private fun String.urlToPath() = replace("/", "<")
    private fun String.pathToUrl() = replace("<", "/")

    fun getAllShows() = showDoc2
        ?.get()
        ?.await()
        ?.toObjects<FirebaseDbModel>()
        ?.map { it.toDbModel() }
        .orEmpty()

    fun insertShow(showDbModel: DbModel) = Completable.create { emitter ->
        showDoc2?.document(showDbModel.url.urlToPath())
            ?.set(showDbModel.toFirebaseDbModel())
            ?.addOnSuccessListener { emitter.onComplete() }
            ?.addOnFailureListener { emitter.onError(it) } ?: emitter.onComplete()
    }

    fun removeShow(showDbModel: DbModel) = Completable.create { emitter ->
        showDoc2?.document(showDbModel.url.urlToPath())
            ?.delete()
            ?.addOnSuccessListener { emitter.onComplete() }
            ?.addOnFailureListener { emitter.onError(it) } ?: emitter.onComplete()
    }

    fun updateShow(showDbModel: DbModel) = Completable.create { emitter ->
        showDoc2?.document(showDbModel.url.urlToPath())
            ?.update(CHAPTER_ID, showDbModel.numChapters)
            ?.addOnSuccessListener { emitter.onComplete() }
            ?.addOnFailureListener { emitter.onError(it) } ?: emitter.onComplete()
    }

    fun insertEpisodeWatched(episodeWatched: ChapterWatched) = Completable.create { emitter ->
        episodeDoc2?.document(episodeWatched.favoriteUrl.urlToPath())
            ?.set("create" to 1)
            ?.addOnSuccessListener {
                episodeDoc2?.document(episodeWatched.favoriteUrl.urlToPath())
                    //?.set("watched" to listOf(episodeWatched.toFirebaseEpisodeWatched()), SetOptions.merge())
                    ?.update("watched", FieldValue.arrayUnion(episodeWatched.toFirebaseChapterWatched()))
                    //?.collection(episodeWatched.url.urlToPath())
                    //?.document("watched")
                    //?.set(episodeWatched.toFirebaseEpisodeWatched())
                    ?.addOnSuccessListener { emitter.onComplete() }
                    ?.addOnFailureListener { emitter.onError(it) } ?: emitter.onComplete()
            } ?: emitter.onComplete()
    }

    fun removeEpisodeWatched(episodeWatched: ChapterWatched) = Completable.create { emitter ->
        episodeDoc2?.document(episodeWatched.favoriteUrl.urlToPath())
            ?.update("watched", FieldValue.arrayRemove(episodeWatched.toFirebaseChapterWatched()))
            //?.collection(episodeWatched.url.urlToPath())
            //?.document("watched")
            //?.delete()
            ?.addOnSuccessListener { emitter.onComplete() }
            ?.addOnFailureListener { emitter.onError(it) } ?: emitter.onComplete()
        emitter.onComplete()
    }

    class FirebaseListener {

        private var listener: ListenerRegistration? = null

        fun getAllShowsFlowable() = PublishSubject.create<List<DbModel>> { emitter ->
            require(listener == null)
            listener = showDoc2?.addSnapshotListener { value, error ->
                value?.toObjects<FirebaseDbModel>()?.map { it.toDbModel() }?.let { emitter.onNext(it) }
            }
            if (listener == null) emitter.onNext(emptyList())
        }.toLatestFlowable()

        fun findItemByUrl(url: String?) = PublishSubject.create<Boolean> { emitter ->
            require(listener == null)
            listener = showDoc2?.whereEqualTo(ITEM_ID, url)?.addSnapshotListener { value, error ->
                value?.toObjects<FirebaseDbModel>()
                    .also { println(it) }
                    ?.map { it.toDbModel() }?.let { emitter.onNext(it.isNotEmpty()) }
            }
            if (listener == null) emitter.onNext(false)
        }.toLatestFlowable()

        fun getAllEpisodesByShow(showUrl: String) = PublishSubject.create<List<ChapterWatched>> { emitter ->
            require(listener == null)
            listener = episodeDoc2
                ?.document(showUrl.urlToPath())
                ?.addSnapshotListener { value, error ->
                    value?.toObject(Watched::class.java)?.watched
                        ?.map { it.toChapterWatchedModel() }
                        ?.let { emitter.onNext(it) }
                }
            if (listener == null) emitter.onNext(emptyList())
        }.toLatestFlowable()

        //fun getAllEpisodesByShow(showDbModel: DbModel) = getAllEpisodesByShow(showDbModel.showUrl)

        fun unregister() {
            listener?.remove()
        }

    }

    private class Watched(val watched: List<FirebaseChapterWatched> = emptyList())
}