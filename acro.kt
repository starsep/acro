#!/usr/bin/env kscript

@file:DependsOn("io.ktor:ktor-client-apache:1.4.3")
@file:DependsOn("io.ktor:ktor-client-gson:1.4.3")
@file:DependsOn("org.jetbrains.kotlin:kotlin-test:1.4.21")
@file:DependsOn("com.uchuhimo:konf:0.23.0")
@file:DependsOn("me.tongfei:progressbar:0.9.0")
@file:CompilerOpts("-jvm-target 1.8")

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import io.ktor.client.HttpClient
import io.ktor.client.features.*
import io.ktor.client.features.cookies.*
import io.ktor.client.features.json.*
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.runBlocking
import me.tongfei.progressbar.ProgressBar
import java.io.File
import java.util.*
import kotlin.math.*
import kotlin.test.assertEquals

const val CONFIG_FILENAME = "config.properties"

fun main(): Unit = runBlocking {
    tests()

    val config = Config { addSpec(CredentialsSpec) }.from.properties.file(File(CONFIG_FILENAME))
    val credentials = Credentials(phoneNumber = config[CredentialsSpec.phone], deviceNumber = config[CredentialsSpec.devicenum])

    val httpClient = HttpClient {
        install(JsonFeature)
        install(HttpCookies) {
            // Will keep an in-memory map with all the cookies from previous requests.
            storage = AcceptAllCookiesStorage()
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }
    with(AcroClient(httpClient)) {
        login(credentials)
        val bikePoints = mutableSetOf<GeoPoint>()
        for (point in ProgressBar.wrap(WarsawArea.points, "Warsaw Points")) {
            val bikesFromThisPoint = bikes(point)
            bikePoints.addAll(bikesFromThisPoint)
            // println("Found ${bikesFromThisPoint.size} bikes, total = ${bikePoints.size}")
        }
        val gson = GsonBuilder().setPrettyPrinting().create()
        File("docs", "bikes.json").writeText(gson.toJson(BikesOutput(bikePoints)))
    }
}

data class BikesOutput(val bikes: Set<GeoPoint>)

const val EARTH_RADIUS_IN_METRES = 6357000.0

fun Double.degreesToRadians() = this / 180 * Math.PI

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    fun distanceInMetres(other: GeoPoint): Double {
        val lat1 = other.latitude.degreesToRadians()
        val lng1 = other.longitude.degreesToRadians()
        val lat2 = latitude.degreesToRadians()
        val lng2 = longitude.degreesToRadians()

        val deltaLng = lng2 - lng1
        val deltaLat = lat2 - lat1

        val a = sin(deltaLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(deltaLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_IN_METRES * c
    }

    operator fun plus(other: GeoPoint) = GeoPoint(
        latitude = latitude + other.latitude,
        longitude = longitude + other.longitude,
    )
}

object WarsawArea {
    val center = GeoPoint(latitude = 52.217049, longitude = 21.017532)

    private const val latitudeDelta = 0.01
    private const val longitudeDelta = 0.01
    private val latitudeRange = -20..21
    private val longitudeRange = -20..21

    val points = latitudeRange.flatMap { latitude ->
        longitudeRange.map { longitude -> center + GeoPoint(latitude = latitude * latitudeDelta, longitude = longitude * longitudeDelta) }
    }
}

class AcroClient(private val httpClient: HttpClient) {
    data class LoginRequest(val method: String, val params: Credentials)
    suspend fun login(credentials: Credentials) {
        httpClient.get<Unit>(API_URL)
        httpClient.post<String>(API_URL) {
            body = LoginRequest(method = "UserLogin", params = credentials)
        }
    }

    data class BikesRequestParams(val longitude: String, val latitude: String, val timestamp: Long)
    data class BikesRequest(val method: String, val params: BikesRequestParams)
    data class Bike(
        @SerializedName("bikeno")
        val number: String,
        val latitude: Double,
        val longitude: Double,
        @SerializedName("bikeStatus")
        val status: Int,
        @SerializedName("bikeid")
        val id: Int,
        @SerializedName("lockno")
        val lockNumber: String,
    ) {
        fun toGeoPoint() = GeoPoint(latitude = latitude, longitude = longitude)
    }
    data class BikesResponseInfo(
        @SerializedName("aroundstations") val stations: List<String>,
        @SerializedName("aroundbikes") val bikes: List<Bike>,
    )
    data class BikesResponse(val result: Int, val info: BikesResponseInfo)
    suspend fun bikes(point: GeoPoint): Set<GeoPoint> {
        val response = httpClient.post<BikesResponse>(API_URL) {
            body = BikesRequest(method = "GetBike", params = BikesRequestParams(
                longitude = point.longitude.toString(),
                latitude = point.latitude.toString(),
                timestamp = Date().time,
            ))
        }
        return response.info.bikes.map { it.toGeoPoint() }.toSet()
    }

    companion object {
        const val API_URL = "https://api.acro.bike/cross/ab_mapp"
    }
}

data class Credentials(
    @SerializedName("phone")
    val phoneNumber: String,
    @SerializedName("devicenum")
    val deviceNumber: String,
)

fun tests() {
    val centerOfCracow = GeoPoint(latitude = 50.0470, longitude = 20.0048)
    assertEquals(250873.9028837086, WarsawArea.center.distanceInMetres(centerOfCracow))
    assertEquals(GeoPoint(43.0, 52.0), GeoPoint(1.0, 2.0).plus(GeoPoint(42.0, 50.0)))
}

object CredentialsSpec : ConfigSpec(prefix = "credentials") {
    val phone by required<String>()
    val devicenum by required<String>()
}