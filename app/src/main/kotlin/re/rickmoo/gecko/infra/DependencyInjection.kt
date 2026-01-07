package re.rickmoo.gecko.infra

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.json.JSONArray
import org.json.JSONObject
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.isSupertypeOf
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.jvmErasure

val objectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
}

fun isPrimitiveType(input: Any): Boolean {
    return when (input) {
        is Boolean -> true
        is Number -> true
        is Char -> true
        is String -> true
        else -> false
    }
}

val boolType = Boolean::class.createType()
val byteType = Byte::class.createType()
val shortType = Short::class.createType()
val intType = Int::class.createType()
val longType = Long::class.createType()
val floatType = Float::class.createType()
val doubleType = Double::class.createType()
val charType = Char::class.createType()
val stringType = String::class.createType()

val collectionType = Collection::class.createType(arguments = listOf(KTypeProjection.STAR))
val arrayType = Array::class.createType(arguments = listOf(KTypeProjection.STAR))
val booleanArrayType = BooleanArray::class.createType()
val byteArrayType = ByteArray::class.createType()
val shortArrayType = ShortArray::class.createType()
val charArrayType = CharArray::class.createType()
val intArrayType = IntArray::class.createType()
val longArrayType = LongArray::class.createType()
val floatArrayType = FloatArray::class.createType()
val doubleArrayType = DoubleArray::class.createType()

val jsonObjectType = JSONObject::class.createType()
val jsonArrayType = JSONArray::class.createType()

fun Any.toType(type: KType): Any? {
    return when (type) {
        byteType -> if (this is Number) toByte() else this.toString().toByte()
        shortType -> if (this is Number) toShort() else this.toString().toShort()
        intType -> if (this is Number) toInt() else this.toString().toInt()
        longType -> if (this is Number) toLong() else this.toString().toLong()
        floatType -> if (this is Number) toFloat() else this.toString().toFloat()
        doubleType -> if (this is Number) toDouble() else this.toString().toDouble()
        boolType -> this as? Boolean ?: this.toString().toBoolean()
        charType -> this as? Char ?: this.toString()[0]
        stringType -> this.toString()
        else -> {
            if (type.isSupertypeOf(this::class.createType())) this else null
        }
    }
}

//@OptIn(ExperimentalStdlibApi::class)
fun JSONArray.toSingleParam(paramType: KType): Any? {
    if (this.length() == 0) {
        return null
    }
    if (paramType.isSubtypeOf(JSONArray::class.createType())) {
        return this
    }
    if (paramType.isSubtypeOf(collectionType) || paramType.jvmErasure.java.isArray) {
        return objectMapper.readValue(this.toString(), paramType.jvmErasure.java)
    }
    return objectMapper.readValue(this[0].toString(), paramType.jvmErasure.java)
}

fun JSONArray.simpleToType(paramType: KType): Any? {
    if (jsonArrayType.isSupertypeOf(paramType)) return this
    return objectMapper.readValue(this.toString(), paramType.jvmErasure.java)
}

fun JSONArray.simpleToType(index: Int, paramType: KType): Any? {
    if (jsonArrayType.isSupertypeOf(paramType)) return JSONObject(this[index].toString())
    if (jsonArrayType.withNullability(true).isSupertypeOf(paramType)) return if (this.isNull(index)) {
        null
    } else JSONObject(this[index].toString())
    return objectMapper.convertValue(this[index], paramType.jvmErasure.java)
}

fun JSONObject.simpleToType(paramType: KType): Any? {
    if (jsonObjectType.isSupertypeOf(paramType)) return this
    return objectMapper.readValue(this.toString(), paramType.jvmErasure.java)
}

fun JSONObject.simpleToType(key: String, paramType: KType): Any? {
    if (jsonObjectType.isSupertypeOf(paramType)) return JSONObject(this[key].toString())
    if (jsonObjectType.withNullability(true).isSupertypeOf(paramType)) return if (this.isNull(key)) {
        null
    } else JSONObject(this[key].toString())
    return objectMapper.readValue(this[key].toString(), paramType.jvmErasure.java)
}

fun KFunction<*>.callNative(app: Any, data: Any): Any? {
    val parameters = parameters
    val namedParamsCount = parameters.count { it.kind == KParameter.Kind.VALUE }
    var offset = 0
    val params = parameters.map {
        when (it.kind) {
            KParameter.Kind.INSTANCE -> {
                offset++
                app
            }

            KParameter.Kind.VALUE -> {
                if (namedParamsCount == 1) {
                    if (data is JSONArray) {
                        data.toSingleParam(it.type)
                    } else {
                        data.toType(it.type)
                    }
                } else {
                    when (data) {
                        is JSONArray -> {
                            data.simpleToType(it.index - offset, it.type)
                        }

                        is JSONObject -> {
                            data.simpleToType(it.name ?: "${it.index}", it.type)
                        }

                        else -> null
                    }
                }
            }

            else -> {
                null
            }
        }
    }
    val callResult = call(*params.toTypedArray())
    if (callResult == Unit) return null
    return callResult
}