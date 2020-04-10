package cz.radekm.machines

import java.time.ZonedDateTime
import java.util.*
import kotlin.test.Test

sealed class Request {
    var requestId: String? = null
    var dateTime: ZonedDateTime? = null
}
class Login(val user: String, val password: String) : Request()
class Send(val body: String) : Request()

sealed class Response {
    var requestId: String? = null
    var dateTime: ZonedDateTime? = null
}
class LoginOk() : Response()
class SendOk(val messageId: Long) : Response()
class Message(val messageId: Long, val body: String) : Response()

class RequestsWaitingForResponse : Appendable<Request> {
    private val waiting = mutableMapOf<String, Request>()

    override fun isFull() = false
    override fun append(x: Request) { waiting[x.requestId!!] = x }

    fun removeByRequestId(requestId: String) = waiting.remove(requestId)

    fun getRequestsOlderThan(threshold: ZonedDateTime) =
            waiting.values.filter { it.dateTime!! <= threshold }
}

class ChatDownstream(waiting: RequestsWaitingForResponse, newRequests: Appendable<Request>) {
    val fillDateTime = pipe<Response, Response> {
        while (true) {
            val resp = await()
            resp.dateTime = ZonedDateTime.now()
            yield(resp)
        }
    }

    val ensureEveryRequestHasResponse = tee<Response, Response, Request> {
        while (true) {
            val resp = await()
            resp.requestId?.let { waiting.removeByRequestId(it) }
            val threshold = ZonedDateTime.now().minusMinutes(5)
            val requestsToResend = waiting.getRequestsOlderThan(threshold)
            for (req in requestsToResend)
                yieldEarly(req)
        }
    }

    init {
        PipelineBuilder<Response>()
                .attach(fillDateTime)
                .attach(ensureEveryRequestHasResponse, newRequests)
    }
}

class ChatUpstream(waiting: RequestsWaitingForResponse) {
    val fillDateTimeAndRequestId = pipe<Request, Request> {
        while (true) {
            val req = await()
            req.dateTime = ZonedDateTime.now()
            if (req.requestId == null) {
                req.requestId = UUID.randomUUID().toString()
            }
            yield(req)
        }
    }

    val storeRequestWaitingForResponse = tee<Request, Request, Request> {
        while (true) {
            val req = await()
            yieldEarly(req)
            yield(req)
        }
    }

    init {
        PipelineBuilder<Request>()
                .attach(fillDateTimeAndRequestId)
                .attach(storeRequestWaitingForResponse, waiting)
    }
}

class ChatPipelineTest {
}
