package app.jwpocket.phone

import android.app.Application
import app.jwpocket.core.CoreGraph
import app.jwpocket.media.MediaGraph
import app.jwpocket.media.PocketGraphOwner
import coil.ImageLoader
import coil.ImageLoaderFactory

class PocketPhoneApp : Application(), PocketGraphOwner, ImageLoaderFactory {

    override val core: CoreGraph by lazy {
        CoreGraph(this, BuildConfig.UPDATE_REPO, BuildConfig.UPDATE_APK, BuildConfig.VERSION_NAME)
    }
    override val media: MediaGraph by lazy { MediaGraph(this, core) }

    override fun onCreate() {
        super.onCreate()
        core.languages.load(core.settings.current.languageCode)
        media.downloads // restore the download list early
    }

    override fun newImageLoader(): ImageLoader = core.imageLoader()
}
