package de.timklge.karootilehunting

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TilehuntingViewModel(val clusters: List<Cluster> = listOf())

class TilehuntingViewModelProvider {
    private val observableStateFlow = MutableStateFlow(TilehuntingViewModel())
    val viewModelFlow = observableStateFlow.asStateFlow()

    suspend fun update(vm: TilehuntingViewModel){
        observableStateFlow.emit(vm)
    }
}