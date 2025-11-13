package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SongVersion
import java.io.Serializable

data class KaraokePlatformPublication(
    val platform: KaraokePlatform,
    val songVersionName: String,
    val publicationId: String,
    val linkToPlay: String,
    val linkToEdit: String,
    val linkToCreate: String,
    val versionNumber: Int
) : Serializable, Comparable<KaraokePlatformPublication>{
    override fun compareTo(other: KaraokePlatformPublication): Int {
        TODO("Not yet implemented")
    }

    companion object {
        fun getList(settings: Settings): List<KaraokePlatformPublication> {
            val result: MutableList<KaraokePlatformPublication> = mutableListOf()
            val platforms = KaraokePlatform.getList()
            platforms.forEach { platform ->
                if (platform.forAllVersions) {
                    val songVersionName = "ALL"
                    val settingFieldId = platform.settingsFieldPublicationId[songVersionName]
                        ?: throw RuntimeException("В мапе settingsFieldPublicationId экземпляра класса KaraokePlatform указано значение '$songVersionName', для которого отсутствует экземпляр в классе SettingField")
                    val publicationId = settings.fields[settingFieldId] ?: ""
                    val linkToPlay = "${platform.prefixPlay}$publicationId${platform.suffixPlay}"
                    val linkToEdit = "${platform.prefixEdit}$publicationId${platform.suffixEdit}"
                    val linkToCreate = platform.linkToCreate
                    val versionNumber = if (!platform.haveVersionNumber) -1 else {
                        val settingFieldVersionNumber = platform.settingsFieldVersionNumber[songVersionName]
                            ?: throw RuntimeException("В мапе settingsFieldVersionNumber экземпляра класса KaraokePlatform указано значение '$songVersionName', для которого отсутствует экземпляр в классе SettingField")
                        (settings.fields[settingFieldVersionNumber]?.nullIfEmpty() ?: "0").toInt()
                    }
                    result.add(
                        KaraokePlatformPublication(
                            platform = platform,
                            songVersionName = songVersionName,
                            publicationId = publicationId,
                            linkToPlay = linkToPlay,
                            linkToEdit = linkToEdit,
                            linkToCreate = linkToCreate,
                            versionNumber = versionNumber
                        )
                    )
                } else {
                    SongVersion.entries.forEach { songVersion ->
                        val songVersionName = songVersion.name
                        val settingFieldId = platform.settingsFieldPublicationId[songVersionName]
                            ?: throw RuntimeException("В мапе settingsFieldPublicationId экземпляра класса KaraokePlatform указано значение '$songVersionName', для которого отсутствует экземпляр в классе SettingField")
                        val publicationId = settings.fields[settingFieldId] ?: ""
                        val linkToPlay = "${platform.prefixPlay}$publicationId${platform.suffixPlay}"
                        val linkToEdit = "${platform.prefixEdit}$publicationId${platform.suffixEdit}"
                        val linkToCreate = platform.linkToCreate
                        val versionNumber = if (!platform.haveVersionNumber) -1 else {
                            val settingFieldVersionNumber = platform.settingsFieldVersionNumber[songVersionName]
                                ?: throw RuntimeException("В мапе settingsFieldVersionNumber экземпляра класса KaraokePlatform указано значение '$songVersionName', для которого отсутствует экземпляр в классе SettingField")
                            (settings.fields[settingFieldVersionNumber]?.nullIfEmpty() ?: "0").toInt()
                        }

                        result.add(
                            KaraokePlatformPublication(
                                platform = platform,
                                songVersionName = songVersionName,
                                publicationId = publicationId,
                                linkToPlay = linkToPlay,
                                linkToEdit = linkToEdit,
                                linkToCreate = linkToCreate,
                                versionNumber = versionNumber
                            )
                        )

                    }

                }
            }
            return result
        }
    }
}