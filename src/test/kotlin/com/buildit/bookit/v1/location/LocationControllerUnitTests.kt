package com.buildit.bookit.v1.location

import com.buildit.bookit.v1.location.dto.Location
import com.buildit.bookit.v1.location.dto.LocationNotFound
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.winterbe.expekt.expect
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.ZoneId

class LocationControllerUnitTests {
    val mockRepository = mock<LocationRepository> {
        on { findAll() }.doReturn(
            listOf(
                Location(1, "NYC", ZoneId.of("Americas/New_York")),
                Location(2, "DEN", ZoneId.of("Americas/Denver"))
            )
        )
        on { findOne(1) }.doReturn(Location(1, "NYC", ZoneId.of("Americas/New_York")))
    }

    @Nested
    inner class `v1|location` {
        @Nested
        inner class `getLocations()` {
            @Test
            fun `should return all locations`() {
                val locationController = LocationController(mockRepository)
                expect(locationController.getLocations().size).to.be.equal(2)
            }
        }

        @Nested
        inner class `getLocation()` {
            @Nested
            inner class `with known location` {
                @Test
                fun `should return the location`() {
                    val locationController = LocationController(mockRepository)
                    expect(locationController.getLocation(1).name).to.be.equal("NYC")
                }
            }

            @Nested
            inner class `with unknown location` {
                @Test
                fun `should throw an exception`() {
                    val locationController = LocationController(mockRepository)
                    assertThat({ locationController.getLocation(2) }, throws<LocationNotFound>())
                }
            }
        }
    }
}
