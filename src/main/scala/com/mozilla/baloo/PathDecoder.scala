package com.mozilla.baloo

import scala.util.matching._
import java.util.UUID

object PathDecoder {
    
    val uriPattern = new Regex("^/([^/]+)/([^/]+)/*([^/]*)$", "endpoint", "ns", "id")
    val uriPatternWithVersion = new Regex("^/([0-9]\\.*[0.9]*)/([^/]+)/([^/]+)/*([^/]*)$", "version", "endpoint", "ns", "id")
    
    def getPathElements(uri: String): (String,String,String,String) = {
        // check to see if this is a versioned URI first
        var m = uriPatternWithVersion.findFirstMatchIn(uri)
        if (m != None) {
            var r = m.get
            val id = r.group("id").length() > 0 match {
               case true => r.group("id")
               case _ => UUID.randomUUID().toString()
            }
            (r.group("version"), r.group("endpoint"), r.group("ns"), id)
        } else {
            // default non-versioned URI
            m = uriPattern.findFirstMatchIn(uri)
            if (m != None) {
                var r = m.get
                val id = r.group("id").length() > 0 match {
                   case true => r.group("id")
                   case _ => UUID.randomUUID().toString()
                }
                ("", r.group("endpoint"), r.group("ns"), id)
            } else {
                ("", "", "", "")
            }
        }
    }
}