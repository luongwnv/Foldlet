package vn.lit.screenos

/** Notification identity/group metadata only. Contents stay in the visible view. */
internal class NotificationListPolicy {
    data class Item(val key: String,val packageName: String,val groupKey: String?,val summary: Boolean,val posted: Long)
    private val order=mutableListOf<String>()
    fun update(items: List<Item>): List<String> {
        val visible=visible(items)
        val keys=visible.map { it.key }.toSet()
        order.retainAll(keys)
        // Keep existing positions while open; append genuinely new cards.
        visible.sortedByDescending { it.posted }.forEach { if(it.key !in order) order.add(it.key) }
        return order.toList()
    }
    companion object {
        fun includeSource(packageName: String,tag: String?,channelId: String?,ownPackage: String): Boolean {
            if(packageName==ownPackage) return false
            // Android posts this after our application overlay becomes visible.
            // Including it makes opening the list add a card to the list itself.
            val ownOverlay="com.android.server.wm.AlertWindowNotification - $ownPackage"
            return packageName!="android" || (tag!=ownOverlay && channelId!=ownOverlay)
        }
        fun counts(items: List<Item>): Map<String,Int> = visible(items).groupingBy { it.packageName }.eachCount()
        fun visible(items: List<Item>): List<Item> {
            val unique=items.distinctBy { it.key }
            val childGroups=unique.filter { !it.summary && it.groupKey!=null }.map { it.packageName to it.groupKey }.toSet()
            return unique.filter { !it.summary || it.groupKey==null || (it.packageName to it.groupKey) !in childGroups }
        }
    }
}
