package com.soblemprolved.interfaceofourown.converters.responsebody

import com.soblemprolved.interfaceofourown.model.Csrf
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.Converter

object GetCsrfConverter : Converter<ResponseBody, Csrf> {
    @Serializable
    private class Token(val token: String)

    private val format = Json { ignoreUnknownKeys = true }

    override fun convert(value: ResponseBody): Csrf {
        val json = value.string()
        val token = format.decodeFromString<Token>(json)
        return Csrf(token.token)
    }
}