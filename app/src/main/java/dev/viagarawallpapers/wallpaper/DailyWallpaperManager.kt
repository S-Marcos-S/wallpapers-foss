// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagarawallpapers.wallpaper

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import dev.viagarawallpapers.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class DailyWallpaperTarget(val id: String) {
    BOTH("both"),
    HOME("home"),
    LOCK("lock");

    companion object {
        fun fromId(id: String): DailyWallpaperTarget = entries.firstOrNull { it.id == id } ?: BOTH
    }
}

object DailyWallpaperManager {

    private val _isChangingWallpaper = MutableStateFlow(false)
    val isChangingWallpaper: StateFlow<Boolean> = _isChangingWallpaper.asStateFlow()

    private val _currentWallpaperId = MutableStateFlow("")
    val currentWallpaperId: StateFlow<String> = _currentWallpaperId.asStateFlow()

    private val advanceMutex = Mutex()

    fun getEffectiveSystemId(context: Context): Int {
        return runCatching {
            WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        }.getOrDefault(-1)
    }

    fun getEffectiveLockId(context: Context): Int {
        return runCatching {
            val wm = WallpaperManager.getInstance(context)
            val lockId = wm.getWallpaperId(WallpaperManager.FLAG_LOCK)
            if (lockId > 0) lockId else wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        }.getOrDefault(-1)
    }

