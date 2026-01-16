package com.mememymood.core.common.util

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.common.result.Resource
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FlowUtilsTest {

    // region asResource Tests

    @Test
    fun `asResource emits Loading first then Success`() = runTest {
        val flow = flowOf("data")

        flow.asResource().test {
            val loading = awaitItem()
            assertThat(loading).isEqualTo(Resource.Loading)

            val success = awaitItem()
            assertThat(success).isInstanceOf(Resource.Success::class.java)
            assertThat((success as Resource.Success).data).isEqualTo("data")

            awaitComplete()
        }
    }

    @Test
    fun `asResource emits multiple Success items after Loading`() = runTest {
        val flow = flowOf(1, 2, 3)

        flow.asResource().test {
            assertThat(awaitItem()).isEqualTo(Resource.Loading)
            assertThat((awaitItem() as Resource.Success).data).isEqualTo(1)
            assertThat((awaitItem() as Resource.Success).data).isEqualTo(2)
            assertThat((awaitItem() as Resource.Success).data).isEqualTo(3)
            awaitComplete()
        }
    }

    @Test
    fun `asResource emits Error when flow throws exception`() = runTest {
        val exception = RuntimeException("Test error")
        val flow = flow<String> {
            throw exception
        }

        flow.asResource().test {
            assertThat(awaitItem()).isEqualTo(Resource.Loading)

            val error = awaitItem()
            assertThat(error).isInstanceOf(Resource.Error::class.java)
            assertThat((error as Resource.Error).message).isEqualTo("Test error")
            assertThat(error.exception).isEqualTo(exception)

            awaitComplete()
        }
    }

    @Test
    fun `asResource handles null exception message`() = runTest {
        val exception = object : Exception() {
            override val message: String? = null
        }
        val flow = flow<String> {
            throw exception
        }

        flow.asResource().test {
            assertThat(awaitItem()).isEqualTo(Resource.Loading)

            val error = awaitItem()
            assertThat(error).isInstanceOf(Resource.Error::class.java)
            assertThat((error as Resource.Error).message).isEqualTo("Unknown error")

            awaitComplete()
        }
    }

    @Test
    fun `asResource emits some items before error`() = runTest {
        val exception = IllegalStateException("Error occurred")
        val flow = flow {
            emit(1)
            emit(2)
            throw exception
        }

        flow.asResource().test {
            assertThat(awaitItem()).isEqualTo(Resource.Loading)
            assertThat((awaitItem() as Resource.Success).data).isEqualTo(1)
            assertThat((awaitItem() as Resource.Success).data).isEqualTo(2)

            val error = awaitItem()
            assertThat(error).isInstanceOf(Resource.Error::class.java)
            assertThat((error as Resource.Error).message).isEqualTo("Error occurred")

            awaitComplete()
        }
    }

    @Test
    fun `asResource handles empty flow`() = runTest {
        val flow = flow<String> { }

        flow.asResource().test {
            assertThat(awaitItem()).isEqualTo(Resource.Loading)
            awaitComplete()
        }
    }

    @Test
    fun `asResource with complex object type`() = runTest {
        data class User(val id: Int, val name: String)
        val user = User(1, "John")
        val flow = flowOf(user)

        flow.asResource().test {
            assertThat(awaitItem()).isEqualTo(Resource.Loading)

            val success = awaitItem()
            assertThat(success).isInstanceOf(Resource.Success::class.java)
            assertThat((success as Resource.Success).data).isEqualTo(user)

            awaitComplete()
        }
    }

    @Test
    fun `asResource with nullable values`() = runTest {
        val flow = flowOf<String?>(null, "value", null)

        flow.asResource().test {
            assertThat(awaitItem()).isEqualTo(Resource.Loading)
            assertThat((awaitItem() as Resource.Success).data).isNull()
            assertThat((awaitItem() as Resource.Success).data).isEqualTo("value")
            assertThat((awaitItem() as Resource.Success).data).isNull()
            awaitComplete()
        }
    }

    // endregion

    // region safeCall Tests

    @Test
    fun `safeCall returns Success when block succeeds`() = runTest {
        val result = safeCall { "success data" }

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        assertThat((result as Resource.Success).data).isEqualTo("success data")
    }

    @Test
    fun `safeCall returns Success with computed value`() = runTest {
        val result = safeCall {
            val a = 5
            val b = 10
            a + b
        }

        assertThat((result as Resource.Success).data).isEqualTo(15)
    }

    @Test
    fun `safeCall returns Error when block throws exception`() = runTest {
        val exception = RuntimeException("Something went wrong")

        val result = safeCall<String> { throw exception }

        assertThat(result).isInstanceOf(Resource.Error::class.java)
        assertThat((result as Resource.Error).message).isEqualTo("Something went wrong")
        assertThat(result.exception).isEqualTo(exception)
    }

    @Test
    fun `safeCall handles exception with null message`() = runTest {
        val exception = object : Exception() {
            override val message: String? = null
        }

        val result = safeCall<String> { throw exception }

        assertThat(result).isInstanceOf(Resource.Error::class.java)
        assertThat((result as Resource.Error).message).isEqualTo("Unknown error")
    }

    @Test
    fun `safeCall handles different exception types`() = runTest {
        val ioException = java.io.IOException("IO failed")
        val illegalArg = IllegalArgumentException("Invalid argument")

        val ioResult = safeCall<String> { throw ioException }
        val illegalResult = safeCall<String> { throw illegalArg }

        assertThat((ioResult as Resource.Error).exception).isInstanceOf(java.io.IOException::class.java)
        assertThat((illegalResult as Resource.Error).exception).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `safeCall with suspend operation`() = runTest {
        val result = safeCall {
            kotlinx.coroutines.delay(10)
            "async result"
        }

        assertThat((result as Resource.Success).data).isEqualTo("async result")
    }

    @Test
    fun `safeCall with nullable return type`() = runTest {
        val resultNull = safeCall<String?> { null }
        val resultValue = safeCall<String?> { "value" }

        assertThat((resultNull as Resource.Success).data).isNull()
        assertThat((resultValue as Resource.Success).data).isEqualTo("value")
    }

    @Test
    fun `safeCall with complex object`() = runTest {
        data class Result(val id: Int, val items: List<String>)
        val expected = Result(1, listOf("a", "b", "c"))

        val result = safeCall { expected }

        assertThat((result as Resource.Success).data).isEqualTo(expected)
    }

    @Test
    fun `safeCall preserves exception type in Error`() = runTest {
        class CustomException(message: String) : Exception(message)
        val customException = CustomException("Custom error")

        val result = safeCall<String> { throw customException }

        assertThat((result as Resource.Error).exception).isInstanceOf(CustomException::class.java)
    }

    @Test
    fun `safeCall with nested suspend calls`() = runTest {
        suspend fun fetchData(): String {
            kotlinx.coroutines.delay(5)
            return "data"
        }

        suspend fun processData(data: String): Int {
            kotlinx.coroutines.delay(5)
            return data.length
        }

        val result = safeCall {
            val data = fetchData()
            processData(data)
        }

        assertThat((result as Resource.Success).data).isEqualTo(4)
    }

    // endregion

    // region Integration Tests

    @Test
    fun `asResource and safeCall work together in typical use case`() = runTest {
        // Simulate a repository pattern
        suspend fun fetchFromNetwork(): String {
            kotlinx.coroutines.delay(10)
            return "network data"
        }

        val flowResult = flow {
            val result = safeCall { fetchFromNetwork() }
            when (result) {
                is Resource.Success -> emit(result.data)
                is Resource.Error -> throw Exception(result.message)
                is Resource.Loading -> { /* no-op */ }
            }
        }

        flowResult.asResource().test {
            assertThat(awaitItem()).isEqualTo(Resource.Loading)

            val success = awaitItem()
            assertThat((success as Resource.Success).data).isEqualTo("network data")

            awaitComplete()
        }
    }

    @Test
    fun `asResource handles flow that delays emissions`() = runTest {
        val flow = flow {
            kotlinx.coroutines.delay(10)
            emit("delayed value")
        }

        flow.asResource().test {
            assertThat(awaitItem()).isEqualTo(Resource.Loading)
            assertThat((awaitItem() as Resource.Success).data).isEqualTo("delayed value")
            awaitComplete()
        }
    }

    // endregion
}
