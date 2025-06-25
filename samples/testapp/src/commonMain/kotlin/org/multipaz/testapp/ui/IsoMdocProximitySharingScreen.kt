package org.multipaz.testapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.multipaz.models.presentment.MdocPresentmentMechanism
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Simple
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodNfc
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.prompt.PromptModel
import org.multipaz.testapp.TestAppSettingsModel
import org.multipaz.util.Logger
import org.multipaz.util.UUID
import org.multipaz.util.toBase64Url
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.jetbrains.compose.resources.painterResource
import org.multipaz.compose.permissions.rememberBluetoothPermissionState
import org.multipaz.compose.presentment.MdocProximityQrPresentment
import org.multipaz.compose.presentment.MdocProximityQrSettings
import org.multipaz.compose.qrcode.generateQrCode
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.testapp.ui.ShowQrCodeDialog
import org.multipaz.mdoc.engagement.EngagementGenerator
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.transport.advertise
import org.multipaz.mdoc.transport.waitForConnection
import org.multipaz.models.presentment.PresentmentSource
import org.multipaz.testapp.Platform
import org.multipaz.testapp.platformAppIcon
import org.multipaz.testapp.platformAppName

private const val TAG = "IsoMdocProximitySharingScreen"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun IsoMdocProximitySharingScreen(
    presentmentSource: PresentmentSource,
    presentmentModel: PresentmentModel,
    settingsModel: TestAppSettingsModel,
    promptModel: PromptModel,
    documentTypeRepository: DocumentTypeRepository,
    onNavigateToPresentmentScreen: () -> Unit,
    showToast: (message: String) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope { promptModel }
    val blePermissionState = rememberBluetoothPermissionState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!blePermissionState.isGranted) {
            Button(
                onClick = { coroutineScope.launch { blePermissionState.launchPermissionRequest() } }
            ) {
                Text("Request BLE permissions")
            }
        } else {
            MdocProximityQrPresentment(
                modifier = Modifier,
                appName = platformAppName,
                appIconPainter = painterResource(platformAppIcon),
                presentmentModel = presentmentModel,
                presentmentSource = presentmentSource,
                promptModel = promptModel,
                documentTypeRepository = documentTypeRepository,
                showQrButton = { onQrButtonClicked ->
                    Button(onClick = {
                        onQrButtonClicked(
                            MdocProximityQrSettings(
                                // TODO: use settingsModel instead of hard-coding here
                                availableConnectionMethods = listOf(
                                    MdocConnectionMethodBle(
                                        supportsPeripheralServerMode = false,
                                        supportsCentralClientMode = true,
                                        peripheralServerModeUuid = null,
                                        centralClientModeUuid = UUID.randomUUID(),
                                    )
                                ),
                                createTransportOptions = MdocTransportOptions(bleUseL2CAP = true)
                            )
                        )
                    }) {
                        Text("Present mDL via QR")
                    }
                },
                showQrCode = { uri ->
                    val qrCodeBitmap = remember { generateQrCode(uri) }
                    Text(text = "Present QR code to mdoc reader")
                    Image(
                        modifier = Modifier.fillMaxWidth(),
                        bitmap = qrCodeBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth
                    )
                    Button(onClick = { presentmentModel.reset() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

    /*
    val showQrCode = remember { mutableStateOf<ByteString?>(null) }
    if (showQrCode.value != null && presentmentModel.state.collectAsState().value != PresentmentModel.State.PROCESSING) {
        Logger.dCbor(TAG, "DeviceEngagement:", showQrCode.value!!.toByteArray())
        val deviceEngagementQrCode = "mdoc:" + showQrCode.value!!.toByteArray().toBase64Url()
        ShowQrCodeDialog(
            title = { Text(text = "Scan QR code") },
            text = { Text(text = "Scan this QR code on another device") },
            dismissButton = "Close",
            data = deviceEngagementQrCode,
            onDismiss = {
                showQrCode.value = null
                presentmentModel.reset()
            }
        )
    }

    if (!blePermissionState.isGranted) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        blePermissionState.launchPermissionRequest()
                    }
                }
            ) {
                Text("Request BLE permissions")
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.padding(8.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            presentmentModel.reset()
                            presentmentModel.setConnecting()
                            presentmentModel.presentmentScope.launch() {
                                val connectionMethods = mutableListOf<MdocConnectionMethod>()
                                val bleUuid = UUID.randomUUID()
                                if (settingsModel.presentmentBleCentralClientModeEnabled.value) {
                                    connectionMethods.add(
                                        MdocConnectionMethodBle(
                                            supportsPeripheralServerMode = false,
                                            supportsCentralClientMode = true,
                                            peripheralServerModeUuid = null,
                                            centralClientModeUuid = bleUuid,
                                        )
                                    )
                                }
                                if (settingsModel.presentmentBlePeripheralServerModeEnabled.value) {
                                    connectionMethods.add(
                                        MdocConnectionMethodBle(
                                            supportsPeripheralServerMode = true,
                                            supportsCentralClientMode = false,
                                            peripheralServerModeUuid = bleUuid,
                                            centralClientModeUuid = null,
                                        )
                                    )
                                }
                                if (settingsModel.presentmentNfcDataTransferEnabled.value) {
                                    connectionMethods.add(
                                        MdocConnectionMethodNfc(
                                            commandDataFieldMaxLength = 0xffff,
                                            responseDataFieldMaxLength = 0x10000
                                        )
                                    )
                                }
                                val options = MdocTransportOptions(
                                    bleUseL2CAP = settingsModel.presentmentBleL2CapEnabled.value
                                )
                                if (connectionMethods.isEmpty()) {
                                    showToast("No connection methods selected")
                                } else {
                                    try {
                                        doHolderFlow(
                                            connectionMethods = connectionMethods,
                                            handover = Simple.NULL,
                                            options = options,
                                            allowMultipleRequests = settingsModel.presentmentAllowMultipleRequests.value,
                                            sessionEncryptionCurve = settingsModel.presentmentSessionEncryptionCurve.value,
                                            showToast = showToast,
                                            presentmentModel = presentmentModel,
                                            showQrCode = showQrCode,
                                            onNavigateToPresentationScreen = onNavigateToPresentmentScreen,
                                        )
                                    } catch (e: Throwable) {
                                        e.printStackTrace()
                                        showToast("Error: $e")
                                    }
                                }
                            }
                        },
                    ) {
                        Text(text = "Share via QR")
                    }
                }
            }
        }
    }
     */
