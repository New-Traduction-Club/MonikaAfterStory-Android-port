package org.renpy.android

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import java.net.URL

@Parcelize
data class PackageInfo(
    val version: String,
    val download_url: String,
    val sha256: String,
    val min_app_version: Int
) : Parcelable

object PackageHelper {
    fun fetchPackages(url: String): List<PackageInfo> {
        return try {
            val response = URL(url).readText()
            val jsonObject = JSONObject(response)
            val packagesArray = jsonObject.getJSONArray("packages")
            val list = mutableListOf<PackageInfo>()
            for (i in 0 until packagesArray.length()) {
                val pkg = packagesArray.getJSONObject(i)
                list.add(PackageInfo(
                    version = pkg.getString("version"),
                    download_url = pkg.getString("download_url"),
                    sha256 = pkg.getString("sha256"),
                    min_app_version = pkg.getInt("min_app_version")
                ))
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
