package luph.vulcanizerv3.updates.ui.components.info

import android.util.Log
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.analytics.logEvent
import com.ketch.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import luph.vulcanizerv3.updates.MainActivity
import luph.vulcanizerv3.updates.R
import luph.vulcanizerv3.updates.data.DETAILFILE
import luph.vulcanizerv3.updates.data.ModDetails
import luph.vulcanizerv3.updates.data.ModDetailsStore
import luph.vulcanizerv3.updates.data.ModType
import luph.vulcanizerv3.updates.data.UpdateStatus
import luph.vulcanizerv3.updates.data.buttonData
import luph.vulcanizerv3.updates.data.infoAlert
import luph.vulcanizerv3.updates.data.infoBoxesData
import luph.vulcanizerv3.updates.ui.components.ImageCarousel
import luph.vulcanizerv3.updates.ui.components.PageNAv
import luph.vulcanizerv3.updates.ui.page.RouteParams
import luph.vulcanizerv3.updates.ui.page.settings.options.subscribe
import luph.vulcanizerv3.updates.ui.page.showNavigation
import luph.vulcanizerv3.updates.utils.apkmanager.installAPK
import luph.vulcanizerv3.updates.utils.modulemanager.installModule

@Composable
fun ModInfo(navController: NavController = NavController(MainActivity.applicationContext()),
            view: View = MainActivity.instance!!.window.decorView, passedModDetails: ModDetails? = null) {
    val initialModDetails: ModDetails = passedModDetails ?: RouteParams.pop(ModDetails::class.java)
        ?: ModDetails()
    val modDetails = remember { initialModDetails }
    val downloadId = remember { mutableIntStateOf(0) }
    val downloadProgressPercentage = remember { mutableIntStateOf(0) }

    val infoState = remember { mutableStateOf(UpdateStatus.NOT_INSTALLED) }


    showNavigation.show = false

    val corePackages = arrayOf("luph.vulcanizerv3.updates")

    val infoAlert = infoAlert()
    val buttonData = buttonData(modDetails, infoState, downloadId, changeUpdateType = { updateStatus, buttonData -> changeUpdateType(updateStatus, buttonData) }, infoAlert, navController, view)
    val infoBoxesData = infoBoxesData(modDetails)


    fun goBack(){
        when(infoState.value) {
            UpdateStatus.INSTALLED -> {
                ModDetailsStore.installedMods.value += modDetails.packageName
                ModDetailsStore.installedModsUpdate.value -= modDetails.packageName
            }
            UpdateStatus.UPDATE_AVAILABLE -> {
                ModDetailsStore.installedMods.value += modDetails.packageName
                ModDetailsStore.installedModsUpdate.value += modDetails.packageName
            }
            UpdateStatus.NOT_INSTALLED -> {
                ModDetailsStore.installedMods.value -= modDetails.packageName
                ModDetailsStore.installedModsUpdate.value -= modDetails.packageName
            }
            else -> {}
        }
        navController.popBackStack()
    }

    BackHandler {
        goBack()
    }



    LaunchedEffect(downloadId.intValue) {
        if (infoState.value != UpdateStatus.UPDATING) {
            if (corePackages.contains(modDetails.packageName))
                when (modDetails.packageName) {
                    "luph.vulcanizerv3.updates" -> {
                        if (ModDetailsStore.isAppUpdatedNeeded().value) {
                            changeUpdateType(UpdateStatus.UPDATE_AVAILABLE, buttonData)
                        } else {
                            changeUpdateType(UpdateStatus.INSTALLED, buttonData)
                        }
                    }

                    else -> {
                        changeUpdateType(UpdateStatus.NOT_INSTALLED, buttonData)
                    }
                }
            else {
                if (modDetails.packageName in ModDetailsStore.getInstalledMods().value) {
                    if (ModDetailsStore.getInstalledModsUpdate().value.contains(modDetails.packageName))
                        changeUpdateType(UpdateStatus.UPDATE_AVAILABLE, buttonData)
                    else changeUpdateType(UpdateStatus.INSTALLED, buttonData)
                } else {
                    changeUpdateType(UpdateStatus.NOT_INSTALLED, buttonData)
                }
            }
        }

        MainActivity.getKetch().getAllDownloads().forEach { downloadModel ->
            if (buttonData.modDetails.url + DETAILFILE.FILE.type == downloadModel.url)
                downloadId.intValue = downloadModel.id
        }
        MainActivity.getKetch().observeDownloadById(downloadId.intValue)
            .flowOn(Dispatchers.IO)
            .collect { downloadModel ->
                downloadProgressPercentage.intValue = downloadModel.progress

                downloadModel.status.let {
                    if (it == Status.SUCCESS) {
                        if (modDetails.packageName in ModDetailsStore.getInstalledMods().value) {
                            return@collect
                        }
                        buttonData.canCancel = false


                        MainActivity.getFirebaseAnalytics().logEvent("downloaded_mod") {
                            param("mod_name", modDetails.name)
                            param("mod_version", modDetails.version)
                            param("mod_author", modDetails.author)
                            modDetails.keywords.forEach { keyword ->
                                param("keyword", keyword)
                            }
                        }
                        GlobalScope.launch(Dispatchers.IO) {
                            var success = false
                            when (modDetails.updateType) {
                                ModType.APK -> {
                                    success = installAPK(downloadModel.path + "/${modDetails.name}")
                                    buttonData.canCancel = true
                                }

                                ModType.MODULE -> {
                                    val result = installModule(downloadModel.path + "/${modDetails.name}")
                                    success = result.second
                                    if (!success) infoBoxesData.showErrorText.value = result.first
                                    buttonData.canCancel = true

                                }

                                else -> {}
                            }
                            if (success) {
                                changeUpdateType(UpdateStatus.INSTALLED, buttonData)

                                if (ModDetailsStore.getNotificationAndInternetPreferences().value.notifyAppUpdates.value)
                                    if (corePackages.contains(modDetails.packageName))
                                        subscribe("Core")
                                    else
                                        subscribe(modDetails.packageName)
                                MainActivity.getFirebaseAnalytics().logEvent("installed_mod") {
                                    param("mod_name", modDetails.name)
                                    param("mod_version", modDetails.version)
                                    param("mod_author", modDetails.author)
                                    modDetails.keywords.forEach { keyword ->
                                        param("keyword", keyword)
                                    }
                                }
                            }
                            else changeUpdateType(UpdateStatus.NOT_INSTALLED, buttonData)
                            MainActivity.getKetch().clearDb(downloadId.intValue)
                        }

                    } else if (it in listOf(Status.FAILED, Status.CANCELLED, Status.PAUSED)) {
                        changeUpdateType(UpdateStatus.NOT_INSTALLED, buttonData)
                        MainActivity.getKetch().clearDb(downloadId.intValue)
                    } else {
                        changeUpdateType(UpdateStatus.UPDATING, buttonData)
                        buttonData.canCancel = true
                    }
                }
            }
    }

    InfoPopup(infoAlert, modDetails, navController, view)

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {

        PageNAv(stringResource(R.string.mod_info_title), navController, { goBack() })


        LazyColumn(Modifier.background(MaterialTheme.colorScheme.surface)) {
            item {
                InfoPane(modDetails, downloadProgressPercentage, infoState, infoBoxesData, view)
                InfoButtons(buttonData, corePackages)

                ImageCarousel(
                    (1..modDetails.images).map { index -> "${modDetails.url}$index.jpg" },
                    modifier = Modifier
                        .height(168.dp)
                        .padding(start = 8.dp, bottom = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

              InfoBoxes(infoBoxesData)
            }
        }
    }
}



