package com.mozilla.baloo

import scala.util.matching._

object PathDecoder {
    val uriPattern = new Regex("^/([^/]+)/*([^/]*)$", "ns", "id")
    def getPathElements(uri: String): List[String] = {
        var m = uriPattern.findFirstMatchIn(uri)
        if (m != None) {
            m.get.subgroups
        } else {
            List[String]()
        }
    }
}