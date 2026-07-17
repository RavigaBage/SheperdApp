package com.example.notes.data

import androidx.room.*
import com.example.data.local.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "sermon_templates",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("categoryId")]
)
data class SermonTemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val categoryId: String?,
    val scriptureReference: String?,
    val summary: String,
    val tags: String?,
    val isAiSuggested: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Dao
interface SermonTemplateDao {
    @Query("SELECT * FROM sermon_templates ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SermonTemplateEntity>>

    @Query("SELECT * FROM sermon_templates WHERE categoryId = :categoryId ORDER BY updatedAt DESC")
    fun observeByCategory(categoryId: String): Flow<List<SermonTemplateEntity>>

    @Query("""
        SELECT * FROM sermon_templates 
        WHERE title LIKE '%' || :query || '%' 
        OR summary LIKE '%' || :query || '%' 
        OR tags LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
    """)
    fun searchByKeyword(query: String): Flow<List<SermonTemplateEntity>>

    @Query("SELECT * FROM sermon_templates WHERE id = :id")
    suspend fun getById(id: String): SermonTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: SermonTemplateEntity)

    @Delete
    suspend fun delete(template: SermonTemplateEntity)
}
