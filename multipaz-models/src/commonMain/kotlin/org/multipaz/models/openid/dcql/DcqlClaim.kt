package org.multipaz.models.openid.dcql

import kotlinx.serialization.json.JsonArray

typealias DcqlClaimId = String

/**
 * DCQL claim.
 *
 * Reference: OpenID4VP 1.0 Section 6.3.
 *
 * @property id the identifier for the claim.
 * @property path the path for the claim
 * @property values A set of acceptable values or `null` to not match on value.
 * @property mdocIntentToRetain the "Intent to Return" flag or `null` of this claim isn't for an ISO mdoc data element.
 */
data class DcqlClaim(
    val id: DcqlClaimId? = null,
    val path: JsonArray,
    val values: JsonArray? = null,
    val mdocIntentToRetain: Boolean? = null,        // ISO mdoc specific
)