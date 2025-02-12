package org.multipaz.models.openid.dcql

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.multipaz.claim.Claim
import org.multipaz.claim.JsonClaim
import org.multipaz.claim.MdocClaim
import org.multipaz.credential.Credential
import org.multipaz.document.DocumentStore
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.sdjwt.credential.SdJwtVcCredential

/**
 * DCQL top-level query.
 *
 * Use [Companion.fromJson] to construct an instance and use [execute] to
 * select credentials which satisfy the query.
 *
 * Reference: OpenID4VP 1.0 Section 6.
 *
 * @property credentialQueries list of Credential Queries.
 * @property credentialSetQueries list of Credential Set Queries.
 */
data class DcqlQuery(
    val credentialQueries: List<DcqlCredentialQuery>,
    val credentialSetQueries: List<DcqlCredentialSetQuery>
) {
    suspend fun execute(
        documentStore: DocumentStore
    ): List<CredentialResponse> {
        val result = mutableListOf<CredentialResponse>()
        for (credentialQuery in credentialQueries) {
            val credsSatisfyingMeta = when (credentialQuery.format) {
                "mso_mdoc" -> {
                    val ret = mutableListOf<Credential>()
                    for (documentId in documentStore.listDocuments()) {
                        val document = documentStore.lookupDocument(documentId) ?: continue
                        document.getCertifiedCredentials().find {
                            it is MdocCredential && it.docType == credentialQuery.mdocDocType
                        }?.let { ret.add(it) }
                    }
                    ret
                }

                "dc+sd-jwt" -> {
                    val ret = mutableListOf<Credential>()
                    for (documentId in documentStore.listDocuments()) {
                        val document = documentStore.lookupDocument(documentId) ?: continue
                        document.getCertifiedCredentials().find {
                            it is SdJwtVcCredential && credentialQuery.vctValues!!.contains(it.vct)
                        }?.let { ret.add(it) }
                    }
                    ret
                }

                else -> emptyList()
            }

            val matches = mutableListOf<CredentialResponseMatch>()
            for (cred in credsSatisfyingMeta) {
                val claimsInCredential = cred.getClaims(null)
                if (credentialQuery.claimSets.isEmpty()) {
                    var didNotMatch = false
                    val matchingClaimValues = mutableListOf<Pair<DcqlClaim, Claim>>()
                    for (claim in credentialQuery.claims) {
                        val matchingCredentialClaimValue = when (cred) {
                            is MdocCredential -> mdocFindMatchingClaimValue(claimsInCredential, claim)
                            is SdJwtVcCredential -> sdJwtVcFindMatchingClaimValue(claimsInCredential, claim)
                            else -> null
                        }
                        if (matchingCredentialClaimValue != null) {
                            matchingClaimValues.add(Pair(claim, matchingCredentialClaimValue))
                        } else {
                            didNotMatch = true
                            break
                        }
                    }
                    if (!didNotMatch) {
                        // All claims matched, we have a candidate
                        matches.add(
                            CredentialResponseMatch(
                                credential = cred,
                                claimValues = matchingClaimValues
                            )
                        )
                    }
                } else {
                    // Go through all the claim sets, one at a time, pick the first to match
                    for (claimSet in credentialQuery.claimSets) {
                        var didNotMatch = false
                        val matchingClaimValues = mutableListOf<Pair<DcqlClaim, Claim>>()
                        for (claimId in claimSet.claimIdentifiers) {
                            val claim = credentialQuery.claimIdToClaim[claimId]
                            if (claim == null) {
                                didNotMatch = true
                                break
                            }
                            val credentialClaimValue = when (cred) {
                                is MdocCredential -> mdocFindMatchingClaimValue(claimsInCredential, claim)
                                is SdJwtVcCredential -> sdJwtVcFindMatchingClaimValue(claimsInCredential, claim)
                                else -> null
                            }
                            if (credentialClaimValue != null) {
                                matchingClaimValues.add(Pair(claim, credentialClaimValue))
                            } else {
                                didNotMatch = true
                                break
                            }
                        }
                        if (!didNotMatch) {
                            // All claims matched, we have a candidate
                            matches.add(
                                CredentialResponseMatch(
                                    credential = cred,
                                    claimValues = matchingClaimValues
                                )
                            )
                            break
                        }
                    }
                }
            }
            result.add(
                CredentialResponse(
                    credentialQuery = credentialQuery,
                    credentialSetQuery = null,
                    matches = matches
                )
            )
        }

        // From 6.3.1.2. Selecting Credentials:
        //
        //   If credential_sets is not provided, the Verifier requests presentations for
        //   all Credentials in credentials to be returned.
        //
        if (credentialSetQueries.isEmpty()) {
            // So, really simple, bail unless we have at least one match per requested credential
            for (response in result) {
                if (response.matches.isEmpty()) {
                    throw DcqlCredentialQueryException(
                        "No matches for credential query with id ${response.credentialQuery.id}"
                    )
                }
            }
            return result
        }

        // From 6.3.1.2. Selecting Credentials:
        //
        //   Otherwise, the Verifier requests presentations of Credentials to be returned satisfying
        //
        //     - all of the Credential Set Queries in the credential_sets array where the
        //       required attribute is true or omitted, and
        //     - optionally, any of the other Credential Set Queries.
        //
        val csqRet = mutableListOf<CredentialResponse>()
        for (csq in credentialSetQueries) {
            // In this case, simply go through all the matches produced above and pick the
            // credentials from the highest preferred option. If none of them work, bail only
            // if the credential set was required.
            //
            var satisfiedCsq = false
            for (option in csq.options) {
                if (option.isSatisfied(result)) {
                    for (credentialId in option.credentialIds) {
                        val responseMatched = result.find { it.credentialQuery.id == credentialId }!!
                        csqRet.add(
                            CredentialResponse(
                                credentialQuery = responseMatched.credentialQuery,
                                credentialSetQuery = csq,
                                matches = responseMatched.matches
                            )
                        )
                    }
                    satisfiedCsq = true
                    break
                }
            }
            if (!satisfiedCsq && csq.required) {
                throw DcqlCredentialQueryException(
                    "No credentials match required credential_set query with purpose ${csq.purpose}"
                )
            }
        }
        return csqRet
    }

    companion object {

        fun fromJson(json: JsonObject): DcqlQuery {
            val dcqlCredentialQueries = mutableListOf<DcqlCredentialQuery>()
            val dcqlCredentialSetQueries = mutableListOf<DcqlCredentialSetQuery>()

            val credentials = json["credentials"]!!.jsonArray
            for (credential in credentials) {
                val c = credential.jsonObject
                val id = c["id"]!!.jsonPrimitive.content
                val format = c["format"]!!.jsonPrimitive.content
                val meta = c["meta"]!!.jsonObject
                var mdocDocType: String? = null
                var vctValues: List<String>? = null
                when (format) {
                    "mso_mdoc" -> {
                        mdocDocType = meta["doctype_value"]!!.jsonPrimitive.content
                    }

                    "dc+sd-jwt" -> {
                        vctValues = meta["vct_values"]!!.jsonArray.map { it.jsonPrimitive.content }
                    }
                }

                val dcqlClaims = mutableListOf<DcqlClaim>()
                val dcqlClaimIdToClaim = mutableMapOf<DcqlClaimId, DcqlClaim>()
                val dcqlClaimSets = mutableListOf<DcqlClaimSet>()

                val claims = c["claims"]!!.jsonArray
                check(claims.size > 0)
                for (claim in claims) {
                    val cl = claim.jsonObject
                    val claimId = cl["id"]?.jsonPrimitive?.content
                    val path = cl["path"]!!.jsonArray
                    val values = cl["values"]?.jsonArray
                    val mdocIntentToRetain = cl["intent_to_retain"]?.jsonPrimitive?.boolean
                    val dcqlClaim = DcqlClaim(
                        id = claimId,
                        path = path,
                        values = values,
                        mdocIntentToRetain = mdocIntentToRetain
                    )
                    dcqlClaims.add(dcqlClaim)
                    if (claimId != null) {
                        dcqlClaimIdToClaim.put(claimId, dcqlClaim)
                    }
                }

                val claimSets = c["claim_sets"]?.jsonArray
                if (claimSets != null) {
                    for (claimSet in claimSets) {
                        val cs = claimSet.jsonArray
                        dcqlClaimSets.add(
                            DcqlClaimSet(
                                claimIdentifiers = cs.map { it.jsonPrimitive.content }
                            )
                        )
                    }
                }

                dcqlCredentialQueries.add(
                    DcqlCredentialQuery(
                        id = id,
                        format = format,
                        mdocDocType = mdocDocType,
                        vctValues = vctValues,
                        claims = dcqlClaims,
                        claimSets = dcqlClaimSets,
                        claimIdToClaim = dcqlClaimIdToClaim
                    )
                )
            }

            val credentialSets = json["credential_sets"]?.jsonArray
            if (credentialSets != null) {
                for (credentialSet in credentialSets) {
                    val s = credentialSet.jsonObject
                    val purpose = s["purpose"]!!
                    val required = s["required"]?.jsonPrimitive?.boolean ?: true

                    val credentialSetOptions = mutableListOf<DcqlCredentialSetOption>()

                    val options = s["options"]!!.jsonArray
                    for (option in options) {
                        credentialSetOptions.add(
                            DcqlCredentialSetOption(
                                credentialIds = option.jsonArray.map { it.jsonPrimitive.content }
                            )
                        )
                    }

                    dcqlCredentialSetQueries.add(
                        DcqlCredentialSetQuery(
                            purpose = purpose,
                            required = required,
                            options = credentialSetOptions
                        )
                    )
                }
            }

            return DcqlQuery(
                credentialQueries = dcqlCredentialQueries,
                credentialSetQueries = dcqlCredentialSetQueries
            )
        }
    }
}