    suspend fun recordCurrentWallpaperIds(context: Context) = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val sysId = getEffectiveSystemId(context)
        val lockId = getEffectiveLockId(context)
        if (sysId > 0) prefs.setDailyWallpaperLastSystemId(sysId)
        if (lockId > 0) prefs.setDailyWallpaperLastLockId(lockId)
    }

    suspend fun checkExternalWallpaperChanges(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val isEnabled = prefs.autoDailyWallpaperEnabled.first()
        if (!isEnabled) return@withContext false

        val stopOnExternal = prefs.dailyWallpaperStopOnExternalChange.first()
        if (!stopOnExternal) return@withContext false

        val lastSysId = prefs.dailyWallpaperLastSystemId.first()
        val lastLockId = prefs.dailyWallpaperLastLockId.first()

        val curSys = getEffectiveSystemId(context)
        val curLock = getEffectiveLockId(context)

        // Se ainda não temos registros gravados, define os atuais como referência
        if (lastSysId <= 0 && lastLockId <= 0) {
            if (curSys > 0) prefs.setDailyWallpaperLastSystemId(curSys)
            if (curLock > 0) prefs.setDailyWallpaperLastLockId(curLock)
            return@withContext false
        }

        val target = DailyWallpaperTarget.fromId(prefs.dailyWallpaperTarget.first())

        val sysChanged = (lastSysId > 0 && curSys > 0 && curSys != lastSysId)
        val lockChanged = (lastLockId > 0 && curLock > 0 && curLock != lastLockId)

        if (!sysChanged && !lockChanged) {
            return@withContext false
        }

        when (target) {
            DailyWallpaperTarget.BOTH -> {
                if (sysChanged && lockChanged) {
                    // Ambas as telas foram trocadas externamente -> para a troca diária automaticamente
                    prefs.setAutoDailyWallpaperEnabled(false)
                    if (curSys > 0) prefs.setDailyWallpaperLastSystemId(curSys)
                    if (curLock > 0) prefs.setDailyWallpaperLastLockId(curLock)
                    return@withContext true
                } else if (sysChanged) {
                    // Somente a tela inicial trocou -> para na tela inicial, mantém na de bloqueio
                    prefs.setDailyWallpaperTarget(DailyWallpaperTarget.LOCK.id)
                    if (curSys > 0) prefs.setDailyWallpaperLastSystemId(curSys)
                    return@withContext true
                } else if (lockChanged) {
                    // Somente a tela de bloqueio trocou -> para na de bloqueio, mantém na inicial
                    prefs.setDailyWallpaperTarget(DailyWallpaperTarget.HOME.id)
                    if (curLock > 0) prefs.setDailyWallpaperLastLockId(curLock)
                    return@withContext true
                }
            }
            DailyWallpaperTarget.HOME -> {
                if (sysChanged) {
                    // Tela inicial trocou externamente -> para a troca diária automaticamente
                    prefs.setAutoDailyWallpaperEnabled(false)
                    if (curSys > 0) prefs.setDailyWallpaperLastSystemId(curSys)
                    return@withContext true
                }
            }
            DailyWallpaperTarget.LOCK -> {
                if (lockChanged) {
                    // Tela de bloqueio trocou externamente -> para a troca diária automaticamente
                    prefs.setAutoDailyWallpaperEnabled(false)
                    if (curLock > 0) prefs.setDailyWallpaperLastLockId(curLock)
                    return@withContext true
                }
            }
        }
        false
    }

    fun isDifferentDay(lastMs: Long, nowMs: Long): Boolean {
        if (lastMs <= 0L) return true
        if (nowMs < lastMs) return false
        val calLast = Calendar.getInstance().apply { timeInMillis = lastMs }
        val calNow = Calendar.getInstance().apply { timeInMillis = nowMs }
        return calLast.get(Calendar.ERA) != calNow.get(Calendar.ERA) ||
               calLast.get(Calendar.YEAR) != calNow.get(Calendar.YEAR) ||
               calLast.get(Calendar.DAY_OF_YEAR) != calNow.get(Calendar.DAY_OF_YEAR)
    }

    suspend fun syncCurrentWallpaper(context: Context) = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val id = prefs.dailyWallpaperCurrentId.first().trim()
        if (id.isNotBlank()) {
            _currentWallpaperId.value = id
        }
    }

    fun checkAndAdvanceIfNewDay(context: Context, coroutineScope: CoroutineScope) {
        coroutineScope.launch(Dispatchers.IO) {
            checkExternalWallpaperChanges(context)

            val prefs = Prefs(context)
            val currentId = prefs.dailyWallpaperCurrentId.first().trim()
            if (currentId.isNotBlank()) {
                _currentWallpaperId.value = currentId
            }

            val isEnabled = prefs.autoDailyWallpaperEnabled.first()
            if (!isEnabled) return@launch

            val lastTimestamp = prefs.dailyWallpaperLastTimestamp.first()
            val now = System.currentTimeMillis()

            if (lastTimestamp <= 0L || currentId.isBlank() || isDifferentDay(lastTimestamp, now)) {
                advanceToNextWallpaper(context, isUserDislike = false)
            }
        }
    }

    suspend fun setEnabled(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        prefs.setAutoDailyWallpaperEnabled(enabled)
        if (enabled) {
            recordCurrentWallpaperIds(context)
            val lastTimestamp = prefs.dailyWallpaperLastTimestamp.first()
            val currentId = prefs.dailyWallpaperCurrentId.first().trim()
            if (currentId.isNotBlank()) {
                _currentWallpaperId.value = currentId
            }
            val now = System.currentTimeMillis()
            if (lastTimestamp <= 0L || currentId.isBlank() || isDifferentDay(lastTimestamp, now)) {
                advanceToNextWallpaper(context, isUserDislike = false)
            }
        }
    }

    suspend fun onWallpaperApplied(context: Context, wallpaperId: String) = withContext(Dispatchers.IO) {
        val trimmed = wallpaperId.trim()
        if (trimmed.isNotBlank()) {
            _currentWallpaperId.value = trimmed
            val prefs = Prefs(context)
            prefs.setDailyWallpaperCurrentId(trimmed)
            prefs.setDailyWallpaperLastTimestamp(System.currentTimeMillis())
            recordCurrentWallpaperIds(context)
        }
    }

    fun selectNextWallpaper(
        allStatic: List<WallpaperItem>,
        dislikedIds: Set<String>,
        currentId: String,
        savedIndex: Int = -1,
        isUserDislike: Boolean = false,
    ): WallpaperItem? {
        if (allStatic.isEmpty()) return null
        val eligible = allStatic.filter { it.id !in dislikedIds }
            .ifEmpty { allStatic }

        val currentIdxInAll = allStatic.indexOfFirst { it.id == currentId }
        if (currentIdxInAll >= 0) {
            for (step in 1..allStatic.size) {
                val candidate = allStatic[(currentIdxInAll + step) % allStatic.size]
                if (candidate in eligible) {
                    return candidate
                }
            }
        }

        if (savedIndex in allStatic.indices) {
            val startOffset = if (isUserDislike) 1 else 0
            for (step in 0 until allStatic.size) {
                val candidate = allStatic[(savedIndex + startOffset + step) % allStatic.size]
                if (candidate in eligible) {
                    return candidate
                }
            }
        }

        return if (isUserDislike && eligible.size > 1) eligible[1] else eligible.firstOrNull()
    }

    @SuppressLint("MissingPermission")
    suspend fun advanceToNextWallpaper(
        context: Context,
        isUserDislike: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!advanceMutex.tryLock()) {
            return@withContext false
        }
        _isChangingWallpaper.value = true

        try {
            checkExternalWallpaperChanges(context)

            val prefs = Prefs(context)
            val isEnabled = prefs.autoDailyWallpaperEnabled.first()
            if (!isEnabled) {
                return@withContext false
            }

            var currentId = _currentWallpaperId.value.ifBlank { prefs.dailyWallpaperCurrentId.first() }.trim()

            if (isUserDislike && currentId.isNotBlank()) {
                WallpaperRepository.addDisliked(context, currentId)
            }

            val catalog = WallpaperRepository.loadCatalog(context)
            val staticWallpapers = catalog.wallpapers.filter { !it.isLive && it.type != "video" }
                .ifEmpty { catalog.wallpapers }

            if (staticWallpapers.isEmpty()) {
                return@withContext false
            }

            val dislikedIds = WallpaperRepository.getDislikedIds(context)
            val eligible = staticWallpapers.filter { it.id !in dislikedIds }
                .ifEmpty { staticWallpapers }

            val savedIndex = prefs.dailyWallpaperIndex.first()
            val candidateItem = selectNextWallpaper(
                allStatic = staticWallpapers,
                dislikedIds = dislikedIds,
                currentId = currentId,
                savedIndex = savedIndex,
                isUserDislike = isUserDislike,
            ) ?: eligible.firstOrNull() ?: return@withContext false

            val target = DailyWallpaperTarget.fromId(prefs.dailyWallpaperTarget.first())
            val flags = when (target) {
                DailyWallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                DailyWallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                DailyWallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
            }

            val startEligibleIndex = eligible.indexOfFirst { it.id == candidateItem.id }.coerceAtLeast(0)
            var targetIndex = startEligibleIndex
            var applySuccess = false
            var attempts = 0
            val maxAttempts = minOf(3, eligible.size)

            while (attempts < maxAttempts && !applySuccess) {
                val candidate = eligible[targetIndex]
                val applyResult = WallpaperRepository.applyWallpaper(context, candidate, flags)
                if (applyResult.isSuccess) {
                    _currentWallpaperId.value = candidate.id
                    prefs.setDailyWallpaperCurrentId(candidate.id)
                    val staticIndex = staticWallpapers.indexOfFirst { it.id == candidate.id }
                    prefs.setDailyWallpaperIndex(if (staticIndex >= 0) staticIndex else targetIndex)
                    prefs.setDailyWallpaperLastTimestamp(System.currentTimeMillis())
                    recordCurrentWallpaperIds(context)
                    applySuccess = true
                } else {
                    targetIndex = (targetIndex + 1) % eligible.size
                    attempts++
                }
            }
            applySuccess
        } finally {
            _isChangingWallpaper.value = false
            advanceMutex.unlock()
        }
    }

    suspend fun handleLike(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        var currentId = _currentWallpaperId.value.ifBlank { prefs.dailyWallpaperCurrentId.first() }.trim()
        if (currentId.isBlank()) {
            val catalog = WallpaperRepository.loadCatalog(context)
            val eligible = catalog.wallpapers.filter { !it.isLive && it.type != "video" }
                .ifEmpty { catalog.wallpapers }
            val savedIndex = prefs.dailyWallpaperIndex.first()
            currentId = if (savedIndex in eligible.indices) {
                eligible[savedIndex].id
            } else {
                eligible.firstOrNull()?.id ?: ""
            }.trim()
            if (currentId.isNotBlank()) {
                _currentWallpaperId.value = currentId
                prefs.setDailyWallpaperCurrentId(currentId)
                prefs.setDailyWallpaperLastTimestamp(System.currentTimeMillis())
            }
        }
        if (currentId.isNotBlank()) {
            _currentWallpaperId.value = currentId
            WallpaperRepository.addFavorite(context, currentId)
            true
        } else {
            false
        }
    }
}
