package media.alexlab.fludremote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityBindingStatusTest {
    @Test fun connectedIsNotStale() {
        assertFalse(AccessibilityBindingStatus.requiresAction(true, true, 120000, 15000))
    }

    @Test fun disabledIsNotStale() {
        assertFalse(AccessibilityBindingStatus.requiresAction(false, false, 120000, 15000))
    }

    @Test fun initialBindingGetsGracePeriod() {
        assertFalse(AccessibilityBindingStatus.requiresAction(true, false, 14999, 15000))
    }

    @Test fun persistentMissingBindingRequiresUserAttention() {
        assertTrue(AccessibilityBindingStatus.requiresAction(true, false, 15000, 15000))
    }
}
