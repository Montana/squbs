/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.proxy.pipedserviceproxyactor

import org.squbs.unicomplex.WebContext
import akka.actor.{ActorContext, ActorLogging, Actor}
import spray.http.StatusCodes._
import com.typesafe.config.Config
import org.squbs.proxy._
import scala.concurrent.{ExecutionContext, Future}
import spray.http.HttpRequest
import spray.http.HttpResponse
import org.squbs.proxy.RequestContext
import spray.http.HttpHeaders.RawHeader

class PipedServiceProxyActor extends Actor with WebContext with ActorLogging {

  def receive = {
    case req: HttpRequest =>
      val customHeader1 = req.headers.find(h => h.name.equals("dummyReqHeader1"))
      val customHeader2 = req.headers.find(h => h.name.equals("dummyReqHeader2"))
      val response = (customHeader1, customHeader2) match {
        case (Some(h1), Some(h2)) => HttpResponse(OK, h1.value + h2.value, List(h1, h2))
				case (Some(h1), None) => HttpResponse(OK, h1.value, List(h1))
				case (None, Some(h2)) => HttpResponse(OK, h2.value, List(h2))
        case other => HttpResponse(OK, "No custom header found")
      }

      sender() ! response
  }
}


class DummyPipedServiceProxyProcessorFactoryForActor extends ServiceProxyProcessorFactory {

	val filter1 = PipeLineFilter(Map("pipeline1" -> "eBay"))
	val filter2 = PipeLineFilter(Map("pipeline2" -> "Paypal"))
	val filter3 = PipeLineFilter(Map("dummyReqHeader1" -> "PayPal"))
	val filter4 = PipeLineFilter(Map("dummyReqHeader2" -> "eBay"))
	val filter5 = PipeLineFilter(Map("dummyReqHeader1" -> "PayPal", "dummyReqHeader2" -> "eBay"))
	val filterEmpty = PipeLineFilter.empty

  def create(settings: Option[Config])(implicit context: ActorContext): ServiceProxyProcessor = {
    new PipeLineProcessor(Seq(PipeLineConfig(Seq(RequestHandler1), filter1),
															PipeLineConfig(Seq(RequestHandler2), filter2),
                              PipeLineConfig(Seq(RequestHandler1, RequestHandler2), filterEmpty)),
	                        Seq(PipeLineConfig(Seq(ResponseHandler1, ResponseHandler2), filter5),
		                          PipeLineConfig(Seq(ResponseHandler2), filter4),
														  PipeLineConfig(Seq(ResponseHandler1), filter3)))
  }

  object RequestHandler1 extends Handler {
    def process(reqCtx: RequestContext)(implicit dispatcher: ExecutionContext, context: ActorContext): Future[RequestContext] = {
      val newreq = reqCtx.request.copy(headers = RawHeader("dummyReqHeader1", "PayPal") :: reqCtx.request.headers)
      Future {
	      reqCtx.copy(request = newreq, attributes = reqCtx.attributes + (("key1" -> "CDC")))
      }
    }
  }

  object RequestHandler2 extends Handler {
    def process(reqCtx: RequestContext)(implicit dispatcher: ExecutionContext, context: ActorContext): Future[RequestContext] = {
      val newreq = reqCtx.request.copy(headers = RawHeader("dummyReqHeader2", "eBay") :: reqCtx.request.headers)
      Future {
	      reqCtx.copy(request = newreq, attributes = reqCtx.attributes + (("key2" -> "CCOE")))
      }
    }
  }

  object ResponseHandler1 extends Handler {
    def process(reqCtx: RequestContext)(implicit dispatcher: ExecutionContext, context: ActorContext): Future[RequestContext] = {
      val newCtx = reqCtx.response match {
        case nr@NormalResponse(r) =>
          reqCtx.copy(response = nr.update(r.copy(headers = RawHeader("dummyRespHeader1", reqCtx.attribute[String]("key1").getOrElse("Unknown")) :: r.headers)))

        case other => reqCtx
      }
      Future {
	      newCtx
      }
    }
  }

  object ResponseHandler2 extends Handler {
    def process(reqCtx: RequestContext)(implicit dispatcher: ExecutionContext, context: ActorContext): Future[RequestContext] = {
      val newCtx = reqCtx.response match {
        case nr@NormalResponse(r) =>
          reqCtx.copy(response = nr.update(r.copy(headers = RawHeader("dummyRespHeader2", reqCtx.attribute[String]("key2").getOrElse("Unknown")) :: r.headers)))

        case other => reqCtx
      }
      Future { newCtx }
    }
  }
}