private fun Claim.filterValueMatch(
    values: JsonArray?,
): Claim? {
    if (values == null) {
        return this
    }
    when (this) {
        is JsonClaim -> if (values.contains(value)) return this
        is MdocClaim -> if (values.contains(value.toJson())) return this
    }
    return null
}

internal fun mdocFindMatchingClaimValue(claims: List<Claim>, dcqlClaim: DcqlClaim): MdocClaim? {
    if (dcqlClaim.path.size != 2) {
        return null
    }
    for (credentialClaim in claims) {
        credentialClaim as MdocClaim
        if (credentialClaim.namespaceName == dcqlClaim.path[0].jsonPrimitive.content &&
            credentialClaim.dataElementName == dcqlClaim.path[1].jsonPrimitive.content) {
            return credentialClaim.filterValueMatch(dcqlClaim.values) as MdocClaim?
        }
    }
    return null
}

internal fun sdJwtVcFindMatchingClaimValue(claims: List<Claim>, dcqlClaim: DcqlClaim): JsonClaim? {
    check(dcqlClaim.path.size >= 1)
    check(dcqlClaim.path[0].isString)
    var ret: JsonClaim? = null
    for (credentialClaim in claims) {
        credentialClaim as JsonClaim
        if (credentialClaim.claimPath[0].jsonPrimitive.content == dcqlClaim.path[0].jsonPrimitive.content) {
            ret = credentialClaim
            break
        }
    }
    if (ret == null) {
        return null
    }
    if (dcqlClaim.path.size == 1) {
        return ret.filterValueMatch(dcqlClaim.values) as JsonClaim?
    }

    // OK, path>1 so we descend into the object...
    var currentObject: JsonElement? = ret.value

    for (n in IntRange(1, dcqlClaim.path.size - 1)) {
        val pathComponent = dcqlClaim.path[n]
        if (pathComponent.isString) {
            if (currentObject is JsonArray) {
                val newObject = buildJsonArray {
                    for (element in currentObject.jsonArray) {
                        add(element.jsonObject[pathComponent.jsonPrimitive.content]!!)
                    }
                }
                currentObject = newObject
            } else if (currentObject is JsonObject) {
                currentObject = currentObject.jsonObject[pathComponent.jsonPrimitive.content]
            } else {
                throw Error("Can only select from object or array of objects")
            }
        } else if (pathComponent.isNumber) {
            currentObject = currentObject!!.jsonArray[pathComponent.jsonPrimitive.int]
        } else if (pathComponent.isNull) {
            currentObject = currentObject!!.jsonArray
        }
    }
    if (currentObject == null) {
        return null
    }
    // TODO: take a DocumentTypeRepository so we can look up attribute
    return JsonClaim(
        displayName = dcqlClaim.path.joinToString(separator = "."),
        attribute = null,
        claimPath = dcqlClaim.path,
        value = currentObject
    ).filterValueMatch(dcqlClaim.values) as JsonClaim?
}


private val JsonElement.isNull: Boolean
    get() = this is JsonNull

private val JsonElement.isNumber: Boolean
    get() = this is JsonPrimitive && !isString && longOrNull != null

private val JsonElement.isString: Boolean
    get() = this is JsonPrimitive && isString
