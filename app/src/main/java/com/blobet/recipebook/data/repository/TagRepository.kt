package com.blobet.recipebook.data.repository

import com.blobet.recipebook.data.local.dao.TagDao
import com.blobet.recipebook.data.local.entity.toDomainModel
import com.blobet.recipebook.data.local.entity.toEntity
import com.blobet.recipebook.domain.model.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepository @Inject constructor(
    private val tagDao: TagDao,
) {
    fun getAllTags(): Flow<List<Tag>> {
        return tagDao.getAllTags().map { list ->
            list.map { it.toDomainModel() }
        }
    }

    suspend fun getAllTagsOnce(): List<Tag> {
        return tagDao.getAllTagsOnce().map { it.toDomainModel() }
    }

    suspend fun createTag(name: String): Tag {
        val normalizedName = normalizeTagKey(name)
        require(normalizedName.isNotBlank()) { "Tag name cannot be blank" }

        val existingByQuery = tagDao.getTagByName(name.trim())
        if (existingByQuery != null) return existingByQuery.toDomainModel()

        val existingByNormalizedKey = tagDao.getAllTagsOnce()
            .firstOrNull { entity -> normalizeTagKey(entity.name) == normalizedName }
        if (existingByNormalizedKey != null) return existingByNormalizedKey.toDomainModel()

        val canonicalName = name.trim().replace(Regex("\\s+"), " ")
        val existing = tagDao.getTagByName(canonicalName)
        if (existing != null) return existing.toDomainModel()

        val tag = Tag(name = canonicalName)
        val id = tagDao.insertTag(tag.toEntity())
        return tag.copy(id = id)
    }

    private fun normalizeTagKey(value: String): String {
        return value.trim().replace(Regex("\\s+"), " ").lowercase()
    }

    suspend fun deleteTag(id: Long) {
        tagDao.deleteTagById(id)
    }
}
