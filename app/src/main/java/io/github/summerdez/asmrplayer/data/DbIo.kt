package io.github.summerdez.asmrplayer.data

import io.github.summerdez.asmrplayer.R
import io.github.summerdez.asmrplayer.data.*
import io.github.summerdez.asmrplayer.data.remote.*
import io.github.summerdez.asmrplayer.data.download.*
import io.github.summerdez.asmrplayer.data.files.*
import io.github.summerdez.asmrplayer.domain.*
import io.github.summerdez.asmrplayer.domain.model.*
import io.github.summerdez.asmrplayer.playback.*
import io.github.summerdez.asmrplayer.presentation.*
import io.github.summerdez.asmrplayer.ui.*
import io.github.summerdez.asmrplayer.ui.activity.*
import io.github.summerdez.asmrplayer.ui.components.*
import io.github.summerdez.asmrplayer.ui.screens.*
import io.github.summerdez.asmrplayer.ui.theme.*
import io.github.summerdez.asmrplayer.ui.util.*
import io.github.summerdez.asmrplayer.di.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal object DbIo {
    private val insideDbIo = ThreadLocal<Boolean>()

    fun <T> run(block: suspend () -> T): T {
        val restoreInterrupt = Thread.interrupted()
        try {
            if (insideDbIo.get() == true) {
                return runBlocking {
                    block()
                }
            }
            return runBlocking(Dispatchers.IO) {
                insideDbIo.set(true)
                try {
                    block()
                } finally {
                    insideDbIo.remove()
                }
            }
        } finally {
            if (restoreInterrupt) {
                Thread.currentThread().interrupt()
            }
        }
    }
}
