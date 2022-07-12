
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okio.IOException
import kotlin.coroutines.resumeWithException

@ExperimentalCoroutinesApi
suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response, onCancellation = null)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }
        })
        continuation.invokeOnCancellation {
            cancel()
        }
    }
}

fun Call.asFlow() = callbackFlow {
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            trySendBlocking(response)
                .onFailure { exception ->
                    if (exception != null)
                        throw exception.fillInStackTrace()
                }
        }

        override fun onFailure(call: Call, e: IOException) {
            cancel(call.request().url.toString(), e)
        }
    })
    awaitClose()
}

