package org.multipaz.models.openid.dcql

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import org.multipaz.cbor.Tstr
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.multipaz.document.Document
import org.multipaz.models.presentment.DocumentStoreTestHarness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestPathSelection {

    companion object {
        private suspend fun addMdlErika(harness: DocumentStoreTestHarness): Document {
            return harness.provisionMdoc(
                displayName = "my-mDL-Erika",
                docType = "org.iso.18013.5.1.mDL",
                data = mapOf(
                    "org.iso.18013.5.1" to listOf(
                        "given_name" to Tstr("Erika"),
                        "family_name" to Tstr("Mustermann"),
                        "resident_address" to Tstr("Sample Street 123"),
                    )
                )
            )
        }

        private suspend fun addPidErika(harness: DocumentStoreTestHarness): Document {
            return harness.provisionSdJwtVc(
                displayName = "my-PID-Erika",
                vct = "https://credentials.example.com/identity_credential",
                data = listOf(
                    "given_name" to JsonPrimitive("Erika"),
                    "family_name" to JsonPrimitive("Mustermann"),
                    "address" to buildJsonObject {
                        put("country", JsonPrimitive("US"))
                        put("state", JsonPrimitive("CA"))
                        put("postal_code", JsonPrimitive(90210))
                        put("street_address", JsonPrimitive("Sample Street 123"))
                        put("house_number", JsonPrimitive(123))
                    },
                    "nationalities" to buildJsonArray { add("German"); add("American") },
                    "degrees" to buildJsonArray {
                        addJsonObject {
                            put("type", JsonPrimitive("Bachelor of Science"))
                            put("university", JsonPrimitive("University of Betelgeuse"))
                        }
                        addJsonObject {
                            put("type", JsonPrimitive("Master of Science"))
                            put("university", JsonPrimitive("University of Betelgeuse"))
                        }
                    }
                )
            )
        }
    }

    @Test
    fun pathSelectionMdoc() = runTest {
        val harness = DocumentStoreTestHarness()
        harness.initialize()
        val mdlErika = addMdlErika(harness)
        val mdlErikaClaims = mdlErika.getCertifiedCredentials()[0].getClaims(null)

        assertEquals(
            "Erika",
            mdocFindMatchingClaimValue(
                claims = mdlErikaClaims,
                dcqlClaim = DcqlClaim(
                    path = buildJsonArray { add("org.iso.18013.5.1"); add("given_name") }
                )
            )!!.value.asTstr
        )

        assertEquals(
            "Erika",
            mdocFindMatchingClaimValue(
                claims = mdlErikaClaims,
                dcqlClaim = DcqlClaim(
                    path = buildJsonArray { add("org.iso.18013.5.1"); add("given_name") },
                    values = buildJsonArray { add("Erika") }
                )
            )!!.value.asTstr
        )

        assertNull(
            mdocFindMatchingClaimValue(
                claims = mdlErikaClaims,
                dcqlClaim = DcqlClaim(
                    path = buildJsonArray { add("org.iso.18013.5.1"); add("given_name") },
                    values = buildJsonArray { add("Max") }
                )
            )
        )
    }

    @Test
    fun pathSelectionVc() = runTest {
        val harness = DocumentStoreTestHarness()
        harness.initialize()
        val pidErika = addPidErika(harness)
        val pidErikaClaims = pidErika.getCertifiedCredentials()[0].getClaims(null)

        assertEquals(
            JsonPrimitive("Erika"),
            sdJwtVcFindMatchingClaimValue(
                claims = pidErikaClaims,
                dcqlClaim = DcqlClaim(path = buildJsonArray { add("given_name") })
            )?.value
        )

        assertEquals(
            null,
            sdJwtVcFindMatchingClaimValue(
                claims = pidErikaClaims,
                dcqlClaim = DcqlClaim(path = buildJsonArray { add("does-not-exist") })
            )?.value
        )

        assertEquals(
            JsonPrimitive("US"),
            sdJwtVcFindMatchingClaimValue(
                claims = pidErikaClaims,
                dcqlClaim = DcqlClaim(path = buildJsonArray { add("address"); add("country") })
            )?.value
        )

        assertEquals(
            JsonPrimitive("CA"),
            sdJwtVcFindMatchingClaimValue(
                claims = pidErikaClaims,
                dcqlClaim = DcqlClaim(path = buildJsonArray { add("address"); add("state") })
            )?.value
        )

        assertEquals(
            JsonPrimitive(123),
            sdJwtVcFindMatchingClaimValue(
                claims = pidErikaClaims,
                dcqlClaim = DcqlClaim(path = buildJsonArray { add("address"); add("house_number") })
            )?.value
        )

        assertEquals(
            null,
            sdJwtVcFindMatchingClaimValue(
                claims = pidErikaClaims,
                dcqlClaim = DcqlClaim(path = buildJsonArray { add("address"); add("does-not-exist") })
            )?.value
        )

        assertEquals(
            buildJsonObject {
                put("country", JsonPrimitive("US"))
                put("state", JsonPrimitive("CA"))
                put("postal_code", JsonPrimitive(90210))
                put("street_address", JsonPrimitive("Sample Street 123"))
                put("house_number", JsonPrimitive(123))
            },
            sdJwtVcFindMatchingClaimValue(
                claims = pidErikaClaims,
                dcqlClaim = DcqlClaim(path = buildJsonArray { add("address") })
            )?.value
        )

        assertEquals(
            JsonPrimitive("American"),
            sdJwtVcFindMatchingClaimValue(
                claims = pidErikaClaims,
                dcqlClaim = DcqlClaim(path = buildJsonArray { add("nationalities"); add(1) })
            )?.value
        )

        assertEquals(
            buildJsonArray {
                add("Bachelor of Science")
                add("Master of Science")
            },
            sdJwtVcFindMatchingClaimValue(
                claims = pidErikaClaims,
                dcqlClaim = DcqlClaim(path = buildJsonArray { add("degrees"); add(JsonNull); add("type") })
            )?.value
        )

        assertEquals(
            JsonPrimitive("Erika"),
            sdJwtVcFindMatchingClaimValue(
                claims = pidErikaClaims,
                dcqlClaim = DcqlClaim(
                    path = buildJsonArray { add("given_name") },
                    values = buildJsonArray { add("Erika") }
                )
            )?.value
        )

        assertEquals(
            null,
            sdJwtVcFindMatchingClaimValue(
                claims = pidErikaClaims,
                dcqlClaim = DcqlClaim(
                    path = buildJsonArray { add("given_name") },
                    values = buildJsonArray { add("Max") }
                )
            )?.value
        )
    }
}
