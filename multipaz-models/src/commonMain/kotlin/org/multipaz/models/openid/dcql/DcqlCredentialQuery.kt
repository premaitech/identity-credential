package org.multipaz.models.openid.dcql

typealias DcqlCredentialQueryId = String

/**
 * DCQL Credential Query.
 *
 * TODO: add support for
 *   - multiple
 *   - trusted_authorities
 *   - require_cryptographic_holder_binding
 *
 * Reference: OpenID4VP 1.0 Section 6.1.
 *
 * @property id the assigned identifier for the Credential Query.
 * @property format the requested format of the credential e.g. "mso_mdoc" or "dc+sd-jwt".
 * @property mdocDocType the ISO mdoc doctype or `null` if format isn't "mso_mdoc".
 * @property vctValues the array of Verifiable Credential Types or `null`.
 * @property claims a list of claims, see [DcqlClaim].
 * @property claimSets a list of claim sets, see [DcqlClaimSet].
 */
data class DcqlCredentialQuery(
    val id: DcqlCredentialQueryId,
    val format: String,

    // from meta
    val mdocDocType: String? = null,
    val vctValues: List<String>? = null,

    val claims: List<DcqlClaim>,
    val claimSets: List<DcqlClaimSet>,

    // just for optimization
    internal val claimIdToClaim: Map<DcqlClaimId, DcqlClaim>
)