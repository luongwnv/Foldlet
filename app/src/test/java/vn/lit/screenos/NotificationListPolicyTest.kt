package vn.lit.screenos

import org.junit.Assert.*
import org.junit.Test

class NotificationListPolicyTest {
    private fun item(key: String,time: Long=1,summary: Boolean=false,pkg: String="routines",group: String?="group")=
        NotificationListPolicy.Item(key,pkg,group,summary,time)
    @Test fun summaryIsHiddenOnlyWhenItsChildrenAreAvailable() {
        val summary=item("summary",summary=true)
        assertEquals(listOf(summary),NotificationListPolicy.visible(listOf(summary)))
        val children=listOf(item("one"),item("two"))
        assertEquals(children,NotificationListPolicy.visible(children+summary))
    }
    @Test fun groupsDoNotCrossPackagesAndKeysAreUnique() {
        val child=item("child")
        val summary=item("summary",summary=true,pkg="other")
        assertEquals(listOf(child,summary),NotificationListPolicy.visible(listOf(child,child,summary)))
    }
    @Test fun badgesCountRealCardsPerPackageWithoutDuplicateSummaries() {
        val notifications=listOf(item("one"),item("two"),item("summary",summary=true),item("other",pkg="messages"),item("other",pkg="messages"))
        assertEquals(mapOf("routines" to 2,"messages" to 1),NotificationListPolicy.counts(notifications))
        assertEquals(mapOf("routines" to 1),NotificationListPolicy.counts(listOf(item("summary",summary=true))))
        assertTrue(NotificationListPolicy.counts(emptyList()).isEmpty())
    }
    @Test fun repostingAnExistingKeyDoesNotMoveItsCard() {
        val policy=NotificationListPolicy()
        assertEquals(listOf("one","two"),policy.update(listOf(item("one",20),item("two",10))))
        assertEquals(listOf("one","two"),policy.update(listOf(item("one",20),item("two",30))))
    }
    @Test fun newCardsAppendWhileOpenAndRemovedCardsStayRemoved() {
        val policy=NotificationListPolicy()
        policy.update(listOf(item("one",20),item("two",10)))
        val latest=listOf(item("one",20),item("two",10),item("new",30))
        assertEquals(listOf("one","two","new"),policy.update(latest))
        assertEquals(listOf("two","new"),policy.update(latest.filter { it.key!="one" }))
        assertEquals(listOf("new","one","two"),NotificationListPolicy().update(latest))
    }
}
