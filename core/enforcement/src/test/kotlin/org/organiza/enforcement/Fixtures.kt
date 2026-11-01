package org.organiza.enforcement

import org.organiza.domain.DomainName
import org.organiza.domain.PackageName
import org.organiza.domain.Parsed

fun pkg(raw: String): PackageName = (PackageName.parse(raw) as Parsed.Valid).value

fun domain(raw: String): DomainName = (DomainName.parse(raw) as Parsed.Valid).value
