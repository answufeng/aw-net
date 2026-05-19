package com.answufeng.net.http.model

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.google.gson.JsonParser

class LenientStringTypeAdapter : TypeAdapter<String>() {
    override fun write(out: JsonWriter, value: String?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value)
        }
    }

    override fun read(input: JsonReader): String? {
        if (input.peek() == JsonToken.NULL) {
            input.nextNull()
            return null
        }
        return when (input.peek()) {
            JsonToken.STRING -> input.nextString()
            JsonToken.BEGIN_OBJECT, JsonToken.BEGIN_ARRAY -> {
                val element = JsonParser.parseReader(input)
                element.toString()
            }
            JsonToken.NUMBER -> input.nextString()
            JsonToken.BOOLEAN -> input.nextBoolean().toString()
            else -> input.nextString()
        }
    }
}
