package app.jwpocket.phone

import android.app.Application
import app.jwpocket.core.CoreGraph
import app.jwpocket.media.MediaGraph
import app.jwpocket.media.PocketGraphOwner
import app.jwpocket.phone.notify.NewVideoCheck
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class PocketPhoneApp : Application(), PocketGraphOwner, ImageLoaderFactory {

    override val core: CoreGraph by lazy {
        CoreGraph(this, BuildConfig.UPDATE_REPO, BuildConfig.UPDATE_APK, BuildConfig.VERSION_NAME)
    }
    override val media: MediaGraph by lazy { MediaGraph(this, core) }

    override fun onCreate() {
        super.onCreate()
        core.languages.load(core.settings.current.languageCode)
        media.downloads // restore the download list early
        // The daily new-video check follows the two notification switches, now and whenever they change.
        core.appScope.launch {
            core.settings.settings
                .distinctUntilChanged { a, b -> a.notifyBroadcasting == b.notifyBroadcasting && a.notifyNews == b.notifyNews }
                .collect { NewVideoCheck.sync(this@PocketPhoneApp, it) }
        }
    }

    override fun newImageLoader(): ImageLoader = core.imageLoader()
}
