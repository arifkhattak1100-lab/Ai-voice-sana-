package com.example.data.database

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SanaMemoryManager(
    private val repository: SanaRepository,
    private val scope: CoroutineScope
) {

    private val _isMemoryEnabled = MutableStateFlow(true)
    val isMemoryEnabled: StateFlow<Boolean> = _isMemoryEnabled.asStateFlow()

    val allMemory: StateFlow<List<MemoryEntity>> = repository.allMemory
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setMemoryEnabled(enabled: Boolean) {
        _isMemoryEnabled.value = enabled
    }

    fun saveMemory(key: String, value: String, category: String = "preferences") {
        if (!_isMemoryEnabled.value) return
        scope.launch {
            repository.saveMemory(key, value, category)
        }
    }

    suspend fun getMemory(key: String): String? = withContext(Dispatchers.IO) {
        if (!_isMemoryEnabled.value) return@withContext null
        repository.getMemory(key)
    }

    fun deleteMemory(id: Long) {
        scope.launch {
            repository.deleteMemory(id)
        }
    }

    fun clearAllMemory() {
        scope.launch {
            repository.clearMemory()
        }
    }

    fun buildMemorySummary(): String {
        if (!_isMemoryEnabled.value) return ""
        val memoryList = allMemory.value
        if (memoryList.isEmpty()) return ""

        val sb = StringBuilder("Known User Memories & Preferences:\n")
        for (item in memoryList) {
            sb.append("- ${item.key}: ${item.value}\n")
        }
        return sb.toString().trim()
    }
}
