package luph.vulcanizerv3.updates.data

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import luph.vulcanizerv3.updates.utils.download.getModDetails
import luph.vulcanizerv3.updates.utils.download.getModList
import luph.vulcanizerv3.updates.utils.download.parseModKeywords

enum class ModType {
    APK, TWRP, MODULE
}


@Serializable
data class ModDetails(
    //From JSON
    var name: String = "error: mod name not found",
    val author: String = "error: author not found",
    val version: String = "error: version not found",
    var updateTypeString: String = "error: update type not found",
    val srcLink: String = "error: source link not found",
    val keywords: List<String> = List(0) { "error: keywords not found" },
    val openName: String = "error: open name not found",
    val packageName: String = "error: package name not found",
    val require: String? = null,
    val images: Int = 0,
    val README: String = "error: README not found",
    val changeLog: String = "error: change log not found",
    val READMEsummary: String = "error: README summary not found",
    val changeLogSummary: String = "error: change log summary not found",

    //Calculated
    var url: String = "error: url not found",
    var updateType: ModType? = null,
)

enum class DETAILFILE(val type: String) {
    ICON("icon.jpg"),
    FILE("file"),
    VERSION("version.json")
}

object ModDetailsStore {
    private var modDetails = mutableStateOf<MutableList<ModDetails>>(mutableListOf())
    private var keywords =
        mutableStateOf<MutableMap<String, MutableList<ModDetails>>>(mutableMapOf())
    private var modList = mutableStateOf<Map<String, String>>(emptyMap())
    private var newMods = mutableStateOf<List<String>>(listOf())
    private var offline = mutableStateOf(false)
    private const val MOD_LIST_FILE = "mod_list.dat"
    private val serializableManager = SerializableManager<String>()

    init {
        refresh()
    }

    fun getAllMods(): State<MutableList<ModDetails>> {
        return modDetails
    }

    fun getModKeywords(): State<Map<String, List<ModDetails>>> {
        return keywords
    }

    fun getAllModKeywords(): State<Map<String, MutableList<ModDetails>>> {
        val currentModKeywords = keywords
        if (currentModKeywords.value.isEmpty()) return currentModKeywords
        currentModKeywords.value["All Mods"] = modDetails.value
        return currentModKeywords
    }

    fun getNewMods(): State<List<String>> {
        return newMods
    }

    fun isOffline(): State<Boolean> {
        return offline
    }

    fun setOffline(value: Boolean) {
        offline.value = value
    }

    private fun saveModList() {
        serializableManager.save(MOD_LIST_FILE, Json.encodeToString(modList.value))
    }

    private fun loadModList(): Map<String, String>? {
        val modListString = serializableManager.load(MOD_LIST_FILE) ?: return null
        return Json.decodeFromString(modListString)
    }

    private fun newMods(): List<String> {
        val savedModList = loadModList() ?: emptyMap()
        val modListPaths = mutableListOf<String>()
        modList.value.forEach { (key, value) ->
            Log.e("ModDetailsStore", "key: $key, value: $value")
            Log.e("ModDetailsStore", "savedModList[key]: ${savedModList[key]}")
            if (!savedModList.containsKey(key) || savedModList[key] != value) {
                modListPaths.add(key.split("/").last())
            }
        }
        Log.e("modlist[aths", modListPaths.toString())
        return modListPaths
    }

    fun refresh() {
        CoroutineScope(Dispatchers.Default).launch {
            modList.value = getModList()
            offline.value = modList.value.isEmpty()
            newMods.value = newMods()
            saveModList()

            modDetails.value = getModDetails(modList.value)
            if (modList.value.isEmpty()) {
                offline.value = true
            }

            keywords.value = parseModKeywords(modDetails.value)
            if (modList.value.isEmpty()) {
                offline.value = true
            }
        }
        return
    }
}

