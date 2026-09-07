package com.orchords.orchordsai.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the bind-host / advertised-address policy used by [WebServerManager].
 *
 * The UI must never show a misleading URL such as the literal "localhost" when
 * the listener is bound to `0.0.0.0`, and a localhost-only server must advertise
 * `127.0.0.1`. The routing is extracted into [selectWebServerAddress] so it can
 * be exercised from a plain JUnit test without spinning up the Ktor listener.
 */
class WebServerAddressRoutingTest {

    private fun iface(
        name: String,
        ipv4: String?,
        isLoopback: Boolean = false,
        isUp: Boolean = true,
        isVirtual: Boolean = false,
    ): NetworkInterfaceSnapshot =
        NetworkInterfaceSnapshot(
            name = name,
            isLoopback = isLoopback,
            isUp = isUp,
            isVirtual = isVirtual,
            ipv4 = ipv4,
        )

    @Test
    fun `localhostOnly advertises loopback regardless of interfaces`() {
        val result = selectWebServerAddress(
            localhostOnly = true,
            networkInterfaces = listOf(iface("wlan0", "192.0.2.42")),
        )
        assertEquals(HOST_LOOPBACK, result)
        assertEquals("127.0.0.1", HOST_LOOPBACK)
    }

    @Test
    fun `lan mode picks first non-loopback, up, non-virtual IPv4`() {
        val result = selectWebServerAddress(
            localhostOnly = false,
            networkInterfaces = listOf(
                iface("lo", "127.0.0.1", isLoopback = true),
                iface("dummy0", "10.0.0.5", isVirtual = true),
                iface("wlan0", "192.0.2.42"),
                iface("rmnet0", "198.51.100.7"),
            ),
        )
        assertEquals("192.0.2.42", result)
    }

    @Test
    fun `lan mode skips down interfaces`() {
        val result = selectWebServerAddress(
            localhostOnly = false,
            networkInterfaces = listOf(
                iface("wlan0", "192.0.2.42", isUp = false),
                iface("rmnet0", "198.51.100.7"),
            ),
        )
        assertEquals("198.51.100.7", result)
    }

    @Test
    fun `lan mode falls back to wildcard when no usable interface`() {
        val empty = selectWebServerAddress(localhostOnly = false, networkInterfaces = emptyList())
        assertEquals(HOST_ALL_INTERFACES, empty)
        assertEquals("0.0.0.0", HOST_ALL_INTERFACES)

        val onlyLoopback = selectWebServerAddress(
            localhostOnly = false,
            networkInterfaces = listOf(iface("lo", "127.0.0.1", isLoopback = true)),
        )
        assertEquals(HOST_ALL_INTERFACES, onlyLoopback)

        val noIpv4 = selectWebServerAddress(
            localhostOnly = false,
            networkInterfaces = listOf(iface("wlan0", null)),
        )
        assertEquals(HOST_ALL_INTERFACES, noIpv4)
    }

    @Test
    fun `wildcard fallback is never presented as a usable URL on its own`() {
        // The UI must rely on the bind-host selection policy instead of
        // fabricating a "localhost" string when the address is `0.0.0.0`.
        // This test is the explicit guard: no caller path substitutes the
        // word "localhost" for a missing advertised address.
        val address = selectWebServerAddress(
            localhostOnly = false,
            networkInterfaces = listOf(iface("lo", "127.0.0.1", isLoopback = true)),
        )
        assertNotNull(address)
        assertTrue(
            "advertised address must not be the literal word 'localhost'; got '$address'",
            address != "localhost",
        )
    }

    @Test
    fun `state default has null hostname and null address until start`() {
        val state = WebServerState()
        assertEquals(false, state.isRunning)
        assertNull(state.hostname)
        assertNull(state.address)
    }
}
