package de.timklge.karootilehunting

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

fun<T> Flow<T>.throttle(timeout: Long): Flow<T> = this
    .conflate()
    .transform {
        emit(it)
        delay(timeout)
    }