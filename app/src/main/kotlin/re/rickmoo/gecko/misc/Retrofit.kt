package re.rickmoo.gecko.misc

import re.rickmoo.gecko.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import kotlin.reflect.KClass

object Retrofit {
    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.UPDATE_BASE_URL)
        .addConverterFactory(JacksonConverterFactory.create(Json.objectMapper))
        .build()

    fun <T : Any> create(clazz: KClass<T>): T {
        return retrofit.create(clazz.java)
    }
}