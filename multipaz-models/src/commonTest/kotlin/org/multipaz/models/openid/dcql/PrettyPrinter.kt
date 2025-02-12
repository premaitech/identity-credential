package org.multipaz.models.openid.dcql

internal class PrettyPrinter() {
    private val sb = StringBuilder()
    private var indent = 0

    fun append(line: String) {
        for (n in IntRange(1, indent)) {
            sb.append(" ")
        }
        sb.append(line)
        sb.append("\n")
    }

    fun pushIndent() {
        indent += 2
    }

    fun popIndent() {
        indent -= 2
        check(indent >= 0)
    }

    override fun toString(): String = sb.toString()
}

internal fun DcqlQuery.print(): String {
    val pp = PrettyPrinter()
    print(pp)
    return pp.toString()
}

internal fun DcqlQuery.print(pp: PrettyPrinter) {
    pp.append("credentials:")
    pp.pushIndent()
    credentialQueries.forEach {
        pp.append("credential:")
        pp.pushIndent()
        it.print(pp)
        pp.popIndent()
    }
    pp.popIndent()

    pp.append("credentialSets:")
    pp.pushIndent()
    if (credentialSetQueries.isNotEmpty()) {
        credentialSetQueries.forEach {
            pp.append("credentialSet:")
            pp.pushIndent()
            it.print(pp)
            pp.popIndent()
        }
    } else {
        pp.append("<empty>")
    }
    pp.popIndent()
}

internal fun DcqlCredentialSetQuery.print(pp: PrettyPrinter) {
    pp.append("purpose: $purpose")
    pp.append("required: $required")
    pp.append("options:")
    pp.pushIndent()
    for (option in options) {
        option.print(pp)
    }
    pp.popIndent()
}

internal fun DcqlCredentialSetOption.print(pp: PrettyPrinter) {
    pp.append("$credentialIds")
}

internal fun DcqlCredentialQuery.print(pp: PrettyPrinter) {
    pp.append("id: $id")
    pp.append("format: $format")
    if (mdocDocType != null) {
        pp.append("mdocDocType: $mdocDocType")
    }
    if (vctValues != null) {
        pp.append("vctValues: $vctValues")
    }
    pp.append("claims:")
    pp.pushIndent()
    claims.forEach {
        pp.append("claim:")
        pp.pushIndent()
        it.print(pp)
        pp.popIndent()
    }
    pp.popIndent()
    pp.append("claimSets:")
    pp.pushIndent()
    if (claimSets.isNotEmpty()) {
        claimSets.forEach {
            pp.append("claimset:")
            pp.pushIndent()
            it.print(pp)
            pp.popIndent()
        }
    } else {
        pp.append("<empty>")
    }
    pp.popIndent()
}

internal fun DcqlClaimSet.print(pp: PrettyPrinter) {
    pp.append("ids: $claimIdentifiers")
}

internal fun DcqlClaim.print(pp: PrettyPrinter) {
    if (id != null) {
        pp.append("id: $id")
    }
    pp.append("path: $path")
    if (values != null) {
        pp.append("values: $values")
    }
    if (mdocIntentToRetain == true) {
        pp.append("mdocIntentToRetain: $mdocIntentToRetain")
    }
}

internal fun CredentialResponse.print(pp: PrettyPrinter) {
    pp.append("response:")
    pp.pushIndent()
    pp.append("credentialQuery:")
    pp.pushIndent()
    pp.append("id: ${credentialQuery.id}")
    pp.popIndent()
    if (credentialSetQuery != null) {
        pp.append("credentialSetQuery:")
        pp.pushIndent()
        pp.append("purpose: ${credentialSetQuery.purpose}")
        pp.append("required: ${credentialSetQuery.required}")
        pp.popIndent()
    }
    pp.append("matches:")
    pp.pushIndent()
    if (matches.isEmpty()) {
        pp.append("<empty>")
    } else {
        for (match in matches.sortedBy { it.credential.document.metadata.displayName }) {
            pp.append("match:")
            pp.pushIndent()
            pp.append("credential: ${match.credential.document.metadata.displayName}")
            pp.append("claims:")
            pp.pushIndent()
            for ((requestClaim, credentialClaimValue) in match.claimValues) {
                pp.append("claim:")
                pp.pushIndent()
                pp.append("path: ${requestClaim.path}")
                pp.append("value: ${credentialClaimValue.render()}")
                pp.popIndent()
            }
            pp.popIndent()
            pp.popIndent()
        }
    }
    pp.popIndent()
    pp.popIndent()
}


fun List<CredentialResponse>.prettyPrint(): String {
    val pp = PrettyPrinter()
    pp.append("responses:")
    pp.pushIndent()
    if (size == 0) {
        pp.append("<empty>")
    } else {
        for (n in IntRange(0, this.size - 1)) {
            val request = elementAt(n)
            request.print(pp)
        }
    }
    pp.popIndent()
    return pp.toString()
}

