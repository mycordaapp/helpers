package mycorda.app.chaos

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.greaterThan
import com.natpryce.hamkrest.lessThan
import mycorda.app.clock.PlatformTimer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.lang.Exception
import javax.swing.plaf.synth.SynthTextAreaUI

class ChaosTest {

    @Test
    fun `it should fail N percent of the time`() {
        val chaos = Chaos(listOf(FailNPercent(10)))
        try {
            // statistically, no chance of passing 100 times
            (1..100).forEach { _ -> chaos.chaos() }
            fail("Should have thrown an exception")
        } catch (ex: Exception) {
            assertThat(ex.message, equalTo("unlucky, punk"))
        }
    }

    @Test
    fun `it should have statistically sensible pass and failure rates`() {
        val chaos = Chaos(listOf(FailNPercent(10)))
        val attempts = 1000
        val variance = 2.0
        var passCount = 0
        var failCount = 0

        // run enough times to get some good stats
        (1..attempts).forEach {
            try {
                chaos.chaos()
                passCount++
            } catch (ex: Exception) {
                failCount++
            }
        }
        val passPercent = (passCount * 100.0) / attempts
        val failPercent = (failCount * 100.0) / attempts

        // are these statistically sensible results?
        assertThat(passPercent, greaterThan(90 - variance)) { "pass rate of $passPercent% is outside bounds" }
        assertThat(passPercent, lessThan(90 + variance)) { "pass rate of $passPercent% is outside bounds" }
        assertThat(failPercent, greaterThan(10 - variance)) { "fail rate of $failPercent% is outside bounds" }
        assertThat(failPercent, lessThan(10 + variance)) { "fail rate of $failPercent% is outside bounds" }
    }

    @Test
    fun `it should have statistically sensible delay rates`() {
        val maxTicks = 10
        val attempts = 100
        val variance = PlatformTimer.clockTick()
        var maxDelay = 0L
        var minDelay = Long.MAX_VALUE

        val chaos = Chaos(DelayUptoNTicks(maxTicks))

        // run enough times to get some good stats
        (1..attempts).forEach { _ ->
            val start = System.nanoTime()
            chaos.chaos()
            val delayMs = (System.nanoTime() - start) / 100000
            if (delayMs < minDelay) {
                minDelay = delayMs
            }
            if (delayMs > maxDelay) {
                maxDelay = delayMs
            }
        }

        // are these statistically sensible results?
        assertThat(minDelay, lessThan(variance)) { "minDelay of ${minDelay}ms is outside bounds" }
        assertThat(
            maxDelay,
            greaterThan((PlatformTimer.clockTick() * maxTicks) - variance)
        ) { "maxDelay of ${maxDelay}ms is outside bounds" }
    }

    @Test
    fun `it should have no chaotic behaviour by default`() {
        val chaos = Chaos()
        val start = System.currentTimeMillis()
        try {
            (1..1000).forEach { _ -> chaos.chaos() }
        } catch (ex: Exception) {
            fail("Should not be any chaotic behaviour")
        }

        // long enough to be reliable, short enough to detect unexpected delays
        val timeTaken = System.currentTimeMillis() - start
        assertThat(timeTaken, lessThan(PlatformTimer.clockTick() * 5))
    }

    @Test
    fun `should support multiple channels`() {
        val chaos = Chaos(
            mapOf(
                "errors" to listOf(FailNPercent(10)),
                "delays" to listOf(DelayUptoNTicks(5)),
                "noop" to listOf(Noop())
            )
        )

        try {
            (1..100).forEach { _ -> chaos.chaos("errors") }
            fail("Should have thrown an exception")
        } catch (ignoreMe: Exception) {
        }


        val start = System.currentTimeMillis()
        (1..100).forEach { _ -> chaos.chaos("delays") }
        val ticksTaken = (System.currentTimeMillis() - start) / PlatformTimer.clockTick()
        assertThat(ticksTaken, greaterThan(100))
        assertThat(ticksTaken, lessThan(500))

        try {
            chaos.chaos("noop")
            chaos.chaos("unknownChannel")   // just prints a warning
        } catch (ex: Exception) {
            fail("unexpected exception: ${ex.message}")
        }
    }


}