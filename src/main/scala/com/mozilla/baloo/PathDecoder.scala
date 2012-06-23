package com.mozilla.baloo

import scala.util.matching._

object PathDecoder {
    val uriPattern = new Regex("^/([^/]+)/*([^/]*)$", "ns", "id")
    def getPathElements(uri: String): List[String] = {
        var iter = uriPattern.findAllIn(uri).matchData 
        if (iter.hasNext) {
            var m = iter.next
            m.subgroups   
        } else {
            List[String]()
        }
    }
}