package app.nama.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shape 30nama's own desktop app reads; not yet checked against a live answer. */
class NotificationTests {
    @Test fun testReadsTheShapeTheOfficialAppReads() {
        var page = NotificationPage.from(parse("""
            {"notifications":[
              {"id":12,"title":"قسمت تازه","content":"<p>اپیزود 4 <b>فصل 2</b><br>اضافه شد</p>","read":false,"notification_type":"post",
               "post":{"id":469773,"title":{"english":"Link Click 2021"}},"date":{"g":"2026-10-16 10:00:00","j":"۱۴۰۵-۰۷-۲۴"}},
              {"id":"13","title":"Offer","notification_type":"pricing","read":1,"date":"2026-10-15"},
              {"title":"no id"}],
             "total_unread":1,"page":1}
            """))
        assertEquals("one without an id is no notification", 2, page.items.size)
        assertEquals(1, page.unread)
        val first = page.items[0]
        assertEquals("12", first.id)
        assertEquals("the HTML it comes with is taken out", "اپیزود 4 فصل 2\nاضافه شد", first.content)
        assertEquals(AccountNotification.Kind.post, first.kind)
        assertEquals(469773, first.post?.id)
        assertEquals("۱۴۰۵-۰۷-۲۴", first.date.jalali)
        assertTrue(page.items[1].read)
        assertEquals("2026-10-15", page.items[1].date.gregorian)
        assertEquals(AccountNotification.Kind.pricing, page.items[1].kind)

        page = page.markRead("12")
        assertEquals(0, page.unread)
    }
}
