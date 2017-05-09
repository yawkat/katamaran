package at.yawk.katamaran

import com.fasterxml.jackson.annotation.JsonCreator
import org.kitteh.irc.client.library.element.User
import java.util.regex.Pattern

/**
 * @author yawkat
 */
data class Config(
        val servers: List<Server>
)

data class Server(
        val host: String,
        val port: Int,
        val secure: Boolean = false,
        val password: String?,

        val channels: Map<String, ChannelConfig>
)

data class ChannelConfig(
        val ops: List<UserMask>,
        val persistInvite: Boolean
)

