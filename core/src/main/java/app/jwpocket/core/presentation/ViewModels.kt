package app.jwpocket.core.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.jwpocket.core.CoreGraph
import app.jwpocket.core.data.Load
import app.jwpocket.core.data.SearchHit
import app.jwpocket.core.model.Category
import app.jwpocket.core.model.CategoryContent
import app.jwpocket.core.model.Video
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/*
 * Screen logic shared by the phone and TV apps. No UI toolkit here: each app
 * draws these states its own way.
 */

/** Reloads [load] when the library language changes or [refresh] ticks. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> CoreGraph.byLanguage(refresh: Flow<Int>, load: (String) -> Flow<Load<T>>): Flow<Load<T>> =
    combine(settings.settings.map { it.languageCode }.distinctUntilChanged(), refresh) { lang, _ -> lang }
        .flatMapLatest { load(it) }

class HomeViewModel(core: CoreGraph) : ViewModel() {
    private val refresh = MutableStateFlow(0)

    val latest: StateFlow<Load<List<Video>>> = core.byLanguage(refresh) { core.library.latest(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

    val categories: StateFlow<Load<List<Category>>> = core.byLanguage(refresh) { core.library.rootCategories(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

    /** The first category's first row, shown as "Featured" (JW Broadcasting picks). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val featured: StateFlow<Load<CategoryContent>> = categories
        .map { (it as? Load.Ready)?.data?.firstOrNull()?.key }
        .distinctUntilChanged()
        .flatMapLatest { key -> if (key == null) flow { emit(Load.Loading) } else core.library.category(core.settings.current.languageCode, key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

    fun refresh() { refresh.value++ }
}

class CategoryViewModel(core: CoreGraph, key: String) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    val content: StateFlow<Load<CategoryContent>> = core.byLanguage(refresh) { core.library.category(it, key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

    fun refresh() { refresh.value++ }
}

class SearchViewModel(private val core: CoreGraph) : ViewModel() {
    val query = MutableStateFlow("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<Load<List<SearchHit>>?> = query
        .debounce(250)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { q ->
            flow {
                if (q.length < 2) { emit(null); return@flow }
                emit(Load.Loading)
                val hits = core.library.search(core.settings.current.languageCode, q)
                emit(Load.Ready(hits))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<Load<List<Category>>> = core.byLanguage(MutableStateFlow(0)) { core.library.rootCategories(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)
}
