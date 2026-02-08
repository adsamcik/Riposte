package com.adsamcik.riposte.core.common.result

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ResourceTest {

    // region Success State Tests

    @Test
    fun `Success state contains data`() {
        val data = "test data"
        val resource = Resource.Success(data)

        assertThat(resource.data).isEqualTo(data)
    }

    @Test
    fun `Success state isSuccess returns true`() {
        val resource = Resource.Success("data")

        assertThat(resource.isSuccess).isTrue()
        assertThat(resource.isError).isFalse()
        assertThat(resource.isLoading).isFalse()
    }

    @Test
    fun `Success getOrNull returns data`() {
        val data = 42
        val resource = Resource.Success(data)

        assertThat(resource.getOrNull()).isEqualTo(data)
    }

    @Test
    fun `Success with null data is valid`() {
        val resource = Resource.Success<String?>(null)

        assertThat(resource.isSuccess).isTrue()
        assertThat(resource.getOrNull()).isNull()
    }

    @Test
    fun `Success with complex object contains correct data`() {
        data class User(val id: Int, val name: String)
        val user = User(1, "John")
        val resource = Resource.Success(user)

        assertThat(resource.data).isEqualTo(user)
        assertThat(resource.data.id).isEqualTo(1)
        assertThat(resource.data.name).isEqualTo("John")
    }

    // endregion

    // region Error State Tests

    @Test
    fun `Error state contains message`() {
        val message = "Something went wrong"
        val resource = Resource.Error(message)

        assertThat(resource.message).isEqualTo(message)
        assertThat(resource.exception).isNull()
    }

    @Test
    fun `Error state contains message and exception`() {
        val message = "Network error"
        val exception = RuntimeException("Connection failed")
        val resource = Resource.Error(message, exception)

        assertThat(resource.message).isEqualTo(message)
        assertThat(resource.exception).isEqualTo(exception)
    }

    @Test
    fun `Error state isError returns true`() {
        val resource = Resource.Error("error")

        assertThat(resource.isSuccess).isFalse()
        assertThat(resource.isError).isTrue()
        assertThat(resource.isLoading).isFalse()
    }

    @Test
    fun `Error getOrNull returns null`() {
        val resource: Resource<String> = Resource.Error("error")

        assertThat(resource.getOrNull()).isNull()
    }

    @Test
    fun `Error with different exception types`() {
        val ioException = java.io.IOException("IO failed")
        val resource = Resource.Error("IO error", ioException)

        assertThat(resource.exception).isInstanceOf(java.io.IOException::class.java)
    }

    // endregion

    // region Loading State Tests

    @Test
    fun `Loading state isLoading returns true`() {
        val resource: Resource<String> = Resource.Loading

        assertThat(resource.isSuccess).isFalse()
        assertThat(resource.isError).isFalse()
        assertThat(resource.isLoading).isTrue()
    }

    @Test
    fun `Loading getOrNull returns null`() {
        val resource: Resource<String> = Resource.Loading

        assertThat(resource.getOrNull()).isNull()
    }

    @Test
    fun `Loading is a singleton`() {
        val loading1: Resource<String> = Resource.Loading
        val loading2: Resource<Int> = Resource.Loading

        assertThat(loading1).isSameInstanceAs(loading2)
    }

    // endregion

    // region Map Transformation Tests

    @Test
    fun `map transforms Success data`() {
        val resource = Resource.Success(5)

        val mapped = resource.map { it * 2 }

        assertThat(mapped).isInstanceOf(Resource.Success::class.java)
        assertThat((mapped as Resource.Success).data).isEqualTo(10)
    }

    @Test
    fun `map preserves Error state`() {
        val resource: Resource<Int> = Resource.Error("error", RuntimeException())

        val mapped = resource.map { it * 2 }

        assertThat(mapped).isInstanceOf(Resource.Error::class.java)
        assertThat((mapped as Resource.Error).message).isEqualTo("error")
    }

    @Test
    fun `map preserves Loading state`() {
        val resource: Resource<Int> = Resource.Loading

        val mapped = resource.map { it * 2 }

        assertThat(mapped).isEqualTo(Resource.Loading)
    }

    @Test
    fun `map can change type`() {
        val resource = Resource.Success(42)

        val mapped = resource.map { "Number: $it" }

        assertThat(mapped).isInstanceOf(Resource.Success::class.java)
        assertThat((mapped as Resource.Success).data).isEqualTo("Number: 42")
    }

    @Test
    fun `map chain works correctly`() {
        val resource = Resource.Success(2)

        val mapped = resource
            .map { it * 3 }
            .map { it + 1 }
            .map { it.toString() }

        assertThat((mapped as Resource.Success).data).isEqualTo("7")
    }

    // endregion

    // region SuspendMap Tests

    @Test
    fun `suspendMap transforms Success data`() = runTest {
        val resource = Resource.Success("hello")

        val mapped = resource.suspendMap { it.uppercase() }

        assertThat((mapped as Resource.Success).data).isEqualTo("HELLO")
    }

    @Test
    fun `suspendMap preserves Error state`() = runTest {
        val exception = RuntimeException("test")
        val resource: Resource<String> = Resource.Error("error", exception)

        val mapped = resource.suspendMap { it.uppercase() }

        assertThat(mapped).isInstanceOf(Resource.Error::class.java)
        assertThat((mapped as Resource.Error).message).isEqualTo("error")
        assertThat(mapped.exception).isEqualTo(exception)
    }

    @Test
    fun `suspendMap preserves Loading state`() = runTest {
        val resource: Resource<String> = Resource.Loading

        val mapped = resource.suspendMap { it.uppercase() }

        assertThat(mapped).isEqualTo(Resource.Loading)
    }

    @Test
    fun `suspendMap works with suspend function`() = runTest {
        val resource = Resource.Success(10)

        val mapped = resource.suspendMap { value ->
            // Simulate async operation
            kotlinx.coroutines.delay(10)
            value * 2
        }

        assertThat((mapped as Resource.Success).data).isEqualTo(20)
    }

    // endregion

    // region Companion Object Factory Tests

    @Test
    fun `success factory creates Success state`() {
        val resource = Resource.success("data")

        assertThat(resource).isInstanceOf(Resource.Success::class.java)
        assertThat((resource as Resource.Success).data).isEqualTo("data")
    }

    @Test
    fun `error factory creates Error state without exception`() {
        val resource = Resource.error("message")

        assertThat(resource).isInstanceOf(Resource.Error::class.java)
        assertThat((resource as Resource.Error).message).isEqualTo("message")
        assertThat(resource.exception).isNull()
    }

    @Test
    fun `error factory creates Error state with exception`() {
        val exception = IllegalStateException("invalid")
        val resource = Resource.error("message", exception)

        assertThat(resource).isInstanceOf(Resource.Error::class.java)
        assertThat((resource as Resource.Error).message).isEqualTo("message")
        assertThat(resource.exception).isEqualTo(exception)
    }

    @Test
    fun `loading factory creates Loading state`() {
        val resource = Resource.loading()

        assertThat(resource).isEqualTo(Resource.Loading)
    }

    // endregion

    // region When Expression Tests (Pattern Matching)

    @Test
    fun `when expression handles all states`() {
        val successResource: Resource<String> = Resource.Success("data")
        val errorResource: Resource<String> = Resource.Error("error")
        val loadingResource: Resource<String> = Resource.Loading

        val successResult = when (successResource) {
            is Resource.Success -> "success: ${successResource.data}"
            is Resource.Error -> "error: ${successResource.message}"
            is Resource.Loading -> "loading"
        }

        val errorResult = when (errorResource) {
            is Resource.Success -> "success: ${errorResource.data}"
            is Resource.Error -> "error: ${errorResource.message}"
            is Resource.Loading -> "loading"
        }

        val loadingResult = when (loadingResource) {
            is Resource.Success -> "success: ${loadingResource.data}"
            is Resource.Error -> "error: ${loadingResource.message}"
            is Resource.Loading -> "loading"
        }

        assertThat(successResult).isEqualTo("success: data")
        assertThat(errorResult).isEqualTo("error: error")
        assertThat(loadingResult).isEqualTo("loading")
    }

    // endregion

    // region Equality Tests

    @Test
    fun `Success states with same data are equal`() {
        val resource1 = Resource.Success("data")
        val resource2 = Resource.Success("data")

        assertThat(resource1).isEqualTo(resource2)
    }

    @Test
    fun `Success states with different data are not equal`() {
        val resource1 = Resource.Success("data1")
        val resource2 = Resource.Success("data2")

        assertThat(resource1).isNotEqualTo(resource2)
    }

    @Test
    fun `Error states with same message and exception are equal`() {
        val exception = RuntimeException("test")
        val resource1 = Resource.Error("error", exception)
        val resource2 = Resource.Error("error", exception)

        assertThat(resource1).isEqualTo(resource2)
    }

    @Test
    fun `Error states with different messages are not equal`() {
        val resource1 = Resource.Error("error1")
        val resource2 = Resource.Error("error2")

        assertThat(resource1).isNotEqualTo(resource2)
    }

    @Test
    fun `Different state types are not equal`() {
        val success: Resource<String> = Resource.Success("data")
        val error: Resource<String> = Resource.Error("error")
        val loading: Resource<String> = Resource.Loading

        assertThat(success).isNotEqualTo(error)
        assertThat(success).isNotEqualTo(loading)
        assertThat(error).isNotEqualTo(loading)
    }

    // endregion

    // region Type Covariance Tests

    @Test
    fun `Resource is covariant in type parameter`() {
        val stringResource: Resource<String> = Resource.Success("hello")
        val anyResource: Resource<Any> = stringResource

        assertThat(anyResource.isSuccess).isTrue()
        assertThat((anyResource as Resource.Success).data).isEqualTo("hello")
    }

    @Test
    fun `Error and Loading work with any type parameter`() {
        val errorAsString: Resource<String> = Resource.Error("error")
        val errorAsInt: Resource<Int> = Resource.Error("error")

        val loadingAsString: Resource<String> = Resource.Loading
        val loadingAsInt: Resource<Int> = Resource.Loading

        assertThat(errorAsString.isError).isTrue()
        assertThat(errorAsInt.isError).isTrue()
        assertThat(loadingAsString.isLoading).isTrue()
        assertThat(loadingAsInt.isLoading).isTrue()
    }

    // endregion
}
