/*
 * Copyright 2012 Mozilla Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mozilla.baloo

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.builder.{Server, ServerBuilder}
import com.twitter.finagle.http.{Request, Response, CompressedHttp, CompressedRichHttp}
import com.twitter.util.Future
import java.net.InetSocketAddress
import java.util.Properties
import java.util.UUID
import kafka.producer._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.util.CharsetUtil.UTF_8
import scala.collection.JavaConversions
import com.mozilla.baloo.validator.Validator
import com.mozilla.baloo.http._
import com.mozilla.bagheera.BagheeraProto.BagheeraMessage
import com.mozilla.bagheera.BagheeraProto.BagheeraMessage.Operation
import com.google.protobuf.ByteString

object Baloo {
    
    class ExceptionHandler extends SimpleFilter[Request, Response] {
        def apply(request: Request, service: Service[Request, Response]) = {
            service(request) handle { case error =>
                val statusCode = error match {
                    case _: InvalidPathException => 
                        NOT_FOUND
                    case _: HttpSecurityException =>
                        FORBIDDEN
                    case _: IllegalArgumentException =>
                        NOT_ACCEPTABLE
                    case _ =>
                        INTERNAL_SERVER_ERROR
                }
                
                val errorResponse = new DefaultHttpResponse(HTTP_1_1, statusCode)
                Response(errorResponse)
            }
        }
    }
    
    class AccessFilter(validator: Validator) extends SimpleFilter[Request, Response] {
        def apply(request: Request, service: Service[Request, Response]) = {
            val (version,endpoint,ns,id) = PathDecoder.getPathElements(request.getUri())
            if (!validator.isValidNamespace(ns)) {
                Future.exception(new HttpSecurityException("Tried to access invalid resource"))
            } else if (!(request.getMethod() == HttpMethod.POST || request.getMethod() == HttpMethod.PUT)) {
                Future.exception(new HttpSecurityException("Tried to access " + request.getMethod() + " resource"))
            } else {
                service(request)
            }
        }
    }
    
    class KafkaService(producer: Producer[String,BagheeraMessage]) extends Service[Request, Response] {
        def apply(request: Request) = {
            val (version,endpoint,ns,id) = PathDecoder.getPathElements(request.getUri())
            var content = request.getContent();
            val respCode = content.readable() && content.readableBytes() > 0 match {
                case true => 
                    var bmsgBuilder = BagheeraMessage.newBuilder()
                    bmsgBuilder.setNamespace(ns)
                    bmsgBuilder.setId(id)
                    bmsgBuilder.setPayload(ByteString.copyFrom(content.toByteBuffer()))
                    bmsgBuilder.setTimestamp(System.currentTimeMillis())
                    producer.send(new ProducerData[String,BagheeraMessage](ns, bmsgBuilder.build()))
                    if (request.containsHeader("X-Obsolete-Document")) {
                        val obsoleteId = request.getHeader("X-Obsolete-Document");
                        var obsBuilder = BagheeraMessage.newBuilder();
                        obsBuilder.setOperation(Operation.DELETE);
                        obsBuilder.setNamespace(ns);
                        obsBuilder.setId(obsoleteId);
                        obsBuilder.setIpAddr(bmsgBuilder.getIpAddr());
                        obsBuilder.setTimestamp(bmsgBuilder.getTimestamp());
                        producer.send(new ProducerData[String,BagheeraMessage](ns, obsBuilder.build()));
                    }
                    CREATED
                case _ =>
                    BAD_REQUEST
            }
            val response = new DefaultHttpResponse(HTTP_1_1, respCode)
            response.setContent(copiedBuffer(id, UTF_8))
            Future.value(Response(response))
        }
    }
    
    def main(args: Array[String]) {
        val port = Option(System getenv "PORT") match {
            case Some(port) => port.toInt
            case None => 8080
        }
        val validNamespaces = Array[String]("telemetry")
        val validator = new Validator(validNamespaces)
        
        // setup kafka producer
        val props = new java.util.Properties
        val in = getClass.getResourceAsStream("/kafka.producer.properties")
        try {
            props.load(in)
        } finally {
            if (in != None) {
                in.close()
            }
        }
        val producerConfig = new ProducerConfig(props)
        val producer = new Producer[String,BagheeraMessage](producerConfig)
        
        val exceptionHandler = new ExceptionHandler
        val accessFilter = new AccessFilter(validator)
        val kafkaService = new KafkaService(producer)
        val restService: Service[Request, Response] 
            = exceptionHandler andThen accessFilter andThen kafkaService
        val server: Server = ServerBuilder()
            .codec(CompressedRichHttp[Request](CompressedHttp()))
            .bindTo(new InetSocketAddress(port))
            .name("baloo")
            .build(restService)
    }
}