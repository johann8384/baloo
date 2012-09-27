package com.twitter.finagle.http

/**
 * This is a near exact copy of the standard HTTP codec in finagle with the exception of adding
 * the HttpContentDecompressor to the server. This allows decompression of GZIP POST body.
 *
 * This code is required to live in the finagle http package for now due to some private classes 
 * in the actual finagle-http code base.
 */

import com.twitter.conversions.storage._
import com.twitter.finagle._
import com.twitter.finagle.transport.Transport
import com.twitter.finagle.http._
import com.twitter.finagle.http.codec._
import com.twitter.finagle.stats.{StatsReceiver, NullStatsReceiver}
import com.twitter.finagle.tracing._
import com.twitter.util.{Try, StorageUnit, Future}
import java.net.InetSocketAddress
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel.{
  ChannelPipelineFactory, UpstreamMessageEvent, Channels,
  ChannelEvent, ChannelHandlerContext, SimpleChannelDownstreamHandler, MessageEvent}
import org.jboss.netty.handler.codec.http._
import com.twitter.finagle.transport.TransportFactory

case class CompressedHttp(
    _compressionLevel: Int = 0,
    _maxRequestSize: StorageUnit = 1.megabyte,
    _maxResponseSize: StorageUnit = 1.megabyte,
    _decompressionEnabled: Boolean = true,
    _channelBufferUsageTracker: Option[ChannelBufferUsageTracker] = None,
    _annotateCipherHeader: Option[String] = None,
    _enableTracing: Boolean = false,
    _maxInitialLineLength: StorageUnit = 4096.bytes,
    _maxHeaderSize: StorageUnit = 8192.bytes)
  extends CodecFactory[HttpRequest, HttpResponse]
{
  def compressionLevel(level: Int) = copy(_compressionLevel = level)
  def maxRequestSize(bufferSize: StorageUnit) = copy(_maxRequestSize = bufferSize)
  def maxResponseSize(bufferSize: StorageUnit) = copy(_maxResponseSize = bufferSize)
  def decompressionEnabled(yesno: Boolean) = copy(_decompressionEnabled = yesno)
  def channelBufferUsageTracker(usageTracker: ChannelBufferUsageTracker) =
    copy(_channelBufferUsageTracker = Some(usageTracker))
  def annotateCipherHeader(headerName: String) = copy(_annotateCipherHeader = Option(headerName))
  def enableTracing(enable: Boolean) = copy(_enableTracing = enable)

  def client = { config =>
    new Codec[HttpRequest, HttpResponse] {
      def pipelineFactory = new ChannelPipelineFactory {
        def getPipeline() = {
          val pipeline = Channels.pipeline()
          pipeline.addLast("httpCodec", new HttpClientCodec())
          pipeline.addLast("httpTracingClientAddr", new HttpTracingClientAddr)
          pipeline.addLast(
            "httpDechunker",
            new HttpChunkAggregator(_maxResponseSize.inBytes.toInt))

          if (_decompressionEnabled)
            pipeline.addLast("httpDecompressor", new HttpContentDecompressor)

          pipeline
        }
      }

      override def prepareConnFactory(
        underlying: ServiceFactory[HttpRequest, HttpResponse]
      ): ServiceFactory[HttpRequest, HttpResponse] =
        if (_enableTracing) {
          new HttpClientTracingFilter[HttpRequest, HttpResponse](config.serviceName) andThen
            super.prepareConnFactory(underlying)
        } else
          super.prepareConnFactory(underlying)

      override val mkClientDispatcher = (mkTrans: TransportFactory) => new HttpClientDispatcher(mkTrans())
    }
  }

  def server = { config =>
    new Codec[HttpRequest, HttpResponse] {
      def pipelineFactory = new ChannelPipelineFactory {
        def getPipeline() = {
          val pipeline = Channels.pipeline()
          if (_channelBufferUsageTracker.isDefined) {
            pipeline.addLast(
              "channelBufferManager", new ChannelBufferManager(_channelBufferUsageTracker.get))
          }

          val maxRequestSizeInBytes = _maxRequestSize.inBytes.toInt
          val maxInitialLineLengthInBytes = _maxInitialLineLength.inBytes.toInt
          val maxHeaderSizeInBytes = _maxHeaderSize.inBytes.toInt 
          pipeline.addLast("httpCodec", new SafeHttpServerCodec(maxInitialLineLengthInBytes, maxHeaderSizeInBytes, maxRequestSizeInBytes))

          if (_compressionLevel > 0) {
            pipeline.addLast(
              "httpCompressor",
              new HttpContentCompressor(_compressionLevel))
          }

          // Response to ``Expect: Continue'' requests.
          pipeline.addLast("respondToExpectContinue", new RespondToExpectContinue)
          pipeline.addLast(
            "httpDechunker",
            new HttpChunkAggregator(maxRequestSizeInBytes))
          
          if (_decompressionEnabled)
            pipeline.addLast("httpDecompressor", new HttpContentDecompressor)
                
          _annotateCipherHeader foreach { headerName: String =>
            pipeline.addLast("annotateCipher", new AnnotateCipher(headerName))
          }

          pipeline.addLast(
            "connectionLifecycleManager",
            new ServerConnectionManager)

          pipeline
        }
      }

      override def prepareConnFactory(
        underlying: ServiceFactory[HttpRequest, HttpResponse]
      ): ServiceFactory[HttpRequest, HttpResponse] = {
        val checkRequest = new CheckHttpRequestFilter
        if (_enableTracing) {
          val tracingFilter = new HttpServerTracingFilter[HttpRequest, HttpResponse](
            config.serviceName,
            config.boundInetSocketAddress
          )
          tracingFilter andThen checkRequest andThen underlying
        } else {
          checkRequest andThen underlying
        }
      }
    }
  }
}

object CompressedHttp {
  def get() = new CompressedHttp()
}

case class CompressedRichHttp[REQUEST <: Request](
     httpFactory: CompressedHttp)
  extends CodecFactory[REQUEST, Response] {

  def client = { config =>
    new Codec[REQUEST, Response] {
      def pipelineFactory = new ChannelPipelineFactory {
        def getPipeline() = {
          val pipeline = httpFactory.client(null).pipelineFactory.getPipeline()
          pipeline.addLast("requestDecoder", new RequestEncoder)
          pipeline.addLast("responseEncoder", new ResponseDecoder)
          pipeline
        }
      }

      override def prepareConnFactory(
        underlying: ServiceFactory[REQUEST, Response]
      ): ServiceFactory[REQUEST, Response] =
        if (httpFactory._enableTracing)
          new HttpClientTracingFilter[REQUEST, Response](config.serviceName) andThen underlying
        else
          underlying
      }
  }

  def server = { config =>
    new Codec[REQUEST, Response] {
      def pipelineFactory = new ChannelPipelineFactory {
        def getPipeline() = {
          val pipeline = httpFactory.server(null).pipelineFactory.getPipeline()
          pipeline.addLast("serverRequestDecoder", new RequestDecoder)
          pipeline.addLast("serverResponseEncoder", new ResponseEncoder)
          pipeline
        }
      }

      override def prepareConnFactory(
        underlying: ServiceFactory[REQUEST, Response]
      ): ServiceFactory[REQUEST, Response] =
        if (httpFactory._enableTracing) {
          val tracingFilter = new HttpServerTracingFilter[REQUEST, Response](config.serviceName, config.boundInetSocketAddress)
          tracingFilter andThen underlying
        } else {
          underlying
        }
    }
  }
}