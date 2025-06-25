package org.multipaz.compose.presentment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Simple
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.engagement.EngagementGenerator
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.advertise
import org.multipaz.mdoc.transport.waitForConnection
import org.multipaz.models.presentment.MdocPresentmentMechanism
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.models.presentment.PresentmentSource
import org.multipaz.prompt.PromptModel
import org.multipaz.util.toBase64Url

/**
 * Settings to use when doing presentment with QR engagement according to ISO/IEC 18013-5:2021.
 *
 * @property availableConnectionMethods the connection methods to advertise in the QR code.
 * @property createTransportOptions the [MdocTransportOptions] to use when creating a new [MdocTransport]
 */
data class MdocProximityQrSettings(
    val availableConnectionMethods: List<MdocConnectionMethod>,
    val createTransportOptions: MdocTransportOptions
)

/**
 * A composable for doing presentment with QR engagement according to ISO/IEC 18013-5:2021.
 *
 * @param modifier a [Modifier].
 * @param appName the name of the application.
 * @param appIconPainter the icon for the application.
 * @param presentmentModel the [PresentmentModel] to use which must have a [PromptModel] associated with it.
 * @param presentmentSource an object for application to provide data and policy.
 * @param promptModel a [PromptModel]
 * @param documentTypeRepository a [DocumentTypeRepository] used to find metadata about documents being requested.
 * @param showQrButton a composable to show for a button to generate a QR code. It should call [onQrButtonClicked]
 *   when the user presses the button and pass a [MdocProximityQrSettings] which contains the settings for what
 *   kind of [org.multipaz.mdoc.transport.MdocTransport] instances to advertise and what options to use when
 *   creating the transports.
 * @param showQrCode a composable which shows the QR code and asks the user to scan it.
 * @param transportFactory the [MdocTransportFactory] to use for creating a transport.
 */
@Composable
fun MdocProximityQrPresentment(
    modifier: Modifier = Modifier,
    appName: String,
    appIconPainter: Painter,
    presentmentModel: PresentmentModel,
    presentmentSource: PresentmentSource,
    promptModel: PromptModel,
    documentTypeRepository: DocumentTypeRepository,
    showQrButton: @Composable (onQrButtonClicked: (settings: MdocProximityQrSettings) -> Unit) -> Unit,
    showQrCode: @Composable (uri: String) -> Unit,
    transportFactory: MdocTransportFactory = MdocTransportFactory.Default,
) {
    val coroutineScope = rememberCoroutineScope { promptModel }
    val state = presentmentModel.state.collectAsState()
    var qrCodeToShow = remember { mutableStateOf<String?>(null) }

    println("state = ${state.value}")

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state.value) {
            PresentmentModel.State.IDLE -> {
                showQrButton({ qrSettings ->
                    println("on QR button clicked qrSettings: $qrSettings")
                    presentmentModel.reset()
                    presentmentModel.setConnecting()
                    coroutineScope.launch {
                        val eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256)
                        val advertisedTransports = qrSettings.availableConnectionMethods.advertise(
                            role = MdocRole.MDOC,
                            transportFactory = transportFactory,
                            options = MdocTransportOptions(bleUseL2CAP = true),
                        )
                        val engagementGenerator = EngagementGenerator(
                            eSenderKey = eDeviceKey.publicKey,
                            version = "1.0"
                        )
                        engagementGenerator.addConnectionMethods(advertisedTransports.map {
                            it.connectionMethod
                        })
                        val encodedDeviceEngagement = ByteString(engagementGenerator.generate())

                        qrCodeToShow.value = "mdoc:" + encodedDeviceEngagement.toByteArray().toBase64Url()
                        println("QR code URI: ${qrCodeToShow.value}")

                        val transport = advertisedTransports.waitForConnection(
                            eSenderKey = eDeviceKey.publicKey,
                            coroutineScope = coroutineScope
                        )
                        presentmentModel.setMechanism(
                            MdocPresentmentMechanism(
                                transport = transport,
                                eDeviceKey = eDeviceKey,
                                encodedDeviceEngagement = encodedDeviceEngagement,
                                handover = Simple.NULL,
                                engagementDuration = null,
                                allowMultipleRequests = false
                            )
                        )
                        qrCodeToShow.value = null
                    }
                })
            }

            PresentmentModel.State.CONNECTING -> {
                showQrCode(qrCodeToShow.value!!)
            }

            PresentmentModel.State.WAITING_FOR_SOURCE,
            PresentmentModel.State.PROCESSING,
            PresentmentModel.State.WAITING_FOR_DOCUMENT_SELECTION,
            PresentmentModel.State.WAITING_FOR_CONSENT,
            PresentmentModel.State.COMPLETED -> {
                Presentment(
                    appName = appName,
                    appIconPainter = appIconPainter,
                    presentmentModel = presentmentModel,
                    presentmentSource = presentmentSource,
                    documentTypeRepository = documentTypeRepository,
                    onPresentmentComplete = {
                        presentmentModel.reset()
                    },
                )
            }
        }
    }
}