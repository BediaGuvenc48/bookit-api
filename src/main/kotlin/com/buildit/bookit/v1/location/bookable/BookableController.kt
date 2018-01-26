package com.buildit.bookit.v1.location.bookable

import com.buildit.bookit.auth.UserPrincipal
import com.buildit.bookit.v1.booking.BookingRepository
import com.buildit.bookit.v1.booking.EndBeforeStartException
import com.buildit.bookit.v1.booking.maxLocalDateTime
import com.buildit.bookit.v1.booking.minLocalDateTime
import com.buildit.bookit.v1.location.bookable.dto.Bookable
import com.buildit.bookit.v1.location.bookable.dto.BookableResource
import com.buildit.bookit.v1.location.dto.Location
import com.buildit.bookit.v1.location.dto.LocationNotFound
import com.buildit.bookit.v1.user.dto.maskSubjectIfOtherUser
import io.swagger.annotations.ApiImplicitParam
import io.swagger.annotations.ApiImplicitParams
import io.swagger.annotations.ApiParam
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import springfox.documentation.annotations.ApiIgnore
import java.time.LocalDate

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class BookableNotFound : RuntimeException("Bookable not found")

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class InvalidBookable : RuntimeException("Bookable does not exist")

@RestController
@RequestMapping("/v1/location/{locationId}/bookable")
@Transactional
class BookableController(private val bookableRepository: BookableRepository, val bookingRepository: BookingRepository) {
    /**
     * Get a bookable
     */
    @Transactional(readOnly = true)
    @GetMapping("/{bookableId}")
    // use implicit params until springfox 2.8.0 released
    // https://github.com/springfox/springfox/commit/1606caf4a421470ccb9b7592b465979dcb4c5ce1
    // https://github.com/springfox/springfox/issues/2107
    // current workaround below from https://github.com/springfox/springfox/issues/2053
    @ApiImplicitParams(
        ApiImplicitParam(name = "locationId", required = true, dataTypeClass = String::class, paramType = "path"),
        ApiImplicitParam(name = "bookableId", required = true, dataTypeClass = String::class, paramType = "path")
    )
    fun getBookable(
        @PathVariable("locationId") @ApiParam(type = "java.lang.String") @ApiIgnore location: Location?,
        @PathVariable("bookableId") @ApiParam(type = "java.lang.String") @ApiIgnore bookable: Bookable?
    ): BookableResource {
        location ?: throw LocationNotFound()
        bookable ?: throw BookableNotFound()
        if (bookable.location != location) {
            throw BookableNotFound()
        }
        return BookableResource(bookable)
    }

    /**
     * Get all bookables
     */
    @Transactional(readOnly = true)
    @GetMapping
    @ApiImplicitParam(name = "locationId", required = true, dataTypeClass = String::class, paramType = "path")
    fun getAllBookables(
        @PathVariable("locationId") @ApiParam(type = "java.lang.String") @ApiIgnore locationId: Location?,
        @AuthenticationPrincipal user: UserPrincipal?,
        @RequestParam("start", required = false)
        @DateTimeFormat(pattern = "yyyy-MM-dd['T'HH:mm[[:ss][.SSS]]]")
        startDateInclusive: LocalDate? = null,
        @RequestParam("end", required = false)
        @DateTimeFormat(pattern = "yyyy-MM-dd['T'HH:mm[[:ss][.SSS]]]")
        endDateExclusive: LocalDate? = null,
        @ApiParam(allowableValues = "bookings")
        @RequestParam("expand", required = false)
        expand: List<String>? = emptyList()
    ): Collection<BookableResource> {
        val location = locationId ?: throw LocationNotFound()
        val start = startDateInclusive ?: LocalDate.MIN
        val end = endDateExclusive ?: LocalDate.MAX

        if (end.isBefore(start)) {
            throw EndBeforeStartException()
        }

        return bookableRepository.findByLocation(location)
            .map { bookable ->
                val bookings = when {
                    "bookings" in expand ?: emptyList() ->
                        bookingRepository.findByBookableAndOverlap(
                            bookable,
                            when (start) {
                                LocalDate.MIN -> minLocalDateTime
                                else -> start.atStartOfDay()
                            },
                            when (end) {
                                LocalDate.MAX -> maxLocalDateTime
                                else -> end.atStartOfDay()
                            }
                        )
                            .map { maskSubjectIfOtherUser(it, user) }
                    else -> emptyList()
                }

                BookableResource(bookable, bookings)
            }
    }
}
