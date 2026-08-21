package app.zhijuan.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class S0NavigationStateTest {
    @Test
    fun `reader and generation return to an existing parent instead of creating a loop`() {
        val library = S0NavigationState(S0Route.LIBRARY_CREATE)
        val reader = library.forward(S0Route.READER)
        val generation = reader.forward(S0Route.GENERATION)

        val returnedReader = generation.forward(S0Route.READER)

        assertEquals(S0Route.READER, returnedReader.route)
        assertEquals(listOf(S0Route.LIBRARY_CREATE), returnedReader.backStack)
        assertEquals(S0Route.LIBRARY_CREATE, returnedReader.back().route)
        assertEquals(emptyList<S0Route>(), returnedReader.back().backStack)
    }

    @Test
    fun `library generation reader back sequence is finite and reaches library`() {
        val reader = S0NavigationState(S0Route.LIBRARY_CREATE)
            .forward(S0Route.GENERATION)
            .forward(S0Route.READER)

        val generation = reader.back()
        val library = generation.back()

        assertEquals(S0Route.GENERATION, generation.route)
        assertEquals(S0Route.LIBRARY_CREATE, library.route)
        assertEquals(emptyList<S0Route>(), library.backStack)
    }

    @Test
    fun `top level navigation clears nested history`() {
        val nested = S0NavigationState(S0Route.LIBRARY_CREATE)
            .forward(S0Route.READER)
            .forward(S0Route.GENERATION)

        val settings = nested.topLevel(S0Route.CONNECT_SETTINGS)

        assertEquals(S0Route.CONNECT_SETTINGS, settings.route)
        assertEquals(emptyList<S0Route>(), settings.backStack)
    }
}
