package com.autobanrobot.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockJobTest {
    @Test
    fun acceptsXUsernameShape() {
        assertTrue(BlockJob.isValidUsername("spam_account"))
        assertTrue(BlockJob.isValidUsername("a"))
    }

    @Test
    fun rejectsInvalidUsername() {
        assertFalse(BlockJob.isValidUsername("not valid"))
        assertFalse(BlockJob.isValidUsername("this_username_is_too_long"))
        assertFalse(BlockJob.isValidUsername("中文账号"))
    }
}
