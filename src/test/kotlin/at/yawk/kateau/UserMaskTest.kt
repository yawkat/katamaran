package at.yawk.kateau

import com.fasterxml.jackson.databind.ObjectMapper
import org.testng.Assert
import org.testng.annotations.Test

/**
 * @author yawkat
 */
class UserMaskTest {
    @Test
    fun `parse basic`() = Assert.assertEquals(
            UserMask.parse("kitty!yawkat@cats.coffee"),
            UserMask("kitty", "yawkat", "cats.coffee")
    )

    @Test
    fun `parse wildcard nick`() = Assert.assertEquals(
            UserMask.parse("*!yawkat@cats.coffee"),
            UserMask(null, "yawkat", "cats.coffee")
    )

    @Test
    fun `parse wildcard user`() = Assert.assertEquals(
            UserMask.parse("kitty!*@cats.coffee"),
            UserMask("kitty", null, "cats.coffee")
    )

    @Test
    fun `parse wildcard host`() = Assert.assertEquals(
            UserMask.parse("kitty!yawkat@*"),
            UserMask("kitty", "yawkat", null)
    )

    @Test
    fun deserialize() = Assert.assertEquals(
            ObjectMapper().findAndRegisterModules()
                    .readValue("\"kitty!yawkat@cats.coffee\"", UserMask::class.java),
            UserMask("kitty", "yawkat", "cats.coffee")
    )
}