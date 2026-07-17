package com.example.notes.data

import com.example.notes.domain.ElementBounds
import com.example.notes.domain.PageElement
import org.junit.Assert.assertEquals
import org.junit.Test

class PageElementMapperTest {

    @Test
    fun `sticky element round trip`() {
        val sticky = PageElement.Sticky(
            id = "test-id",
            orderIndex = 1,
            bounds = ElementBounds(10f, 20f, 100f, 50f),
            text = "Hello Sticky",
            colorHex = "#FFFF00"
        )

        val pageId = "page-1"
        val entity = sticky.toEntity(pageId)
        
        assertEquals("test-id", entity.id)
        assertEquals(pageId, entity.pageId)
        assertEquals(ElementType.STICKY, entity.type)
        
        val domain = entity.toDomain() as PageElement.Sticky
        
        assertEquals(sticky.id, domain.id)
        assertEquals(sticky.orderIndex, domain.orderIndex)
        assertEquals(sticky.bounds, domain.bounds)
        assertEquals(sticky.text, domain.text)
        assertEquals(sticky.colorHex, domain.colorHex)
    }

    @Test
    fun `ink element round trip`() {
        val ink = PageElement.Ink(
            id = "ink-id",
            orderIndex = 2,
            bounds = ElementBounds(0f, 0f, 200f, 200f),
            points = emptyList(),
            colorHex = "#000000",
            brushWidth = 5f,
            recognizedText = null
        )

        val entity = ink.toEntity("page-1")
        val domain = entity.toDomain() as PageElement.Ink

        assertEquals(ink, domain)
    }
}
