package cz.radekm.machines.experimental

import cz.radekm.machines.Machine
import java.time.ZonedDateTime
import java.util.*

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
    val fillDateTime = pipeTransform<Response, Response> { resp ->
        resp.dateTime = ZonedDateTime.now()
        yield(resp)
    }

    val ensureEveryRequestHasResponse = teeTransform<Response, Response, Request> { resp ->
        resp.requestId?.let { waiting.removeByRequestId(it) }
        val threshold = ZonedDateTime.now().minusMinutes(5)
        val requestsToResend = waiting.getRequestsOlderThan(threshold)
        for (req in requestsToResend)
            yieldEarly(req)
        yield(resp)
    }

    val pipeline: List<Machine<Tee<*, *, *>>> = PipelineBuilder<Response>()
            .attach(fillDateTime)
            .attach(ensureEveryRequestHasResponse, newRequests)
            .build()
}

class ChatUpstream(waiting: RequestsWaitingForResponse) {
    val fillDateTimeAndRequestId = pipeTransform<Request, Request> { req ->
        req.dateTime = ZonedDateTime.now()
        if (req.requestId == null) {
            req.requestId = UUID.randomUUID().toString()
        }
        yield(req)
    }

    val storeRequestWaitingForResponse = teeTransform<Request, Request, Request> { req ->
        yieldEarly(req)
        yield(req)
    }

    val pipeline = PipelineBuilder<Request>()
                .attach(fillDateTimeAndRequestId)
                .attach(storeRequestWaitingForResponse, waiting)
                .build()
}

class ChatPipelineTest {
}
