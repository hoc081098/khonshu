package com.freeletics.khonshu.navigation.internal

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.freeletics.khonshu.navigation.NavRoot

internal class StoreViewModel(
    internal val globalSavedStateHandle: SavedStateHandle,
) : ViewModel() {
    internal val navRoot: NavRoot? get() = globalSavedStateHandle[START_ROOT_KEY]
    private val savedInputStartRoot: NavRoot? = globalSavedStateHandle[INPUT_START_ROOT_KEY]

    private val stores = mutableMapOf<StackEntry.Id, NavigationExecutorStore>()
    private val savedStateHandles = mutableMapOf<StackEntry.Id, SavedStateHandle>()

    fun provideStore(id: StackEntry.Id): NavigationExecutor.Store {
        return stores.getOrPut(id) { NavigationExecutorStore() }
    }

    @SuppressLint("RestrictedApi")
    fun provideSavedStateHandle(id: StackEntry.Id): SavedStateHandle {
        return savedStateHandles.getOrPut(id) {
            val restoredBundle = globalSavedStateHandle.get<Bundle>(id.value)
            SavedStateHandle.createHandle(restoredBundle, null).also {
                globalSavedStateHandle.setSavedStateProvider(id.value, it.savedStateProvider())
            }
        }
    }

    fun removeEntry(id: StackEntry.Id) {
        val store = stores.remove(id)
        store?.close()

        savedStateHandles.remove(id)
        globalSavedStateHandle.clearSavedStateProvider(id.value)
        globalSavedStateHandle.remove<Any>(id.value)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public override fun onCleared() {
        for (store in stores.values) {
            store.close()
        }
        stores.clear()

        for (key in savedStateHandles.keys) {
            globalSavedStateHandle.clearSavedStateProvider(key.value)
            globalSavedStateHandle.remove<Any>(key.value)
        }
        savedStateHandles.clear()
    }

    internal fun setInputStartRoot(root: NavRoot) {
        savedInputStartRoot.let { currentSavedInputStartRoot ->
            when {
                currentSavedInputStartRoot == null -> {
                    globalSavedStateHandle[INPUT_START_ROOT_KEY] = root
                    globalSavedStateHandle[START_ROOT_KEY] = root
                }

                currentSavedInputStartRoot != root -> {
                    // Clear all saved state
                    onCleared()

                    globalSavedStateHandle[INPUT_START_ROOT_KEY] = root
                    globalSavedStateHandle[START_ROOT_KEY] = root
                }

                else -> {
                    // Do nothing
                }
            }
        }
    }

    internal fun setStartRoot(root: NavRoot) {
        globalSavedStateHandle[START_ROOT_KEY] = root
    }

    private companion object {
        private const val START_ROOT_KEY = "startRoot"
        private const val INPUT_START_ROOT_KEY = "inputStartRoot"
    }
}
