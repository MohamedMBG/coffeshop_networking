package com.example.loyaltyapp.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.loyaltyapp.R;

import org.junit.Test;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class ApiErrorsTest {

    private static final MediaType JSON = MediaType.parse("application/json");

    private static okhttp3.Response rawWith(int status, String retryAfter) {
        okhttp3.Response.Builder b = new okhttp3.Response.Builder()
                .code(status)
                .message("err")
                .protocol(Protocol.HTTP_1_1)
                .request(new Request.Builder().url("http://localhost/").build());
        if (retryAfter != null) {
            b.header("Retry-After", retryAfter);
        }
        return b.build();
    }

    private static Response<Object> errorResponse(int status, String body, String retryAfter) {
        return Response.error(ResponseBody.create(body, JSON), rawWith(status, retryAfter));
    }

    // ---- code -> string mapping ----

    @Test
    public void messageRes_knownCode_wins() {
        assertEquals(R.string.err_insufficient_points,
                ApiErrors.messageRes("INSUFFICIENT_POINTS", 422));
    }

    @Test
    public void messageRes_unknownCode_fallsBackToStatus() {
        assertEquals(R.string.err_gone, ApiErrors.messageRes("SOME_NEW_CODE", 410));
        assertEquals(R.string.err_conflict, ApiErrors.messageRes(null, 409));
        assertEquals(R.string.err_rate_limited, ApiErrors.messageRes(null, 429));
        assertEquals(R.string.err_generic, ApiErrors.messageRes(null, 500));
    }

    // ---- Retry-After parsing ----

    @Test
    public void parseRetryAfter_handlesSecondsAndGarbage() {
        assertEquals(Integer.valueOf(30), ApiErrors.parseRetryAfter("30"));
        assertEquals(Integer.valueOf(0), ApiErrors.parseRetryAfter(" 0 "));
        assertNull(ApiErrors.parseRetryAfter(null));
        assertNull(ApiErrors.parseRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT"));
        assertNull(ApiErrors.parseRetryAfter("-5"));
    }

    // ---- full parse ----

    @Test
    public void from_parsesEnvelopeCodeAndMessage() {
        Response<Object> resp = errorResponse(422,
                "{\"ok\":false,\"code\":\"INSUFFICIENT_POINTS\",\"message\":\"need 50 more\"}", null);

        ApiException ex = ApiErrors.from(resp);

        assertEquals(422, ex.httpStatus);
        assertEquals("INSUFFICIENT_POINTS", ex.code);
        assertEquals("need 50 more", ex.getMessage());
        assertEquals(R.string.err_insufficient_points, ex.messageRes);
        assertNull(ex.retryAfterSeconds);
    }

    @Test
    public void from_readsRetryAfterOn429() {
        Response<Object> resp = errorResponse(429,
                "{\"ok\":false,\"code\":\"RATE_LIMITED\",\"message\":\"slow down\"}", "15");

        ApiException ex = ApiErrors.from(resp);

        assertTrue(ex.isRateLimited());
        assertEquals(Integer.valueOf(15), ex.retryAfterSeconds);
        assertEquals(R.string.err_rate_limited, ex.messageRes);
    }

    @Test
    public void from_malformedBody_fallsBackToStatus() {
        Response<Object> resp = errorResponse(409, "not json", null);

        ApiException ex = ApiErrors.from(resp);

        assertEquals(409, ex.httpStatus);
        assertNull(ex.code);
        assertEquals(R.string.err_conflict, ex.messageRes);
    }
}
