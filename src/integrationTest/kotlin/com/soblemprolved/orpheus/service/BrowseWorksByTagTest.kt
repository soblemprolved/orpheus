package com.soblemprolved.orpheus.service

import com.soblemprolved.orpheus.model.WorkFilterParameters
import com.soblemprolved.orpheus.service.models.AO3Error
import com.soblemprolved.orpheus.service.models.AO3Response
import com.soblemprolved.orpheus.utilities.AO3ServiceParameterResolver
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AO3ServiceParameterResolver::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class BrowseWorksByTagTest(private val service: AO3Service) {
    @Test
    fun `(Integration) Successfully request works from tag`() {
        val response = runBlocking {
            service.browseWorksByTag("F/M", 1)
        }
        assertTrue(response is AO3Response.Success)
        // TODO: add more stuff
    }

    @Test
    fun `(Integration) Successfully request works with non-standard rating names`() {
        val response = runBlocking {
            service.browseWorksByTag(
                "F/M",
                1,
                WorkFilterParameters(
                    showRatingGeneral = false,
                    showRatingTeen = false,
                    showRatingMature = false,
                    showRatingExplicit = false,
                    showRatingNotRated = false
                )
            )
        }
        assertTrue(response is AO3Response.Success)
    }

    @Test
    fun `(Integration) Successfully request works with multiple arguments`() {
        val response = runBlocking {
            service.browseWorksByTag(
                "F/M",
                1,
                WorkFilterParameters(
                    includedTags = listOf("Romance", "Pepper Potts/Tony Stark").toMutableList(),
                    excludedTags = listOf("Smut", "John Wick").toMutableList()
                )
            )
        }
        assertTrue(response is AO3Response.Success)
    }

    @Test
    fun `Return a TagSynonymError when requesting works from a synonym`() {
        val response = runBlocking {
            service.browseWorksByTag("Roy Mustang/Riza Hawkeye", 1)
        }
        assertTrue(response is AO3Response.Failure && response.error is AO3Error.TagSynonymError)
    }

    @Test
    fun `Return a TagNotFilterableError when requesting works from a tag that is not marked common`() {
        val response = runBlocking {
            service.browseWorksByTag(
                "the sea and the land as concepts of fear and desire in the face of infinity",
                1
            )
        }
        assertTrue(response is AO3Response.Failure && response.error is AO3Error.TagNotFilterableError)
    }

    @Test
    fun `Return an empty list when the page requested exceeds the total number of pages`() {
        val response = runBlocking {
            service.browseWorksByTag("009-1 (Manga)", 2)
        }
        assertTrue(response is AO3Response.Success)
        assertTrue((response as AO3Response.Success).value.workBlurbs.isEmpty())
    }
}
