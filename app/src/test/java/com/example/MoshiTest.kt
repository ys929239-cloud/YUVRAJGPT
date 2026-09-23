package com.example

import com.example.data.api.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Test

class MoshiTest {
    @Test
    fun testSerialization() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(GenerateContentRequest::class.java)
        val req = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = "Hello!")))),
            systemInstruction = Content(parts = listOf(Part(text = "You are a bot")))
        )
        println("MOSHI_OUTPUT: " + adapter.toJson(req))
    }
}
