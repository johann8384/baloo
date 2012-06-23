package com.mozilla.baloo

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.builder.{Server, ServerBuilder}
import com.twitter.finagle.http.{Request, Response, BalooHttp, BalooRichHttp}
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

object Bagheera {
    
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
    
    class AccessHandler(validator: Validator) extends SimpleFilter[Request, Response] {
        def apply(request: Request, service: Service[Request, Response]) = {
            val pathElements = PathDecoder.getPathElements(request.getUri())
            if (!validator.isValidUri(request.getUri())) {
                Future.exception(new SecurityException("Tried to access invalid resource"))
            } else if (pathElements.size < 1 || !validator.isValidNamespace(pathElements(0))) {
                Future.exception(new SecurityException("Tried to access invalid resource"))
            } else if (!(request.getMethod() == HttpMethod.POST || request.getMethod() == HttpMethod.PUT)) {
                Future.exception(new SecurityException("Tried to access " + request.getMethod() + " resource"))
            } else {
                service(request)
            }
        }
    }
    
    class KafkaService extends Service[Request, Response] {
        def apply(request: Request) = {
            val pathElements = PathDecoder.getPathElements(request.getUri())
            var ns: String = {
                pathElements.size match {
                    case 2 => pathElements(0)
                    case 1 => pathElements(0)
                    case _ => ""
                }
            }
            var id: String = {
                pathElements.size match {
                    case 2 => 
                        if (pathElements(1).length() == 0) {
                            UUID.randomUUID().toString()
                        } else {
                            pathElements(1)
                        }
                    case 1 => UUID.randomUUID().toString()
                    case _ => ""
                }
            }

            var content = request.getContent();
            if (content.readable() && content.readableBytes() > 0) {
                var sb = new StringBuilder(id)
                sb.append("\u0001")
                sb.append(content.toString(UTF_8))
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
        val validNamespaces = Set("telemetry")
        val validator = new Validator(JavaConversions.setAsJavaSet(validNamespaces))
        
        // setup kafka producer
        //val props = new Properties
        //val producerConfig = new ProducerConfig(props)
        //val producer = new Producer(producerConfig)
        
        val exceptionHandler = new ExceptionHandler
        val accessHandler = new AccessHandler(validator)
        val kafkaService = new KafkaService
        val restService: Service[Request, Response] 
            = exceptionHandler andThen accessHandler andThen kafkaService
        val server: Server = ServerBuilder()
            .codec(BalooRichHttp[Request](BalooHttp()))
            .bindTo(new InetSocketAddress(port))
            .name("baloo")
            .build(restService)
    }
}