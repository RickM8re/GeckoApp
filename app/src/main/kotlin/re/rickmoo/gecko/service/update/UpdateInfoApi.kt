package re.rickmoo.gecko.service.update

import okhttp3.ResponseBody
import re.rickmoo.gecko.misc.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url

interface UpdateInfoApi {

    @GET
    suspend fun getUpdateInfo(@Url url: String): UpdateIndexModel

    @GET
    suspend fun getUpdateMarkdown(@Url url: String): ResponseBody

    companion object {
        val INSTANCE: UpdateInfoApi by lazy { Retrofit.create(UpdateInfoApi::class) }
    }
}