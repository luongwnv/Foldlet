package vn.lit.screenos

import org.junit.Assert.*
import org.junit.Test

class CoverInputSessionTest {
    private val text=CoverInputSession.Editor("example.editor",10,null,1)
    @Test fun restartingSameEditorPreservesDismissalAndKeyboardTarget() {
        val session=CoverInputSession()
        session.start(text,false)
        val dismissed=session.generation
        repeat(10) { session.start(text,true) }
        assertEquals(dismissed,session.generation)
    }
    @Test fun changingFieldOrInputTypeInvalidatesOldTargetEvenOnRestart() {
        val session=CoverInputSession()
        session.start(text,false)
        val first=session.generation
        session.start(text.copy(fieldId=11),true)
        assertNotEquals(first,session.generation)
        val second=session.generation
        session.start(text.copy(fieldId=11,inputType=2),true)
        assertNotEquals(second,session.generation)
    }
    @Test fun NewInputAndFinishInvalidateTheOldTarget() {
        val session=CoverInputSession()
        session.start(text,false)
        val first=session.generation
        session.start(text,false)
        assertNotEquals(first,session.generation)
        val second=session.generation
        session.finish();session.start(text,true)
        assertNotEquals(second,session.generation)
    }
    @Test fun nativeNumberAndDialPadsNeverAutoOpenFoldletKeyboard() {
        for(type in listOf(0,2,3,4,0x2002,0x12))
            assertFalse(CoverKeyboardPolicy.allowsAutomatic("example.app",type))
        assertFalse(CoverKeyboardPolicy.allowsAutomatic("com.sec.android.app.popupcalculator",0x20001))
    }
    @Test fun textSearchEmailAndPasswordEditorsRemainEligible() {
        for(type in listOf(1,0x20001,0x21,0x81))
            assertTrue(CoverKeyboardPolicy.allowsAutomatic("example.app",type))
        assertTrue(CoverKeyboardPolicy.allowsAutomatic("com.samsung.android.dialer",1))
    }
}
