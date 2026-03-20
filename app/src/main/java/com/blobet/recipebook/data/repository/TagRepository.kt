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
        val existing = tagDao.getTagByName(name)
        if (existing != null) return existing.toDomainModel()

        val tag = Tag(name = name)
        val id = tagDao.insertTag(tag.toEntity())
        return tag.copy(id = id)
    }

    suspend fun deleteTag(id: Long) {
        tagDao.deleteTagById(id)
    }
}
