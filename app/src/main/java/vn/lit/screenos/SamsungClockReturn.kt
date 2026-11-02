package vn.lit.screenos

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display

/** Keep Foldlet covering Samsung until the clock behind it has settled. */
class SamsungClockReturn(
    private val owner: CoverMonitorService,
    private val accessibility: NavigationService,
    private val display: Display,
    private val complete: (Result)->Unit,
) {
    enum class Result { CLOCK, HIDDEN, UNAVAILABLE }
    private val handler=Handler(Looper.getMainLooper())
    private var stopped=false
    private var deadline=0L
    private var confirmedAt=0L
    private var moved=false
    private val poll=object: Runnable {
        override fun run() {
            if(stopped || CoverMonitorService.current!==owner) return
            if(display.state!=Display.STATE_ON || owner.foldedForTaskControl()==false || !Platform.unlocked(owner)) {
                finish(Result.HIDDEN);return
            }
            val now=SystemClock.uptimeMillis()
            if(SamsungCoverPage.isClock(accessibility,display.displayId)) {
                if(confirmedAt==0L) confirmedAt=now
                // Leave several compositor frames for Samsung's clock buffer.
                if(now-confirmedAt>=160) { finish(Result.CLOCK);return }
            } else {
                confirmedAt=0
                if(SamsungCoverPage.isNotifications && !moved)
                    moved=SamsungCoverPage.showClock(accessibility,display.displayId)
            }
            if(now>=deadline) { finish(Result.UNAVAILABLE);return }
            handler.postDelayed(this,100)
        }
    }
    fun start() {
        deadline=SystemClock.uptimeMillis()+3000
        if(!Platform.samsungHome(owner,display.displayId)) { finish(Result.UNAVAILABLE);return }
        handler.postDelayed(poll,80)
    }
    fun cancel() { stopped=true;handler.removeCallbacksAndMessages(null) }
    private fun finish(result: Result) {
        if(stopped) return
        cancel();complete(result)
    }
}
