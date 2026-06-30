package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.BoardEntity
import com.example.domain.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class BoardRepository(private val context: Context, private val database: AppDatabase) {
    private val dao = database.dao()
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    fun getAllBoards(): Flow<List<Board>> {
        return dao.getAllBoardsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getBoardById(id: String): Board? {
        return dao.getBoardById(id)?.toDomain()
    }

    suspend fun createBoard(title: String, template: BoardTemplate, isPaged: Boolean): Board = withContext(Dispatchers.IO) {
        val board = Board(
            id = UUID.randomUUID().toString(),
            title = title,
            thumbnailUri = null,
            categoryId = null,
            createdAt = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis(),
            templateType = template,
            isPaged = isPaged
        )
        dao.insertBoard(BoardEntity.fromDomain(board))
        board
    }

    suspend fun saveBoardContent(boardId: String, strokes: List<BoardStroke>, elements: List<BoardElement>) = withContext(Dispatchers.IO) {
        val content = BoardContent(strokes, elements)
        val adapter = moshi.adapter(BoardContent::class.java)
        val json = adapter.toJson(content)
        val file = File(context.filesDir, "board_$boardId.json")
        file.writeText(json)
        
        // Update last modified
        val entity = dao.getBoardById(boardId)
        if (entity != null) {
            dao.insertBoard(entity.copy(lastModified = System.currentTimeMillis()))
        }
    }

    suspend fun loadBoardContent(boardId: String): Pair<List<BoardStroke>, List<BoardElement>> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "board_$boardId.json")
        if (!file.exists()) return@withContext emptyList<BoardStroke>() to emptyList<BoardElement>()
        
        try {
            val json = file.readText()
            val adapter = moshi.adapter(BoardContent::class.java)
            val content = adapter.fromJson(json)
            if (content != null) {
                content.strokes to content.elements
            } else {
                emptyList<BoardStroke>() to emptyList<BoardElement>()
            }
        } catch (e: Exception) {
            emptyList<BoardStroke>() to emptyList<BoardElement>()
        }
    }

    suspend fun deleteBoard(boardId: String) = withContext(Dispatchers.IO) {
        dao.deleteBoardById(boardId)
        val file = File(context.filesDir, "board_$boardId.json")
        if (file.exists()) file.delete()
    }

    data class BoardContent(
        val strokes: List<BoardStroke>,
        val elements: List<BoardElement>
    )
}
