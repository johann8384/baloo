package com.mozilla.baloo

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.builder.{Server, ServerBuilder}
import com.twitter.finagle.http.{Request, Response, CompressedHttp, CompressedRichHttp}
import com.twitter.util.Future
import com.mozilla.baloo.validator.Validator
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
import com.mozilla.bagheera.BagheeraProto.BagheeraMessage
import com.mozilla.bagheera.BagheeraProto.BagheeraMessage.Operation
import com.google.protobuf.ByteString

object Baloo {
    
    class ExceptionHandler extends SimpleFilter[Request, Response] {
        def apply(request: Request, service: Service[Request, Response]) = {
            service(request) handle { case error =>
                val statusCode = error match {
                    case _: IllegalArgumentException =>
                        NOT_ACCEPTABLE
                    case _: SecurityException =>
                        FORBIDDEN
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
            val pathElements = PathDecoder.getPathElements(request.getUri())
            if (pathElements.size < 1 || !validator.isValidNamespace(pathElements(0))) {
                Future.exception(new SecurityException("Tried to access invalid resource"))
            } else if (!(request.getMethod() == HttpMethod.POST || request.getMethod() == HttpMethod.PUT)) {
                Future.exception(new SecurityException("Tried to access " + request.getMethod() + " resource"))
            } else {
                service(request)
            }
        }
    }
    
    class KafkaService(producer: Producer[String,BagheeraMessage]) extends Service[Request, Response] {
        def apply(request: Request) = {
            val pathElements = PathDecoder.getPathElements(request.getUri())            
            var ns: String = {
                pathElements.size match {
                    case 2 => pathElements(0)
                    case _ => ""
                }
            }
            var id: String = {
                pathElements(1).length match {
                    case 0 => UUID.randomUUID().toString()
                    case _ => pathElements(1)
                }
            }

            var content = request.getContent();
            if (content.readable() && content.readableBytes() > 0) {
                var bmsgBuilder = BagheeraMessage.newBuilder()
                bmsgBuilder.setNamespace(ns)
                bmsgBuilder.setId(id)
                bmsgBuilder.setPayload(ByteString.copyFrom(content.toByteBuffer()))
                bmsgBuilder.setTimestamp(System.currentTimeMillis())
                producer.send(new ProducerData[String,BagheeraMessage](ns, bmsgBuilder.build()))
            }
            val response = new DefaultHttpResponse(HTTP_1_1, CREATED)
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